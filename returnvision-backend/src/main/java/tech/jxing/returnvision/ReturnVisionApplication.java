package tech.jxing.returnvision;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 【启动类】退运智录后端服务
 *
 * 职责：Spring Boot 应用入口，扫描 Mapper 接口
 * @EnableScheduling：启用定时任务（F02 保留期清理）
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("tech.jxing.returnvision.model.mapper")
public class ReturnVisionApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReturnVisionApplication.class, args);
    }
}
