package server;

import java.io.*;
import java.net.*;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;

/**
 * Server 类是聊天服务器的核心类，负责监听客户端连接、处理消息收发。
 */
public class Server {
    // 存储所有已连接的客户端对象
    private static final List<Client> clients = new ArrayList<>();
    // 存储所有已经注册的用户名（防止重复）
    private static final Set<String> usernames = new HashSet<>();
    // 服务器监听的端口号
    private static int PORT;
    // 用于线程安全操作的锁
    private static final ReentrantLock lock = new ReentrantLock();
    // 日志记录器，用于输出运行时信息
    static final Logger logger = Logger.getLogger(Server.class.getName());

    // 静态代码块：在类加载时执行一次，用于初始化日志配置和读取配置文件
    static {
        // 设置日志格式为控制台输出
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter() {

            public synchronized String format(LogRecord lr) {
                // 自定义日志格式：[时间] [日志级别] 日志内容
                String format = "[%1$tF %1$tT] [%2$-7s] %3$s %n";
                return String.format(format,
                        new Date(lr.getMillis()),
                        lr.getLevel(),
                        lr.getMessage()
                );
            }
        });
        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false); // 关闭默认的日志处理器

        // 获取当前工作目录
        String currentDir = System.getProperty("user.dir");
        File configFile = new File(currentDir, "server.properties"); // 配置文件路径

        try (InputStream input = new FileInputStream(configFile)) {
            Properties prop = new Properties();
            prop.load(input); // 加载配置文件内容

            // 从配置文件中读取端口号，默认8000
            PORT = Integer.parseInt(prop.getProperty("port", "8000"));

            // 数据库相关配置
            String DB_URL = prop.getProperty("db.url");
            String DB_USER = prop.getProperty("db.username");
            String DB_PASSWORD = prop.getProperty("db.password");

            // 如果数据库配置不全，则抛出异常
            if (DB_URL == null || DB_USER == null || DB_PASSWORD == null) {
                throw new IOException("缺少数据库配置项");
            }

            // 初始化数据库连接池
            DBUtil.init(DB_URL, DB_USER, DB_PASSWORD);
            logger.info("数据库连接池初始化成功");
        } catch (IOException e) {
            // 如果读取配置失败，使用默认端口8000
            logger.warning("加载配置失败，使用默认端口 8000");
            PORT = 8000;
        }
    }

    /**
     * 主函数，程序入口点。
     * 启动服务器并监听客户端连接。
     */
    public static void main(String[] args) {
        logger.info("服务器正在启动... 监听端口: " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("服务器已启动并监听于端口: " + PORT);

            // 添加关闭钩子，在JVM关闭前释放资源
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                DBUtil.close(); // 关闭数据库连接池
                logger.info("数据库连接池已关闭");
            }));

            while (true) {
                // 接受新的客户端连接请求
                Socket socket = serverSocket.accept();
                // 为每个客户端创建一个新线程来处理通信
                new Thread(new Client(socket)).start();
                logger.info("接受了一个新的连接请求");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "服务器启动失败", e);
        }
    }

    /**
     * 内部类 Client，代表一个客户端连接。
     * 每个客户端由一个独立线程处理。
     */
    private static class Client implements Runnable {
        private final Socket socket; // 客户端套接字
        private DataOutputStream out; // 输出流，用于向客户端发送数据
        private String username; // 客户端用户名

        public Client(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                // 获取输入输出流
                DataInputStream in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                // 读取客户端发送的第一个消息作为用户名
                username = in.readUTF();
                logger.info("接收到用户名：" + username);

                // 检查用户名是否已存在
                if (usernames.contains(username)) {
                    out.writeUTF("[ERROR] 用户名已存在！");
                    out.flush();
                    disconnect(); // 断开连接
                    return;
                }

                // 将该用户加入在线列表
                lock.lock();
                try {
                    usernames.add(username);
                    clients.add(this);
                } finally {
                    lock.unlock();
                }

                // 广播通知其他用户有人加入了聊天室
                broadcastMessage(username + " 进入了聊天室", "system");
                // 更新所有用户的好友列表
                broadcastUserList();
                // 发送最近的聊天历史给该用户
                sendRecentChatHistory();

                String message;
                // 循环接收客户端发送的消息
                while ((message = in.readUTF()) != null && !message.isEmpty()) {
                    logger.info("收到消息：[" + username + "]：" + message);

                    // 判断是否是私聊命令
                    if (message.startsWith("/msg ")) {
                        String[] parts = message.split(" ", 3);
                        if (parts.length == 3) {
                            String target = parts[1]; // 私聊目标
                            String content = parts[2]; // 私聊内容
                            sendPrivateMessage(target, content); // 发送私聊消息
                        }
                    } else {
                        // 普通群聊消息广播给所有人
                        broadcastMessage("[" + username + "]：" + message, "user");
                    }
                }
            } catch (IOException e) {
                logger.log(Level.FINE, "客户端断开连接", e);
            } finally {
                disconnect(); // 最终断开连接
            }
        }

        /**
         * 发送私聊消息给指定用户
         */
        private void sendPrivateMessage(String target, String message) {
            lock.lock();
            try {
                for (Client client : clients) {
                    if (client.username.equals(target)) { // 找到目标用户
                        try {
                            String msg = "[PRIVATE]" + username + " 私信：" + message;
                            client.out.writeUTF(msg); // 发送私信
                            client.out.flush();
                            ChatLogDAO.logMessage(this.username, target, message, "user"); // 记录日志
                            return;
                        } catch (IOException ignored) {}
                    }
                }
                // 如果找不到目标用户，回复原用户
                try {
                    out.writeUTF("[系统消息] 用户 '" + target + "' 不存在或不在线");
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
            lock.lock();
            try {
                for (Client client : clients) {
                    try {
                        client.out.writeUTF(msg); // 发送消息
                        client.out.flush();
                    } catch (IOException ignored) {}
                }
                // 将消息写入数据库日志
                ChatLogDAO.logMessage(this.username, null, msg, log_level);
            } finally {
                lock.unlock();
            }
        }

        /**
         * 向所有用户广播当前在线用户列表
         */
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
                sb.setLength(sb.length() - 1); // 去掉最后一个逗号
            }

            lock.lock();
            try {
                for (Client client : clients) {
                    try {
                        client.out.writeUTF(sb.toString()); // 发送用户列表
                    } catch (IOException ignored) {}
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * 向新用户发送最近50条聊天记录
         */
        private void sendRecentChatHistory() {
            List<String> history = ChatLogDAO.getRecentChatHistory(50, username);
            for (String msg : history) {
                try {
                    out.writeUTF("[HISTORY]" + msg); // 发送历史消息
                } catch (IOException e) {
                    logger.log(Level.FINE, "发送历史消息失败", e);
                }
            }
        }

        /**
         * 客户端断开连接时的清理操作
         */
        private void disconnect() {
            if (username != null) {
                lock.lock();
                try {
                    usernames.remove(username); // 从用户名集合中移除
                    clients.remove(this); // 从客户端列表中移除
                } finally {
                    lock.unlock();
                }
                // 广播通知其他人该用户离开
                broadcastMessage(username + " 离开了聊天室", "system");
                // 更新所有用户的在线列表
                broadcastUserList();
            }

            try {
                socket.close(); // 关闭客户端连接
            } catch (IOException ignored) {}
        }
    }
}