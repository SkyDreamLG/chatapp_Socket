# 📄 WS-Test - 基于 WebSocket 的在线聊天系统  
> **项目名称：chatapp_Socket（WebSocket 聊天系统）**  
> **开发语言：Java 21**  
> **适用环境：仅在 Java 21 上测试，未验证其他版本兼容性**

---

## 🧩 一、项目简介

本项目是一个基于 **TCP Socket 的图形化聊天系统**，包含服务器端和客户端两个部分：

- 用户可以通过图形界面连接到服务器
- 支持发送公共消息（群聊）
- 支持私信功能（格式为 `/msg 用户名 内容`）
- 实时更新在线用户列表
- 所有聊天记录都会写入数据库
- 使用 HikariCP 管理数据库连接池
- 支持优雅关闭服务器并释放资源


---

## 📁 二、完整目录结构说明（当前项目）

```bash
.
├── init.sql                          # 数据库初始化脚本（创建 chat_log 表）
├── lib/                              # 第三方依赖 JAR 包
│   ├── commons-codec-1.11.jar        # Apache 工具类
│   ├── guava-30.1.1-jre.jar          # Google 工具类
│   ├── HikariCP-6.3.0.jar            # 高性能数据库连接池
│   ├── mysql-connector-java-8.0.30.jar # MySQL JDBC 驱动
│   └── ...                           # 其他依赖包
├── LICENSE                           # 开源协议文件（如有）
├── out/                              # 编译输出目录
│   ├── artifacts/
│   │   └── WS_test_jar/
│   │       └── WS-test.jar           # 打包好的可运行 JAR 文件
│   └── production/
│       └── WS-test/
│           ├── client/               # 客户端编译后的 .class 文件
│           ├── META-INF/             # MANIFEST.MF 清单文件
│           └── server/               # 服务端编译后的 .class 文件
├── README.md                         # 当前文档（项目说明）
├── server.properties                 # 服务器配置文件（端口、数据库信息）
├── src/                              # 源码目录
│   ├── client/
│   │   └── Client.java               # 客户端主程序（图形界面）
│   ├── META-INF/
│   │   └── MANIFEST.MF               # JAR 包清单文件
│   └── server/
│       ├── ChatLogDAO.java           # 聊天记录写入数据库工具类
│       ├── DBUtil.java               # 数据库连接池工具类（使用 HikariCP）
│       └── Server.java               # 服务器主程序（含 ClientHandler 内部类）
└── WS-test.iml                       # IntelliJ IDEA 项目配置文件
```

---

## 🛠️ 三、技术栈说明

| 技术 | 描述 |
|------|------|
| **语言版本** | Java 21（必须使用 JDK 21） |
| **图形界面** | Swing（纯 Java GUI 库） |
| **网络通信** | TCP Socket + DataInputStream/DataOutputStream |
| **线程管理** | Thread + ReentrantLock（保证线程安全） |
| **数据库** | MySQL（支持聊天记录持久化） |
| **连接池** | HikariCP（高性能数据库连接池） |
| **打包方式** | JAR（可通过 java -jar 运行） |

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

### ⚙️ 配置文件 `server.properties`

```properties
port=8000
db.url=jdbc:mysql://localhost:3306/chatdb?useSSL=false&serverTimezone=UTC
db.username=root
db.password=your_password
```

> 如果没有这个文件或内容错误，默认只启动服务器，不启用数据库功能。

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
    message TEXT NOT NULL
);
```

### 📌 功能说明：

- 所有群发消息和私信都会被记录进数据库
- 使用 `ChatLogDAO` 类封装数据库操作
- 使用 **HikariCP** 连接池管理数据库连接
- 支持高并发场景下的稳定连接

---

## 🧱 七、编译与运行指南

### ✅ 准备工作

1. 确保你已安装 **JDK 21**
2. 下载并安装 **MySQL 8.x**
3. 创建数据库和表（运行 `init.sql`）
4. 修改 `server.properties` 中的数据库连接信息

---

### 🧱 编译步骤（手动方式）

#### 1. 编译服务器端

```bash
cd src
javac -d ../out/production/WS-test -cp "../lib/*" server/*.java
```

#### 2. 编译客户端

```bash
javac -d ../out/production/WS-test -cp "../lib/*" client/*.java
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

## 📦 八、打包成 JAR 文件（推荐）

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
jar cfm Client.jar src/META-INF/MANIFEST.MF client/*.class
```

### 3. 打包服务器 JAR

```bash
jar cfm Server.jar src/META-INF/MANIFEST.MF server/*.class
```

### 4. 运行 JAR 文件

```bash
java -jar Server.jar
java -jar Client.jar
```

---

## 🧾 九、协议说明（客户端与服务端交互）

| 类型 | 协议格式 |
|------|----------|
| **用户名注册** | 客户端首次发送字符串即为用户名 |
| **普通消息** | `[username]: message` |
| **私信消息** | 以 `[PRIVATE]` 开头，如：`[PRIVATE] user 私信内容` |
| **用户列表协议** | 以 `[USER_LIST]` 开头，后面紧跟逗号分隔的用户名列表 |
| **错误提示** | 如用户名冲突返回 `[ERROR] 用户名已存在！` |

---

## 🧪 十、测试建议（适合初学者）

### ✅ 正常流程测试

1. 启动服务器
2. 启动第一个客户端 A，输入用户名 `A`
3. 启动第二个客户端 B，输入用户名 `B`
4. A 发送消息 `Hello!`，B 应收到 `[A]: Hello!`
5. B 点击用户列表中的 A，自动填充 `/msg A `，发送一条私信
6. A 应收到来自 B 的私信内容，格式为：`【私信】B: xxxxx`

### ❌ 异常情况测试

- 输入非法端口，应提示“请输入有效的端口号！”
- 不输入用户名直接连接，应提示“请输入用户名和服务器信息！”
- 输入重复用户名，应提示“[ERROR] 用户名已存在！”
- 关闭服务器，客户端应检测到断开连接并提示“服务器断开连接。”

---

## 📌 十一、注意事项（新手必看）

- 请确保服务器先于客户端启动。
- 客户端连接失败时会提示“连接服务器失败，请检查网络或服务器是否运行。”
- 当服务器关闭或断开连接时，客户端会自动退出接收线程并释放资源。
- 若需多人测试，请确保在同一局域网内，或通过公网 IP 配置转发。
- 使用 MySQL 数据库时，请确保数据库服务已启动，并创建好 `chatdb` 数据库和 `chat_log` 表。

---

## 🚀 十二、后续扩展建议（适合进阶）

如果你有兴趣继续开发这个项目，可以尝试以下方向：

| 功能 | 说明 |
|------|------|
| 登录认证 | 添加密码验证、数据库用户表 |
| 聊天历史回放 | 客户端连接后从数据库加载历史消息 |
| 使用 ORM | 使用 Hibernate 或 MyBatis 替代 JDBC |
| WebSocket 改造 | 使用 WebSocket 提升实时性与性能 |
| 多房间/频道聊天 | 支持不同的聊天频道 |
| 图片/表情传输 | 支持富文本或图片发送 |
| 管理员命令 | 如 `/kick`, `/ban` 等 |
| 日志分析系统 | 对聊天记录进行统计和分析 |

---

## 📞 十三、联系方式 & 反馈

如果你有任何问题、改进建议或发现 Bug，欢迎联系我！

---

📄 **文档版本：v1.4**  
📅 最后更新时间：2025年6月11日  
👤 作者：SkyDreamLG
