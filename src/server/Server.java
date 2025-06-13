// Server.java 位于 server 包中
package server;

// 引入必要的类库
import shared.Message; // 公共的消息类，用于客户端与服务器之间通信

import java.io.*;
import java.security.KeyStore;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;
import javax.net.ssl.*;

/**
 * Server 类是聊天服务器的核心类。
 * 它的主要功能包括：
 * - 启动服务器并监听指定端口
 * - 管理客户端连接（登录、断开等）
 * - 接收客户端发送的消息
 * - 广播消息给所有在线用户
 * - 支持私聊和群发
 * - 使用 SSL/TLS 加密通信
 * - 记录聊天日志到数据库
 */
public class Server {
    // 存储当前连接的所有客户端对象
    private static final List<Client> clients = new ArrayList<>();
    // 所有已登录用户的用户名集合（避免重复）
    private static final Set<String> usernames = new HashSet<>();
    // 服务器监听的端口号
    private static int PORT;
    // 多线程安全锁，防止并发问题
    private static final ReentrantLock lock = new ReentrantLock();
    // 日志记录器，用于输出运行信息和错误信息
    static final Logger logger = Logger.getLogger(Server.class.getName());

    // 新增的SSL相关字段
    private static SSLContext sslContext = null;

    /*
      静态代码块：在类加载时执行，用于初始化服务器配置。
      包括：
      - 设置日志格式
      - 读取 server.properties 配置文件
      - 初始化数据库连接池
      - 初始化 SSL 上下文（如果启用了加密通信）
     */
    static {
        // 设置控制台日志输出格式
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {
            public synchronized String format(LogRecord lr) {
                return String.format("[%1$tF %1$tT] [%2$-7s] %3$s %n",
                        new Date(lr.getMillis()), lr.getLevel(), lr.getMessage());
            }
        });
        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false); // 不使用默认的日志处理器

        try {
            // 读取 server.properties 配置文件
            Properties prop = new Properties();
            try (InputStream input = new FileInputStream("server.properties")) {
                prop.load(input);
            }

            // 获取服务器监听端口，默认8000
            PORT = Integer.parseInt(prop.getProperty("port", "8000"));

            // 获取SSL证书密码
            String keyStorePassword = prop.getProperty("ssl.keypassword");

            if (keyStorePassword != null && !keyStorePassword.isEmpty()) {
                File keyStoreFile = new File("keystore.p12");
                if (!keyStoreFile.exists()) {
                    logger.severe("找不到证书文件: " + keyStoreFile.getAbsolutePath());
                    throw new IOException("证书文件不存在");
                }

                // 加载密钥库
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

            // 读取数据库配置
            String DB_URL = prop.getProperty("db.url");
            String DB_USER = prop.getProperty("db.username");
            String DB_PASSWORD = prop.getProperty("db.password");

            if (DB_URL == null || DB_USER == null || DB_PASSWORD == null)
                throw new IOException("缺少数据库配置项");

            // 初始化数据库连接池
            DBUtil.init(DB_URL, DB_USER, DB_PASSWORD);
            logger.info("数据库连接池初始化成功");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "初始化失败", e);
            PORT = 8000; // 如果出错，使用默认端口8000
        }
    }

    /**
     * 主函数：程序入口点。
     * 启动服务器，开始监听客户端连接。
     */
    public static void main(String[] args) {
        logger.info("服务器正在启动... 监听端口: " + PORT);

        try (
                // 创建SSL服务器Socket，绑定端口
                SSLServerSocket serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(PORT)
        ) {
            serverSocket.setNeedClientAuth(false);
            serverSocket.setWantClientAuth(false);

            logger.info("服务器已启动并监听于端口: " + PORT);

            // 添加关闭钩子，在JVM退出时释放资源
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                DBUtil.close();
                logger.info("数据库连接池已关闭");
            }));

            // 持续接受新连接
            while (true) {
                SSLSocket socket = (SSLSocket) serverSocket.accept();
                new Thread(new Client(socket)).start(); // 为每个客户端创建一个线程
                logger.info("接受了一个新的连接请求");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "服务器启动失败", e);
        }
    }

    /**
     * Client 内部类：代表一个客户端连接。
     * 每个客户端都有自己的线程，负责处理其发送的消息。
     */
    private static class Client implements Runnable {
        private final SSLSocket socket; // 客户端Socket连接
        private ObjectOutputStream out; // 输出流，向客户端发送数据
        private String username;        // 当前客户端的用户名

        public Client(SSLSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                socket.setSoTimeout(30000); // 设置30秒超时时间

                // 初始化输入输出流
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                // 输入流，接收客户端发送的数据
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

                // 接收客户端发送的第一个消息：用户名
                username = (String) in.readObject();

                // 检查用户名是否重复
                if (usernames.contains(username)) {
                    out.writeObject(Message.system("[ERROR] 用户名已存在！"));
                    out.flush();
                    disconnect();
                    return;
                }

                // 将该客户端加入在线列表
                lock.lock();
                try {
                    usernames.add(username);
                    clients.add(this);
                } finally {
                    lock.unlock();
                }

                // 广播欢迎消息，并发送在线用户列表和最近历史消息
                broadcastMessage(username + " 进入了聊天室", "system");
                broadcastUserList();
                sendRecentChatHistory();

                Message message;
                // 循环接收客户端发送的消息
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
                disconnect(); // 断开连接并清理资源
            }
        }

        /**
         * 发送私信给指定用户
         */
        private void sendPrivateMessage(String target, String message) {
            Message msg = Message.privateMsg(username, target, message);
            lock.lock();
            try {
                for (Client client : clients) {
                    if (client.username.equals(target)) {
                        try {
                            client.out.writeObject(msg);
                            client.out.flush();
                            ChatLogDAO.logMessage(this.username, target, message, "user"); // 记录私信
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

        /**
         * 广播消息给所有在线用户
         */
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
                ChatLogDAO.logMessage(this.username, null, msg, log_level); // 记录群发消息
            } finally {
                lock.unlock();
            }
        }

        /**
         * 广播当前在线用户列表
         */
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

        /**
         * 发送最近的历史聊天记录给新上线的用户
         */
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

        /**
         * 断开客户端连接并清理资源
         */
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