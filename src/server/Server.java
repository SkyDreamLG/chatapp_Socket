package server;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;

public class Server {
    private static final List<ClientHandler> clients = new ArrayList<>();
    private static final Set<String> usernames = new HashSet<>();
    private static int PORT;
    private static final ReentrantLock lock = new ReentrantLock();
    private static final Logger logger = Logger.getLogger(Server.class.getName());

    static {
        // 设置日志格式
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            private static final String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";

            @Override
            public synchronized String format(LogRecord lr) {
                return String.format(format,
                        new Date(lr.getMillis()),
                        lr.getLevel(),
                        lr.getMessage()
                );
            }
        });

        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false); // 禁用父处理器以避免重复输出

        try (InputStream input = new FileInputStream("server.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            PORT = Integer.parseInt(prop.getProperty("port", "8000")); // 默认8000
            logger.info("加载配置文件成功");
        } catch (IOException | NumberFormatException ex) {
            logger.warning("加载配置失败，使用默认端口 8000");
            PORT = 8000;
        }
    }

    public static void main(String[] args) {
        logger.info("服务器正在启动... 监听端口: " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("服务器已启动并监听于端口: " + PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket)).start();
                logger.info("接受了一个新的连接请求");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "服务器启动失败", e);
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream in;
        private DataOutputStream out;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                // 接收用户名
                username = in.readUTF();
                logger.info("接收到用户名：" + username);

                if (usernames.contains(username)) {
                    out.writeUTF("[ERROR] 用户名已存在！");
                    out.flush();
                    disconnect();
                    logger.warning("用户名冲突：" + username);
                    return;
                }

                lock.lock();
                try {
                    usernames.add(username);
                    clients.add(this);
                } finally {
                    lock.unlock();
                }

                broadcastMessage(username + " 进入了聊天室");
                logger.info(username + " 加入了聊天室");
                broadcastUserList();

                String message;
                while ((message = in.readUTF()) != null && !message.isEmpty()) {
                    logger.info("收到消息：[" + username + "]：" + message);

                    if (message.startsWith("/msg ")) {
                        // 处理私信命令
                        String[] parts = message.split(" ", 3); // /msg user msg
                        if (parts.length == 3) {
                            String targetUser = parts[1];
                            String privateMsg = parts[2];
                            sendPrivateMessage(targetUser, privateMsg);
                        }
                    } else {
                        // 正常广播消息
                        broadcastMessage("[" + username + "]：" + message);
                    }
                }
            } catch (IOException e) {
                logger.log(Level.FINE, "客户端断开连接", e);
            } finally {
                disconnect();
            }
        }

        /**
         * 发送私信给指定用户
         */
        private void sendPrivateMessage(String targetUsername, String message) {
            lock.lock();
            try {
                for (ClientHandler client : clients) {
                    if (client.username.equals(targetUsername)) {
                        try {
                            client.out.writeUTF("[PRIVATE]" + username + " 私信：" + message);
                            client.out.flush();
                            logger.info("私信已发送 [" + username + " -> " + targetUsername + "]：" + message);
                            return;
                        } catch (IOException ignored) {
                            logger.log(Level.FINE, "私信发送失败", ignored);
                        }
                    }
                }
                // 如果目标不存在，可选择性地回传错误信息给发送者
                try {
                    out.writeUTF("[系统消息] 用户 '" + targetUsername + "' 不存在或不在线");
                    out.flush();
                } catch (IOException ignored) {}
            } finally {
                lock.unlock();
            }
        }

        private void broadcastMessage(String msg) {
            lock.lock();
            try {
                for (ClientHandler client : clients) {
                    try {
                        client.out.writeUTF(msg);
                        client.out.flush();
                    } catch (IOException ignored) {
                        logger.log(Level.FINE, "发送消息失败", ignored);
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        private void broadcastUserList() {
            StringBuilder userList = new StringBuilder("[USER_LIST]");
            lock.lock();
            try {
                for (ClientHandler client : clients) {
                    userList.append(client.username).append(",");
                }
            } finally {
                lock.unlock();
            }

            if (userList.length() > "[USER_LIST]".length()) {
                userList.setLength(userList.length() - 1); // 移除最后逗号
            }

            lock.lock();
            try {
                for (ClientHandler client : clients) {
                    try {
                        client.out.writeUTF(userList.toString());
                    } catch (IOException ignored) {
                        logger.log(Level.FINE, "发送用户列表失败", ignored);
                    }
                }
            } finally {
                lock.unlock();
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
                broadcastMessage(username + " 离开了聊天室");
                logger.info(username + " 离开了聊天室");
                broadcastUserList();
            }

            try {
                socket.close();
            } catch (IOException ignored) {
                logger.log(Level.FINE, "关闭socket失败", ignored);
            }
        }
    }
}