package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天客户端主类
 * 功能：
 * - 提供图形化界面与服务器交互
 * - 支持连接/断开服务器
 * - 发送公共消息或私信
 * - 接收并显示服务器广播的消息
 * - 实时更新在线用户列表
 */
public class Client extends JFrame {
    // 用户名输入框
    private JTextField usernameField;
    // 服务器IP地址输入框
    private JTextField ipField;
    // 端口号输入框
    private JTextField portField;
    // 消息输入框
    private JTextField messageField;
    // 聊天记录展示区域
    private JTextArea chatArea;
    // 在线用户列表的数据模型
    private DefaultListModel<String> onlineUsersModel = new DefaultListModel<>();
    // 在线用户列表组件
    private JList<String> onlineUsersList = new JList<>(onlineUsersModel);
    // 连接按钮
    private JButton connectButton;
    // 发送消息按钮
    private JButton sendButton;
    // 数据输出流，用于向服务器发送数据
    private DataOutputStream out;
    // 数据输入流，用于接收服务器消息
    private DataInputStream is;
    // 客户端Socket连接
    private Socket socket;
    // 接收服务器消息的线程
    private Thread recvThread;
    // 原子布尔值，表示是否已连接到服务器
    private AtomicBoolean connected = new AtomicBoolean(false);

    /**
     * 构造函数：初始化客户端界面
     */
    public Client() {
        setTitle("聊天客户端");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // 自定义关闭逻辑
        setLocationRelativeTo(null); // 居中显示窗口

        // 添加窗口关闭监听器，确保退出前断开连接
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                System.exit(0);
            }
        });

        // 顶部面板：用户名、IP、端口、连接按钮
        JPanel topPanel = new JPanel(new FlowLayout());
        topPanel.add(new JLabel("用户名:"));
        usernameField = new JTextField(10);
        topPanel.add(usernameField);
        topPanel.add(new JLabel("服务器IP:"));
        ipField = new JTextField("localhost", 10);
        topPanel.add(ipField);
        topPanel.add(new JLabel("端口:"));
        portField = new JTextField("8000", 5);
        topPanel.add(portField);
        connectButton = new JButton("连接");
        topPanel.add(connectButton);
        add(topPanel, BorderLayout.NORTH);

        // 中间聊天内容区域
        chatArea = new JTextArea();
        chatArea.setEditable(false); // 只读
        JScrollPane scrollPane = new JScrollPane(chatArea);

        // 右侧在线用户列表区域
        JScrollPane userScrollPane = new JScrollPane(onlineUsersList);
        userScrollPane.setBorder(BorderFactory.createTitledBorder("在线用户"));

        // 使用 JSplitPane 分割左右两部分，可拖动调整宽度
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, userScrollPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(600); // 初始分割位置
        add(splitPane, BorderLayout.CENTER);

        // 底部消息输入区域
        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("发送");
        sendButton.setEnabled(false); // 初始禁用发送功能
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // 鼠标点击用户列表自动填充私信命令
        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    String selectedUser = onlineUsersList.getSelectedValue();
                    if (selectedUser != null) {
                        messageField.setText("/msg " + selectedUser + " ");
                        messageField.setCaretPosition(messageField.getText().length()); // 光标定位到末尾
                    }
                } else if (e.getClickCount() == 2) {
                    onlineUsersList.clearSelection(); // 双击取消选择
                }
            }
        });

        // 连接按钮监听事件
        connectButton.addActionListener(e -> {
            if (!connected.get()) {
                connectToServer(); // 尝试连接
            } else {
                disconnect(); // 已连接则断开
            }
        });

        // 发送按钮和回车键均可触发发送消息
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        setVisible(true); // 显示窗口
    }

    /**
     * 向聊天区域追加一条消息（使用SwingUtilities.invokeLater保证线程安全）
     */
    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> chatArea.append(message + "\n"));
    }

    /**
     * 根据连接状态启用或禁用控件
     */
    private void enableConnectButtons(boolean enable) {
        connectButton.setText(enable ? "连接" : "断开");
        sendButton.setEnabled(!enable); // 连接后启用发送
        messageField.setEditable(!enable);
        usernameField.setEditable(enable); // 连接后禁止修改配置
        ipField.setEditable(enable);
        portField.setEditable(enable);
    }

    /**
     * 尝试连接到服务器
     */
    private void connectToServer() {
        String nickname = usernameField.getText().trim();
        String serverIp = ipField.getText().trim();
        int serverPort;
        try {
            serverPort = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            appendMessage("请输入有效的端口号！");
            return;
        }

        if (nickname.isEmpty() || serverIp.isEmpty()) {
            appendMessage("请输入用户名和服务器信息！");
            return;
        }

        try {
            // 创建Socket连接
            socket = new Socket(serverIp, serverPort);
            out = new DataOutputStream(socket.getOutputStream());
            is = new DataInputStream(socket.getInputStream());

            // 发送用户名给服务器
            out.writeUTF(nickname);
            out.flush();

            // 接收服务器响应，检查用户名是否重复
            String response = is.readUTF();
            if (response.startsWith("[ERROR]")) {
                appendMessage(response);
                disconnect();
                return;
            }

            connected.set(true); // 设置为已连接
            enableConnectButtons(false); // 更新界面状态
            appendMessage("已连接到服务器");

            // 启动独立线程接收服务器消息
            recvThread = new Thread(new RecvThread());
            recvThread.start();

        } catch (IOException e) {
            appendMessage("连接服务器失败，请检查网络或服务器是否运行。");
        }
    }

    /**
     * 断开与服务器的连接，释放资源
     */
    private void disconnect() {
        connected.set(false);

        // 关闭输出流
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        // 关闭输入流
        try { if (is != null) is.close(); } catch (IOException ignored) {}
        // 关闭Socket连接
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) {}

        // 等待接收线程结束
        if (recvThread != null && recvThread.isAlive()) {
            try {
                recvThread.join(1000); // 最多等待1秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 捕获中断信号
            }
        }

        enableConnectButtons(true); // 恢复界面状态
        appendMessage("已与服务器断开连接");
    }

    /**
     * 发送消息到服务器
     */
    private void sendMessage() {
        String msg = messageField.getText().trim();
        if (!msg.isEmpty()) {
            try {
                out.writeUTF(msg);
                out.flush();

                // 在本地显示自己发送的消息
                appendMessage("【我】" + msg);

                messageField.setText(""); // 清空输入框
            } catch (IOException ex) {
                appendMessage("消息发送失败！");
            }
        }
    }

    /**
     * 接收服务器消息的线程
     */
    class RecvThread implements Runnable {
        @Override
        public void run() {
            try {
                while (connected.get()) {
                    String str = is.readUTF(); // 接收消息

                    if (str.startsWith("[USER_LIST]")) {
                        // 处理用户列表更新
                        String[] users = str.substring(11).split(",");
                        final DefaultListModel<String> tempModel = new DefaultListModel<>();
                        for (String user : users) {
                            if (!user.isEmpty()) {
                                tempModel.addElement(user);
                            }
                        }

                        // 更新UI中的用户列表
                        SwingUtilities.invokeLater(() -> {
                            onlineUsersModel.clear();
                            for (int i = 0; i < tempModel.size(); i++) {
                                onlineUsersModel.addElement(tempModel.getElementAt(i));
                            }
                        });
                    } else if (str.startsWith("[PRIVATE]")) {
                        // 处理私信消息
                        appendMessage("【私信】" + str.substring(9)); // 去掉 [PRIVATE]
                    } else {
                        // 正常广播消息
                        appendMessage(str);
                    }
                }
            } catch (EOFException | SocketException e) {
                // 正常断开连接
                SwingUtilities.invokeLater(() -> appendMessage("服务器断开连接。"));
            } catch (IOException e) {
                // 其他IO异常
                SwingUtilities.invokeLater(() -> appendMessage("与服务器通信中断。"));
            } finally {
                connected.set(false);
                SwingUtilities.invokeLater(() -> enableConnectButtons(true));
            }
        }
    }

    /**
     * 程序入口点
     */
    public static void main(String[] args) {
        // 使用SwingUtilities.invokeLater启动GUI线程
        SwingUtilities.invokeLater(Client::new);
    }
}