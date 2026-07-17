package tech.jxing.returnvision.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 【配置层】CORS跨域配置
 *
 * 职责：允许前端 Vue 开发服务器（localhost:5173）及线上域名跨域访问后端 API
 * 说明：线上同源访问理论上不触发 CORS，但 Nginx 反代会 scheme 降级为 http，
 *      若 server.forward-headers-strategy 未生效，Spring 会误判为跨域请求，
 *      因此这里同时放行线上域名作为兜底，避免出现 403 Invalid CORS request。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:*",
                        "https://returnvision.jxing.tech",
                        "http://returnvision.jxing.tech"
                )
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
