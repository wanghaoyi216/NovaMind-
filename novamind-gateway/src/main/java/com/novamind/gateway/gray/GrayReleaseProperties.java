package com.novamind.gateway.gray;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 灰度发布配置属性（绑定 nacos gray-rule.yaml 的 tj.gray.* 节点）
 *
 * <p>使用 {@link RefreshScope} 后，Nacos 推送即热更新，无需重启网关。
 * 该 Bean 必须由 Spring 容器管理，因此即使在 Spring Cloud Gateway 的 WebFlux
 * 环境下也走 @Component 注入。</p>
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "tj.gray")
public class GrayReleaseProperties {

    /** 灰度总开关 */
    private boolean enabled = false;

    /** 当前阶段（10 / 30 / 100） */
    private int currentPhase = 0;

    /** 哈希分桶模数（默认 100） */
    private int bucketModulus = 100;

    /** 灰度标记 Header 名 */
    private String grayHeader = "X-Gray-Tag";

    /** 强制走新版本的 header 取值 */
    private String forceGrayValue = "gray";

    /** 强制走老版本的 header 取值 */
    private String forceBaseValue = "base";

    /** 三档规则列表 */
    private List<GrayRule> rules = new ArrayList<>();

    @Data
    public static class GrayRule {
        /** 阶段编号（10/30/100） */
        private int phase;
        /** 灰度百分比 */
        private int percent;
        /** 是否启用 */
        private boolean enabled;
        /** 目标 agent / 服务名 */
        private String agent;
        /** 目标版本号（v2 / canary / blue 等） */
        private String version;
        /** 描述 */
        private String description;
        /** 命中的桶索引区间（"0-9" / "0-29" / "0-99"） */
        private String bucketRange;
    }
}
