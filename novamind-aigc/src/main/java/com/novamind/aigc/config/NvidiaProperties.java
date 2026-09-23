package com.novamind.aigc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "tj.ai.nvidia")
public class NvidiaProperties {

    private String apiKey;
    private String baseUrl = "https://integrate.api.nvidia.com/v1";
    private String defaultTextModel = "deepseek-ai/deepseek-r1";
    private String defaultVisionModel = "meta/llama-3.2-90b-vision-instruct";
    private List<String> textModels;
    private List<String> visionModels;

}
