package server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class Server {
    private static final List<ClientHandler> clients = new ArrayList<>();
    private static final Set<String> usernames = new HashSet<>();
    private static int PORT;
    private static final ReentrantLock lock = new ReentrantLock();

    static {
        try (InputStream input = new FileInputStream("server.properties")) {
            Properties prop = new Properties();
            prop.load(input);
            PORT = Integer.parseInt(prop.getProperty("port", "8000")); // 默认8000
        } catch (IOException | NumberFormatException ex) {
            System.out.println("加载配置失败，使用默认端口 8000");
            PORT = 8000;
        }
    }

    public static void main(String[] args) {
        System.out.println("服务器正在启动... 监听端口: " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(new ClientHandler(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
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

                broadcastMessage(username + " 进入了聊天室");
                broadcastUserList();

                String message;
                while ((message = in.readUTF()) != null && !message.isEmpty()) {
                    broadcastMessage("[" + username + "]：" + message);
                }
            } catch (IOException e) {
                // 客户端断开连接
            } finally {
                disconnect();
            }
        }

        private void broadcastMessage(String msg) {
            lock.lock();
            try {
                for (ClientHandler client : clients) {
                    try {
                        client.out.writeUTF(msg);
                        client.out.flush();
                    } catch (IOException ignored) {}
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
                    } catch (IOException ignored) {}
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
                broadcastUserList();
            }

            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
}