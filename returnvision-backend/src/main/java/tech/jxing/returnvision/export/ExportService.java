package tech.jxing.returnvision.export;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.model.entity.ReturnRecord;
import tech.jxing.returnvision.model.mapper.ReturnRecordMapper;
import tech.jxing.returnvision.security.AuthUser;

import java.time.LocalDate;
import java.util.List;

/**
 * 【导出管控模块】导出业务服务（F02）
 *
 * 职责：导出退货记录 Excel（含水印 + 限流 + 按角色过滤数据范围）
 * 层级：export 层
 * 调用方：ExportController
 * 关联：docs/04 第 4.9.5 节
 *
 * 防护策略：
 *   1. 强制审计（由 Controller 的 @AuditLog 完成）
 *   2. 水印（导出人+时间+IP+条数）
 *   3. 限流（单次 1000 条 + 每日 5 次）
 *   4. 告警（导出后飞书通知，由 Controller 完成）
 *
 * 数据范围：
 *   - STAFF：只能导出自己处理的（created_by = 当前用户）
 *   - SUPERVISOR/ADMIN：可导出全部
 */
@Service
@Slf4j
public class ExportService {

    private static final String STATUS_SYNCED = "synced";
    private static final String STATUS_PENDING = "pending";

    private final ReturnRecordMapper returnRecordMapper;
    private final ExportRateLimiter rateLimiter;
    private final int maxRowsPerExport;

    public ExportService(ReturnRecordMapper returnRecordMapper,
                         ExportRateLimiter rateLimiter,
                         org.springframework.core.env.Environment env) {
        this.returnRecordMapper = returnRecordMapper;
        this.rateLimiter = rateLimiter;
        this.maxRowsPerExport = env.getProperty("export.max-rows-per-export", Integer.class, 1000);
    }

    /**
     * 导出退货记录
     *
     * 实现步骤：
     *   1. 限流校验（每日次数）
     *   2. 构造查询条件（按角色过滤 + 筛选条件）
     *   3. 数量校验（单次不超过 1000）
     *   4. 查询数据
     *   5. 生成带水印的 Excel
     *
     * @param currentUser 当前用户
     * @param status      状态筛选（可选）
     * @param startDate   开始日期（可选）
     * @param endDate     结束日期（可选）
     * @param request     HttpServletRequest（取 IP）
     * @return Excel 字节数组
     */
    public byte[] exportReturnRecords(AuthUser currentUser, String status,
                                       LocalDate startDate, LocalDate endDate,
                                       HttpServletRequest request) {
        // 步骤1：限流校验（每日次数）
        if (!rateLimiter.tryAcquire(currentUser.getUserId())) {
            throw new BizException(1011, "今日导出次数已超限（最多 5 次）");
        }

        // 步骤2：构造查询条件
        LambdaQueryWrapper<ReturnRecord> wrapper = new LambdaQueryWrapper<>();

        // 步骤2.1：按角色过滤数据范围（复用 F03 客服范围细化）
        if (currentUser.getRoles() != null) {
            List<String> roles = currentUser.getRoles();
            boolean isStaffOnly = roles.contains("STAFF")
                    && !roles.contains("SUPERVISOR")
                    && !roles.contains("ADMIN");
            if (isStaffOnly) {
                wrapper.eq(ReturnRecord::getCreatedBy, currentUser.getUserId());
            }
        }

        // 步骤2.2：状态筛选
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ReturnRecord::getStatus, status);
        }

        // 步骤2.3：日期范围筛选
        if (startDate != null) {
            wrapper.ge(ReturnRecord::getCreatedAt, startDate.atStartOfDay());
        }
        if (endDate != null) {
            wrapper.le(ReturnRecord::getCreatedAt, endDate.plusDays(1).atStartOfDay());
        }

        wrapper.orderByDesc(ReturnRecord::getCreatedAt);

        // 步骤3：数量校验（先查总数，超限拒绝）
        long total = returnRecordMapper.selectCount(wrapper);
        if (total > maxRowsPerExport) {
            log.warn("[导出管控] 用户 {} 导出超限：{} 条（上限 {}）", currentUser.getUsername(), total, maxRowsPerExport);
            throw new BizException(1011, "导出条数超限：" + total + " 条（单次上限 " + maxRowsPerExport + " 条），请缩小筛选范围");
        }

        // 步骤4：查询数据
        List<ReturnRecord> records = returnRecordMapper.selectList(wrapper);
        log.info("[导出管控] 用户 {} 导出 {} 条退货记录", currentUser.getUsername(), records.size());

        // 步骤5：生成带水印的 Excel
        String exportUser = currentUser.getUsername();
        String exportIp = getClientIp(request);

        return ExcelWatermarkUtil.buildReturnRecordsExcel(records, exportUser, exportIp);
    }

    /**
     * 获取客户端真实 IP（处理代理转发）
     */
    private String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
