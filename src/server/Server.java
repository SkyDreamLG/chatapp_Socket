// src/server/Server.java
package server;

import shared.Message;

import java.io.*;
import java.net.*;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;

public class Server {
    private static final List<Client> clients = new ArrayList<>();
    private static final Set<String> usernames = new HashSet<>();
    private static int PORT;
    private static final ReentrantLock lock = new ReentrantLock();
    static final Logger logger = Logger.getLogger(Server.class.getName());

    static {
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            public synchronized String format(LogRecord lr) {
                return String.format("[%1$tF %1$tT] [%2$-7s] %3$s %n",
                        new Date(lr.getMillis()), lr.getLevel(), lr.getMessage());
            }
        });
        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false);

        try (InputStream input = new FileInputStream("server.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            PORT = Integer.parseInt(prop.getProperty("port", "8000"));
            String DB_URL = prop.getProperty("db.url");
            String DB_USER = prop.getProperty("db.username");
            String DB_PASSWORD = prop.getProperty("db.password");

            if (DB_URL == null || DB_USER == null || DB_PASSWORD == null)
                throw new IOException("缺少数据库配置项");

            DBUtil.init(DB_URL, DB_USER, DB_PASSWORD);
            logger.info("数据库连接池初始化成功");
        } catch (IOException e) {
            logger.warning("加载配置失败，使用默认端口 8000");
            PORT = 8000;
        }
    }

    public static void main(String[] args) {
        logger.info("服务器正在启动... 监听端口: " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("服务器已启动并监听于端口: " + PORT);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                DBUtil.close();
                logger.info("数据库连接池已关闭");
            }));

            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new Client(socket)).start();
                logger.info("接受了一个新的连接请求");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "服务器启动失败", e);
        }
    }

    private static class Client implements Runnable {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String username;

        public Client(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new ObjectInputStream(socket.getInputStream());
                out = new ObjectOutputStream(socket.getOutputStream());

                username = (String) in.readObject();

                if (usernames.contains(username)) {
                    out.writeObject(Message.system("[ERROR] 用户名已存在！"));
                    out.flush();
                    disconnect();
                    return;
                }

                lock.lock();
                try {
                    usernames.add(username);
                    clients.add(this);
                } finally {
                    lock.unlock();
                }

                broadcastMessage(username + " 进入了聊天室", "system");
                broadcastUserList();
                sendRecentChatHistory();

                Message message;
                while ((message = (Message) in.readObject()) != null) {
                    if ("chat".equals(message.type)) {
                        String content = (String) message.data.get("content");
                        broadcastMessage("[" + username + "]：" + content, "user");
                    } else if ("private".equals(message.type)) {
                        String target = (String) message.data.get("to");
                        String content = (String) message.data.get("content");
                        sendPrivateMessage(target, content);
                    }
                }
            } catch (Exception e) {
                logger.log(Level.FINE, "客户端断开连接", e);
            } finally {
                disconnect();
            }
        }

        private void sendPrivateMessage(String target, String message) {
            Message msg = Message.privateMsg(username, target, message);
            lock.lock();
            try {
                for (Client client : clients) {
                    if (client.username.equals(target)) {
                        try {
                            client.out.writeObject(msg);
                            client.out.flush();
                            ChatLogDAO.logMessage(this.username, target, message, "user");
                            return;
                        } catch (IOException ignored) {}
                    }
                }
                try {
                    out.writeObject(Message.system("用户 '" + target + "' 不存在或不在线"));
                    out.flush();
                } catch (IOException ignored) {}
            } finally {
                lock.unlock();
            }
        }

        private void broadcastMessage(String msg, String log_level) {
            Message message = Message.chat(username, msg);
            lock.lock();
            try {
                for (Client client : clients) {
                    try {
                        client.out.writeObject(message);
                        client.out.flush();
                    } catch (IOException ignored) {}
                }
                ChatLogDAO.logMessage(this.username, null, msg, log_level);
            } finally {
                lock.unlock();
            }
        }

        private void broadcastUserList() {
            Map<String, Object> userMap = new HashMap<>();
            lock.lock();
            try {
                for (Client client : clients) {
                    userMap.put(client.username, true);
                }
            } finally {
                lock.unlock();
            }

            Message msg = Message.userList(userMap);
            lock.lock();
            try {
                for (Client client : clients) {
                    try {
                        client.out.writeObject(msg);
                        client.out.flush();
                    } catch (IOException ignored) {}
                }
            } finally {
                lock.unlock();
            }
        }

        private void sendRecentChatHistory() {
            List<String> history = ChatLogDAO.getRecentChatHistory(50, username);
            for (String msg : history) {
                try {
                    out.writeObject(Message.history(msg));
                    out.flush();
                } catch (IOException e) {
                    logger.log(Level.FINE, "发送历史消息失败", e);
                }
            }
        }

        private void disconnect() {
            if (username != null) {
                lock.lock();
                try {
                    usernames.remove(username);
                    clients.remove(this);
                } finally {
                    lock.unlock();
                }
                broadcastMessage(username + " 离开了聊天室", "system");
                broadcastUserList();
            }

            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}