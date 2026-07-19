package tech.jxing.returnvision.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.model.entity.OperationLog;
import tech.jxing.returnvision.model.mapper.OperationLogMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【审计模块】审计日志查询服务
 *
 * 职责：提供审计日志的分页+筛选查询
 * 层级：audit 层
 * 调用方：AuditController
 * 关联：docs/06 第四章审计日志接口
 */
@Service
@Slf4j
public class AuditService {

    private final OperationLogMapper operationLogMapper;

    public AuditService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 分页查询审计日志
     *
     * 实现步骤：
     *   1. 构造查询条件（user_id / action / 时间范围）
     *   2. 按 created_at DESC 分页查询
     *   3. 组装返回
     *
     * @param page       页码
     * @param size       每页条数
     * @param userId     操作者user_id（可选）
     * @param action     操作类型（可选）
     * @param startDate  开始日期（可选）
     * @param endDate    结束日期（可选）
     * @return Map 含 logs / total / page / size
     */
    public Map<String, Object> queryLogs(int page, int size, Long userId, String action,
                                          LocalDate startDate, LocalDate endDate) {
        // 步骤1：构造查询条件
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(OperationLog::getUserId, userId);
        }
        if (action != null && !action.isEmpty()) {
            wrapper.eq(OperationLog::getAction, action);
        }
        if (startDate != null) {
            wrapper.ge(OperationLog::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(OperationLog::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        }
        wrapper.orderByDesc(OperationLog::getCreatedAt);

        // 步骤2：分页查询
        Page<OperationLog> pageObj = new Page<>(page, size);
        Page<OperationLog> result = operationLogMapper.selectPage(pageObj, wrapper);

        // 步骤3：组装返回
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("logs", result.getRecords());
        resultMap.put("total", result.getTotal());
        resultMap.put("page", page);
        resultMap.put("size", size);
        return resultMap;
    }
}
