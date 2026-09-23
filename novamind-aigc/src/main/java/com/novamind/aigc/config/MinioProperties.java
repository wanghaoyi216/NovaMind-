package com.novamind.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "tj.minio")
public class MinioProperties {
    private String endpoint;
    private String publicEndpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
}

