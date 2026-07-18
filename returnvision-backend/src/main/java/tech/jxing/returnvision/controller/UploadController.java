package tech.jxing.returnvision.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.jxing.returnvision.common.ResponseResult;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.feishu.FeishuService;
import tech.jxing.returnvision.model.entity.ReturnRecord;
import tech.jxing.returnvision.model.mapper.ReturnRecordMapper;
import tech.jxing.returnvision.service.CosClientService;
import tech.jxing.returnvision.service.LlmAnalyzerService;
import tech.jxing.returnvision.service.OcrCrossValidatorService;
import tech.jxing.returnvision.service.ValidatorService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 【接口层】退货OCR识别控制器
 *
 * 职责：串联所有服务，提供8个API接口
 * 层级：Controller 层
 *
 * 接口列表：
 *   POST /api/upload         - 上传图片识别（同步）
 *   POST /api/upload/sse     - SSE流式上传识别（实时步骤推送）
 *   POST /api/upload/batch   - 批量上传识别（返回扁平数组）
 *   POST /api/confirm        - 确认写入飞书（单条）
 *   POST /api/confirm/batch   - 批量确认写入飞书
 *   GET  /api/records         - 查询记录列表（分页）
 *   POST /api/records/batch   - 批量上传（旧接口，返回汇总结构）
 *   GET  /api/dashboard/stats - 仪表盘统计数据
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class UploadController {

    private final CosClientService cosClientService;
    private final OcrCrossValidatorService crossValidatorService;
    private final LlmAnalyzerService llmAnalyzerService;
    private final ValidatorService validatorService;
    private final FeishuService feishuService;
    private final ReturnRecordMapper recordMapper;
    private final ObjectMapper objectMapper;

    /** SSE异步处理线程池（守护线程，不阻塞JVM关闭） */
    private final ExecutorService sseExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "sse-worker");
        t.setDaemon(true);
        return t;
    });

    public UploadController(CosClientService cosClientService,
                            OcrCrossValidatorService crossValidatorService,
                            LlmAnalyzerService llmAnalyzerService,
                            ValidatorService validatorService,
                            FeishuService feishuService,
                            ReturnRecordMapper recordMapper,
                            ObjectMapper objectMapper) {
        this.cosClientService = cosClientService;
        this.crossValidatorService = crossValidatorService;
        this.llmAnalyzerService = llmAnalyzerService;
        this.validatorService = validatorService;
        this.feishuService = feishuService;
        this.recordMapper = recordMapper;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdown() {
        sseExecutor.shutdown();
        try {
            if (!sseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            sseExecutor.shutdownNow();
        }
    }

    // ==================== 上传识别接口 ====================

    /**
     * 上传快递面单图片，自动OCR识别（同步模式）
     *
     * 业务流程：
     *   1. 上传图片到腾讯云COS
     *   2. 双引擎OCR并行识别 + 交叉验证
     *   3. DeepSeek LLM分析（退货原因+分类）
     *   4. 数据校验（运单号/电话/地址格式）
     *   5. 保存到数据库（status=pending）
     *   6. 返回识别结果供前端展示
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/upload")
    public ResponseResult<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            // 步骤1：上传图片到COS
            byte[] imageBytes = file.getBytes();
            String filename = System.currentTimeMillis() + "-" + file.getOriginalFilename();
            String cosUrl = cosClientService.uploadToCos(imageBytes, filename);
            log.info("[上传] COS上传完成，url={}", cosUrl);

            // 步骤2：双引擎OCR + 交叉验证
            Map<String, Object> ocrResult = crossValidatorService.dualEngineOcr(cosUrl);
            Map<String, Object> ocrData = (Map<String, Object>) ocrResult.getOrDefault("data", new HashMap<>());

            // 步骤3：DeepSeek LLM分析（OCR有数据时才调用）
            Map<String, Object> llmResult = new HashMap<>();
            if (ocrData != null && !getString(ocrData, "waybill_no").isEmpty()) {
                llmResult = llmAnalyzerService.analyze(ocrData);
            }

            // 步骤4：数据校验
            Map<String, Object> validationResult = validatorService.validate(ocrData != null ? ocrData : new HashMap<>());

            // 步骤4.1：OCR 有效数据校验--识别失败时不写库，避免退货记录页出现"失败数据"
            if (ocrData == null
                    || (getString(ocrData, "waybill_no").isEmpty() && getString(ocrData, "rec_name").isEmpty())) {
                log.warn("[上传] 双引擎OCR均未识别出有效数据，不写入数据库，cosUrl={}", cosUrl);
                throw new BizException(2001, "面单识别失败：未识别出有效运单信息，请重新上传清晰的面单图片");
            }

            // 步骤5：保存到数据库
            ReturnRecord record = buildRecord(ocrData, llmResult, ocrResult, cosUrl, validationResult);
            recordMapper.insert(record);
            log.info("[上传] 记录已保存，record_id={}", record.getId());

            // 步骤6：返回识别结果
            Map<String, Object> result = new HashMap<>();
            result.put("record_id", record.getId());
            result.put("data", ocrData);
            result.put("cross_validation", ocrResult.get("action"));
            result.put("confidence", ocrResult.get("confidence"));
            result.put("source", ocrResult.get("source"));
            result.put("diff_fields", ocrResult.getOrDefault("diff_fields", new ArrayList<>()));
            result.put("diff_detail", ocrResult.getOrDefault("diff_detail", new HashMap<>()));
            result.put("return_reason", llmResult.getOrDefault("return_reason", ""));
            result.put("return_category", llmResult.getOrDefault("return_category", ""));
            result.put("image_url", cosUrl);
            result.put("validation", validationResult);
            return ResponseResult.success(result);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[上传] 处理失败", e);
            throw new BizException(9001, "上传处理失败：" + e.getMessage());
        }
    }

    /**
     * SSE流式上传识别（实时步骤推送）
     *
     * 业务流程：
     *   1. 创建SseEmitter，异步执行识别流程
     *   2. 推送步骤1：COS上传（active → done）
     *   3. 推送步骤2：双引擎OCR并行识别（active → done，含子步骤）
     *   4. 推送步骤3：交叉验证仲裁（active → done）
     *   5. 推送步骤4：DeepSeek语义分析（active → done）
     *   6. 推送结果事件：扁平化识别结果
     *   7. 完成SSE流
     *
     * 前端通过 fetch + ReadableStream 读取 data: 行，解析JSON事件
     */
    @PostMapping(value = "/upload/sse")
    public SseEmitter uploadSse(@RequestParam("file") MultipartFile file) {
        // 步骤1：创建SseEmitter，设置2分钟超时
        SseEmitter emitter = new SseEmitter(120_000L);
        log.info("[SSE上传] 开始，filename={}", file.getOriginalFilename());

        // 步骤2：异步执行识别流程
        sseExecutor.execute(() -> {
            try {
                // 步骤2.1：推送步骤1 active
                sendSseEvent(emitter, "step", 1, "上传至云存储", "active", null, null);

                // COS上传
                byte[] imageBytes = file.getBytes();
                String filename = System.currentTimeMillis() + "-" + file.getOriginalFilename();
                String cosUrl = cosClientService.uploadToCos(imageBytes, filename);
                log.info("[SSE上传] COS上传完成，url={}", cosUrl);

                // 推送步骤1 done
                sendSseEvent(emitter, "step", 1, "上传至云存储", "done", null, null);

                // 步骤2.2：推送步骤2 active（含子步骤）
                List<Map<String, String>> subStepsActive = new ArrayList<>();
                subStepsActive.add(subStep("智谱OCR", "active"));
                subStepsActive.add(subStep("阿里云OCR", "active"));
                sendSseEvent(emitter, "step", 2, "双引擎OCR并行识别", "active", subStepsActive, null);

                // 双引擎OCR + 交叉验证（dualEngineOcr内部完成两步）
                Map<String, Object> ocrResult = crossValidatorService.dualEngineOcr(cosUrl);
                Map<String, Object> ocrData = (Map<String, Object>) ocrResult.getOrDefault("data", new HashMap<>());

                // 推送步骤2 done + 步骤3 done（OCR和验证已在同一步完成）
                List<Map<String, String>> subStepsDone = new ArrayList<>();
                subStepsDone.add(subStep("智谱OCR", "done"));
                subStepsDone.add(subStep("阿里云OCR", "done"));
                sendSseEvent(emitter, "step", 2, "双引擎OCR并行识别", "done", subStepsDone, null);

                // 步骤2.3：推送步骤3 active → done（交叉验证已在dualEngineOcr中完成，几乎瞬时）
                sendSseEvent(emitter, "step", 3, "交叉验证 + 仲裁", "active", null, null);
                sendSseEvent(emitter, "step", 3, "交叉验证 + 仲裁", "done", null, null);

                // 步骤2.4：推送步骤4 active
                sendSseEvent(emitter, "step", 4, "DeepSeek 语义分析", "active", null, null);

                // DeepSeek LLM分析
                Map<String, Object> llmResult = new HashMap<>();
                if (ocrData != null && !getString(ocrData, "waybill_no").isEmpty()) {
                    llmResult = llmAnalyzerService.analyze(ocrData);
                }

                // 推送步骤4 done
                sendSseEvent(emitter, "step", 4, "DeepSeek 语义分析", "done", null, null);

                // 步骤2.5：数据校验
                Map<String, Object> validationResult = validatorService.validate(ocrData != null ? ocrData : new HashMap<>());

                // 步骤2.5.1：OCR 有效数据校验--识别失败时不写库，避免退货记录页出现"失败数据"
                // 判定标准：运单号和收件人姓名均为空，视为双引擎均未识别出有效信息
                if (ocrData == null
                        || (getString(ocrData, "waybill_no").isEmpty() && getString(ocrData, "rec_name").isEmpty())) {
                    log.warn("[SSE上传] 双引擎OCR均未识别出有效数据，不写入数据库，cosUrl={}", cosUrl);
                    throw new BizException(2001, "面单识别失败：未识别出有效运单信息，请重新上传清晰的面单图片");
                }

                // 步骤2.6：保存到数据库
                ReturnRecord record = buildRecord(ocrData, llmResult, ocrResult, cosUrl, validationResult);
                recordMapper.insert(record);
                log.info("[SSE上传] 记录已保存，record_id={}", record.getId());

                // 步骤2.7：构建扁平结果并推送
                Map<String, Object> resultData = buildFlatResult(ocrData, llmResult, ocrResult, cosUrl, validationResult, record.getId());
                sendSseResultEvent(emitter, resultData);

                // 步骤3：完成SSE流
                emitter.complete();
                log.info("[SSE上传] 完成，record_id={}", record.getId());
            } catch (Exception e) {
                log.error("[SSE上传] 处理失败", e);
                sendSseErrorEvent(emitter, e.getMessage() != null ? e.getMessage() : "处理失败");
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * 批量上传快递面单图片（返回扁平数组，前端直接渲染列表）
     *
     * 业务流程：
     *   1. 遍历所有上传的文件
     *   2. 对每个文件执行上传识别流程
     *   3. 返回结果数组（每项含record_id/waybill_no/express_company/rec_name/return_reason/cross_validation）
     */
    @PostMapping("/upload/batch")
    public ResponseResult<List<Map<String, Object>>> uploadBatch(@RequestParam("files") MultipartFile[] files) {
        log.info("[批量上传] 共{}个文件", files.length);

        // 步骤1-2：遍历文件，逐个处理
        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                Map<String, Object> result = doUploadInternal(file);
                results.add(result);
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("filename", file.getOriginalFilename());
                errorResult.put("error", e.getMessage());
                errorResult.put("cross_validation", "error");
                results.add(errorResult);
                log.warn("[批量上传] 文件处理失败：{}，原因：{}", file.getOriginalFilename(), e.getMessage());
            }
        }

        // 步骤3：返回扁平数组
        log.info("[批量上传] 完成，成功{}，失败{}", 
                results.stream().filter(r -> !r.getOrDefault("cross_validation", "").equals("error")).count(),
                results.stream().filter(r -> r.getOrDefault("cross_validation", "").equals("error")).count());
        return ResponseResult.success(results);
    }

    /**
     * 批量确认写入飞书
     *
     * 业务流程：
     *   1. 解析record_ids数组
     *   2. 遍历每个record_id，执行确认流程
     *   3. 汇总成功/失败数量
     */
    @PostMapping("/confirm/batch")
    public ResponseResult<Map<String, Object>> batchConfirm(@RequestBody Map<String, Object> request) {
        // 步骤1：解析record_ids
        Object idsObj = request.get("record_ids");
        if (idsObj == null) {
            throw new BizException(1001, "record_ids不能为空");
        }
        @SuppressWarnings("unchecked")
        List<Object> idList = (List<Object>) idsObj;
        log.info("[批量确认] 共{}条记录", idList.size());

        // 步骤2：遍历确认
        int success = 0;
        int failed = 0;
        List<Map<String, Object>> failedItems = new ArrayList<>();
        for (Object idObj : idList) {
            try {
                Long recordId = Long.valueOf(idObj.toString());
                confirmSingle(recordId);
                success++;
            } catch (Exception e) {
                failed++;
                Map<String, Object> failedItem = new HashMap<>();
                failedItem.put("record_id", idObj);
                failedItem.put("error", e.getMessage());
                failedItems.add(failedItem);
                log.warn("[批量确认] 记录{}确认失败：{}", idObj, e.getMessage());
            }
        }

        // 步骤3：返回汇总
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("failed_items", failedItems);
        log.info("[批量确认] 完成，成功{}，失败{}", success, failed);
        return ResponseResult.success(result);
    }

    /**
     * 仪表盘统计数据
     *
     * 业务流程：
     *   1. 统计今日上传量
     *   2. 统计各状态数量（pending/synced/review）
     *   3. 统计7天趋势
     *   4. 查询最近5条记录
     *   5. 组装返回
     */
    @GetMapping("/dashboard/stats")
    public ResponseResult<Map<String, Object>> dashboardStats() {
        // 步骤1：统计今日上传量
        LocalDateTime todayStart = LocalDateTime.now().with(LocalTime.MIN);
        Long todayCount = recordMapper.selectCount(new LambdaQueryWrapper<ReturnRecord>()
                .ge(ReturnRecord::getCreatedAt, todayStart));

        // 步骤2：统计各状态数量
        Long totalCount = recordMapper.selectCount(new LambdaQueryWrapper<>());
        Long pendingCount = recordMapper.selectCount(new LambdaQueryWrapper<ReturnRecord>()
                .eq(ReturnRecord::getStatus, "pending"));
        Long syncedCount = recordMapper.selectCount(new LambdaQueryWrapper<ReturnRecord>()
                .eq(ReturnRecord::getStatus, "synced"));
        Long confirmedCount = recordMapper.selectCount(new LambdaQueryWrapper<ReturnRecord>()
                .eq(ReturnRecord::getStatus, "confirmed"));

        // 步骤3：7天趋势（按日期分组统计）
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7).with(LocalTime.MIN);
        QueryWrapper<ReturnRecord> trendWrapper = new QueryWrapper<>();
        trendWrapper.select("DATE(created_at) as date", "COUNT(*) as count")
                .ge("created_at", weekAgo)
                .groupBy("DATE(created_at)")
                .orderByAsc("DATE(created_at)");
        List<Map<String, Object>> trend = recordMapper.selectMaps(trendWrapper);

        // 步骤4：最近5条记录
        Page<ReturnRecord> recentPage = new Page<>(1, 5);
        LambdaQueryWrapper<ReturnRecord> recentWrapper = new LambdaQueryWrapper<ReturnRecord>()
                .orderByDesc(ReturnRecord::getCreatedAt);
        List<ReturnRecord> recent = recordMapper.selectPage(recentPage, recentWrapper).getRecords();

        // 步骤5：组装返回
        Map<String, Object> stats = new HashMap<>();
        stats.put("today_count", todayCount);
        stats.put("total_count", totalCount);
        stats.put("pending_count", pendingCount);
        stats.put("synced_count", syncedCount);
        stats.put("review_count", confirmedCount);
        stats.put("trend", trend);
        stats.put("recent_records", recent);
        log.info("[仪表盘] 统计完成，今日={}，总计={}，待确认={}，已同步={}",
                todayCount, totalCount, pendingCount, syncedCount);
        return ResponseResult.success(stats);
    }

    // ==================== 确认写入飞书接口 ====================

    /**
     * 确认退货记录，写入飞书多维表格（单条）
     *
     * 业务流程：
     *   1. 根据record_id加载记录
     *   2. 如果有edited_data，更新记录字段
     *   3. 更新状态为confirmed
     *   4. 写入飞书多维表格
     *   5. 更新状态为synced，回填feishu_record_id
     */
    @PostMapping("/confirm")
    public ResponseResult<Map<String, Object>> confirm(@RequestBody Map<String, Object> request) {
        // 步骤1：加载记录
        Long recordId = Long.valueOf(request.get("record_id").toString());
        ReturnRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(1002, "记录不存在：id=" + recordId);
        }

        // 步骤2：应用前端编辑的数据
        @SuppressWarnings("unchecked")
        Map<String, Object> editedData = (Map<String, Object>) request.get("edited_data");
        if (editedData != null) {
            applyEditedData(record, editedData);
        }

        // 步骤3-5：确认并写入飞书
        String feishuRecordId = confirmSingle(recordId, record);

        // 步骤6：返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("feishu_record_id", feishuRecordId);
        return ResponseResult.success(result);
    }

    // ==================== 查询记录接口 ====================

    /**
     * 查询退货记录列表
     *
     * 业务流程：
     *   1. 构建分页查询条件
     *   2. 按状态筛选（可选），按创建时间倒序
     *   3. 返回分页结果
     */
    @GetMapping("/records")
    public ResponseResult<Map<String, Object>> records(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        // 步骤1：构建分页查询
        Page<ReturnRecord> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<ReturnRecord> wrapper = new LambdaQueryWrapper<>();

        // 步骤2：按状态筛选 + 时间倒序
        if (!status.isEmpty()) {
            wrapper.eq(ReturnRecord::getStatus, status);
        }
        wrapper.orderByDesc(ReturnRecord::getCreatedAt);

        Page<ReturnRecord> result = recordMapper.selectPage(pageObj, wrapper);

        // 步骤3：返回分页结果
        Map<String, Object> response = new HashMap<>();
        response.put("total", result.getTotal());
        response.put("page", page);
        response.put("size", size);
        response.put("records", result.getRecords());
        return ResponseResult.success(response);
    }

    /**
     * 删除单条退货记录
     *
     * 业务流程：
     *   1. 加载记录，校验是否存在
     *   2. 校验状态：已同步(synced)的记录不允许删除
     *   3. 删除数据库记录
     */
    @DeleteMapping("/records/{id}")
    public ResponseResult<Map<String, Object>> deleteRecord(@PathVariable Long id) {
        // 步骤1：加载记录
        ReturnRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException(1002, "记录不存在：id=" + id);
        }

        // 步骤2：校验状态——已同步记录不允许删除
        if ("synced".equals(record.getStatus())) {
            throw new BizException(1003, "已同步至飞书的记录不允许删除");
        }

        // 步骤3：删除记录
        recordMapper.deleteById(id);
        log.info("[删除] 记录已删除，id={}", id);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("id", id);
        return ResponseResult.success(result);
    }

    /**
     * 批量删除退货记录
     *
     * 业务流程：
     *   1. 解析 record_ids 数组
     *   2. 逐条校验并删除（已同步记录跳过）
     *   3. 汇总成功/失败数量
     */
    @DeleteMapping("/records/batch")
    public ResponseResult<Map<String, Object>> batchDeleteRecords(@RequestBody Map<String, Object> request) {
        // 步骤1：解析 record_ids
        Object idsObj = request.get("record_ids");
        if (idsObj == null) {
            throw new BizException(1001, "record_ids不能为空");
        }
        @SuppressWarnings("unchecked")
        List<Object> idList = (List<Object>) idsObj;
        log.info("[批量删除] 共{}条记录", idList.size());

        // 步骤2：逐条删除
        int success = 0;
        int failed = 0;
        List<Map<String, Object>> failedItems = new ArrayList<>();
        for (Object idObj : idList) {
            try {
                Long recordId = Long.valueOf(idObj.toString());
                ReturnRecord record = recordMapper.selectById(recordId);
                if (record == null) {
                    throw new BizException(1002, "记录不存在");
                }
                if ("synced".equals(record.getStatus())) {
                    throw new BizException(1003, "已同步记录不允许删除");
                }
                recordMapper.deleteById(recordId);
                success++;
            } catch (Exception e) {
                failed++;
                Map<String, Object> failedItem = new HashMap<>();
                failedItem.put("record_id", idObj);
                failedItem.put("error", e.getMessage());
                failedItems.add(failedItem);
            }
        }

        // 步骤3：返回汇总
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("failed_items", failedItems);
        log.info("[批量删除] 完成，成功{}，失败{}", success, failed);
        return ResponseResult.success(result);
    }

    /**
     * 批量上传快递面单图片（旧接口，返回汇总结构）
     *
     * 业务流程：
     *   1. 遍历所有上传的文件
     *   2. 对每个文件执行上传识别流程
     *   3. 汇总成功/失败数量
     */
    @PostMapping("/records/batch")
    public ResponseResult<Map<String, Object>> batchUpload(@RequestParam("files") MultipartFile[] files) {
        log.info("[批量上传-旧] 共{}个文件", files.length);

        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int failed = 0;

        // 步骤1-2：遍历文件，逐个处理
        for (MultipartFile file : files) {
            try {
                Map<String, Object> result = doUploadInternal(file);
                results.add(result);
                success++;
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("filename", file.getOriginalFilename());
                errorResult.put("error", e.getMessage());
                results.add(errorResult);
                failed++;
                log.warn("[批量上传-旧] 文件处理失败：{}，原因：{}", file.getOriginalFilename(), e.getMessage());
            }
        }

        // 步骤3：汇总结果
        Map<String, Object> response = new HashMap<>();
        response.put("total", files.length);
        response.put("success", success);
        response.put("failed", failed);
        response.put("results", results);
        log.info("[批量上传-旧] 完成，成功{}，失败{}", success, failed);
        return ResponseResult.success(response);
    }

    // ==================== 内部方法 ====================

    /**
     * 单条确认写入飞书（内部复用，供confirm和batchConfirm调用）
     *
     * 实现步骤：
     *   1. 加载记录
     *   2. 更新状态为confirmed
     *   3. 写入飞书多维表格
     *   4. 更新状态为synced，回填feishu_record_id
     */
    private String confirmSingle(Long recordId) {
        ReturnRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(1002, "记录不存在：id=" + recordId);
        }
        return confirmSingle(recordId, record);
    }

    /**
     * 单条确认写入飞书（已加载记录的版本）
     *
     * 实现步骤：
     *   1. 更新状态为confirmed
     *   2. 写入飞书多维表格
     *   3. 更新状态为synced，回填feishu_record_id
     */
    private String confirmSingle(Long recordId, ReturnRecord record) {
        // 步骤1：更新状态为confirmed
        record.setStatus("confirmed");
        record.setConfirmedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.info("[确认] 记录已确认，record_id={}", recordId);

        // 步骤2：写入飞书
        Map<String, Object> feishuData = convertRecordToMap(record);
        String feishuRecordId = feishuService.writeRecord(feishuData, record.getImageUrl());

        // 步骤3：更新状态为synced
        record.setFeishuRecordId(feishuRecordId);
        record.setStatus("synced");
        record.setSyncedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.info("[确认] 飞书写入完成，record_id={}, feishu_record_id={}", recordId, feishuRecordId);

        return feishuRecordId;
    }

    /**
     * 上传识别内部逻辑（供upload和batchUpload复用）
     *
     * 实现步骤：
     *   1. 上传图片到COS
     *   2. 双引擎OCR + 交叉验证
     *   3. DeepSeek LLM分析
     *   4. 数据校验
     *   5. 保存到数据库
     *   6. 返回扁平结果
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> doUploadInternal(MultipartFile file) throws Exception {
        // 步骤1：COS上传
        byte[] imageBytes = file.getBytes();
        String filename = System.currentTimeMillis() + "-" + file.getOriginalFilename();
        String cosUrl = cosClientService.uploadToCos(imageBytes, filename);

        // 步骤2：双引擎OCR
        Map<String, Object> ocrResult = crossValidatorService.dualEngineOcr(cosUrl);
        Map<String, Object> ocrData = (Map<String, Object>) ocrResult.getOrDefault("data", new HashMap<>());

        // 步骤3：LLM分析
        Map<String, Object> llmResult = new HashMap<>();
        if (ocrData != null && !getString(ocrData, "waybill_no").isEmpty()) {
            llmResult = llmAnalyzerService.analyze(ocrData);
        }

        // 步骤4：数据校验
        Map<String, Object> validationResult = validatorService.validate(ocrData != null ? ocrData : new HashMap<>());

        // 步骤4.1：OCR 有效数据校验--识别失败时不写库，避免退货记录页出现"失败数据"
        if (ocrData == null
                || (getString(ocrData, "waybill_no").isEmpty() && getString(ocrData, "rec_name").isEmpty())) {
            log.warn("[批量上传] 双引擎OCR均未识别出有效数据，不写入数据库，filename={}, cosUrl={}",
                    file.getOriginalFilename(), cosUrl);
            throw new BizException(2001, "面单识别失败：未识别出有效运单信息");
        }

        // 步骤5：保存到数据库
        ReturnRecord record = buildRecord(ocrData, llmResult, ocrResult, cosUrl, validationResult);
        recordMapper.insert(record);

        // 步骤6：返回扁平结果
        Map<String, Object> result = new HashMap<>();
        result.put("record_id", record.getId());
        result.put("filename", file.getOriginalFilename());
        result.put("waybill_no", getString(ocrData, "waybill_no"));
        result.put("express_company", getString(ocrData, "express_company"));
        result.put("rec_name", getString(ocrData, "rec_name"));
        result.put("return_reason", getString(llmResult, "return_reason"));
        result.put("return_category", getString(llmResult, "return_category"));
        result.put("cross_validation", ocrResult.get("action"));
        result.put("image_url", cosUrl);
        return result;
    }

    /**
     * 构建扁平化识别结果（SSE结果事件专用）
     *
     * 实现步骤：
     *   1. 填充OCR基础字段
     *   2. 填充LLM分析结果
     *   3. 填充交叉验证信息（置信度转数值、差异字段）
     *   4. 填充校验结果和图片URL
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildFlatResult(Map<String, Object> ocrData, Map<String, Object> llmResult,
                                                Map<String, Object> ocrResult, String cosUrl,
                                                Map<String, Object> validationResult, Long recordId) {
        Map<String, Object> result = new HashMap<>();

        // 步骤1：OCR基础字段
        result.put("record_id", recordId);
        result.put("waybill_no", getString(ocrData, "waybill_no"));
        result.put("express_company", getString(ocrData, "express_company"));
        result.put("goods", getString(ocrData, "goods"));
        result.put("rec_name", getString(ocrData, "rec_name"));
        result.put("rec_phone", getString(ocrData, "rec_phone"));
        result.put("rec_address", getString(ocrData, "rec_address"));
        result.put("sender_name", getString(ocrData, "sender_name"));
        result.put("sender_phone", getString(ocrData, "sender_phone"));
        result.put("sender_address", getString(ocrData, "sender_address"));

        // 步骤2：LLM分析结果
        result.put("return_reason", getString(llmResult, "return_reason"));
        result.put("return_category", getString(llmResult, "return_category"));

        // 步骤3：交叉验证信息
        result.put("cross_validation", ocrResult.get("action"));
        result.put("engine_source", ocrResult.get("source"));
        // 置信度字符串转数值（前端按数值范围判断高/中/低）
        String confStr = getString(ocrResult, "confidence");
        double confNum = switch (confStr) {
            case "high" -> 0.95;
            case "medium" -> 0.75;
            case "low" -> 0.5;
            default -> 0.7;
        };
        result.put("confidence", confNum);
        result.put("diff_fields", ocrResult.getOrDefault("diff_fields", new ArrayList<>()));
        result.put("diff_detail", ocrResult.getOrDefault("diff_detail", new HashMap<>()));

        // 步骤4：校验结果和图片URL
        result.put("image_url", cosUrl);
        result.put("validation_errors", validationResult.getOrDefault("errors", new ArrayList<>()));
        result.put("validation_warnings", validationResult.getOrDefault("warnings", new ArrayList<>()));

        return result;
    }

    /**
     * 发送SSE步骤事件
     */
    private void sendSseEvent(SseEmitter emitter, String type, int step, String label,
                             String status, List<Map<String, String>> subSteps, String meta) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        event.put("step", step);
        event.put("label", label);
        event.put("status", status);
        if (subSteps != null) {
            event.put("subSteps", subSteps);
        }
        if (meta != null) {
            event.put("meta", meta);
        }
        sendSseJson(emitter, event);
    }

    /**
     * 发送SSE结果事件
     */
    private void sendSseResultEvent(SseEmitter emitter, Map<String, Object> data) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "result");
        event.put("data", data);
        sendSseJson(emitter, event);
    }

    /**
     * 发送SSE错误事件
     */
    private void sendSseErrorEvent(SseEmitter emitter, String msg) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "error");
        event.put("msg", msg);
        sendSseJson(emitter, event);
    }

    /**
     * 发送SSE JSON事件（核心方法）
     */
    private void sendSseJson(SseEmitter emitter, Map<String, Object> event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event().data(json, MediaType.TEXT_PLAIN));
        } catch (Exception e) {
            log.error("[SSE] 发送事件失败", e);
        }
    }

    /**
     * 构建子步骤Map
     */
    private Map<String, String> subStep(String name, String status) {
        Map<String, String> map = new HashMap<>();
        map.put("name", name);
        map.put("status", status);
        return map;
    }

    /**
     * 构建ReturnRecord实体
     */
    private ReturnRecord buildRecord(Map<String, Object> ocrData, Map<String, Object> llmResult,
                                     Map<String, Object> ocrResult, String cosUrl,
                                     Map<String, Object> validationResult) {
        ReturnRecord record = new ReturnRecord();
        record.setWaybillNo(getString(ocrData, "waybill_no"));
        record.setRecName(getString(ocrData, "rec_name"));
        record.setRecPhone(getString(ocrData, "rec_phone"));
        record.setRecAddress(getString(ocrData, "rec_address"));
        record.setSenderName(getString(ocrData, "sender_name"));
        record.setSenderPhone(getString(ocrData, "sender_phone"));
        record.setSenderAddress(getString(ocrData, "sender_address"));
        record.setExpressCompany(getString(ocrData, "express_company"));
        record.setGoods(getString(ocrData, "goods"));
        record.setImageUrl(cosUrl);
        record.setOcrEngine(getString(ocrResult, "source"));
        record.setReturnReason(getString(llmResult, "return_reason"));
        record.setReturnCategory(getString(llmResult, "return_category"));

        // LLM置信度
        Object llmConf = llmResult.get("llm_confidence");
        if (llmConf instanceof Number) {
            record.setLlmConfidence(BigDecimal.valueOf(((Number) llmConf).doubleValue()));
        }

        // 状态：校验不通过或需人工确认时设为pending，否则也设为pending（都需要人工确认才写飞书）
        record.setStatus("pending");
        record.setCreatedAt(LocalDateTime.now());
        return record;
    }

    /**
     * 应用前端编辑的数据到记录
     */
    private void applyEditedData(ReturnRecord record, Map<String, Object> edited) {
        if (edited.containsKey("waybill_no")) record.setWaybillNo(edited.get("waybill_no").toString());
        if (edited.containsKey("rec_name")) record.setRecName(edited.get("rec_name").toString());
        if (edited.containsKey("rec_phone")) record.setRecPhone(edited.get("rec_phone").toString());
        if (edited.containsKey("rec_address")) record.setRecAddress(edited.get("rec_address").toString());
        if (edited.containsKey("sender_name")) record.setSenderName(edited.get("sender_name").toString());
        if (edited.containsKey("sender_phone")) record.setSenderPhone(edited.get("sender_phone").toString());
        if (edited.containsKey("sender_address")) record.setSenderAddress(edited.get("sender_address").toString());
        if (edited.containsKey("express_company")) record.setExpressCompany(edited.get("express_company").toString());
        if (edited.containsKey("goods")) record.setGoods(edited.get("goods").toString());
        if (edited.containsKey("return_reason")) record.setReturnReason(edited.get("return_reason").toString());
        if (edited.containsKey("return_category")) record.setReturnCategory(edited.get("return_category").toString());
    }

    /**
     * 将ReturnRecord转换为Map（供FeishuService使用）
     */
    private Map<String, Object> convertRecordToMap(ReturnRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("waybill_no", record.getWaybillNo() != null ? record.getWaybillNo() : "");
        map.put("rec_name", record.getRecName() != null ? record.getRecName() : "");
        map.put("rec_phone", record.getRecPhone() != null ? record.getRecPhone() : "");
        map.put("rec_address", record.getRecAddress() != null ? record.getRecAddress() : "");
        map.put("sender_name", record.getSenderName() != null ? record.getSenderName() : "");
        map.put("sender_phone", record.getSenderPhone() != null ? record.getSenderPhone() : "");
        map.put("sender_address", record.getSenderAddress() != null ? record.getSenderAddress() : "");
        map.put("express_company", record.getExpressCompany() != null ? record.getExpressCompany() : "");
        map.put("goods", record.getGoods() != null ? record.getGoods() : "");
        map.put("return_reason", record.getReturnReason() != null ? record.getReturnReason() : "");
        map.put("return_category", record.getReturnCategory() != null ? record.getReturnCategory() : "");
        return map;
    }

    /**
     * 安全获取字符串值
     */
    private String getString(Map<String, Object> map, String key) {
        if (map == null) return "";
        Object val = map.get(key);
        return val == null ? "" : val.toString().trim();
    }
}
