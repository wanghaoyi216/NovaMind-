package com.novamind.common.autoconfigure.idempotency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novamind.common.annotations.Idempotent;
import com.novamind.common.autoconfigure.idempotency.IdempotencyProperties;
import com.novamind.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * <h1>@Idempotent 切面 (P0 改造)</h1>
 *
 * <p>流程：</p>
 * <ol>
 *   <li>解析 SpEL，拿到 idempotency key（例 {@code "#req.orderId"} -> {@code "10086"}）</li>
 *   <li>查 Redis：{@code <prefix><spEL result>}</li>
 *   <li>命中且 {@code returnCached=true} -> 反序列化并直接返回</li>
 *   <li>命中且 {@code returnCached=false} -> 抛 {@link BizIllegalException}</li>
 *   <li>未命中 -> 执行业务方法，结果序列化为 JSON 写入 Redis，TTL 由注解 / 配置给出</li>
 * </ol>
 *
 * @author P0-refactor
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.aspectj.lang.ProceedingJoinPoint")
@ConditionalOnProperty(prefix = "novamind.idempotency", name = "annotation-enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(IdempotencyProperties.class)
public class IdempotentAspect {

    private final StringRedisTemplate redisTemplate;
    private final IdempotencyProperties properties;
    private final ObjectMapper objectMapper;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        if (!properties.isEnabled()) {
            return pjp.proceed();
        }
        // 1.解析 SpEL key
        String idemKey = parseSpelKey(pjp, idempotent);
        if (idemKey == null || idemKey.isBlank()) {
            // SpEL 计算结果为空 -> 不做幂等保护，直接执行
            return pjp.proceed();
        }
        String cacheKey = idempotent.prefix() + idemKey;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        // 2.命中
        if (cached != null) {
            log.debug("[Idempotent@Aspect] 命中缓存, key={}", cacheKey);
            if (idempotent.returnCached()) {
                MethodSignature sig = (MethodSignature) pjp.getSignature();
                Type genericReturnType = sig.getMethod().getGenericReturnType();
                return objectMapper.readValue(cached, new TypeReference<Object>() {
                    @Override
                    public Type getType() {
                        return genericReturnType;
                    }
                });
            }
            throw new BizIllegalException(idempotent.message());
        }

        // 3.未命中 -> 执行业务，结果写缓存
        Object result = pjp.proceed();
        try {
            String json = objectMapper.writeValueAsString(result);
            long ttl;
            if (idempotent.ttlSeconds() > 0) {
                ttl = idempotent.ttlSeconds();
            } else {
                ttl = TimeUnit.HOURS.toSeconds(properties.getExpireHours());
            }
            redisTemplate.opsForValue().set(cacheKey, json, ttl, TimeUnit.SECONDS);
            log.debug("[Idempotent@Aspect] 写入缓存, key={}, ttlSec={}", cacheKey, ttl);
        } catch (Exception e) {
            log.warn("[Idempotent@Aspect] 写缓存失败, key={}", cacheKey, e);
        }
        return result;
    }

    /**
     * 解析 SpEL，绑定 #req 到第一个参数
     */
    private String parseSpelKey(ProceedingJoinPoint pjp, Idempotent idempotent) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Object[] args = pjp.getArgs();
        EvaluationContext ec = new StandardEvaluationContext();
        // 把第一个参数绑定到 #req 上，兼容 OrderServiceImpl#placeOrder(req)
        if (args != null && args.length > 0) {
            ec.setVariable("req", args[0]);
        }
        // 也提供 #a0 / #p0 这种 spring 风格变量
        ec.setVariable("a0", args == null ? null : args[0]);
        ec.setVariable("p0", args == null ? null : args[0]);
        // 提供 #root.args 数组
        ec.setVariable("root", java.util.Map.of("args", args == null ? new Object[0] : args,
                "method", method));
        Expression expr = parser.parseExpression(idempotent.key());
        try {
            Object val = expr.getValue(ec);
            if (val == null) return null;
            return String.valueOf(val);
        } catch (Exception e) {
            log.warn("[Idempotent@Aspect] SpEL 解析失败, expr={}, args={}", idempotent.key(),
                    Arrays.toString(args), e);
            return null;
        }
    }
}
