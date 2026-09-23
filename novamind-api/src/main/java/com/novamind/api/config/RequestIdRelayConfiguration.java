package com.novamind.api.config;

import com.novamind.common.utils.UserContext;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.novamind.common.constants.Constant.*;

@Configuration
@EnableFeignClients(basePackages = "com.novamind.api.client")
public class RequestIdRelayConfiguration {

    @Bean
    public RequestInterceptor requestIdInterceptor() {
        return template -> {
            // 1. 透传分布式链路追踪 RequestId
            String requestId = MDC.get(REQUEST_ID_HEADER);
            if (requestId != null) {
                template.header(REQUEST_ID_HEADER, requestId);
            }
            // 2. 标记内部 Feign 调用来源
            template.header(REQUEST_FROM_HEADER, FEIGN_ORIGIN_NAME);

            // 3. 透传当前登录用户上下文（若存在）
            Long userId = UserContext.getUser();
            if (userId != null) {
                template.header(USER_HEADER, String.valueOf(userId));
            }
        };
    }
}