package tech.jxing.returnvision.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 【公共模块】AES/GCM 加解密工具
 *
 * 职责：对飞书 app_secret 等敏感字段做可逆加解密
 * 层级：common.util 层
 * 关联：docs/14 §3.7.1 / §3.7.2
 *
 * 设计要点：
 *   1. 算法 AES/GCM/NoPadding（带认证标签，防篡改，比 CBC 安全）
 *   2. IV 每次随机生成 12 字节（GCM 推荐长度，安全红线：同一密钥下 IV 不可重复）
 *   3. 认证标签长度 128 bit（GCM 最大值）
 *   4. 存储格式 "iv:ciphertext:tag"（Base64 拼接，tag 附在 ciphertext 后）
 *   5. 密钥版本路由：环境变量 AES_SECRET_KEY_V1 / AES_SECRET_KEY_V2 ... 支持平滑轮换
 *   6. 密钥来源仅环境变量，不落库、不入 git
 *
 * 安全红线（自审重点）：
 *   - IV 必须每次 SecureRandom 生成，不可复用
 *   - 密钥不可日志输出（本类 log 仅记录版本号与操作结果，不记录密钥与明文）
 */
@Component
@Slf4j
public class AesCryptoUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";
    private static final int IV_LENGTH = 12;          // GCM 推荐 IV 长度（字节）
    private static final int TAG_LENGTH_BITS = 128;   // GCM 认证标签长度（bit）
    private static final String PAYLOAD_DELIMITER = ":";

    /** 按版本号路由的密钥集合（启动时从环境变量加载，支持 V1/V2/...） */
    private final Map<Integer, SecretKeySpec> keyByVersion = new HashMap<>();

    /** 默认加密用的密钥版本（新数据加密时用此版本） */
    private final int defaultKeyVersion;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 构造器注入：从环境变量加载所有版本的 AES 密钥
     *
     * 实现步骤：
     *   1. 依次尝试加载 AES_SECRET_KEY_V1, V2, ... 直到某个版本未配置
     *   2. 第一个版本作为默认加密版本
     *   3. 至少需要 V1，否则启动告警但不禁用（FeishuService 凭证未配置时已有降级）
     *
     * @param keyV1 环境变量 AES_SECRET_KEY_V1（32 字节 Base64）
     * @param keyV2 环境变量 AES_SECRET_KEY_V2（可选，轮换时配置）
     */
    public AesCryptoUtil(
            @Value("${AES_SECRET_KEY_V1:}") String keyV1,
            @Value("${AES_SECRET_KEY_V2:}") String keyV2) {
        int loaded = 0;
        if (keyV1 != null && !keyV1.isEmpty()) {
            keyByVersion.put(1, toKeySpec(keyV1));
            loaded++;
        }
        if (keyV2 != null && !keyV2.isEmpty()) {
            keyByVersion.put(2, toKeySpec(keyV2));
            loaded++;
        }
        this.defaultKeyVersion = loaded > 0 ? 1 : -1;
        if (loaded == 0) {
            log.warn("[加密] 未配置 AES_SECRET_KEY_V1，app_secret 加解密功能不可用（FeishuConfig 注册将失败）");
        } else {
            log.info("[加密] AES 密钥加载完成，已加载 {} 个版本，默认加密版本 V{}", loaded, defaultKeyVersion);
        }
    }

    /**
     * 加密明文（用默认版本密钥）
     *
     * 实现步骤：
     *   1. 取默认版本密钥，IV 随机生成
     *   2. GCM 加密（输出含 ciphertext + tag）
     *   3. 拼装 "iv:ciphertext:tag" 返回
     *
     * @param plaintext 明文
     * @return "iv:ciphertext:tag"（Base64），调用方需自行保存 aes_key_version
     */
    public String encrypt(String plaintext) {
        return encrypt(plaintext, defaultKeyVersion);
    }

    /**
     * 加密明文（指定版本密钥）
     *
     * @param plaintext 明文
     * @param version   密钥版本
     */
    public String encrypt(String plaintext, int version) {
        if (plaintext == null) {
            return null;
        }
        SecretKeySpec key = keyByVersion.get(version);
        if (key == null) {
            throw new IllegalStateException("AES 密钥版本 V" + version + " 未配置");
        }
        try {
            // 步骤1：随机 IV（安全红线：每次必须随机，不可复用）
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            // 步骤2：GCM 加密
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherTextWithTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 步骤3：拼装 "iv:ciphertext:tag"（tag 附在 ciphertext 后，整体 Base64）
            Base64.Encoder encoder = Base64.getEncoder();
            return encoder.encodeToString(iv)
                    + PAYLOAD_DELIMITER
                    + encoder.encodeToString(cipherTextWithTag);
        } catch (Exception e) {
            log.error("[加密] 加密失败，version={}", version, e);
            throw new IllegalStateException("AES 加密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解密密文（按版本路由密钥）
     *
     * 实现步骤：
     *   1. 拆分 "iv:ciphertext:tag"
     *   2. 按版本取密钥
     *   3. GCM 解密（tag 校验失败会抛 AEADBadTagException，表示密文被篡改或密钥错误）
     *
     * @param payload "iv:ciphertext:tag"（Base64）
     * @param version 密钥版本（来自 feishu_config.aes_key_version）
     * @return 明文
     */
    public String decrypt(String payload, int version) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        SecretKeySpec key = keyByVersion.get(version);
        if (key == null) {
            throw new IllegalStateException("AES 密钥版本 V" + version + " 未配置，无法解密");
        }
        try {
            // 步骤1：拆分
            String[] parts = payload.split(PAYLOAD_DELIMITER, -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("密文格式错误，期望 'iv:ciphertext:tag'");
            }
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] iv = decoder.decode(parts[0]);
            byte[] cipherTextWithTag = decoder.decode(parts[1]);

            // 步骤2：GCM 解密（tag 校验内置）
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherTextWithTag);

            // 步骤3：返回明文
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[加密] 解密失败，version={}", version, e);
            throw new IllegalStateException("AES 解密失败：" + e.getMessage(), e);
        }
    }

    /**
     * 是否已配置可用密钥（注册功能依赖此判断）
     */
    public boolean isAvailable() {
        return !keyByVersion.isEmpty();
    }

    /**
     * 默认加密版本（注册时写入 feishu_config.aes_key_version）
     */
    public int getDefaultKeyVersion() {
        return defaultKeyVersion;
    }

    /**
     * Base64 字符串转 AES 密钥
     */
    private SecretKeySpec toKeySpec(String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        // AES-256 需要 32 字节密钥
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException("AES 密钥长度非法，期望 16/24/32 字节，实际 " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
