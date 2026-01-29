-- =============================================
-- 精简菜单配置 - 工业故障监测平台
-- 整合后：9个菜单 → 4个核心菜单
-- 执行前请备份数据库！
-- =============================================

USE ruoyi_vue_pro;

-- =============================================
-- 1. 清理旧菜单（删除重复/冗余的菜单）
-- =============================================

-- 删除被整合的菜单
UPDATE system_menu SET deleted = b'1' WHERE component IN (
  'realtime/BackendFilterSimple',   -- Backend滤波(简化) - 与BackendFilter重复
  'realtime/SignalAnalysis',        -- 信号分析 - 功能已在FilterMonitor中
  'realtime/SignalAnalysisPlatform', -- 信号分析平台 - Tab设计不如直接菜单
  'realtime/UnifiedSignalAnalysis', -- 统一信号分析 - 与信号分析平台重复
  'realtime/AnomalyMonitor'         -- 异常监测 - 与FilterMonitor重复
) AND deleted = b'0';

-- =============================================
-- 2. 创建/更新精简后的菜单结构
-- =============================================

-- 2.1 创建"实时监控"一级目录
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '实时监控', '', 1, 10, 0, '/realtime', 'ep:monitor', NULL, 0, b'1', b'1', b'1', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE path = '/realtime' AND deleted = b'0');

SET @realtime_id = (SELECT id FROM system_menu WHERE path = '/realtime' AND deleted = b'0' LIMIT 1);

-- 2.2 实时监控（整合FilterMonitor + AnomalyMonitor）
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '实时监控', 'realtime:filter:query', 2, 1, @realtime_id, 'monitor', 'ep:data-line', 'realtime/FilterMonitor', 0, b'1', b'1', b'0', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'realtime/FilterMonitor' AND deleted = b'0');

-- 更新已存在的菜单名称和排序
UPDATE system_menu SET name = '实时监控', path = 'monitor', sort = 1 
WHERE component = 'realtime/FilterMonitor' AND deleted = b'0';

-- 2.3 历史分析（TDMS文件分析）
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '历史分析', 'realtime:tdms:query', 2, 2, @realtime_id, 'history', 'ep:document', 'realtime/TDMSSignalViewer', 0, b'1', b'1', b'0', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'realtime/TDMSSignalViewer' AND deleted = b'0');

-- 更新已存在的菜单名称和排序
UPDATE system_menu SET name = '历史分析', path = 'history', sort = 2 
WHERE component = 'realtime/TDMSSignalViewer' AND deleted = b'0';

-- 2.4 Backend服务（滤波控制台）
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 'Backend服务', 'realtime:backend:query', 2, 3, @realtime_id, 'backend', 'ep:cpu', 'realtime/BackendFilter', 0, b'1', b'1', b'0', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'realtime/BackendFilter' AND deleted = b'0');

-- 更新已存在的菜单名称和排序
UPDATE system_menu SET name = 'Backend服务', sort = 3 
WHERE component = 'realtime/BackendFilter' AND deleted = b'0';

-- 2.5 系统状态
INSERT INTO system_menu (name, permission, type, sort, parent_id, path, icon, component, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT '系统状态', 'realtime:status:query', 2, 4, @realtime_id, 'status', 'ep:circle-check', 'realtime/SystemStatus', 0, b'1', b'1', b'0', '1', NOW(), '1', NOW(), b'0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE component = 'realtime/SystemStatus' AND deleted = b'0');

-- 更新已存在的菜单排序
UPDATE system_menu SET sort = 4 
WHERE component = 'realtime/SystemStatus' AND deleted = b'0';

-- =============================================
-- 3. 清理自适应滤波器模块（如果不需要）
-- =============================================

-- 如需保留，注释掉以下行
-- UPDATE system_menu SET deleted = b'1' WHERE path = '/filter' AND deleted = b'0';
-- UPDATE system_menu SET deleted = b'1' WHERE parent_id IN (SELECT id FROM system_menu WHERE path = '/filter') AND deleted = b'0';

-- =============================================
-- 4. 为超级管理员分配菜单权限
-- =============================================

-- 分配一级菜单权限
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 1, id, '1', NOW(), '1', NOW(), b'0', 1
FROM system_menu 
WHERE path = '/realtime' AND deleted = b'0'
AND id NOT IN (SELECT menu_id FROM system_role_menu WHERE role_id = 1 AND deleted = b'0');

-- 分配子菜单权限
INSERT INTO system_role_menu (role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT 1, m.id, '1', NOW(), '1', NOW(), b'0', 1
FROM system_menu m
WHERE m.parent_id = @realtime_id
AND m.deleted = b'0'
AND m.id NOT IN (SELECT menu_id FROM system_role_menu WHERE role_id = 1 AND deleted = b'0');

-- =============================================
-- 5. 查看整合结果
-- =============================================

SELECT 
    CASE WHEN parent_id = 0 THEN '📁' ELSE '  📄' END AS 类型,
    id,
    name AS 菜单名称,
    path AS 路径,
    component AS 组件,
    sort AS 排序
FROM system_menu
WHERE (path = '/realtime' OR parent_id = @realtime_id)
AND deleted = b'0'
ORDER BY parent_id, sort;

SELECT '✅ 菜单整合完成！9个菜单 → 4个核心菜单' AS 提示;
SELECT '请清除浏览器缓存并重新登录查看效果。' AS 操作提示;
