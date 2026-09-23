package com.novamind.aigc.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "tj.platform", name = "file", havingValue = "ALI")
@EnableConfigurationProperties(AliYunProperties.class)
public class AliYunConfig {

    @Bean
    public OSS aliOssClient(AliYunProperties prop) {
        return new OSSClientBuilder()
                .build(prop.getOss().getEndpoint(),
                        prop.getOss().getAccessId(),
                        prop.getOss().getAccessKey());
    }

}
