package fun.commons.lotask4j.config;

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
 * - FastJSON2 为默认的 JSON 序列化库
 * - API 文档配置
 * - 拦截器和过滤器
 *
 * 注意: OpenID 组件由 framework4j-id 自动注入，无需手动配置
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {


}
