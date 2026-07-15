package tech.jxing.returnvision.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【接口层】退货OCR识别控制器
 *
 * 职责：串联所有服务，提供4个API接口
 * 层级：Controller 层
 *
 * 接口列表：
 *   POST /api/upload        - 上传图片识别
 *   POST /api/confirm       - 确认写入飞书
 *   GET  /api/records       - 查询记录列表
 *   POST /api/records/batch - 批量上传
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

    public UploadController(CosClientService cosClientService,
                            OcrCrossValidatorService crossValidatorService,
                            LlmAnalyzerService llmAnalyzerService,
                            ValidatorService validatorService,
                            FeishuService feishuService,
                            ReturnRecordMapper recordMapper) {
        this.cosClientService = cosClientService;
        this.crossValidatorService = crossValidatorService;
        this.llmAnalyzerService = llmAnalyzerService;
        this.validatorService = validatorService;
        this.feishuService = feishuService;
        this.recordMapper = recordMapper;
    }

    /**
     * 上传快递面单图片，自动OCR识别
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
     * 确认退货记录，写入飞书多维表格
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
        Map<String, Object> editedData = (Map<String, Object>) request.get("edited_data");
        if (editedData != null) {
            applyEditedData(record, editedData);
        }

        // 步骤3：更新状态为confirmed
        record.setStatus("confirmed");
        record.setConfirmedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.info("[确认] 记录已确认，record_id={}", recordId);

        // 步骤4：写入飞书
        Map<String, Object> feishuData = convertRecordToMap(record);
        String feishuRecordId = feishuService.writeRecord(feishuData, record.getImageUrl());

        // 步骤5：更新状态为synced
        record.setFeishuRecordId(feishuRecordId);
        record.setStatus("synced");
        record.setSyncedAt(LocalDateTime.now());
        recordMapper.updateById(record);
        log.info("[确认] 飞书写入完成，record_id={}, feishu_record_id={}", recordId, feishuRecordId);

        // 步骤6：返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("feishu_record_id", feishuRecordId);
        return ResponseResult.success(result);
    }

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
     * 批量上传快递面单图片
     *
     * 业务流程：
     *   1. 遍历所有上传的文件
     *   2. 对每个文件执行上传识别流程
     *   3. 汇总成功/失败数量
     */
    @PostMapping("/records/batch")
    public ResponseResult<Map<String, Object>> batchUpload(@RequestParam("files") MultipartFile[] files) {
        log.info("[批量上传] 共{}个文件", files.length);

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
                log.warn("[批量上传] 文件处理失败：{}，原因：{}", file.getOriginalFilename(), e.getMessage());
            }
        }

        // 步骤3：汇总结果
        Map<String, Object> response = new HashMap<>();
        response.put("total", files.length);
        response.put("success", success);
        response.put("failed", failed);
        response.put("results", results);
        log.info("[批量上传] 完成，成功{}，失败{}", success, failed);
        return ResponseResult.success(response);
    }

    // ==================== 内部方法 ====================

    /**
     * 上传识别内部逻辑（供upload和batchUpload复用）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> doUploadInternal(MultipartFile file) throws Exception {
        byte[] imageBytes = file.getBytes();
        String filename = System.currentTimeMillis() + "-" + file.getOriginalFilename();
        String cosUrl = cosClientService.uploadToCos(imageBytes, filename);

        Map<String, Object> ocrResult = crossValidatorService.dualEngineOcr(cosUrl);
        Map<String, Object> ocrData = (Map<String, Object>) ocrResult.getOrDefault("data", new HashMap<>());

        Map<String, Object> llmResult = new HashMap<>();
        if (ocrData != null && !getString(ocrData, "waybill_no").isEmpty()) {
            llmResult = llmAnalyzerService.analyze(ocrData);
        }

        Map<String, Object> validationResult = validatorService.validate(ocrData != null ? ocrData : new HashMap<>());

        ReturnRecord record = buildRecord(ocrData, llmResult, ocrResult, cosUrl, validationResult);
        recordMapper.insert(record);

        Map<String, Object> result = new HashMap<>();
        result.put("record_id", record.getId());
        result.put("filename", file.getOriginalFilename());
        result.put("waybill_no", getString(ocrData, "waybill_no"));
        result.put("cross_validation", ocrResult.get("action"));
        return result;
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
