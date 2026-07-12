package tech.jxing.returnvision;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 【启动类】退运智录后端服务
 *
 * 职责：Spring Boot 应用入口，扫描 Mapper 接口
 */
@SpringBootApplication
@MapperScan("tech.jxing.returnvision.model.mapper")
public class ReturnVisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReturnVisionApplication.class, args);
    }
}
