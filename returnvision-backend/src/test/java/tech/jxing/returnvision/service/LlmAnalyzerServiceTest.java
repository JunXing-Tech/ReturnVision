package tech.jxing.returnvision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.jxing.returnvision.common.exception.LlmError;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 【测试类】LlmAnalyzerService 改造测试（F08 v2.2）
 * <p>
 * 1. Mockito mock OkHttpClient + DictService
 * 2. 覆盖字典非空/为空的 prompt 注入、code->label 反查、越界降级、无 API Key 降级
 * 3. 对应清单：test-checklists/2026-07-21_F08-退货字典.md AT-13~AT-19, AT-37
 * 4. 不调真实 DeepSeek：通过 mock OkHttpClient 拦截 HTTP 调用
 * </p>
 *
 * @author ReturnVision
 */
class LlmAnalyzerServiceTest {

    private OkHttpClient httpClient;
    private DictService dictService;
    private LlmAnalyzerService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        httpClient = mock(OkHttpClient.class);
        dictService = mock(DictService.class);
        service = new LlmAnalyzerService(
                "test-api-key", "https://api.deepseek.com", "deepseek-chat",
                httpClient, objectMapper, dictService);
    }

    /** 构造 DeepSeek 成功响应的裸 body */
    private Response mockSuccessResponseRaw(String body) {
        Response response = mock(Response.class);
        when(response.isSuccessful()).thenReturn(true);
        ResponseBody responseBody = mock(ResponseBody.class);
        try {
            when(responseBody.string()).thenReturn(body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        when(response.body()).thenReturn(responseBody);
        return response;
    }

    /** 构造 DeepSeek 成功响应（content 是 JSON 字符串） */
    private Response buildResponse(String llmContentJson) {
        try {
            String escaped = objectMapper.writeValueAsString(llmContentJson);
            String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":" + escaped + "}}]}";
            return mockSuccessResponseRaw(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void stubHttp(Response response) throws IOException {
        Call call = mock(Call.class);
        when(call.execute()).thenReturn(response);
        when(httpClient.newCall(any(Request.class))).thenReturn(call);
    }

    private Map<String, Object> ocrData() {
        Map<String, Object> m = new HashMap<>();
        m.put("waybill_no", "SF12345678");
        m.put("rec_name", "张三");
        return m;
    }

    // ============ AT-13/14：prompt 字典注入 ============

    @Test
    @DisplayName("AT-13：字典非空 -> DictService.listActiveLeafItems 被调用（prompt 包含字典项）")
    void analyze_dictNonEmpty_shouldCallListActiveLeafItems() throws IOException {
        List<Map<String, Object>> leaves = List.of(
                Map.of("item_code", "QUALITY", "item_label", "质量问题"),
                Map.of("item_code", "LOGISTICS", "item_label", "物流问题")
        );
        when(dictService.listActiveLeafItems()).thenReturn(leaves);
        when(dictService.getItemLabelByCode("QUALITY")).thenReturn("质量问题");
        stubHttp(buildResponse("{\"return_reason\":\"破损\",\"return_category_code\":\"QUALITY\",\"llm_confidence\":0.9}"));

        service.analyze(ocrData());

        verify(dictService).listActiveLeafItems();
    }

    @Test
    @DisplayName("AT-14：字典为空 -> listActiveLeafItems 返回空，仍调用（LLM 用硬编码兜底）")
    void analyze_dictEmpty_shouldStillCallAndFallback() throws IOException {
        when(dictService.listActiveLeafItems()).thenReturn(List.of());
        // 硬编码兜底时 LLM 返回的 code 在字典里查不到，会走降级"其他"
        when(dictService.getItemLabelByCode(anyString())).thenReturn(null);
        stubHttp(buildResponse("{\"return_reason\":\"未知\",\"return_category_code\":\"OTHER\",\"llm_confidence\":0.5}"));

        Map<String, Object> result = service.analyze(ocrData());

        verify(dictService).listActiveLeafItems();
        // 字典为空 + 查不到 label -> 降级"其他"
        assertEquals("其他", result.get("return_category"));
    }

    // ============ AT-15/16：code -> label 反查 ============

    @Test
    @DisplayName("AT-15：LLM 返回 code 在字典中 -> return_category 转为 label")
    void analyze_codeInDict_shouldConvertToLabel() throws IOException {
        when(dictService.listActiveLeafItems()).thenReturn(List.of(
                Map.of("item_code", "QUALITY", "item_label", "质量问题")));
        when(dictService.getItemLabelByCode("QUALITY")).thenReturn("质量问题");
        stubHttp(buildResponse("{\"return_reason\":\"破损\",\"return_category_code\":\"QUALITY\",\"llm_confidence\":0.9}"));

        Map<String, Object> result = service.analyze(ocrData());

        assertEquals("质量问题", result.get("return_category"));
        assertNull(result.get("return_category_code"), "return_category_code 应被移除");
    }

    @Test
    @DisplayName("AT-16：LLM 返回 code 不在字典中 -> 降级为'其他'，记 warn")
    void analyze_codeNotInDict_shouldFallbackToOther() throws IOException {
        when(dictService.listActiveLeafItems()).thenReturn(List.of(
                Map.of("item_code", "QUALITY", "item_label", "质量问题")));
        when(dictService.getItemLabelByCode("UNKNOWN_CODE")).thenReturn(null);
        when(dictService.getItemLabelByCode("OTHER")).thenReturn("其他");
        stubHttp(buildResponse("{\"return_reason\":\"x\",\"return_category_code\":\"UNKNOWN_CODE\",\"llm_confidence\":0.3}"));

        Map<String, Object> result = service.analyze(ocrData());

        assertEquals("其他", result.get("return_category"));
    }

    @Test
    @DisplayName("AT-16b：字典+OTHER 都查不到 -> 用硬编码'其他'")
    void analyze_fallbackWhenOtherAlsoMissing_shouldUseHardcoded() throws IOException {
        when(dictService.listActiveLeafItems()).thenReturn(List.of());
        when(dictService.getItemLabelByCode(anyString())).thenReturn(null);
        stubHttp(buildResponse("{\"return_reason\":\"x\",\"return_category_code\":\"UNKNOWN\",\"llm_confidence\":0.3}"));

        Map<String, Object> result = service.analyze(ocrData());

        assertEquals("其他", result.get("return_category"));
    }

    // ============ AT-17：容错 ============

    @Test
    @DisplayName("AT-17：LLM 返回缺 return_category_code -> code 为空字符串，降级'其他'")
    void analyze_missingCategoryCode_shouldFallback() throws IOException {
        when(dictService.listActiveLeafItems()).thenReturn(List.of());
        when(dictService.getItemLabelByCode("")).thenReturn(null);
        when(dictService.getItemLabelByCode("OTHER")).thenReturn("其他");
        stubHttp(buildResponse("{\"return_reason\":\"x\",\"llm_confidence\":0.5}"));

        Map<String, Object> result = service.analyze(ocrData());

        assertEquals("其他", result.get("return_category"));
    }

    @Test
    @DisplayName("AT-17b：DeepSeek API 失败 -> 抛 LlmError")
    void analyze_apiFailure_shouldThrowLlmError() throws IOException {
        when(dictService.listActiveLeafItems()).thenReturn(List.of());
        Response failResponse = mock(Response.class);
        when(failResponse.isSuccessful()).thenReturn(false);
        ResponseBody failBody = mock(ResponseBody.class);
        when(failBody.string()).thenReturn("server error");
        when(failResponse.body()).thenReturn(failBody);
        stubHttp(failResponse);

        assertThrows(LlmError.class, () -> service.analyze(ocrData()));
    }

    // ============ AT-18：API Key 未配置 -> 不查字典（AT-37） ============

    @Test
    @DisplayName("AT-18/AT-37：API Key 未配置 -> 不查字典，直接返回空对象")
    void analyze_noApiKey_shouldNotQueryDict() {
        LlmAnalyzerService noKeyService = new LlmAnalyzerService(
                "", "https://api.deepseek.com", "deepseek-chat",
                httpClient, objectMapper, dictService);

        Map<String, Object> result = noKeyService.analyze(ocrData());

        assertEquals("", result.get("return_reason"));
        assertEquals("", result.get("return_category"));
        assertEquals(0.0, result.get("llm_confidence"));
        verifyNoInteractions(dictService);
    }

    @Test
    @DisplayName("AT-18b：API Key 为 null -> 同样不查字典")
    void analyze_nullApiKey_shouldNotQueryDict() {
        LlmAnalyzerService nullKeyService = new LlmAnalyzerService(
                null, "https://api.deepseek.com", "deepseek-chat",
                httpClient, objectMapper, dictService);

        nullKeyService.analyze(ocrData());
        verifyNoInteractions(dictService);
    }
}