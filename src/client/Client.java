package client;

import shared.Message; // 引入自定义的消息类

import javax.net.ssl.*; // 用于建立安全连接（SSL/TLS）
import java.awt.*; // 图形界面基础包
import java.awt.event.*; // 事件监听相关包
import javax.swing.*; // Swing 图形界面组件
import javax.swing.border.EmptyBorder; // 边框设置
import java.io.*; // 输入输出流
// 网络通信相关类
import java.security.KeyStore; // 用于信任证书库
import java.time.LocalDateTime; // 获取当前时间
import java.time.format.DateTimeFormatter; // 时间格式化
import java.util.Map; // 映射类型数据结构
import java.util.concurrent.atomic.AtomicBoolean; // 线程安全布尔值

import static client.SecurityUtil.hashPasswordWithSalt;

public class Client extends JFrame {
    // 消息显示区域组件
    private final JTextArea messageArea = new JTextArea();
    // 输入消息的文本框
    private final JTextField inputField = new JTextField();
    // 在线用户列表组件
    private final JList<String> userList = new JList<>();
    // 用户列表的数据模型
    private final DefaultListModel<String> onlineUsersModel = new DefaultListModel<>();

    // 输出对象流，用于发送消息给服务器
    private ObjectOutputStream out;
    // 输入对象流，用于接收服务器消息
    private ObjectInputStream is;
    // 标记是否已连接到服务器
    private final AtomicBoolean connected = new AtomicBoolean(false);

    // SSL 相关字段
    private static SSLContext sslContext = null; // SSL上下文
    private boolean isSecureConnection = false; // 是否使用加密连接

    // 构造方法：初始化图形界面和功能
    public Client() {
        // 初始化SSL上下文（使用系统默认信任库 + 主机名校验）
        try {
            // 获取默认的信任管理器（信任JVM内置的CA证书）
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null); // 使用默认信任库（cacerts）

            // 初始化SSLContext
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            // 设置默认的主机名校验器（基于证书中的SAN/CN）
            HttpsURLConnection.setDefaultHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "初始化SSL失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }

        // 设置窗口标题
        setTitle("聊天客户端");
        // 设置窗口大小
        setSize(800, 600);
        // 设置关闭操作为退出程序
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // 设置布局为边框布局
        setLayout(new BorderLayout());

        // 消息显示区域配置
        messageArea.setEditable(false); // 不可编辑
        messageArea.setFont(new Font("微软雅黑", Font.PLAIN, 14)); // 设置字体
        JScrollPane scrollPane = new JScrollPane(messageArea); // 加上滚动条

        // 用户列表面板配置
        userList.setModel(onlineUsersModel); // 设置数据模型
        userList.setFont(new Font("微软雅黑", Font.PLAIN, 14)); // 设置字体
        JScrollPane userScrollPane = new JScrollPane(userList); // 加上滚动条
        userScrollPane.setPreferredSize(new Dimension(150, getHeight())); // 设置宽度

        // 分割窗格：左边是消息区，右边是用户列表
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, userScrollPane);
        splitPane.setDividerLocation(600); // 左侧宽度
        splitPane.setOneTouchExpandable(true); // 允许一键展开

        add(splitPane, BorderLayout.CENTER); // 添加到中间区域

        // 输入框 + 发送按钮面板
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBorder(new EmptyBorder(5, 5, 5, 5)); // 设置边距

        inputField.setFont(new Font("微软雅黑", Font.PLAIN, 14)); // 设置输入框字体
        inputPanel.add(inputField, BorderLayout.CENTER); // 添加输入框

        JButton sendButton = new JButton("发送"); // 创建发送按钮
        sendButton.setFont(new Font("微软雅黑", Font.PLAIN, 14)); // 设置按钮字体
        inputPanel.add(sendButton, BorderLayout.EAST); // 添加到右侧

        add(inputPanel, BorderLayout.SOUTH); // 添加到底部区域

        // 菜单栏设置
        JMenuBar menuBar = new JMenuBar(); // 菜单栏容器
        JMenu menu = new JMenu("连接"); // 创建菜单项“连接”
        JMenuItem connectItem = new JMenuItem("连接服务器"); // 连接服务器菜单项
        JMenuItem disconnectItem = new JMenuItem("断开连接"); // 断开连接菜单项

        // 给菜单项添加点击事件
        connectItem.addActionListener(e -> showConnectDialog()); // 显示连接对话框
        disconnectItem.addActionListener(e -> disconnect()); // 执行断开连接操作

        menu.add(connectItem); // 添加菜单项
        menu.add(disconnectItem);
        menuBar.add(menu); // 添加菜单到菜单栏
        setJMenuBar(menuBar); // 设置菜单栏到窗口

        // 发送按钮和回车键绑定发送消息方法
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        // 双击用户名自动填充私聊命令
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // 双击事件
                    String selectedUser = userList.getSelectedValue(); // 获取选中用户
                    if (selectedUser != null && !selectedUser.equals("System")) {
                        inputField.setText("/msg " + selectedUser + " "); // 填充私聊命令
                        inputField.requestFocusInWindow(); // 让光标聚焦在输入框
                    }
                }
            }
        });

        setVisible(true); // 显示窗口
    }

    private void showRegisterDialog(ObjectOutputStream out) {
        JPanel panel = new JPanel(new GridLayout(3, 2));

        JTextField regUserField = new JTextField();
        JPasswordField regPassField = new JPasswordField();
        JPasswordField confirmPassField = new JPasswordField();

        panel.add(new JLabel("用户名:"));
        panel.add(regUserField);
        panel.add(new JLabel("密码:"));
        panel.add(regPassField);
        panel.add(new JLabel("确认密码:"));
        panel.add(confirmPassField);

        int option = JOptionPane.showConfirmDialog(
                null, panel, "注册账号", JOptionPane.OK_CANCEL_OPTION);

        if (option == JOptionPane.OK_OPTION) {
            String username = regUserField.getText().trim();
            String password = new String(regPassField.getPassword());
            String confirmPassword = new String(confirmPassField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(null, "用户名或密码不能为空！");
                return;
            }

            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(null, "两次输入的密码不一致！");
                return;
            }

            // 生成盐并哈希密码
            byte[] salt = SecurityUtil.generateSalt();
            String hashedPassword = hashPasswordWithSalt(password, salt);

            // 构造注册消息
            Message registerMsg = Message.register(username, hashedPassword, salt);

            try {
                this.out.writeObject(registerMsg);
                this.out.flush();

                // === 新增：接收服务器返回结果 ===
                Object response = is.readObject(); // 假设服务器返回的是 String 类型
                if (response instanceof String result) {

                    if ("success".equals(result)) {
                        JOptionPane.showMessageDialog(null, "注册成功！");
                    } else {
                        JOptionPane.showMessageDialog(null, "注册失败：" + result);
                    }
                } else {
                    JOptionPane.showMessageDialog(null, "未知响应格式");
                }

            } catch (IOException | ClassNotFoundException ex) {
                JOptionPane.showMessageDialog(null, "注册失败：" + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void showLoginDialog(ObjectOutputStream out, ObjectInputStream in, String host, int port) {
        // 登录面板
        JPanel loginPanel = new JPanel(new GridLayout(2, 2));

        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();

        loginPanel.add(new JLabel("用户名:"));
        loginPanel.add(usernameField);
        loginPanel.add(new JLabel("密码:"));
        loginPanel.add(passwordField);

        // 按钮区域
        JButton loginButton = new JButton("登录");
        JButton registerButton = new JButton("注册");

        // 使用 final 数组来延迟初始化 loginDialog
        final JDialog[] loginDialogHolder = new JDialog[1];

        // 登录按钮动作监听器
        loginButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword());

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(loginDialogHolder[0], "用户名或密码不能为空！");
                return;
            }

            try {
                // 发送请求获取 salt
                Message getSaltMsg = Message.getSalt(username);
                out.writeObject(getSaltMsg);
                out.flush();

                // 接收 salt 响应
                Object response = in.readObject();
                if (!(response instanceof Message saltResponse)) {
                    JOptionPane.showMessageDialog(loginDialogHolder[0], "服务器响应错误");
                    return;
                }

                // 判断是否是正确的 salt 响应类型
                if (!"returnsalt".equals(saltResponse.type)) {
                    JOptionPane.showMessageDialog(loginDialogHolder[0], "未收到 salt 响应，收到的是：" + saltResponse.type);
                    return;
                }

                // 获取 salt 并验证类型
                Object saltObj = saltResponse.data.get("salt");
                if (!(saltObj instanceof byte[] serverSalt)) {
                    JOptionPane.showMessageDialog(loginDialogHolder[0], "服务器返回的 salt 格式错误");
                    return;
                }

                // 对密码进行哈希加密
                String hashedPassword = hashPasswordWithSalt(password, serverSalt);

                // 发送登录消息
                Message loginMsg = Message.login(username, hashedPassword);
                out.writeObject(loginMsg);
                out.flush();

                // 接收登录结果
                Object loginResult = in.readObject();
                if (loginResult instanceof String result) {
                    if ("success".equals(result)) {
                        connected.set(true);
                        setTitle("聊天客户端 - 已连接到 TLS://" + host + ":" + port);
                        loginDialogHolder[0].dispose(); // 关闭窗口
                        new Thread(new RecvThread()).start();
                    } else {
                        JOptionPane.showMessageDialog(loginDialogHolder[0], result);
                    }
                } else {
                    JOptionPane.showMessageDialog(loginDialogHolder[0], "无效的登录响应");
                }

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(loginDialogHolder[0], "与服务器通信失败: " + ex.getMessage());
                ex.printStackTrace();
            } catch (ClassNotFoundException ex) {
                JOptionPane.showMessageDialog(loginDialogHolder[0], "无法解析服务器消息: " + ex.getMessage());
                ex.printStackTrace();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(loginDialogHolder[0], "发生未知错误: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // 注册按钮动作监听器
        registerButton.addActionListener(e -> {
            loginDialogHolder[0].dispose(); // 关闭登录窗口
            showRegisterDialog(out); // 打开注册窗口
        });

        // 使用 JOptionPane 构建登录窗口
        Object[] options = {loginButton, registerButton}; // 自定义按钮数组

        JOptionPane optionPane = new JOptionPane(
                loginPanel,
                JOptionPane.PLAIN_MESSAGE,
                JOptionPane.DEFAULT_OPTION,
                null,
                options,
                loginButton
        );

        // 初始化 dialog
        loginDialogHolder[0] = optionPane.createDialog(this, "登录");
        loginDialogHolder[0].setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // 显示登录窗口
        loginDialogHolder[0].setVisible(true);
    }

    // 显示连接服务器对话框
    private void showConnectDialog() {
        JTextField ipField = new JTextField("localhost"); // 默认IP地址
        JTextField portField = new JTextField("8000"); // 默认端口号
        JPanel panel = new JPanel(new GridLayout(2, 2)); // 表单布局
        panel.add(new JLabel("IP 地址:")); // 提示标签
        panel.add(ipField); // IP输入框
        panel.add(new JLabel("端口号:")); // 提示标签
        panel.add(portField); // 端口输入框

        // 显示确认对话框
        int result = JOptionPane.showConfirmDialog(this, panel, "连接服务器", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) { // 如果点击确定
            try {
                String host = ipField.getText().trim(); // 获取IP
                int port = Integer.parseInt(portField.getText().trim()); // 获取端口

                // 验证输入
                if (host.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "IP地址不能为空", "输入错误", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                if (port < 1 || port > 65535) {
                    JOptionPane.showMessageDialog(this, "端口号必须在1-65535之间", "输入错误", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                disconnect_beforeconnect(); // 如果之前有连接先断开

                // 使用SSL工厂创建安全连接
                SSLSocketFactory factory = sslContext.getSocketFactory();
                SSLSocket sslSocket = (SSLSocket) factory.createSocket(host, port);

                // 设置协议版本（TLS 1.2 和 TLS 1.3）
                sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
                // 设置加密套件（默认支持的）
                sslSocket.setEnabledCipherSuites(sslSocket.getSupportedCipherSuites());

                // 开始握手验证
                try {
                    sslSocket.startHandshake();
                } catch (SSLHandshakeException ex) {
                    throw new IOException("TLS 握手失败，请检查服务器证书是否有效：" + ex.getMessage(), ex);
                }

                // 将SSL套接字赋值给普通socket变量
                isSecureConnection = true; // 标记为安全连接

                // 初始化输入输出流
                out = new ObjectOutputStream(sslSocket.getOutputStream());
                out.flush();
                is = new ObjectInputStream(sslSocket.getInputStream());

                // 在连接成功后调用登录窗口
                showLoginDialog(out, is, host, port);

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

    // 发送消息的方法
    private void sendMessage() {
        String text = inputField.getText().trim(); // 获取输入内容
        if (text.isEmpty()) return; // 如果为空就返回

        try {
            Message msg;
            if (text.startsWith("/msg ")) { // 如果是以 /msg 开头表示私信
                String[] parts = text.split(" ", 3); // 分成三部分
                if (parts.length >= 3) {
                    String target = parts[1]; // 私信目标
                    String content = parts[2]; // 内容

                    msg = Message.privateMsg("我", target, content); // 创建私信消息
                    out.writeObject(msg); // 发送给服务器
                    out.flush();
                    appendMessage("[私信] 我 → " + target + ": " + content); // 显示到消息区
                }
            } else { // 普通群发消息
                msg = Message.chat("我", text); // 创建群发消息
                out.writeObject(msg); // 发送
                out.flush();
                appendMessage("我: " + text); // 显示到消息区
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "发送消息失败：" + ex.getMessage(), "发送错误", JOptionPane.ERROR_MESSAGE);
        }

        inputField.setText(""); // 清空输入框
    }

    // 断开连接的方法
    private void disconnect() {
        try {
            if (out != null) out.close(); // 关闭输出流
            if (is != null) is.close(); // 关闭输入流
            connected.set(false); // 设置为未连接
            appendMessage("已断开连接"); // 显示提示信息
            setTitle("聊天客户端 - 未连接"); // 修改标题
        } catch (IOException ignored) {} // 忽略异常
    }

    // 连接前尝试断开旧连接
    private void disconnect_beforeconnect() {
        try {
            if (out != null) out.close();
            if (is != null) is.close();
            connected.set(false);
            setTitle("聊天客户端 - 未连接");
        } catch (IOException ignored) {}
    }

    // 添加一条消息到消息区域
    private void appendMessage(String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")); // 当前时间
        // 使用SwingUtilities.invokeLater保证UI更新在主线程执行
        SwingUtilities.invokeLater(() -> {
            messageArea.append("[" + time + "] " + message + "\n"); // 添加消息
            messageArea.setCaretPosition(messageArea.getDocument().getLength()); // 自动滚动到底部
        });
    }

    public boolean isSecureConnection() {
        return isSecureConnection;
    }

    // 接收服务器消息的线程类
    class RecvThread implements Runnable {
        @Override
        public void run() {
            try {
                while (connected.get()) { // 循环读取消息直到断开连接
                    Message msg = (Message) is.readObject(); // 读取消息

                    switch (msg.type) {
                        case "user_list": // 用户列表更新
                            Map<String, Object> users = (Map<String, Object>) msg.data.get("users");
                            final DefaultListModel<String> tempModel = new DefaultListModel<>();
                            for (String user : users.keySet()) {
                                tempModel.addElement(user);
                            }

                            // 更新在线用户列表
                            SwingUtilities.invokeLater(() -> {
                                onlineUsersModel.clear(); // 清空旧列表
                                for (int i = 0; i < tempModel.size(); i++) {
                                    onlineUsersModel.addElement(tempModel.getElementAt(i));
                                }
                            });
                            break;

                        case "history": // 历史记录
                            String log = (String) msg.data.get("log");
                            appendMessage(log); // 添加历史消息
                            break;

                        case "private": // 收到私信
                            String from = (String) msg.data.get("sender");
                            String content = (String) msg.data.get("content");
                            appendMessage("【私信】" + from + ": " + content); // 显示私信
                            break;

                        case "chat": // 收到群发消息
                            String sender = (String) msg.data.get("sender");
                            String text = (String) msg.data.get("content");
                            appendMessage("[" + sender + "] " + text); // 显示消息
                            break;

                        case "system": // 系统消息
                            String sysMsg = (String) msg.data.get("content");
                            appendMessage("[系统消息] " + sysMsg); // 显示系统消息
                            break;

                        default:
                            appendMessage("未知消息类型: " + msg.type); // 未知消息类型
                    }
                }
            } catch (Exception e) {
                // 如果发生异常（如服务器断开），显示提示并重置状态
                SwingUtilities.invokeLater(() -> {
                    appendMessage("服务器断开连接。");
                    connected.set(false);
                    setTitle("聊天客户端 - 未连接");
                });
            } finally {
                connected.set(false); // 最终标记为未连接
            }
        }
    }

    // 程序入口点
    public static void main(String[] args) {
        try {
            // 设置外观为系统默认风格
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {}

        // 启动图形界面
        SwingUtilities.invokeLater(Client::new);
    }
}