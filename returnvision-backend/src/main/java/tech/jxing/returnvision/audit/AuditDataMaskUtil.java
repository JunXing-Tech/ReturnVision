package tech.jxing.returnvision.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【审计模块】请求参数脱敏工具
 *
 * 职责：对请求参数 JSON 脱敏，密码等敏感字段值替换为 ***
 * 层级：audit 层
 * 关联：docs/04 第 4.8.7 节
 *
 * 脱敏字段：
 *   password / old_password / new_password / app_secret / api_key 等
 */
@Component
@Slf4j
public class AuditDataMaskUtil {

    private static final List<String> SENSITIVE_FIELDS = List.of(
            "password", "old_password", "new_password",
            "app_secret", "api_key", "secret", "token"
    );

    private static final String MASKED_VALUE = "***";

    private final ObjectMapper objectMapper;

    public AuditDataMaskUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 对参数对象脱敏后转 JSON 字符串
     *
     * 实现步骤：
     *   1. 参数为空返回 null
     *   2. 参数为 Map，递归脱敏
     *   3. 其他类型尝试转 JSON
     *
     * @param args 方法参数数组
     * @return 脱敏后的 JSON 字符串
     */
    public String mask(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }

        try {
            // 简化处理：只取第一个参数（鉴权接口通常只有一个请求体）
            // 复杂场景可扩展为遍历所有参数
            Object arg = args[0];
            if (arg == null) {
                return null;
            }

            // 步骤2：Map 类型递归脱敏
            if (arg instanceof Map) {
                Map<String, Object> masked = maskMap((Map<String, Object>) arg);
                return objectMapper.writeValueAsString(masked);
            }

            // 步骤3：其他类型直接转 JSON（不含敏感字段时安全）
            return objectMapper.writeValueAsString(arg);
        } catch (Exception e) {
            log.warn("[审计脱敏] 序列化失败：{}", e.getMessage());
            return "[serialize_error]";
        }
    }

    /**
     * 递归脱敏 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> maskMap(Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (SENSITIVE_FIELDS.contains(key)) {
                result.put(entry.getKey(), MASKED_VALUE);
            } else if (entry.getValue() instanceof Map) {
                result.put(entry.getKey(), maskMap((Map<String, Object>) entry.getValue()));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
