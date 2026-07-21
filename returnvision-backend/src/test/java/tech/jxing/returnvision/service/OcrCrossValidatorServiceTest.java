package tech.jxing.returnvision.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.jxing.returnvision.ocrstats.OcrLogWriter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 【测试类】OcrCrossValidatorService 双引擎交叉验证单元测试
 * <p>
 * 1. Mockito mock 三个依赖：OcrZhipuService / OcrAliyunService / OcrLogWriter
 * 2. 覆盖 5 种仲裁分支（双失败/单失败×2/全一致/部分差异/运单号冲突）+ 字段比对 + 阿里云置信度阈值 + 埋点行为
 * 3. 对应清单：test-checklists/2026-07-21_OcrCrossValidatorService-单元测试.md AT-01~AT-26
 * 4. 由于被测方法用 CompletableFuture.supplyAsync，mock 的 OCR Service 会同步返回，.get() 立即收敛
 * </p>
 *
 * @author ReturnVision
 */
class OcrCrossValidatorServiceTest {

    private OcrZhipuService zhipuService;
    private OcrAliyunService aliyunService;
    private OcrLogWriter logWriter;
    private OcrCrossValidatorService service;

    private static final String IMAGE_URL = "https://cos.example.com/test.jpg";

    /** 全字段一致的合法结果（智谱） */
    private Map<String, Object> zhipuAllSame() {
        Map<String, Object> m = new HashMap<>();
        m.put("waybill_no", "SF12345678");
        m.put("rec_name", "张三");
        m.put("rec_phone", "13812345678");
        m.put("rec_address", "北京市朝阳区");
        m.put("sender_name", "李四");
        m.put("sender_phone", "13912345678");
        m.put("sender_address", "上海市浦东新区");
        m.put("express_company", "顺丰");
        m.put("goods", "退货商品");
        return m;
    }

    /** 全字段一致的合法结果（阿里云，带 confidence） */
    private Map<String, Object> aliyunAllSame() {
        Map<String, Object> m = new HashMap<>();
        m.put("waybill_no", "SF12345678");
        m.put("rec_name", "张三");
        m.put("rec_phone", "13812345678");
        m.put("rec_address", "北京市朝阳区");
        m.put("sender_name", "李四");
        m.put("sender_phone", "13912345678");
        m.put("sender_address", "上海市浦东新区");
        m.put("express_company", "顺丰");
        m.put("goods", "退货商品");
        Map<String, Object> conf = new HashMap<>();
        conf.put("waybill_no_prob", 96);
        conf.put("rec_name_prob", 92);
        return m.put("confidence", conf) == null ? m : m;
    }

    @BeforeEach
    void setUp() {
        zhipuService = org.mockito.Mockito.mock(OcrZhipuService.class);
        aliyunService = org.mockito.Mockito.mock(OcrAliyunService.class);
        logWriter = org.mockito.Mockito.mock(OcrLogWriter.class);
        service = new OcrCrossValidatorService(zhipuService, aliyunService, logWriter);
    }

    // ============ 5 种仲裁分支（AT-01~AT-06） ============

    @Test
    @DisplayName("AT-01：双引擎全字段一致 -> accept / cross_validated / high")
    void allFieldsMatch_shouldAcceptWithHighConfidence() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipuAllSame());
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyunAllSame());

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        assertEquals("accept", result.get("action"));
        assertEquals("cross_validated", result.get("source"));
        assertEquals("high", result.get("confidence"));
        assertNull(result.get("diff_fields"), "全一致时不应设 diff_fields");
    }

    @Test
    @DisplayName("AT-02：双引擎成功但运单号冲突 -> manual / low / reason 含运单号不一致")
    void waybillConflict_shouldManual() {
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("waybill_no", "SF11111111");
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.put("waybill_no", "SF22222222");
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        assertEquals("manual", result.get("action"));
        assertEquals("low", result.get("confidence"));
        assertEquals("运单号双引擎结果不一致", result.get("reason"));
        List<String> diffFields = (List<String>) result.get("diff_fields");
        assertTrue(diffFields.contains("waybill_no"));
    }

    @Test
    @DisplayName("AT-03：非运单号字段差异 -> review / cross_validated / medium / diff_fields 非空")
    void nonWaybillDiff_shouldReview() {
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("rec_address", "北京市朝阳区A");
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.put("rec_address", "北京市朝阳区B");
        Map<String, Object> conf = new HashMap<>();
        conf.put("rec_address_prob", 95);
        aliyun.put("confidence", conf);
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        assertEquals("review", result.get("action"));
        assertEquals("cross_validated", result.get("source"));
        assertEquals("medium", result.get("confidence"));
        List<String> diffFields = (List<String>) result.get("diff_fields");
        assertTrue(diffFields.contains("rec_address"));
        assertFalse(diffFields.contains("waybill_no"), "不应包含运单号");
    }

    @Test
    @DisplayName("AT-04：智谱失败阿里云成功 -> accept / aliyun_only / medium / note 含智谱失败")
    void zhipuFailsAliyunSuccess_shouldAcceptAliyunOnly() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenThrow(new RuntimeException("zhipu timeout"));
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyunAllSame());

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        assertEquals("accept", result.get("action"));
        assertEquals("aliyun_only", result.get("source"));
        assertEquals("medium", result.get("confidence"));
        assertTrue(((String) result.get("note")).contains("智谱失败"));
    }

    @Test
    @DisplayName("AT-05：阿里云失败智谱成功 -> accept / zhipu_only / medium / note 含阿里云失败")
    void aliyunFailsZhipuSuccess_shouldAcceptZhipuOnly() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipuAllSame());
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenThrow(new RuntimeException("aliyun 500"));

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        assertEquals("accept", result.get("action"));
        assertEquals("zhipu_only", result.get("source"));
        assertEquals("medium", result.get("confidence"));
        assertTrue(((String) result.get("note")).contains("阿里云失败"));
    }

    @Test
    @DisplayName("AT-06：双引擎均失败 -> manual / low / reason 含双引擎失败 / data 为空 Map")
    void bothFail_shouldManualWithEmptyData() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenThrow(new RuntimeException("zhipu down"));
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenThrow(new RuntimeException("aliyun down"));

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        assertEquals("manual", result.get("action"));
        assertEquals("low", result.get("confidence"));
        assertEquals("双引擎均识别失败", result.get("reason"));
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertTrue(data.isEmpty(), "双失败时 data 应为空 Map");
    }

    // ============ 字段比对规则（AT-07~AT-14） ============

    @Test
    @DisplayName("AT-07：两引擎某字段一致 -> chosenData 取该一致值")
    void fieldsMatch_shouldTakeSameValue() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipuAllSame());
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyunAllSame());

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals("SF12345678", data.get("waybill_no"));
        assertEquals("张三", data.get("rec_name"));
    }

    @Test
    @DisplayName("AT-08：智谱有值阿里云空 -> 取智谱值，不算 diff")
    void zhipuHasAliyunEmpty_shouldTakeZhipuNoDiff() {
        Map<String, Object> zhipu = zhipuAllSame();
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.put("rec_name", "");
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals("张三", data.get("rec_name"), "应取智谱值");
        assertEquals("accept", result.get("action"), "不应被记为差异");
        assertNull(result.get("diff_fields"));
    }

    @Test
    @DisplayName("AT-09：阿里云有值智谱空 -> 取阿里云值，不算 diff")
    void aliyunHasZhipuEmpty_shouldTakeAliyunNoDiff() {
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("rec_name", "");
        Map<String, Object> aliyun = aliyunAllSame();
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals("张三", data.get("rec_name"), "应取阿里云值");
        assertEquals("accept", result.get("action"));
    }

    @Test
    @DisplayName("AT-10：两引擎均空 -> chosenData 该字段为 \"\"，不算 diff")
    void bothEmpty_shouldBeEmptyStringNoDiff() {
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("goods", "");
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.put("goods", "");
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals("", data.get("goods"));
        assertEquals("accept", result.get("action"));
    }

    @Test
    @DisplayName("AT-11/AT-20：两引擎不一致且阿里云 prob=80（≥阈值）-> 取阿里云 / chosen=aliyun")
    void diffWithAliyunProb80_shouldTakeAliyun() {
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("rec_address", "地址A");
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.put("rec_address", "地址B");
        Map<String, Object> conf = new HashMap<>();
        conf.put("rec_address_prob", 80);
        aliyun.put("confidence", conf);
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals("地址B", data.get("rec_address"), "prob=80 应取阿里云值");
        Map<String, Object> diffDetail = (Map<String, Object>) result.get("diff_detail");
        Map<String, Object> addrDetail = (Map<String, Object>) diffDetail.get("rec_address");
        assertEquals("aliyun", addrDetail.get("chosen"));
        assertEquals(80, addrDetail.get("aliyun_prob"));
    }

    @Test
    @DisplayName("AT-12/AT-21：两引擎不一致且阿里云 prob=79（<阈值）-> 取智谱 / chosen=zhipu")
    void diffWithAliyunProb79_shouldTakeZhipu() {
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("rec_address", "地址A");
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.put("rec_address", "地址B");
        Map<String, Object> conf = new HashMap<>();
        conf.put("rec_address_prob", 79);
        aliyun.put("confidence", conf);
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals("地址A", data.get("rec_address"), "prob=79 应取智谱值");
        Map<String, Object> diffDetail = (Map<String, Object>) result.get("diff_detail");
        Map<String, Object> addrDetail = (Map<String, Object>) diffDetail.get("rec_address");
        assertEquals("zhipu", addrDetail.get("chosen"));
    }

    @Test
    @DisplayName("AT-13：差异详情结构含 zhipu / aliyun / aliyun_prob / chosen 四子字段")
    void diffDetail_shouldContainFourSubFields() {
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("rec_address", "地址A");
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.put("rec_address", "地址B");
        Map<String, Object> conf = new HashMap<>();
        conf.put("rec_address_prob", 90);
        aliyun.put("confidence", conf);
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        Map<String, Object> diffDetail = (Map<String, Object>) result.get("diff_detail");
        Map<String, Object> detail = (Map<String, Object>) diffDetail.get("rec_address");
        assertTrue(detail.containsKey("zhipu"));
        assertTrue(detail.containsKey("aliyun"));
        assertTrue(detail.containsKey("aliyun_prob"));
        assertTrue(detail.containsKey("chosen"));
        assertEquals(4, detail.size());
    }

    @Test
    @DisplayName("AT-14：多字段非运单号差异 -> diff_fields 含全部冲突字段")
    void multipleNonWaybillDiff_shouldAllInDiffFields() {
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("rec_address", "地址A1");
        zhipu.put("goods", "商品A");
        zhipu.put("sender_name", "发件人A");
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.put("rec_address", "地址B1");
        aliyun.put("goods", "商品B");
        aliyun.put("sender_name", "发件人B");
        Map<String, Object> conf = new HashMap<>();
        conf.put("rec_address_prob", 85);
        conf.put("goods_prob", 90);
        conf.put("sender_name_prob", 90);
        aliyun.put("confidence", conf);
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        List<String> diffFields = (List<String>) result.get("diff_fields");
        assertEquals(3, diffFields.size());
        assertTrue(diffFields.contains("rec_address"));
        assertTrue(diffFields.contains("goods"));
        assertTrue(diffFields.contains("sender_name"));
    }

    // ============ 飞书埋点行为（AT-15~AT-19） ============

    @Test
    @DisplayName("AT-15：全部一致 -> writeSimpleLog(zhipu,true) + writeLog(aliyun,true,fieldConfidence非空) 各 1 次")
    void allMatch_shouldWriteBothLogs() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipuAllSame());
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyunAllSame());

        service.dualEngineOcr(IMAGE_URL);

        verify(logWriter, times(1)).writeSimpleLog(any(), eq("zhipu_ocr"), anyLong(), eq(true), any(), isNull());
        verify(logWriter, times(1)).writeLog(any(), eq("aliyun_waybill"), anyLong(), eq(true), any(),
                argThat(fc -> fc != null && !fc.isEmpty()), isNull());
    }

    @Test
    @DisplayName("AT-16：智谱失败阿里云成功 -> writeSimpleLog(zhipu,false) + writeLog(aliyun,true) 各 1 次")
    void zhipuFails_shouldWriteZhipuFailAndAliyunSuccessLogs() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenThrow(new RuntimeException("zhipu err"));
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyunAllSame());

        service.dualEngineOcr(IMAGE_URL);

        verify(logWriter, times(1)).writeSimpleLog(any(), eq("zhipu_ocr"), anyLong(), eq(false), any(), anyString());
        verify(logWriter, times(1)).writeLog(any(), eq("aliyun_waybill"), anyLong(), eq(true), any(),
                argThat(fc -> fc != null && !fc.isEmpty()), isNull());
    }

    @Test
    @DisplayName("AT-17：阿里云失败智谱成功 -> writeSimpleLog(zhipu,true) + writeSimpleLog(aliyun,false) 各 1 次")
    void aliyunFails_shouldWriteZhipuSuccessAndAliyunFailLogs() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipuAllSame());
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenThrow(new RuntimeException("aliyun err"));

        service.dualEngineOcr(IMAGE_URL);

        verify(logWriter, times(1)).writeSimpleLog(any(), eq("zhipu_ocr"), anyLong(), eq(true), any(), isNull());
        verify(logWriter, times(1)).writeSimpleLog(any(), eq("aliyun_waybill"), anyLong(), eq(false), any(), anyString());
    }

    @Test
    @DisplayName("AT-18：双失败 -> writeSimpleLog 2 次均 success=false，errorMsg 非空")
    void bothFail_shouldWriteTwoFailLogs() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenThrow(new RuntimeException("zhipu down"));
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenThrow(new RuntimeException("aliyun down"));

        service.dualEngineOcr(IMAGE_URL);

        verify(logWriter, times(1)).writeSimpleLog(any(), eq("zhipu_ocr"), anyLong(), eq(false), any(), anyString());
        verify(logWriter, times(1)).writeSimpleLog(any(), eq("aliyun_waybill"), anyLong(), eq(false), any(), anyString());
        verify(logWriter, never()).writeLog(any(), anyString(), anyLong(), anyBoolean(), any(), any(), any());
    }

    @Test
    @DisplayName("AT-19：运单号冲突 -> 双成功埋点(success=true)，但 action=manual")
    void waybillConflict_shouldWriteSuccessLogsButManualAction() {
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("waybill_no", "SF11111111");
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.put("waybill_no", "SF22222222");
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        assertEquals("manual", result.get("action"), "应转人工");
        verify(logWriter, times(1)).writeSimpleLog(any(), eq("zhipu_ocr"), anyLong(), eq(true), any(), isNull());
        verify(logWriter, times(1)).writeLog(any(), eq("aliyun_waybill"), anyLong(), eq(true), any(),
                argThat(fc -> fc != null), isNull());
    }

    // ============ 边界与契约（AT-22~AT-26） ============

    @Test
    @DisplayName("AT-22：resultAliyun 缺 confidence 字段 -> 不报错，冲突字段按 prob=0 处理（取智谱）")
    void aliyunMissingConfidence_shouldFallbackToZhipuOnConflict() {
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("rec_address", "地址A");
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.remove("confidence");
        aliyun.put("rec_address", "地址B");
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        assertEquals("地址A", data.get("rec_address"), "缺 confidence 应按 prob=0 取智谱");
        Map<String, Object> diffDetail = (Map<String, Object>) result.get("diff_detail");
        Map<String, Object> detail = (Map<String, Object>) diffDetail.get("rec_address");
        assertEquals(0, detail.get("aliyun_prob"));
        assertEquals("zhipu", detail.get("chosen"));
    }

    @Test
    @DisplayName("AT-23：两个 OCR Service 均被调用，传入同一 imageUrl 参数")
    void bothOcrServices_shouldBeCalledWithSameImageUrl() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipuAllSame());
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyunAllSame());

        service.dualEngineOcr(IMAGE_URL);

        verify(zhipuService, times(1)).ocrByZhipu(IMAGE_URL);
        verify(aliyunService, times(1)).ocrByAliyun(IMAGE_URL);
    }

    @Test
    @DisplayName("AT-24：chosenData 含全部 9 个 FIELDS 字段，无遗漏无重复")
    void chosenData_shouldContainAllNineFields() {
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipuAllSame());
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyunAllSame());

        Map<String, Object> result = service.dualEngineOcr(IMAGE_URL);

        Map<String, Object> data = (Map<String, Object>) result.get("data");
        String[] expectedFields = {"waybill_no", "rec_name", "rec_phone", "rec_address",
                "sender_name", "sender_phone", "sender_address", "express_company", "goods"};
        assertEquals(expectedFields.length, data.size(), "应是 9 个字段");
        for (String f : expectedFields) {
            assertTrue(data.containsKey(f), "应含字段: " + f);
        }
    }

    @Test
    @DisplayName("AT-25：extractFieldConfidence 只提取 prob>0 的字段（通过埋点入参验证）")
    void extractFieldConfidence_shouldOnlyKeepPositiveProb() {
        Map<String, Object> aliyun = aliyunAllSame();
        Map<String, Object> conf = new HashMap<>();
        conf.put("waybill_no_prob", 96);
        conf.put("rec_name_prob", 0);  // 0 不应进 fieldConfidence
        conf.put("rec_phone_prob", 88);
        aliyun.put("confidence", conf);
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipuAllSame());
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);

        service.dualEngineOcr(IMAGE_URL);

        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(logWriter, times(1)).writeLog(any(), eq("aliyun_waybill"), anyLong(), eq(true), any(),
                captor.capture(), isNull());
        Map<String, Object> fieldConfidence = captor.getValue();
        assertTrue(fieldConfidence.containsKey("waybill_no"));
        assertTrue(fieldConfidence.containsKey("rec_phone"));
        assertFalse(fieldConfidence.containsKey("rec_name"), "prob=0 不应进 fieldConfidence");
    }

    @Test
    @DisplayName("AT-26：3 种 confidence 取值分布：一致=high / 差异或单降级=medium / 失败或冲突=low")
    void confidenceValues_shouldDistributeCorrectly() {
        // high：全一致
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipuAllSame());
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyunAllSame());
        assertEquals("high", service.dualEngineOcr(IMAGE_URL).get("confidence"));

        // low：双失败
        org.mockito.Mockito.reset(zhipuService, aliyunService);
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenThrow(new RuntimeException("zhipu err"));
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenThrow(new RuntimeException("aliyun err"));
        assertEquals("low", service.dualEngineOcr(IMAGE_URL).get("confidence"));

        // low：运单号冲突
        org.mockito.Mockito.reset(zhipuService, aliyunService);
        Map<String, Object> zhipu = zhipuAllSame();
        zhipu.put("waybill_no", "SF11111111");
        Map<String, Object> aliyun = aliyunAllSame();
        aliyun.put("waybill_no", "SF22222222");
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun);
        assertEquals("low", service.dualEngineOcr(IMAGE_URL).get("confidence"));

        // medium：非运单差异
        org.mockito.Mockito.reset(zhipuService, aliyunService);
        Map<String, Object> zhipu2 = zhipuAllSame();
        zhipu2.put("rec_address", "地址A");
        Map<String, Object> aliyun2 = aliyunAllSame();
        aliyun2.put("rec_address", "地址B");
        Map<String, Object> conf = new HashMap<>();
        conf.put("rec_address_prob", 85);
        aliyun2.put("confidence", conf);
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenReturn(zhipu2);
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyun2);
        assertEquals("medium", service.dualEngineOcr(IMAGE_URL).get("confidence"));

        // medium：单降级
        org.mockito.Mockito.reset(zhipuService, aliyunService);
        when(zhipuService.ocrByZhipu(IMAGE_URL)).thenThrow(new RuntimeException("zhipu err"));
        when(aliyunService.ocrByAliyun(IMAGE_URL)).thenReturn(aliyunAllSame());
        assertEquals("medium", service.dualEngineOcr(IMAGE_URL).get("confidence"));
    }
}