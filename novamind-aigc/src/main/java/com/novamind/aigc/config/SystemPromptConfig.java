package com.novamind.aigc.config;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Getter
@Configuration
@RequiredArgsConstructor
public class SystemPromptConfig {

    private final NacosConfigManager nacosConfigManager;
    private final AIProperties aiProperties;

    private final AtomicReference<String> chatSystemMessage = new AtomicReference<>();
    private final AtomicReference<String> routeAgentSystemMessage = new AtomicReference<>();
    private final AtomicReference<String> recommendAgentSystemMessage = new AtomicReference<>();
    private final AtomicReference<String> buyAgentSystemMessage = new AtomicReference<>();
    private final AtomicReference<String> textSystemMessage = new AtomicReference<>();

    @PostConstruct
    public void init() {
        loadConfig(aiProperties.getSystem().getChat(), chatSystemMessage);
        loadConfig(aiProperties.getSystem().getRouteAgent(), routeAgentSystemMessage);
        loadConfig(aiProperties.getSystem().getRecommendAgent(), recommendAgentSystemMessage);
        loadConfig(aiProperties.getSystem().getBuyAgent(), buyAgentSystemMessage);
        loadConfig(aiProperties.getSystem().getText(), textSystemMessage);
    }

    private void loadConfig(AIProperties.System.Chat chatConfig, AtomicReference<String> target) {
        try {
            var dataId = chatConfig.getDataId();
            var group = chatConfig.getGroup();
            var timeoutMs = chatConfig.getTimeoutMs();
            var config = nacosConfigManager.getConfigService().getConfig(dataId, group, timeoutMs);

            target.set(config);
            log.info("读取配置[{}]成功", target);

            nacosConfigManager.getConfigService().addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String info) {
                    target.set(info);
                    log.info("热更新配置[{}]成功", target);
                }
            });
        } catch (Exception e) {
            log.error("加载或监听Nacos配置失败", e);
        }
    }

}