package tech.jxing.returnvision.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.jxing.returnvision.security.TenantContext;

import java.util.concurrent.TimeUnit;

/**
 * 【配置层】应用配置
 *
 * 职责：提供 OkHttpClient Bean + MyBatis-Plus 拦截器（租户隔离 + 分页）
 * 关联：docs/14 §3.3.1
 *
 * 多租户改造（v2.3）：
 *   - TenantLineInnerInterceptor 对 return_records / sys_user 自动加 feishu_config_id 条件
 *   - 平台级（feishuConfigId=null）不加条件，看全部
 *   - 后台任务无 SecurityContext，天然返回 null，不加条件，清理所有租户
 *   - 插件顺序：租户插件必须在分页插件之前（否则分页 SQL 不带租户条件）
 */
@Configuration
public class AppConfig {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 步骤1：租户隔离插件（必须在分页之前 add）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long cid = TenantContext.currentFeishuConfigId();
                // 平台级返回 null（配合 ignoreTable 动态跳过，见下）
                return cid == null ? null : new LongValue(cid);
            }

            @Override
            public String getTenantIdColumn() {
                return "feishu_config_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                // 平台级（无 SecurityContext 或平台 ADMIN）不对任何表加条件
                // 后台定时任务无 SecurityContext，天然走此分支，清理所有租户
                if (TenantContext.currentFeishuConfigId() == null) {
                    return true;
                }
                // 公司级：仅 return_records / sys_user 加租户条件，其他表不隔离
                return !"return_records".equals(tableName) && !"sys_user".equals(tableName);
            }
        }));

        // 步骤2：分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}

