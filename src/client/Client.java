package client;

import shared.Message;

import javax.net.ssl.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client extends JFrame {
    private final JTextArea messageArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JList<String> userList = new JList<>();
    private final DefaultListModel<String> onlineUsersModel = new DefaultListModel<>();

    private ObjectOutputStream out;
    private ObjectInputStream is;
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // SSL相关字段
    private static SSLContext sslContext = null;
    private boolean isSecureConnection = false;

    public Client() {
        // 初始化SSL上下文（使用系统默认信任库 + 主机名校验）
        try {
            // 获取默认信任管理器（信任系统内置的 CA 列表）
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null); // 使用默认信任库（JVM 内置的 cacerts）

            // 初始化SSLContext
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            // 使用 Java 默认的主机名校验器（基于证书中的 SAN/CN）
            HttpsURLConnection.setDefaultHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "初始化SSL失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }

        setTitle("聊天客户端");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 消息显示区域
        messageArea.setEditable(false);
        messageArea.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(messageArea);

        // 用户列表面板
        userList.setModel(onlineUsersModel);
        userList.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setPreferredSize(new Dimension(150, getHeight()));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, userScrollPane);
        splitPane.setDividerLocation(600);
        splitPane.setOneTouchExpandable(true);

        add(splitPane, BorderLayout.CENTER);

        // 输入框 + 发送按钮面板
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        inputField.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        inputPanel.add(inputField, BorderLayout.CENTER);

        JButton sendButton = new JButton("发送");
        sendButton.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        inputPanel.add(sendButton, BorderLayout.EAST);

        add(inputPanel, BorderLayout.SOUTH);

        // 菜单栏
        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("连接");
        JMenuItem connectItem = new JMenuItem("连接服务器");
        JMenuItem disconnectItem = new JMenuItem("断开连接");

        connectItem.addActionListener(e -> showConnectDialog());
        disconnectItem.addActionListener(e -> disconnect());

        menu.add(connectItem);
        menu.add(disconnectItem);
        menuBar.add(menu);
        setJMenuBar(menuBar);

        // 事件监听器
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selectedUser = userList.getSelectedValue();
                    if (selectedUser != null && !selectedUser.equals("System")) {
                        inputField.setText("/msg " + selectedUser + " ");
                        inputField.requestFocusInWindow();
                    }
                }
            }
        });

        setVisible(true);
    }

    private void showConnectDialog() {
        JTextField ipField = new JTextField("localhost");
        JTextField portField = new JTextField("8000");
        JPanel panel = new JPanel(new GridLayout(2, 2));
        panel.add(new JLabel("IP 地址:"));
        panel.add(ipField);
        panel.add(new JLabel("端口号:"));
        panel.add(portField);

        int result = JOptionPane.showConfirmDialog(this, panel, "连接服务器", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                String host = ipField.getText().trim();
                int port = Integer.parseInt(portField.getText().trim());

                // 验证输入
                if (host.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "IP地址不能为空", "输入错误", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                if (port < 1 || port > 65535) {
                    JOptionPane.showMessageDialog(this, "端口号必须在1-65535之间", "输入错误", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                disconnect_beforeconnect();

                // 强制使用 TLS
                SSLSocketFactory factory = sslContext.getSocketFactory();
                SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port);

                // 设置协议版本和加密套件（可选）
                sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
                sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());

                // 开始握手
                try {
                    sslSocket.startHandshake();
                } catch (SSLHandshakeException ex) {
                    throw new IOException("TLS 握手失败，请检查服务器证书是否有效：" + ex.getMessage(), ex);
                }

                Socket socket = sslSocket;
                isSecureConnection = true;

                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                is = new ObjectInputStream(socket.getInputStream());

                // 登录用户名
                String nickname = JOptionPane.showInputDialog(this, "请输入用户名:");
                if (nickname == null || nickname.trim().isEmpty()) {
                    socket.close();
                    return;
                }

                out.writeObject(nickname);
                out.flush();

                connected.set(true);
                new Thread(new RecvThread()).start();

                // 更新标题栏显示连接状态
                setTitle("聊天客户端 - 已连接到 TLS://" + host + ":" + port);

            } catch (SSLException ex) {
                JOptionPane.showMessageDialog(this, "TLS 握手失败：\n" + ex.getMessage(),
                        "TLS 错误", JOptionPane.ERROR_MESSAGE);
                connected.set(false);
                setTitle("聊天客户端 - 未连接");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "连接失败：\n" + ex.getMessage(),
                        "连接错误", JOptionPane.ERROR_MESSAGE);
                connected.set(false);
                setTitle("聊天客户端 - 未连接");
            }
        }
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        try {
            Message msg;
            if (text.startsWith("/msg ")) {
                String[] parts = text.split(" ", 3);
                if (parts.length >= 3) {
                    String target = parts[1];
                    String content = parts[2];

                    msg = Message.privateMsg("我", target, content);
                    out.writeObject(msg);
                    out.flush();
                    appendMessage("[私信] 我 → " + target + ": " + content);
                }
            } else {
                msg = Message.chat("我", text);
                out.writeObject(msg);
                out.flush();
                appendMessage("我: " + text);
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "发送消息失败：" + ex.getMessage(), "发送错误", JOptionPane.ERROR_MESSAGE);
        }

        inputField.setText("");
    }

    private void disconnect() {
        try {
            if (out != null) out.close();
            if (is != null) is.close();
            connected.set(false);
            appendMessage("已断开连接");
            setTitle("聊天客户端 - 未连接");
        } catch (IOException ignored) {}
    }

    private void disconnect_beforeconnect() {
        try {
            if (out != null) out.close();
            if (is != null) is.close();
            connected.set(false);
            setTitle("聊天客户端 - 未连接");
        } catch (IOException ignored) {}
    }

    private void appendMessage(String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            messageArea.append("[" + time + "] " + message + "\n");
            messageArea.setCaretPosition(messageArea.getDocument().getLength());
        });
    }

    public boolean isSecureConnection() {
        return isSecureConnection;
    }

    class RecvThread implements Runnable {
        @Override
        public void run() {
            try {
                while (connected.get()) {
                    Message msg = (Message) is.readObject();

                    switch (msg.type) {
                        case "user_list":
                            Map<String, Object> users = (Map<String, Object>) msg.data.get("users");
                            final DefaultListModel<String> tempModel = new DefaultListModel<>();
                            for (Object user : users.keySet()) {
                                tempModel.addElement((String) user);
                            }

                            SwingUtilities.invokeLater(() -> {
                                onlineUsersModel.clear();
                                for (int i = 0; i < tempModel.size(); i++) {
                                    onlineUsersModel.addElement(tempModel.getElementAt(i));
                                }
                            });
                            break;

                        case "history":
                            String log = (String) msg.data.get("log");
                            appendMessage(log);
                            break;

                        case "private":
                            String from = (String) msg.data.get("sender");
                            String content = (String) msg.data.get("content");
                            appendMessage("【私信】" + from + ": " + content);
                            break;

                        case "chat":
                            String sender = (String) msg.data.get("sender");
                            String text = (String) msg.data.get("content");
                            appendMessage("[" + sender + "] " + text);
                            break;

                        case "system":
                            String sysMsg = (String) msg.data.get("content");
                            appendMessage("[系统消息] " + sysMsg);
                            break;

                        default:
                            appendMessage("未知消息类型: " + msg.type);
                    }
                }
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("服务器断开连接。");
                    connected.set(false);
                    setTitle("聊天客户端 - 未连接");
                });
            } finally {
                connected.set(false);
            }
        }
    }

    public static void main(String[] args) {
        try {
            // 设置外观为系统默认
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {}

        SwingUtilities.invokeLater(Client::new);
    }
}