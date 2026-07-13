package tech.jxing.returnvision.controller;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tech.jxing.returnvision.common.ResponseResult;
import tech.jxing.returnvision.service.CosClientService;
import tech.jxing.returnvision.service.OcrCrossValidatorService;

import java.io.IOException;
import java.util.Map;

/**
 * 【接口层】临时测试控制器（联调完成后删除）
 *
 * 职责：测试 COS上传 + 双引擎OCR + 交叉验证 完整流程
 */
@Slf4j
@RestController
public class TestController {

    private final OcrCrossValidatorService crossValidatorService;
    private final CosClientService cosClientService;
    private final OkHttpClient httpClient;

    public TestController(OcrCrossValidatorService crossValidatorService,
                          CosClientService cosClientService,
                          OkHttpClient httpClient) {
        this.crossValidatorService = crossValidatorService;
        this.cosClientService = cosClientService;
        this.httpClient = httpClient;
    }

    /**
     * 测试完整流程：上传图片文件 -> COS -> 双引擎OCR -> 交叉验证
     *
     * 业务流程：
     *   1. 接收上传的图片文件
     *   2. 上传到腾讯云COS
     *   3. 用COS URL调用双引擎交叉验证
     *   4. 返回识别结果
     */
    @PostMapping("/api/test/ocr-upload")
    public ResponseResult<Map<String, Object>> testOcrUpload(@RequestParam("file") MultipartFile file) {
        try {
            // 步骤1：获取图片字节
            byte[] imageBytes = file.getBytes();
            String filename = System.currentTimeMillis() + "-" + file.getOriginalFilename();

            // 步骤2：上传到COS
            String cosUrl = cosClientService.uploadToCos(imageBytes, filename);
            log.info("[测试] COS上传成功，url={}", cosUrl);

            // 步骤3：双引擎OCR + 交叉验证
            Map<String, Object> result = crossValidatorService.dualEngineOcr(cosUrl);
            return ResponseResult.success(result);
        } catch (IOException e) {
            log.error("[测试] 文件读取失败", e);
            return ResponseResult.error(1001, "文件读取失败");
        }
    }

    /**
     * 测试完整流程：下载远程图片 -> COS -> 双引擎OCR -> 交叉验证
     *
     * 业务流程：
     *   1. 下载远程图片
     *   2. 上传到腾讯云COS
     *   3. 用COS URL调用双引擎交叉验证
     *   4. 返回识别结果
     */
    @GetMapping("/api/test/ocr-url")
    public ResponseResult<Map<String, Object>> testOcrByUrl(@RequestParam String imageUrl) {
        try {
            log.info("[测试] 下载远程图片，url={}", imageUrl);

            // 步骤1：下载远程图片
            Request request = new Request.Builder().url(imageUrl).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return ResponseResult.error(1001, "图片下载失败：HTTP " + response.code());
                }
                byte[] imageBytes = response.body().bytes();
                String filename = System.currentTimeMillis() + "-test.jpg";

                // 步骤2：上传到COS
                String cosUrl = cosClientService.uploadToCos(imageBytes, filename);
                log.info("[测试] COS上传成功，url={}", cosUrl);

                // 步骤3：双引擎OCR + 交叉验证
                Map<String, Object> result = crossValidatorService.dualEngineOcr(cosUrl);
                return ResponseResult.success(result);
            }
        } catch (Exception e) {
            log.error("[测试] 流程异常", e);
            return ResponseResult.error(9001, "测试失败：" + e.getMessage());
        }
    }
}
