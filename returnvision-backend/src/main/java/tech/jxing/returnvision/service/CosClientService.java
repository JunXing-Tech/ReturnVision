package tech.jxing.returnvision.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.common.exception.CosError;

import java.io.ByteArrayInputStream;

/**
 * 【业务逻辑层】腾讯云COS图片上传服务
 *
 * 职责：将快递单照片上传至腾讯云COS，返回公开访问URL
 * 层级：Service 层
 * 调用方：UploadController（步骤10）
 */
@Service
@Slf4j
public class CosClientService {

    private final String cosRegion;
    private final String cosBucket;
    private final COSClient cosClient;

    /**
     * 构造器注入COS配置，并初始化COSClient
     *
     * @param cosRegion    COS区域
     * @param cosSecretId  COS SecretId
     * @param cosSecretKey COS SecretKey
     * @param cosBucket    COS Bucket名
     */
    public CosClientService(
            @Value("${cos.region}") String cosRegion,
            @Value("${cos.secret-id}") String cosSecretId,
            @Value("${cos.secret-key}") String cosSecretKey,
            @Value("${cos.bucket}") String cosBucket) {
        this.cosRegion = cosRegion;
        this.cosBucket = cosBucket;

        // 凭证未配置时优雅降级
        if (cosSecretId == null || cosSecretId.isEmpty()
                || cosSecretKey == null || cosSecretKey.isEmpty()) {
            log.warn("[COS] 凭证未配置，COS上传功能不可用。请设置 COS_SECRET_ID 和 COS_SECRET_KEY 环境变量");
            this.cosClient = null;
            return;
        }

        COSCredentials cred = new BasicCOSCredentials(cosSecretId, cosSecretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(cosRegion));
        this.cosClient = new COSClient(cred, clientConfig);
        log.info("[COS] 客户端初始化完成，region={}, bucket={}", cosRegion, cosBucket);
    }

    /**
     * 销毁COS客户端，释放资源
     */
    @PreDestroy
    public void destroy() {
        if (cosClient != null) {
            cosClient.shutdown();
            log.info("[COS] 客户端已关闭");
        }
    }

    /**
     * 上传快递单照片到腾讯云COS，返回公开访问URL
     *
     * 实现步骤：
     *   1. 检查COS客户端是否可用
     *   2. 组装上传请求（文件路径、元数据）
     *   3. 调用COS SDK上传
     *   4. 拼接并返回公开访问URL
     *
     * @param imageBytes 图片字节数组
     * @param filename   文件名（如 SF1234567890.jpg）
     * @return COS公开访问URL
     */
    public String uploadToCos(byte[] imageBytes, String filename) {
        // 步骤1：检查COS客户端是否可用
        if (cosClient == null) {
            throw new CosError("COS客户端未初始化，请检查COS凭证配置");
        }

        log.info("[COS] 开始上传图片，filename={}, size={}bytes", filename, imageBytes.length);

        // 步骤2：组装上传请求
        String key = "return-waybills/" + filename;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(imageBytes.length);
        // 根据文件扩展名设置Content-Type
        String contentType = filename.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        metadata.setContentType(contentType);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);

        try {
            // 步骤3：调用COS SDK上传
            PutObjectRequest putRequest = new PutObjectRequest(cosBucket, key, inputStream, metadata);
            cosClient.putObject(putRequest);
            log.info("[COS] 上传成功，key={}", key);
        } catch (CosServiceException e) {
            log.error("[COS] 上传失败（服务端异常），errorCode={}, msg={}", e.getErrorCode(), e.getMessage());
            throw new CosError("COS上传失败：" + e.getMessage());
        } catch (CosClientException e) {
            log.error("[COS] 上传失败（客户端异常），msg={}", e.getMessage());
            throw new CosError("COS上传失败：" + e.getMessage());
        }

        // 步骤4：拼接并返回公开访问URL
        return String.format("https://%s.cos.%s.myqcloud.com/%s", cosBucket, cosRegion, key);
    }
}
