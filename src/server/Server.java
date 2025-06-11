package server;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.*;
import java.util.logging.Logger;
import java.sql.SQLException;

/**
 * 聊天服务器主类
 * 功能：
 * - 接收客户端连接请求
 * - 处理多个客户端并发连接
 * - 实现消息广播、私信通信
 * - 维护在线用户名单和用户列表推送
 */
public class Server {
    // 存储所有已连接客户端的处理对象
    private static final List<ClientHandler> clients = new ArrayList<>();
    // 存储所有已注册的用户名（用于避免重复）
    private static final Set<String> usernames = new HashSet<>();
    // 服务器监听端口号
    private static int PORT;
    // 线程安全锁，用于同步操作clients和usernames集合
    private static final ReentrantLock lock = new ReentrantLock();
    // 日志记录器
    static final Logger logger = Logger.getLogger(Server.class.getName());

    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;

    /**
     * 静态代码块：在类加载时执行一次
     * 主要功能：
     * - 初始化日志格式
     * - 加载配置文件server.properties中的端口号
     */
    static {
        // 设置日志格式为 [日期 时间] [日志级别] 日志内容
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

        // 添加自定义的日志处理器并禁用默认的日志输出方式
        logger.addHandler(consoleHandler);
        logger.setUseParentHandlers(false); // 避免重复输出

        // 从配置文件中读取端口设置，默认8000
        try (InputStream input = new FileInputStream("server.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            PORT = Integer.parseInt(prop.getProperty("port", "8000")); // 默认8000

            DB_URL = prop.getProperty("db.url");
            DB_USER = prop.getProperty("db.username");
            DB_PASSWORD = prop.getProperty("db.password");

            if (DB_URL == null || DB_USER == null || DB_PASSWORD == null) {
                throw new IOException("缺少必要的数据库配置项");
            }

            // 初始化数据库连接池
            DBUtil.init(DB_URL, DB_USER, DB_PASSWORD);
            logger.info("数据库连接池初始化成功");

        } catch (IOException | NumberFormatException ex) {
            logger.warning("加载配置失败，使用默认端口 8000");
            PORT = 8000;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "初始化数据库失败", e);
            System.exit(1);
        }
    }

    /**
     * 程序入口点
     * 启动服务器并持续监听客户端连接
     */
    public static void main(String[] args) {
        logger.info("服务器正在启动... 监听端口: " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            logger.info("服务器已启动并监听于端口: " + PORT);

            // 注册关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                DBUtil.close();
                logger.info("数据库连接池已关闭");
            }));

            while (true) {
                // 接收新客户端连接
                Socket socket = serverSocket.accept();
                // 为每个客户端创建一个新的线程来处理通信
                new Thread(new ClientHandler(socket)).start();
                logger.info("接受了一个新的连接请求");
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "服务器启动失败", e);
        }
    }

    /**
     * 内部类：客户端连接处理器
     * 每个客户端连接都会创建一个ClientHandler实例，并在独立线程中运行
     */
    static class ClientHandler implements Runnable {
        private final Socket socket;          // 客户端Socket连接
        private DataInputStream in;           // 输入流，接收客户端数据
        private DataOutputStream out;         // 输出流，向客户端发送数据
        private String username;              // 当前客户端用户名

        /**
         * 构造函数
         * @param socket 客户端Socket连接
         */
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        /**
         * 线程执行体
         * 处理客户端连接的整个生命周期
         */
        @Override
        public void run() {
            try {
                // 初始化输入输出流
                in = new DataInputStream(socket.getInputStream());
                out = new DataOutputStream(socket.getOutputStream());

                // 接收客户端发送的用户名
                username = in.readUTF();
                logger.info("接收到用户名：" + username);

                // 检查用户名是否已被占用
                if (usernames.contains(username)) {
                    out.writeUTF("[ERROR] 用户名已存在！");
                    out.flush();
                    disconnect(); // 关闭连接
                    logger.warning("用户名冲突：" + username);
                    return;
                }

                // 使用锁确保线程安全地添加用户
                lock.lock();
                try {
                    usernames.add(username);
                    clients.add(this);
                } finally {
                    lock.unlock();
                }

                // 广播通知其他用户该用户加入
                broadcastMessage(username + " 进入了聊天室");
                logger.info(username + " 加入了聊天室");

                // 向所有客户端发送当前用户列表
                broadcastUserList();

                String message;
                // 循环读取消息直到客户端断开连接
                while ((message = in.readUTF()) != null && !message.isEmpty()) {
                    logger.info("收到消息：[" + username + "]：" + message);

                    // 判断是否是私信命令
                    if (message.startsWith("/msg ")) {
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
                disconnect(); // 清理资源
            }
        }

        /**
         * 发送私信给指定用户
         * @param targetUsername 目标用户名
         * @param message 私信内容
         */
        private void sendPrivateMessage(String targetUsername, String message) {
            lock.lock();
            try {
                for (ClientHandler client : clients) {
                    if (client.username.equals(targetUsername)) {
                        try {
                            String privateMsg = "[PRIVATE]" + username + " 私信：" + message;
                            client.out.writeUTF(privateMsg);
                            client.out.flush();
                            logger.info("私信已发送 [" + username + " -> " + targetUsername + "]：" + message);

                            // 记录私信到数据库
                            ChatLogDAO.logMessage(this.username, targetUsername, message);
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

        /**
         * 向所有在线用户广播一条消息
         * @param msg 要广播的消息
         */
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

                // 记录群发消息到数据库（receiver 为 null）
                ChatLogDAO.logMessage(this.username, null, msg);
            } finally {
                lock.unlock();
            }
        }

        /**
         * 向所有在线用户广播当前在线用户列表
         */
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

            // 去掉最后一个逗号
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

        /**
         * 断开连接并清理资源
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