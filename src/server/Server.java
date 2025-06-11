package server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;

public class Server {
    private static final List<Client> clients = new ArrayList<>();
    private static final Set<String> usernames = new HashSet<>();
    private static int PORT = 8000;
    private static final ReentrantLock lock = new ReentrantLock();
    static final Logger logger = Logger.getLogger(Server.class.getName());
    private static String DB_URL, DB_USER, DB_PASSWORD;

    static {
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            private final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";

            public synchronized String format(LogRecord lr) {
                return String.format(format,
                        new Date(lr.getMillis()),
                        lr.getLevel(),
                        lr.getMessage()
                );
            }
        });
        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false);

        // 获取当前工作目录
        String currentDir = System.getProperty("user.dir");
        File configFile = new File(currentDir, "server.properties");

        try (InputStream input = new FileInputStream(configFile)) {
            Properties prop = new Properties();
            prop.load(input);
            PORT = Integer.parseInt(prop.getProperty("port", "8000"));
            DB_URL = prop.getProperty("db.url");
            DB_USER = prop.getProperty("db.username");
            DB_PASSWORD = prop.getProperty("db.password");

            if (DB_URL == null || DB_USER == null || DB_PASSWORD == null) {
                throw new IOException("缺少数据库配置项");
            }

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
        private DataInputStream in;
        private DataOutputStream out;
        private String username;

        public Client(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                username = in.readUTF();
                logger.info("接收到用户名：" + username);

                if (usernames.contains(username)) {
                    out.writeUTF("[ERROR] 用户名已存在！");
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

                String message;
                while ((message = in.readUTF()) != null && !message.isEmpty()) {
                    logger.info("收到消息：[" + username + "]：" + message);

                    if (message.startsWith("/msg ")) {
                        String[] parts = message.split(" ", 3);
                        if (parts.length == 3) {
                            String target = parts[1];
                            String content = parts[2];
                            sendPrivateMessage(target, content, "user");
                        }
                    } else {
                        broadcastMessage("[" + username + "]：" + message, "user");
                    }
                }
            } catch (IOException e) {
                logger.log(Level.FINE, "客户端断开连接", e);
            } finally {
                disconnect();
            }
        }

        private void sendPrivateMessage(String target, String message, String log_level) {
            lock.lock();
            try {
                for (Client client : clients) {
                    if (client.username.equals(target)) {
                        try {
                            String msg = "[PRIVATE]" + username + " 私信：" + message;
                            client.out.writeUTF(msg);
                            client.out.flush();
                            ChatLogDAO.logMessage(this.username, target, message, log_level);
                            return;
                        } catch (IOException ignored) {}
                    }
                }
                try {
                    out.writeUTF("[系统消息] 用户 '" + target + "' 不存在或不在线");
                    out.flush();
                } catch (IOException ignored) {}
            } finally {
                lock.unlock();
            }
        }

        private void broadcastMessage(String msg, String log_level) {
            lock.lock();
            try {
                for (Client client : clients) {
                    try {
                        client.out.writeUTF(msg);
                        client.out.flush();
                    } catch (IOException ignored) {}
                }
                ChatLogDAO.logMessage(this.username, null, msg, log_level);
            } finally {
                lock.unlock();
            }
        }

        private void broadcastUserList() {
            StringBuilder sb = new StringBuilder("[USER_LIST]");
            lock.lock();
            try {
                for (Client client : clients) {
                    sb.append(client.username).append(",");
                }
            } finally {
                lock.unlock();
            }

            if (sb.length() > "[USER_LIST]".length()) {
                sb.setLength(sb.length() - 1);
            }

            lock.lock();
            try {
                for (Client client : clients) {
                    try {
                        client.out.writeUTF(sb.toString());
                    } catch (IOException ignored) {}
                }
            } finally {
                lock.unlock();
            }
        }

        private void sendRecentChatHistory() {
            List<String> history = ChatLogDAO.getRecentChatHistory(50);
            for (String msg : history) {
                try {
                    out.writeUTF("[HISTORY]" + msg);
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