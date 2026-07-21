package tech.jxing.returnvision;

import org.springframework.test.context.ActiveProfiles;

/**
 * 测试基类（可扩展骨架）
 * <p>
 * 1. 统一声明 @ActiveProfiles("test")，让所有继承此类的测试自动进入 test profile
 * 2. 此基类不加 @SpringBootTest，避免占位测试被迫拉起整个 Spring 上下文
 * 3. 后续扩展：公共 @MockBean 声明、ResponseResult 断言工具方法等下沉到此
 * </p>
 *
 * @author ReturnVision
 */
@ActiveProfiles("test")
public abstract class AbstractTest {
}