package fun.commons.lotask4j.config;

import com.alibaba.fastjson2.support.config.FastJsonConfig;
import com.alibaba.fastjson2.support.spring6.http.converter.FastJsonHttpMessageConverter;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Web MVC 配置类
 *
 * 配置:
 * - FastJSON2 HTTP 消息转换器 (camelCase 命名, 项目统一契约)
 * - API 文档配置
 * - 拦截器和过滤器
 *
 * 注意:
 * 1. OpenID 组件由 framework4j-id 自动注入，无需手动配置。
 * 2. 不加 @EnableWebMvc (保留 framework4j 各模块的 WebMvcConfigurer 自动装配)。
 * 3. 显式注册 FastJsonHttpMessageConverter 并保持默认 (camelCase) 命名策略:
 *    SDK 某处自动注册的转换器带 SnakeCase 策略 (实测响应 appSecret→app_secret、
 *    反序列化只认 snake 键, camelCase 的 @RequestBody 驼峰字段绑定失败)。
 *    本项目前端 (frontend/src/api) 与 DTO 全部按 camelCase 契约编写, 故统一覆盖为
 *    默认策略, 序列化/反序列化行为一致且可预期。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        FastJsonHttpMessageConverter converter = new FastJsonHttpMessageConverter();

        FastJsonConfig config = new FastJsonConfig();
        config.setCharset(StandardCharsets.UTF_8);
        // 命名策略保持默认 camelCase (项目统一契约; SDK 自动注册的转换器带 SnakeCase 策略)

        converter.setFastJsonConfig(config);

        converter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                new MediaType("application", "*+json")));

        // 插到队头: 优先于 SDK/默认注册的转换器
        List<HttpMessageConverter<?>> ordered = new ArrayList<>();
        ordered.add(converter);
        ordered.addAll(converters);
        converters.clear();
        converters.addAll(ordered);
    }
}
