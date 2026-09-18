#!/usr/bin/env python3
"""
把 src/main/resources/db/ 下的 8 份独立 SQL 合并成一份完整建库脚本。

用法:
    python3 scripts/gen-full-schema.py

产物:
    scripts/full-schema.sql

设计:按行过滤 + 原样拼接,**不做 SQL 语句切分**
------------------------------------------------
第一版是"按 `;` 切语句、再分类输出",踩了两个坑:

1. `SET @ORDER_MIN = ...` 不属于「建表/插入/更新」任何一类 → 被静默丢弃。
   seed-demo-data.sql 靠用户变量给 25 笔订单和 33 条明细供值,
   变量没赋值 → 只插进去 1 笔订单、0 条明细,而且因为是 INSERT IGNORE,
   **全程不报错**。
2. 存储过程体里有 `;`,按 `;` 切会把 `BEGIN...END` 切碎,
   碎片漏进输出 → `END IF` 语法错误。

结论:**SQL 脚本是有序的,分类重排等于改语义。**
所以改成逐行过滤器 —— 只删明确不要的行,其余原样保留,顺序不动。
"""

import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
DB_DIR = HERE.parent / 'src' / 'main' / 'resources' / 'db'
OUT = HERE / 'full-schema.sql'

# 顺序 = 依赖顺序
ORDER = [
    'users.sql',
    'book_categories.sql',
    'books.sql',
    'orders.sql',
    'order_items.sql',
    'feedback.sql',
    'ip_bans.sql',
    'seed-demo-data.sql',
]

# 逐行丢弃的语句(给老库做迁移用的,全新建库不需要)
DROP_PREFIX = (
    'USE ',
    'CREATE DATABASE',
    'DROP PROCEDURE',
    'CALL ',
    'ALTER TABLE',        # 老库补列/补索引
)

# 这些行**延后**到文件末尾执行
#
#   `UPDATE books SET category_id = ... WHERE isbn = '...'` 以及紧随其后的
#   `book_count` 回填,在 book_categories.sql 里位于 books 表创建之前 ——
#   按原顺序执行必然报 `Table 'books' doesn't exist`,
#   所以演示书的 category_id 一直是 NULL、分类计数一直是 0。
#
# 合并时把它们整体挪到所有 INSERT 之后,顺手修掉这个顺序问题。
#
# 用**章节横幅**当分界点而不是逐条匹配 SQL:横幅是人写的注释,位置明确,
# 一眼能看出"从这里往后要延后"。找不到横幅时**直接报错退出** ——
# 宁可生成失败,也不要悄悄产出一份跑不起来的脚本。
DEFER_FROM_BANNER = '给演示图书分配分类'


def filter_lines(text: str) -> tuple[list[str], list[str]]:
    """
    返回 (正文行, 需延后的行)。

    遇到 `DEFER_FROM_BANNER` 横幅后,该文件剩下的内容全部进入 deferred ——
    不再逐行判断。因为那一节里的语句是一个整体(赋值 + 回填 + 自检),
    拆开执行没有意义。
    """
    body: list[str] = []
    deferred: list[str] = []
    in_delim = False          # 是否在 DELIMITER $$ ... DELIMITER ; 块内
    deferring = False         # 是否已越过横幅、进入延后区

    for line in text.splitlines():
        if not deferring and DEFER_FROM_BANNER in line:
            deferring = True
        if deferring:
            deferred.append(line)
            continue

        s = line.strip()
        up = s.upper()

        # DELIMITER 是 mysql 客户端指令,进出存储过程块各出现一次
        if up.startswith('DELIMITER'):
            in_delim = not in_delim
            continue
        if in_delim:
            continue

        if any(up.startswith(p) for p in DROP_PREFIX):
            continue

        body.append(line)
    return body, deferred


def main() -> None:
    header = """-- ============================================================================
-- TMLibrary 完整建库脚本
--
-- 由 scripts/gen-full-schema.py 从 src/main/resources/db/ 下 8 份文件合并生成。
-- **不要手工编辑本文件** —— 改原始 SQL 后重新跑生成脚本。
--
-- 内容:8 张表 + 34 条分类种子(8 大类 / 26 小类)+ 演示数据
--       (5 个账号 / 12 本书 / 25 笔订单 / 33 条明细)
--
-- 用法:
--   mysql -uroot -p < scripts/full-schema.sql
--   (或在 phpMyAdmin / 宝塔的「导入」里执行)
--
-- ⚠️ 只用于**全新建库**。已存在的库上跑会因为主键冲突失败。
--    老库升级请分别执行 db/ 下的迁移脚本。
--
-- ⚠️ 演示数据(账号 / 订单)会一起插进去。生产环境如果不想要,
--    把文件末尾「演示数据」那一段整体删掉即可(不影响表结构)。
--
-- 字符集 utf8mb4 / 排序规则 utf8mb4_general_ci / 引擎 InnoDB
-- 外键:零外键(一致性由应用层 @Transactional + 行锁 + 状态守卫保证)
-- MySQL 8.0+
-- ============================================================================
"""

    parts = [
        header, '',
        'CREATE DATABASE IF NOT EXISTS `tmlibrary`',
        '    DEFAULT CHARACTER SET utf8mb4',
        '    DEFAULT COLLATE utf8mb4_general_ci;',
        '',
        'USE `tmlibrary`;',
        '',
    ]

    all_deferred: list[str] = []
    table_count = 0

    for name in ORDER:
        path = DB_DIR / name
        if not path.exists():
            print(f'  ! 跳过(不存在):{name}')
            continue

        body, deferred = filter_lines(path.read_text(encoding='utf-8'))
        all_deferred.extend(deferred)

        # 该延后的必须真延后了 —— 横幅改名/被删会导致这条断言失败,
        # 总比生成一份跑不起来的脚本强
        if name == 'book_categories.sql':
            assert deferred, (
                f'在 {name} 里没找到横幅「{DEFER_FROM_BANNER}」—— '
                '检查原始 SQL 是否改了这段注释,改了就同步更新生成脚本'
            )
            assert not any('COUNT(*) FROM `books`' in ln for ln in body), \
                'book_count 回填没被延后,会因 books 表不存在而失败'

        bar = '=' * 76
        parts.append(f'-- {bar}')
        parts.append(f'-- {name}')
        parts.append(f'-- {bar}')
        parts.append('')
        parts.extend(body)
        parts.append('')

        table_count += sum(1 for ln in body if ln.strip().upper().startswith('CREATE TABLE'))
        print(f'  {name:22s} 正文 {len(body):3d} 行'
              + (f' / 延后 {len(deferred)} 行' if deferred else ''))

    # ── 延后的分类分配(必须在演示书插入之后) ──
    if all_deferred:
        bar = '=' * 76
        parts.append(f'-- {bar}')
        parts.append('-- 给演示图书分配分类')
        parts.append('--')
        parts.append('-- 这些 UPDATE 原本在 book_categories.sql 里,位于 books 建表**之前**,')
        parts.append('-- 按原顺序执行会报 Table \'books\' doesn\'t exist —— 所以演示书的')
        parts.append('-- category_id 一直是 NULL。这里挪到所有 INSERT 之后执行。')
        parts.append(f'-- {bar}')
        parts.append('')
        parts.extend(all_deferred)
        parts.append('')

    parts.append(f'-- 合计:{table_count} 张表 + {len(all_deferred)} 条分类分配')

    OUT.write_text('\n'.join(parts), encoding='utf-8')
    print(f'\n✓ 生成 {OUT}  ({OUT.stat().st_size / 1024:.1f} KB)')


if __name__ == '__main__':
    main()
