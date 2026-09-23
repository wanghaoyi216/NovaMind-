package com.novamind.common.autoconfigure.idempotency;

import com.novamind.common.exceptions.BizIllegalException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;

/**
 * <h1>Idempotency-Key 拦截器 (P0 改造)</h1>
 *
 * <p>拦截 POST / PUT / DELETE / PATCH 请求：</p>
 * <ol>
 *   <li>读取 {@code Idempotency-Key} HTTP 头</li>
 *   <li>命中 Redis 缓存 -> 直接把缓存的响应体 / 状态码复写到 {@link HttpServletResponse}</li>
 *   <li>未命中 -> 走业务，wrap 一个 {@link CachedResponseWrapper} 缓冲响应，
 *       {@link #afterCompletion} 时把状态码 + body 序列化进 Redis（带 24h TTL）</li>
 * </ol>
 *
 * <p>Redis 存的字节格式：</p>
 * <pre>
 *   4 bytes: HTTP 状态码 (ASCII 数字)\n
 *   其余:   原始响应体字节流
 * </pre>
 *
 * <p>这样既保留了状态码，也让 JSON / 二进制响应可序列化。</p>
 *
 * @author P0-refactor
 */
@Slf4j
public class IdempotencyKeyInterceptor implements HandlerInterceptor {

    /** request attribute key，标识这次请求是否要写缓存（命中则无需缓存） */
    public static final String ATTR_IS_HIT = "idempotency.hit";
    public static final String ATTR_CACHE_KEY = "idempotency.cache-key";
    public static final String ATTR_RESPONSE_WRAPPER = "idempotency.response-wrapper";

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyProperties properties;

    public IdempotencyKeyInterceptor(StringRedisTemplate redisTemplate, IdempotencyProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!properties.isEnabled() || !properties.isInterceptorEnabled()) {
            return true;
        }
        String method = request.getMethod();
        if (method == null || !properties.getInterceptMethods().contains(method.toUpperCase())) {
            return true;
        }
        // 排除路径
        String uri = request.getRequestURI();
        for (String prefix : properties.getExcludePaths()) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        String idemKey = request.getHeader(properties.getHeaderName());
        if (idemKey == null || idemKey.isBlank()) {
            if (properties.isRequireHeader()) {
                throw new BizIllegalException("missing required header: " + properties.getHeaderName());
            }
            return true; // 没带 Idempotency-Key 头，不做幂等保护（不阻止业务）
        }
        String cacheKey = properties.getCacheKeyPrefix() + idemKey;

        // 1.命中 -> 直接复写响应
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            writeCached(response, cached);
            request.setAttribute(ATTR_IS_HIT, Boolean.TRUE);
            log.debug("[Idempotency] 命中缓存, key={}", idemKey);
            return false; // 不进入 controller
        }

        // 2.未命中 -> 包装 response，业务完成后我们写到 Redis
        CachedResponseWrapper wrapper = new CachedResponseWrapper(response);
        request.setAttribute(ATTR_RESPONSE_WRAPPER, wrapper);
        request.setAttribute(ATTR_CACHE_KEY, cacheKey);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (Boolean.TRUE.equals(request.getAttribute(ATTR_IS_HIT))) {
            return;
        }
        String cacheKey = (String) request.getAttribute(ATTR_CACHE_KEY);
        CachedResponseWrapper wrapper = (CachedResponseWrapper) request.getAttribute(ATTR_RESPONSE_WRAPPER);
        if (cacheKey == null || wrapper == null) {
            return;
        }
        try {
            byte[] body = wrapper.getBody();
            int status = wrapper.getStatus();
            String payload = status + "\n" + new String(body, StandardCharsets.UTF_8);
            redisTemplate.opsForValue().set(cacheKey, payload, properties.getExpireHours(), TimeUnit.HOURS);
            log.debug("[Idempotency] 写入缓存, key={}, status={}, bytes={}", cacheKey, status, body.length);
        } catch (Exception e) {
            log.warn("[Idempotency] 写缓存失败, key={}", cacheKey, e);
        }
    }

    private void writeCached(HttpServletResponse response, String cached) throws IOException {
        int newline = cached.indexOf('\n');
        if (newline < 0) {
            response.setStatus(200);
            response.getWriter().write(cached);
            return;
        }
        int status = 200;
        try {
            status = Integer.parseInt(cached.substring(0, newline));
        } catch (Exception ignore) {
            // 状态码解析失败兜底 200
        }
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        // 不在响应头里暴露 Idempotency-Key 命中标记，避免信息泄漏
        response.getOutputStream().write(cached.substring(newline + 1).getBytes(StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    /**
     * 用来在 afterCompletion 阶段拿到响应体的 wrapper
     */
    public static class CachedResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final PrintWriter writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8));
        private int status = 200;

        public CachedResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public PrintWriter getWriter() {
            return writer;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {
                    // 不实现 listener 模式
                }

                @Override
                public void write(int b) {
                    buffer.write(b);
                }
            };
        }

        @Override
        public void setStatus(int sc) {
            super.setStatus(sc);
            this.status = sc;
        }

        @Override
        public void sendError(int sc) throws IOException {
            super.sendError(sc);
            this.status = sc;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            super.sendError(sc, msg);
            this.status = sc;
        }

        public byte[] getBody() {
            writer.flush();
            return buffer.toByteArray();
        }

        public int getStatus() {
            return status;
        }
    }

    /**
     * 工具方法：把 {@link IdempotencyProperties#cacheKeyPrefix} 与 key 拼接，并加上短哈希，
     * 防止 key 直接被 Redis keyspace scan 看到。
     */
    public static String hashKey(String prefix, String key) {
        CRC32 crc = new CRC32();
        crc.update(key.getBytes(StandardCharsets.UTF_8));
        return prefix + crc.getValue();
    }

    /**
     * 方便测试和直接调用层获取 TTL
     */
    public Duration ttl() {
        return Duration.ofHours(properties.getExpireHours());
    }
}
