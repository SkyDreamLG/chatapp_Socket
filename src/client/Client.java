// src/client/Client.java
package client;

import shared.Message;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
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

    public Client() {
        setTitle("聊天客户端");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 消息显示区域
        messageArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(messageArea);

        // 用户列表面板
        userList.setModel(onlineUsersModel);
        JScrollPane userScrollPane = new JScrollPane(userList);
        userScrollPane.setPreferredSize(new Dimension(150, getHeight()));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, userScrollPane);
        splitPane.setDividerLocation(600);

        add(splitPane, BorderLayout.CENTER);

        // 输入框 + 发送按钮
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);

        JButton sendButton = new JButton("发送");
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
                Socket socket = new Socket(ipField.getText(), Integer.parseInt(portField.getText()));
                out = new ObjectOutputStream(socket.getOutputStream());
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
            } catch (Exception ex) {
                appendMessage("连接失败：" + ex.getMessage());
                connected.set(false);
            }
        }
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        if (text.startsWith("/msg ")) {
            String[] parts = text.split(" ", 3);
            if (parts.length >= 3) {
                String target = parts[1];
                String content = parts[2];

                try {
                    Message msg = Message.privateMsg("我", target, content);
                    out.writeObject(msg);
                    out.flush();
                    appendMessage("[私信] 我 → " + target + ": " + content);
                } catch (IOException ignored) {}
            }
        } else {
            try {
                Message msg = Message.chat("我", text);
                out.writeObject(msg);
                out.flush();
                appendMessage("我: " + text);
            } catch (IOException ignored) {}
        }

        inputField.setText("");
    }

    private void disconnect() {
        try {
            if (out != null) out.close();
            if (is != null) is.close();
            connected.set(false);
            appendMessage("已断开连接");
        } catch (IOException ignored) {}
    }

    private void appendMessage(String message) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        messageArea.append("[" + time + "] " + message + "\n");
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
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
                SwingUtilities.invokeLater(() -> appendMessage("服务器断开连接。"));
            } finally {
                connected.set(false);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}