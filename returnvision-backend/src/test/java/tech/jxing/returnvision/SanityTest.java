package tech.jxing.returnvision;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 占位测试（Phase 1 测试基础设施可用性验证）
 * <p>
 * 1. 不依赖任何业务代码、不拉起 Spring 上下文、不连数据库
 * 2. 仅用于证明 src/test 骨架、JUnit5 依赖、test profile 配置整体可运行
 * 3. 后续真实测试落地后此测试可保留作为"基础设施冒烟"也可删除
 * </p>
 *
 * @author ReturnVision
 */
class SanityTest {

    @Test
    @DisplayName("测试基础设施可运行 - JUnit5 已就绪")
    void contextLoads() {
        assertTrue(true, "占位测试通过：测试基础设施可运行");
    }
}