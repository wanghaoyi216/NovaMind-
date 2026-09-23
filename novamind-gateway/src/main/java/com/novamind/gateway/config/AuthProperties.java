package com.novamind.gateway.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "tj.auth")
public class AuthProperties implements InitializingBean {

    private Set<String> excludePath = new HashSet<>();

    @Override
    public void afterPropertiesSet() throws Exception {
        excludePath.add("*:/error/**");
        excludePath.add("GET:/actuator/**");
        excludePath.add("GET:/jwks");
        excludePath.add("POST:/accounts/login");
        excludePath.add("POST:/accounts/admin/login");
        excludePath.add("GET:/accounts/refresh");
        excludePath.add("POST:/users/register");
        excludePath.add("GET:/users/info/**");
        excludePath.add("GET:/categorys/all");
        excludePath.add("GET:/medias/signature/play");
        excludePath.add("GET:/session/hot");
        excludePath.add("GET:/ais/session/hot");
        excludePath.add("GET:/chat/templates");
        excludePath.add("GET:/ais/chat/templates");
    }
}
