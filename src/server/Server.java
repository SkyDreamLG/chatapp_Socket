// src/server/Server.java
package server;

import shared.Message;

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;
import javax.net.ssl.*;

public class Server {
    private static final List<Client> clients = new ArrayList<>();
    private static final Set<String> usernames = new HashSet<>();
    private static int PORT;
    private static final ReentrantLock lock = new ReentrantLock();
    static final Logger logger = Logger.getLogger(Server.class.getName());

    // 新增的SSL相关字段
    private static SSLContext sslContext = null;

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

        try {
            // 读取 server.properties
            Properties prop = new Properties();
            try (InputStream input = new FileInputStream("server.properties")) {
                prop.load(input);
            }

            PORT = Integer.parseInt(prop.getProperty("port", "8000"));

            // 获取SSL配置
            String keyStorePassword = prop.getProperty("ssl.keypassword");

            if (keyStorePassword != null && !keyStorePassword.isEmpty()) {
                // 固定使用当前目录下的 keystore.p12
                File keyStoreFile = new File("keystore.p12");
                if (!keyStoreFile.exists()) {
                    logger.severe("找不到证书文件: " + keyStoreFile.getAbsolutePath());
                    throw new IOException("证书文件不存在");
                }

                // 初始化密钥库
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (InputStream ksIs = new FileInputStream(keyStoreFile)) {
                    keyStore.load(ksIs, keyStorePassword.toCharArray());
                }

                // 初始化KeyManagerFactory
                KeyManagerFactory kmf = KeyManagerFactory
                        .getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, keyStorePassword.toCharArray());

                // 初始化SSLContext
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(kmf.getKeyManagers(), null, null);

                logger.info("SSL上下文初始化成功，证书路径: " + keyStoreFile.getAbsolutePath());
            } else {
                logger.warning("缺少SSL密码配置，服务器将不使用加密通信");
            }

            // 数据库配置加载（略去部分细节）
            String DB_URL = prop.getProperty("db.url");
            String DB_USER = prop.getProperty("db.username");
            String DB_PASSWORD = prop.getProperty("db.password");

            if (DB_URL == null || DB_USER == null || DB_PASSWORD == null)
                throw new IOException("缺少数据库配置项");

            DBUtil.init(DB_URL, DB_USER, DB_PASSWORD);
            logger.info("数据库连接池初始化成功");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "初始化失败", e);
            PORT = 8000;
        }
    }

    public static void main(String[] args) {
        logger.info("服务器正在启动... 监听端口: " + PORT);

        try (SSLServerSocket serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(PORT)) {
            serverSocket.setNeedClientAuth(false);
            serverSocket.setWantClientAuth(false);

            logger.info("服务器已启动并监听于端口: " + PORT);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                DBUtil.close();
                logger.info("数据库连接池已关闭");
            }));

            while (true) {
                SSLSocket socket = (SSLSocket) serverSocket.accept();
                new Thread(new Client(socket)).start();
                logger.info("接受了一个新的连接请求");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "服务器启动失败", e);
        }
    }

    private static class Client implements Runnable {
        private final SSLSocket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String username;

        public Client(SSLSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(30000); // 30秒超时

                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

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