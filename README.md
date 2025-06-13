# 📄 chatapp_Socket - 基于 TCP Socket 的在线聊天系统
> **项目名称：chatapp_Socket（Socket 聊天系统）**  
> **开发语言：Java 21**  
> **适用环境：仅在 Java 21 上测试，未验证其他版本兼容性**  
> **文档版本：v2.0**  
> **更新时间：2025年6月14日**

---

## 🧩 一、项目简介

本项目是一个基于 **TCP Socket 的图形化聊天系统**，包含服务器端和客户端两个部分：

- 用户可以通过图形界面连接到服务器
- 支持发送公共消息（群聊）
- 支持私信功能（格式为 `/msg 用户名 内容`）
- 实时更新在线用户列表
- 所有聊天记录都会写入数据库，并支持显示历史聊天记录
- 使用 HikariCP 管理数据库连接池
- 支持 TLS 加密通信（通过 `keystore.p12` 证书文件）
- 支持优雅关闭服务器并释放资源

---

## 📁 二、完整目录结构说明（当前项目）

```bash
.
├── init.sql                          # 数据库初始化脚本（创建 chat_log 表）
├── keystore.p12                      # TLS 证书文件（需自行生成）
├── lib/                              # 第三方依赖 JAR 包
│   ├── bcrypt-0.10.2.jar             # 密码加密工具
│   ├── HikariCP-6.3.0.jar            # 高性能数据库连接池
│   ├── mysql-connector-java-8.0.30.jar # MySQL JDBC 驱动
│   └── ...                           # 其他依赖包
├── LICENSE                           # 开源协议文件
├── README.md                         # 当前文档（项目说明）
├── server.properties                 # 服务器配置文件（端口、数据库信息、SSL密码）
├── src/                              # 源码目录
│   ├── client/
│   │   └── Client.java               # 客户端主程序（图形界面）
│   ├── server/
│   │   ├── ChatLogDAO.java           # 聊天记录写入数据库工具类
│   │   ├── DBUtil.java               # 数据库连接池工具类（使用 HikariCP）
│   │   └── Server.java               # 服务器主程序（含 ClientHandler 内部类）
│   └── shared/
│       └── Message.java              # 消息封装类（用于客户端与服务器之间通信）
└── WS-test.iml                       # IntelliJ IDEA 项目配置文件
```

---

## 🛠️ 三、技术栈说明

| 技术 | 描述 |
|------|------|
| **语言版本** | Java 21（必须使用 JDK 21） |
| **图形界面** | Swing（纯 Java GUI 库） |
| **网络通信** | TCP Socket + SSL/TLS 加密通信 |
| **线程管理** | Thread + ReentrantLock（保证线程安全） |
| **数据库** | MySQL（支持聊天记录持久化） |
| **连接池** | HikariCP（高性能数据库连接池） |
| **打包方式** | JAR（可通过 java -jar 运行） |
| **加密通信** | 使用 TLS 1.3，证书格式为 PKCS#12 |

---

## 🔌 四、服务器端说明 `Server.java`

### ✅ 功能说明：

- 启动 TCP 服务器监听指定端口
- 接收多个客户端连接
- 维护在线用户列表（避免用户名重复）
- 广播消息给所有在线用户
- 处理私信请求（格式 `/msg 用户名 内容`）
- 将每条消息保存至数据库（支持群发和私信记录）
- 使用日志记录器（Logger）记录运行状态
- 在 JVM 关闭时自动释放数据库资源
- 提供显示历史聊天记录的功能
- 支持 TLS 加密通信（通过 `keystore.p12`）

### ⚙️ 配置文件 `server.properties`

```properties
port=8000
db.url=jdbc:mysql://localhost:3306/chatdb?useSSL=false&serverTimezone=UTC
db.username=root
db.password=your_password
ssl.keypassword=your_ssl_password
```

> 如果没有这个文件或内容错误，会导致服务器启动失败。

---

## 💬 五、客户端说明 `Client.java`

### ✅ 功能说明：

- 提供图形界面让用户输入用户名、IP 地址、端口号
- 支持连接/断开服务器
- 发送普通消息或私信
- 接收并显示服务器广播的消息
- 实时更新在线用户列表
- 双击清空用户名，点击插入 `/msg` 命令
- 支持错误提示和断开重连机制
- 支持显示历史聊天记录
- 支持 TLS 加密通信（自动识别服务器是否启用）

### 📐 界面布局说明：

- **顶部面板**：用户名、IP、端口、连接按钮
- **中间区域**：
  - 左侧：聊天记录区（只读）
  - 右侧：在线用户列表（点击插入 `/msg` 命令）
- **底部面板**：消息输入框 + 发送按钮

---

## 🧪 六、数据库支持模块说明

### ✅ 数据库初始化脚本 `init.sql`

```sql
CREATE DATABASE IF NOT EXISTS chatdb;

USE chatdb;

CREATE TABLE IF NOT EXISTS chat_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    send_time DATETIME NOT NULL,
    sender VARCHAR(255) NOT NULL,
    receiver VARCHAR(255), -- 群发时为 NULL
    message TEXT NOT NULL,
    log_level VARCHAR(50)
);
```

### ✅ 功能增强说明：

- 所有消息（包括私信和系统通知）都写入数据库
- 使用 `ChatLogDAO` 类封装数据库操作
- 支持客户端连接后加载最近聊天记录（调用 `getRecentChatHistory(...)` 方法）
- 使用 **HikariCP** 连接池管理数据库连接
- 支持高并发场景下的稳定连接

---

## 🔒 七、TLS 加密通信说明

- 服务器使用 `keystore.p12` 文件作为 TLS 证书存储
- 客户端自动使用 SSL/TLS 协议连接服务器
- 服务器根据 `ssl.keypassword` 配置项决定是否启用加密通信
- 证书文件路径固定为当前目录下的 `keystore.p12`
- 若证书缺失或密码错误，服务器将无法启动 TLS 模式

---

## 🧱 八、编译与运行指南

### ✅ 准备工作

1. 确保你已安装 **JDK 21**
2. 下载并安装 **MySQL 8.x**
3. 创建数据库和表（运行 `init.sql`）
4. 修改 `server.properties` 中的数据库连接信息
5. 生成或获取 `keystore.p12` 证书文件（用于 TLS 加密）

---

### 🧱 编译步骤（手动方式）

#### 1. 编译服务器端

```bash
cd src
javac -d ../out/production/WS-test -cp "../lib/*" server/*.java shared/Message.java
```

#### 2. 编译客户端

```bash
javac -d ../out/production/WS-test -cp "../lib/*" client/*.java shared/Message.java
```

---

### ▶️ 启动服务器

```bash
cd out/production/WS-test
java -cp ".:../../../../../lib/*" server.Server
```

> Windows 用户请将 `:` 替换为 `;`

---

### ▶️ 启动客户端

```bash
cd out/production/WS-test
java -cp ".:../../../../../lib/*" client.Client
```

---

## 📦 九、打包成 JAR 文件（推荐）

### 1. 创建 MANIFEST 文件（确保路径正确）

**客户端 MANIFEST（src/META-INF/MANIFEST.MF）**

```txt
Manifest-Version: 1.0
Main-Class: client.Client
```

**服务器 MANIFEST（src/META-INF/MANIFEST.MF）**

```txt
Manifest-Version: 1.0
Main-Class: server.Server
```

### 2. 打包客户端 JAR

```bash
jar cfm Client.jar src/META-INF/MANIFEST.MF client/*.class shared/Message.class
```

### 3. 打包服务器 JAR

```bash
jar cfm Server.jar src/META-INF/MANIFEST.MF server/*.class shared/Message.class
```

### 4. 运行 JAR 文件

```bash
java -jar Server.jar
java -jar Client.jar
```

---

## 🧾 十、协议说明（客户端与服务端交互）

| 类型         | 协议格式                                       |
|------------|--------------------------------------------|
| **用户名注册**  | 客户端首次发送字符串即为用户名                            |
| **普通消息**   | `[username]: message`                      |
| **私信消息**   | 以 `[PRIVATE]` 开头，如：`[PRIVATE] user 私信内容`   |
| **系统消息**   | 以 `[SYSTEM]` 开头，如：`[SYSTEM] 用户 A 已上线`      |
| **历史消息**   | 以 `[HISTORY]` 开头，如：`[HISTORY] [time] 信息内容` |
| **用户列表协议** | 以 `[USER_LIST]` 开头，后面紧跟逗号分隔的用户名列表          |
| **错误提示**   | 如用户名冲突返回 `[ERROR] 用户名已存在！`                 |

---

## 🧪 十一、测试建议（适合初学者）

### ✅ 正常流程测试

1. 启动服务器
2. 启动第一个客户端 A，输入用户名 `A`
3. 启动第二个客户端 B，输入用户名 `B`
4. A 发送消息 `Hello!`，B 应收到 `[A]: Hello!`
5. B 点击用户列表中的 A，自动填充 `/msg A `，发送一条私信
6. A 应收到来自 B 的私信内容，格式为：`【私信】B: xxxxx`
7. 查看数据库表 `chat_log`，确认消息是否成功写入
8. 重启客户端，查看是否有最近聊天记录加载出来
9. 测试历史聊天记录功能，确保新加入的用户可以查看之前的聊天记录且只能看到自己发送或发给自己的私信

### ❌ 异常情况测试

- 输入非法端口，应提示“请输入有效的端口号！”
- 不输入用户名直接连接，应提示“请输入用户名和服务器信息！”
- 输入重复用户名，应提示“[ERROR] 用户名已存在！”
- 关闭服务器，客户端应检测到断开连接并提示“服务器断开连接。”

---

## 📌 十二、注意事项（新手必看）

- 请确保服务器先于客户端启动。
- 客户端连接失败时会提示“连接服务器失败，请检查网络或服务器是否运行。”
- 当服务器关闭或断开连接时，客户端会自动退出接收线程并释放资源。
- 若需多人测试，请确保在同一局域网内，或通过公网 IP 配置转发。
- 使用 MySQL 数据库时，请确保数据库服务已启动，并创建好 `chatdb` 数据库和 `chat_log` 表。
- TLS 通信需要证书文件 `keystore.p12` 和正确的密码配置，否则将降级为明文通信。

---

## 🚀 十三、后续扩展建议（适合进阶）

如果你有兴趣继续开发这个项目，可以尝试以下方向：

| 功能 | 说明 |
|------|------|
| 登录认证 | 添加密码验证、数据库用户表 |
| 使用 ORM | 使用 Hibernate 或 MyBatis 替代 JDBC |
| WebSocket 改造 | 使用 WebSocket 提升实时性与性能 |
| 多房间/频道聊天 | 支持不同的聊天频道 |
| 图片/表情传输 | 支持富文本或图片发送 |
| 管理员命令 | 如 `/kick`, `/ban` 等 |
| 日志分析系统 | 对聊天记录进行统计和分析 |
| 消息撤回功能 | 支持一定时间内撤回已发送消息 |
| 消息加密 | 使用 AES 加密私信内容（需协商密钥） |
| 支持多平台 | 打包为 Android/iOS 应用（使用 Kotlin Multiplatform） |

---

## 📞 十四、联系方式 & 反馈

如果你有任何问题、改进建议或发现 Bug，欢迎联系我！

---

📄 **文档版本：v2.0**  
📅 最后更新时间：2025年6月14日  
👤 作者：SkyDreamLG