package com.novamind.common.autoconfigure.mvc;


import com.novamind.common.autoconfigure.mvc.advice.CommonExceptionAdvice;
import com.novamind.common.autoconfigure.mvc.advice.WrapperResponseBodyAdvice;
import com.novamind.common.autoconfigure.mvc.converter.WrapperResponseMessageConverter;
import com.novamind.common.filters.RequestIdFilter;
import jakarta.servlet.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@Slf4j
@ConditionalOnClass({CommonExceptionAdvice.class, Filter.class, WebMvcConfigurer.class})
@Configuration
@Import(HealthProbesController.class) // P0 改造：导入三态探针 Controller（@Controller 注解可被 RequestMappingHandlerMapping 识别）
public class MvcConfig implements WebMvcConfigurer {

    /**
     * <h1>通用的ControllerAdvice异常处理器</h1>
     */
    @Bean
    public CommonExceptionAdvice commonExceptionAdvice(){
        return new CommonExceptionAdvice();
    }

    @Bean
    public RequestIdFilter requestIdFilter(){
        return new RequestIdFilter();
    }

    @Bean
    @ConditionalOnMissingClass("org.springframework.cloud.gateway.filter.GlobalFilter")
    public WrapperResponseMessageConverter wrapperResponseMessageConverter(
            MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter
    ){
        return new WrapperResponseMessageConverter(mappingJackson2HttpMessageConverter);
    }

    @Bean
    public WrapperResponseBodyAdvice wrapperResponseBodyAdvice(){
        return new WrapperResponseBodyAdvice();
    }

    /**
     * <h2>P0 改造：健康探针</h2>
     * 监听 {@link ApplicationReadyEvent}，应用 ready 后把 startup 状态翻为 true，
     * K8s startupProbe 再调一次就 200 了。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onReady(ApplicationReadyEvent event) {
        // 这个监听钩子的作用是给将来业务方启动后做 DB / MQ / Redis 自检预留的位置
        log.info("[MvcConfig] ApplicationReady, startup probe 可用");
    }
}
