-- ============================================================
-- F08 线上脏数据清理脚本（2026-07-22 紧急修复）
-- ============================================================
-- 背景：
--   F08 早期 uk_dict_item 含 parent_id，一级项 parent_id=NULL 时
--   MySQL 唯一约束对 NULL 不生效（NULL != NULL），导致每次重启
--   schema.sql 都重复插入 5 个一级项。线上 QUALITY/LOGISTICS 等
--   各有 3 份重复记录，引发 TooManyResultsException。
--
-- 执行前提：
--   1. 先部署修复后的代码（uk 改为 dict_id+item_code）
--   2. 再执行本脚本清理历史脏数据
--
-- 执行方式：
--   mysql -u root -p returnvision < cleanup-dict-duplicates.sql
--
-- 回滚：本脚本只 DELETE 重复行，不 DROP 表，可安全执行
-- ============================================================

-- 步骤1：查看重复情况（执行前先确认）
SELECT dict_id, item_code, COUNT(*) as dup_count
FROM sys_dict_item
WHERE status = 'active'
GROUP BY dict_id, item_code
HAVING COUNT(*) > 1;

-- 步骤2：清理重复行（保留 id 最小的那一条）
DELETE n1 FROM sys_dict_item n1
INNER JOIN sys_dict_item n2
WHERE n1.dict_id = n2.dict_id
  AND n1.item_code = n2.item_code
  AND n1.status = n2.status
  AND n1.id > n2.id;

-- 步骤3：确认清理结果（应返回 0 行）
SELECT dict_id, item_code, COUNT(*) as dup_count
FROM sys_dict_item
WHERE status = 'active'
GROUP BY dict_id, item_code
HAVING COUNT(*) > 1;

-- 步骤4：确认种子数据完整（应返回 5 行一级项）
SELECT item_code, item_label, sort_order, status
FROM sys_dict_item
WHERE dict_id = (SELECT id FROM sys_dict WHERE dict_code = 'return_category')
  AND parent_id IS NULL
  AND status = 'active'
ORDER BY sort_order;