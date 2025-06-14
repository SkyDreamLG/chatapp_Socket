CREATE TABLE IF NOT EXISTS chat_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    send_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sender VARCHAR(50) NOT NULL,
    receiver VARCHAR(50),
    message TEXT NOT NULL,
    log_level VARCHAR(50)
    );
CREATE TABLE users (
                       uid INT AUTO_INCREMENT PRIMARY KEY,
                       username VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(64) NOT NULL, -- SHA-256 哈希值长度为64字符
                       salt VARBINARY(16) NOT NULL, -- 存储二进制盐值
                       is_active BOOLEAN DEFAULT TRUE
);