-- 对话历史持久化表结构
-- 由 spring.sql.init.mode=always 在应用启动时自动执行（仅当库已存在时）

CREATE TABLE IF NOT EXISTS chat_session (
  user_id      VARCHAR(64)  NOT NULL,
  title        VARCHAR(128) NOT NULL DEFAULT '新对话',
  last_message TEXT,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      VARCHAR(64)  NOT NULL,
  role         VARCHAR(16)  NOT NULL,
  content      MEDIUMTEXT   NOT NULL,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
