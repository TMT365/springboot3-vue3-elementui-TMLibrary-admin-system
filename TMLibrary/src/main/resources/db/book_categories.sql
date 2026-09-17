-- ============================================================================
-- book_categories — 图书分类(两级:大类 / 小类)
--
-- 结构:
--   parent_id = 0  → 大类(顶层,如「文学小说」)
--   parent_id > 0  → 小类,指向所属大类(如「中国文学」挂在「文学小说」下)
--   book_count     → **直接挂载**在本分类下的图书数(大类恒为 0,
--                     商城展示时由后端把子类数量累加上去)
--
-- 为什么顶层用 0 而不是 NULL:
--   MySQL 的 UNIQUE 索引里 NULL 互不相等,parent_id=NULL 时
--   (NULL, '文学小说') 可以插任意多遍,唯一约束形同虚设。
--   用 0 当哨兵值,uk 才真正生效。
--
-- 执行:
--   mysql -u root -p tmlibrary < src/main/resources/db/book_categories.sql
--
-- 注意:本脚本同时负责给 books 表补 category_id 列(带 IF NOT EXISTS 判断,
--       MySQL 8.0 的 ADD COLUMN 不支持 IF NOT EXISTS,用存储过程兜底)。
-- ============================================================================

USE `tmlibrary`;

CREATE TABLE IF NOT EXISTS `book_categories` (
    -- AUTO_INCREMENT 是必须的:种子数据自己指定 id(固定值保证幂等),
    -- 但后台新建分类时应用层不传 id,没有自增就会报 "Field 'id' doesn't have a default value"。
    `id`           INT          NOT NULL AUTO_INCREMENT COMMENT '主键;种子数据用固定 id 保证幂等',
    `parent_id`    INT          NOT NULL DEFAULT 0      COMMENT '0 = 大类;>0 = 所属大类 id',
    `name`         VARCHAR(50)  NOT NULL                COMMENT '分类名',
    `icon`         VARCHAR(30)  NULL                    COMMENT 'Element Plus 图标名(只有大类有)',
    `sort_order`   INT          NOT NULL DEFAULT 0      COMMENT '排序,小的在前',
    `book_count`   INT          NOT NULL DEFAULT 0      COMMENT '直接挂载的图书数(大类恒为 0)',
    `created_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    -- 同一父下不允许重名(顶层用 0 才能让这条约束真正生效,见文件头说明)
    UNIQUE KEY `uk_book_categories_parent_name` (`parent_id`, `name`),
    KEY `idx_book_categories_parent_sort` (`parent_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='图书分类(两级)';

-- ---------------------------------------------------------------------------
-- 老库补 AUTO_INCREMENT(幂等:已是自增则跳过)
--
--   自增起点会落在种子最大值之后(804),新分类的 id 不再遵循"父 id × 100 + 序号"
--   的可读性约定 —— 建树靠的是 parent_id,不依赖 id 编码,所以没有影响。
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `add_book_categories_auto_increment`;
DELIMITER $$
CREATE PROCEDURE `add_book_categories_auto_increment`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'tmlibrary' AND TABLE_NAME = 'book_categories'
          AND COLUMN_NAME = 'id' AND EXTRA LIKE '%auto_increment%'
    ) THEN
        ALTER TABLE `book_categories`
            MODIFY COLUMN `id` INT NOT NULL AUTO_INCREMENT COMMENT '主键;种子数据用固定 id 保证幂等,新建的由自增分配';
    END IF;
END$$
DELIMITER ;

CALL `add_book_categories_auto_increment`();
DROP PROCEDURE `add_book_categories_auto_increment`;

-- ---------------------------------------------------------------------------
-- books 补 category_id 列(幂等:列已存在则跳过)
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS `add_books_category_id`;
DELIMITER $$
CREATE PROCEDURE `add_books_category_id`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'tmlibrary' AND TABLE_NAME = 'books' AND COLUMN_NAME = 'category_id'
    ) THEN
        ALTER TABLE `books`
            ADD COLUMN `category_id` INT NULL COMMENT '所属小类 id(逻辑引用 book_categories.id,无物理外键)' AFTER `stock_quantity`,
            ADD KEY `idx_books_category` (`category_id`);
    END IF;
END$$
DELIMITER ;

CALL `add_books_category_id`();
DROP PROCEDURE `add_books_category_id`;

-- ---------------------------------------------------------------------------
-- 种子:8 大类 + 26 小类(内容与原先前端写死的 categoryTree 一一对应)
--   id 编码规则:大类 1~8;小类 = 大类id × 100 + 序号(如 101、102…)
--   INSERT IGNORE → 重复执行不报错、不新增
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO `book_categories` (id, parent_id, name, icon, sort_order) VALUES
    -- 大类
    (1, 0, '文学小说', 'Reading',       1),
    (2, 0, '计算机',   'Cpu',           2),
    (3, 0, '历史人文', 'Clock',         3),
    (4, 0, '哲学思辨', 'MagicStick',    4),
    (5, 0, '艺术设计', 'PictureFilled', 5),
    (6, 0, '商业经管', 'DataAnalysis',  6),
    (7, 0, '教育考试', 'Notebook',      7),
    (8, 0, '少儿亲子', 'Star',          8),
    -- 文学小说
    (101, 1, '中国文学',   NULL, 1),
    (102, 1, '外国文学',   NULL, 2),
    (103, 1, '古典文学',   NULL, 3),
    (104, 1, '现当代文学', NULL, 4),
    -- 计算机
    (201, 2, '编程语言',       NULL, 1),
    (202, 2, '算法与数据结构', NULL, 2),
    (203, 2, 'AI / 机器学习',  NULL, 3),
    (204, 2, '系统架构',       NULL, 4),
    -- 历史人文
    (301, 3, '中国史',   NULL, 1),
    (302, 3, '世界史',   NULL, 2),
    (303, 3, '古代文明', NULL, 3),
    -- 哲学思辨
    (401, 4, '中国哲学', NULL, 1),
    (402, 4, '西方哲学', NULL, 2),
    (403, 4, '伦理学',   NULL, 3),
    -- 艺术设计
    (501, 5, '绘画',     NULL, 1),
    (502, 5, '平面设计', NULL, 2),
    (503, 5, '摄影',     NULL, 3),
    -- 商业经管
    (601, 6, '管理学',   NULL, 1),
    (602, 6, '金融投资', NULL, 2),
    (603, 6, '市场营销', NULL, 3),
    -- 教育考试
    (701, 7, '教材教辅', NULL, 1),
    (702, 7, '考试认证', NULL, 2),
    (703, 7, '语言学习', NULL, 3),
    -- 少儿亲子
    (801, 8, '绘本',     NULL, 1),
    (802, 8, '儿童文学', NULL, 2),
    (803, 8, '启蒙教育', NULL, 3);

-- ---------------------------------------------------------------------------
-- 给演示图书分配分类(按 ISBN,只动 seed 里那 12 本;真实书不动)
-- ---------------------------------------------------------------------------
UPDATE `books` SET `category_id` = 204 WHERE `isbn` = '9787111544937'; -- 深入理解计算机系统 → 系统架构
UPDATE `books` SET `category_id` = 202 WHERE `isbn` = '9787111407010'; -- 算法导论           → 算法与数据结构
UPDATE `books` SET `category_id` = 201 WHERE `isbn` = '9787115216878'; -- 代码整洁之道       → 编程语言
UPDATE `books` SET `category_id` = 204 WHERE `isbn` = '9787111075752'; -- 设计模式           → 系统架构
UPDATE `books` SET `category_id` = 201 WHERE `isbn` = '9787115509581'; -- 重构               → 编程语言
UPDATE `books` SET `category_id` = 201 WHERE `isbn` = '9787111370048'; -- Java 并发编程实战  → 编程语言
UPDATE `books` SET `category_id` = 201 WHERE `isbn` = '9787111543246'; -- 深入理解 Java 虚拟机 → 编程语言
UPDATE `books` SET `category_id` = 102 WHERE `isbn` = '9787544253994'; -- 百年孤独           → 外国文学
UPDATE `books` SET `category_id` = 101 WHERE `isbn` = '9787506365437'; -- 活着               → 中国文学
UPDATE `books` SET `category_id` = 101 WHERE `isbn` = '9787536692930'; -- 三体               → 中国文学
UPDATE `books` SET `category_id` = 302 WHERE `isbn` = '9787508647357'; -- 人类简史           → 世界史
UPDATE `books` SET `category_id` = 101 WHERE `isbn` = '9787530216552'; -- 沉默的大多数       → 中国文学

-- ---------------------------------------------------------------------------
-- 回填 book_count —— 从 books 实表重算,不依赖上面的分配是否完整
--   (幂等:重复执行结果一致,不怕漏改哪本)
-- ---------------------------------------------------------------------------
UPDATE `book_categories` c
SET c.`book_count` = (
    SELECT COUNT(*) FROM `books` b WHERE b.`category_id` = c.`id`
)
WHERE c.`parent_id` > 0;

-- 大类恒为 0(数量由后端累加子类得出)
UPDATE `book_categories` SET `book_count` = 0 WHERE `parent_id` = 0;

-- ---------------------------------------------------------------------------
-- 自检
-- ---------------------------------------------------------------------------
SELECT c.id, c.parent_id,
       IF(c.parent_id = 0, '大类', '  小类') AS level,
       c.name, c.book_count
FROM `book_categories` c
ORDER BY c.parent_id, c.sort_order, c.id;
