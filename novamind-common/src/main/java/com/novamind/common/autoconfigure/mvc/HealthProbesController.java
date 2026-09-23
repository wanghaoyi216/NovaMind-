package com.novamind.common.autoconfigure.mvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <h1>健康探针 (P0 改造)</h1>
 *
 * <p>K8s / Nacos / Spring Cloud Admin 的三类探针端点：</p>
 * <ol>
 *   <li>{@code /probe/liveness}  - 进程是否还活着（只检查 JVM 自身）</li>
 *   <li>{@code /probe/readiness} - 是否准备好接流量（应用启动完成 + 关键依赖 OK）</li>
 *   <li>{@code /probe/startup}   - 启动期探针（仅当 startup 阶段未完成时返回 503）</li>
 * </ol>
 *
 * <p>为什么路径不用 {@code /actuator/health/xxx}？</p>
 * <ul>
 *   <li>自定义 Controller 与 Actuator 内置探针路径重叠时，
 *       RequestMappingHandlerMapping（order=0）会永久遮蔽内置端点，
 *       导致 Actuator 健康聚合、健康分组等能力整体失效。</li>
 *   <li>业务自研探针走独立前缀 {@code /probe/**}，与 Actuator 互不干扰。</li>
 * </ul>
 *
 * <p>本实现采用 {@link Controller} 而非 {@code @RestController}，
 * 这样 MvcConfig 通过 {@code @Import} 直接导入即可，不需要 component-scan。</p>
 *
 * @author P0-refactor
 */
@Controller
public class HealthProbesController {

    /**
     * 标记 startup 是否完成。在 {@code ApplicationReadyEvent} 中翻转为 true（默认实现：永远 true）。
     * 留 hook 给业务方监听启动完成事件更新此值。
     */
    private volatile boolean startupCompleted = true;

    /**
     * 数据源，可能不存在（如纯网关服务、本地未配置数据库），故 required=false。
     */
    @Autowired(required = false)
    private DataSource dataSource;

    /**
     * Redis 客户端，可能不存在，故 required=false。
     */
    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    /* ============================================================
     * 探针：Liveness —— 进程是否还活着
     * ============================================================ */
    @GetMapping(value = "/probe/liveness", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> liveness() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("probe", "liveness");
        body.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(body);
    }

    /* ============================================================
     * 探针：Readiness —— 是否准备好接流量
     * 注意：返回值 200 表示 ready；503 表示 unready（k8s 会停止发请求）
     * P0 修复：不再只看 startupCompleted 标志（该标志当前恒为 true），
     * 补充真实依赖检查：DB connection.isValid + Redis ping。
     * 任一依赖失败返回 503 + DOWN 详情；
     * 对应 bean 不存在时降级为只检查 startupCompleted
     * （本地开发没起 MySQL 不能永远 503）。
     * ============================================================ */
    @GetMapping(value = "/probe/readiness", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("probe", "readiness");
        body.put("timestamp", System.currentTimeMillis());

        // 1.最轻量的检查：进程是否响应 + 启动是否完成
        if (!startupCompleted) {
            body.put("status", "UNREADY");
            body.put("reason", "startup not completed");
            return ResponseEntity.status(503).body(body);
        }

        // 2.真实依赖检查：DB / Redis，任一失败即 DOWN
        Map<String, Object> details = new LinkedHashMap<>();
        boolean dbDown = checkDatabase(details);
        boolean redisDown = checkRedis(details);
        body.put("details", details);

        if (dbDown || redisDown) {
            body.put("status", "DOWN");
            return ResponseEntity.status(503).body(body);
        }
        body.put("status", "UP");
        return ResponseEntity.ok(body);
    }

    /**
     * 数据库连通性检查：从池里借一条连接做 isValid(1) 探活。
     * @return true 表示 DOWN（失败）；false 表示 UP 或跳过
     */
    private boolean checkDatabase(Map<String, Object> details) {
        if (dataSource == null) {
            // bean 不存在：本地开发没起 MySQL 不能永远 503，降级为跳过
            details.put("db", "SKIPPED(no DataSource bean)");
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(1)) {
                details.put("db", "UP");
                return false;
            }
            details.put("db", "DOWN(connection invalid)");
            return true;
        } catch (Exception e) {
            details.put("db", "DOWN:" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return true;
        }
    }

    /**
     * Redis 连通性检查：PING 期望返回 PONG。
     * @return true 表示 DOWN（失败）；false 表示 UP 或跳过
     */
    private boolean checkRedis(Map<String, Object> details) {
        if (stringRedisTemplate == null || stringRedisTemplate.getConnectionFactory() == null) {
            // bean 不存在：降级为跳过
            details.put("redis", "SKIPPED(no StringRedisTemplate bean)");
            return false;
        }
        try (RedisConnection conn = stringRedisTemplate.getConnectionFactory().getConnection()) {
            String pong = conn.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                details.put("redis", "UP");
                return false;
            }
            details.put("redis", "DOWN(ping=" + pong + ")");
            return true;
        } catch (Exception e) {
            details.put("redis", "DOWN:" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return true;
        }
    }

    /* ============================================================
     * 探针：Startup —— 启动期
     * 启动未完成时返回 503，启动完成后 200 并写回 startup completed 时间
     * ============================================================ */
    @GetMapping(value = "/probe/startup", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startup() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("probe", "startup");
        body.put("timestamp", System.currentTimeMillis());
        if (!startupCompleted) {
            body.put("status", "STARTING");
            return ResponseEntity.status(503).body(body);
        }
        body.put("status", "UP");
        return ResponseEntity.ok(body);
    }

    /**
     * 设置 startup 状态，由业务方通过 {@code ApplicationReadyEvent} 监听器调用。
     * 这里保留 setter 不开放 setter，仅包级别。MVC 启动时会调用 {@link #markStartupCompleted()}。
     */
    public void markStartupCompleted() {
        this.startupCompleted = true;
    }
}
