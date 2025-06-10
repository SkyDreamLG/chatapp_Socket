package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client extends JFrame {
    private JTextField usernameField, ipField, portField;
    private JTextField messageField;
    private JTextArea chatArea;
    private DefaultListModel<String> onlineUsersModel = new DefaultListModel<>();
    private JList<String> onlineUsersList = new JList<>(onlineUsersModel);
    private JButton connectButton, sendButton;
    private DataOutputStream out;
    private DataInputStream is;
    private Socket socket;
    private Thread recvThread;
    private AtomicBoolean connected = new AtomicBoolean(false);

    public Client() {
        setTitle("聊天客户端");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        // 窗口关闭事件
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                System.exit(0);
            }
        });

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

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        add(scrollPane, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("发送");
        sendButton.setEnabled(false);
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendButton, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        JScrollPane userScrollPane = new JScrollPane(onlineUsersList);
        userScrollPane.setBorder(BorderFactory.createTitledBorder("在线用户"));

        // 添加鼠标事件监听器以支持自动填充和取消选择
        onlineUsersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineUsersList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    String selectedUser = onlineUsersList.getSelectedValue();
                    if (selectedUser != null) {
                        messageField.setText("/msg " + selectedUser + " ");
                        messageField.setCaretPosition(messageField.getText().length());
                    }
                } else if (e.getClickCount() == 2) {
                    // 双击取消选择
                    onlineUsersList.clearSelection();
                }
            }
        });

        add(userScrollPane, BorderLayout.EAST);

        connectButton.addActionListener(e -> {
            if (!connected.get()) {
                connectToServer();
            } else {
                disconnect();
            }
        });

        sendButton.addActionListener(e -> {
            String msg = messageField.getText().trim();
            if (!msg.isEmpty()) {
                try {
                    out.writeUTF(msg);
                    out.flush();
                    messageField.setText("");
                } catch (IOException ex) {
                    appendMessage("消息发送失败！");
                }
            }
        });

        setVisible(true);
    }

    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> chatArea.append(message + "\n"));
    }

    private void enableConnectButtons(boolean enable) {
        connectButton.setText(enable ? "连接" : "断开");
        sendButton.setEnabled(!enable);
        messageField.setEditable(!enable);
        usernameField.setEditable(enable);
        ipField.setEditable(enable);
        portField.setEditable(enable);
    }

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
            socket = new Socket(serverIp, serverPort);
            out = new DataOutputStream(socket.getOutputStream());
            is = new DataInputStream(socket.getInputStream());

            out.writeUTF(nickname);
            out.flush();

            String response = is.readUTF(); // 若用户名冲突则返回[ERROR]
            if (response.startsWith("[ERROR]")) {
                appendMessage(response);
                disconnect();
                return;
            }

            connected.set(true);
            enableConnectButtons(false);
            appendMessage("已连接到服务器");

            recvThread = new Thread(new RecvThread());
            recvThread.start();

        } catch (IOException e) {
            appendMessage("连接服务器失败，请检查网络或服务器是否运行。");
        }
    }

    private void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close(); // 主动关闭 socket，触发 readUTF 抛出异常，从而退出线程
            }
        } catch (IOException ignored) {}

        try {
            if (out != null) out.close();
            if (is != null) is.close();
        } catch (IOException ignored) {}

        if (recvThread != null && recvThread.isAlive()) {
            try {
                recvThread.join(500); // 最多等待500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        connected.set(false);
        enableConnectButtons(true);
        appendMessage("已与服务器断开连接");
    }

    class RecvThread implements Runnable {
        @Override
        public void run() {
            try {
                while (connected.get()) {
                    String str = is.readUTF();
                    if (str.startsWith("[USER_LIST]")) {
                        String[] users = str.substring(11).split(",");
                        final DefaultListModel<String> tempModel = new DefaultListModel<>();
                        for (String user : users) {
                            if (!user.isEmpty()) {
                                tempModel.addElement(user);
                            }
                        }
                        SwingUtilities.invokeLater(() -> {
                            onlineUsersModel.clear();
                            for (int i = 0; i < tempModel.size(); i++) {
                                onlineUsersModel.addElement(tempModel.getElementAt(i));
                            }
                        });
                    } else if (str.startsWith("[PRIVATE]")) {
                        appendMessage("【私信】" + str.substring(9)); // 去掉 [PRIVATE]
                    } else {
                        appendMessage(str);
                    }
                }
            } catch (EOFException | SocketException e) {
                // 正常断开
                SwingUtilities.invokeLater(() -> appendMessage("服务器断开连接。"));
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> appendMessage("与服务器通信中断。"));
            } finally {
                connected.set(false);
                SwingUtilities.invokeLater(() -> enableConnectButtons(true));
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}