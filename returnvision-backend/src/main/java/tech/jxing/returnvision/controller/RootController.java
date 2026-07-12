package tech.jxing.returnvision.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 【接口层】根路由控制器
 *
 * 职责：提供健康检查端点，访问 / 返回 {"status": "ok"}
 */
@RestController
public class RootController {

    /**
     * 健康检查
     *
     * @return {"status": "ok"}
     */
    @GetMapping("/")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
