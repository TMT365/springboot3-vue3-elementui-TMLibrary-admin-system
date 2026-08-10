-- H2 兼容 schema(集成测试用)
-- 与 src/main/resources/db/schema.sql 的差异:
--   - 去掉 ENGINE=InnoDB DEFAULT CHARSET=utf8mb4(H2 不识别)
--   - 去掉 ON UPDATE CURRENT_TIMESTAMP(简化为普通 DEFAULT,H2 行为略有差异)
--   - H2 URL 加 MODE=MySQL 兼容 MySQL 字段字面量

CREATE TABLE book (
    id          SMALLINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(200)  NOT NULL,
    author      VARCHAR(100)  NOT NULL,
    isbn        VARCHAR(20)   NOT NULL UNIQUE,
    price       DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    published_date DATE          NOT NULL,
    stock_quantity       INT           NOT NULL DEFAULT 0,
    created_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
