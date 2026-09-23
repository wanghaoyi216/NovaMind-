package com.novamind.aigc.quota;

import com.novamind.common.exceptions.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * AI 对话的"每用户每日 Token 配额"治理（移植自墨问 per-user LLM Token 配额思路）。
 *
 * <p><b>记账方式</b>：Redis {@code INCRBY} 按 {@code userId + 自然日窗口} 原子记账，
 * key 形如 {@code aigc:token_quota:{userId}:{yyyyMMdd}}；当日首次写入设置 25 小时过期
 * （跨过零点后旧 key 仍保留 1h 余量，避免跨日边界的误判与残留泄漏）。
 * 超过上限时拒绝请求并返回友好提示。
 *
 * <p><b>失败哲学</b>：配额是成本治理手段，不是核心链路依赖 —— Redis 异常一律
 * fail-open 放行并记 warn 日志，绝不让配额检查本身把对话功能打挂。
 */
@Slf4j
@Component
public class TokenQuotaService {

    private static final String KEY_PREFIX = "aigc:token_quota:";

    /** 自然日窗口后缀格式：yyyyMMdd */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 25 小时过期：覆盖一个完整自然日 + 跨零点后的余量，避免边界误判 */
    private static final Duration KEY_TTL = Duration.ofHours(25);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 每用户每日 Token 上限，默认 20 万 */
    @Value("${aigc.quota.daily-token-limit:200000}")
    private long dailyTokenLimit;

    /**
     * 检查并预扣当日配额。原子 INCRBY 记账，超限抛 {@link BadRequestException}
     * （由 {@code CommonExceptionAdvice} 统一转换为错误响应）。
     *
     * @param userId          用户 id；为 null（未登录调用方）时直接跳过配额
     * @param estimatedTokens 本次请求预估消耗的 token 数（如 question.length() / 4）
     */
    public void checkAndDeduct(Long userId, int estimatedTokens) {
        // 未登录调用方无法归属到具体用户，跳过配额记账
        if (userId == null) {
            return;
        }
        int est = Math.max(1, estimatedTokens);
        try {
            var key = KEY_PREFIX + userId + ":" + LocalDate.now().format(DAY_FORMAT);
            Long used = this.stringRedisTemplate.opsForValue().increment(key, est);
            if (used != null && used == est) {
                // 当日首次记账 → 设置 25h 过期（跨自然日的余量窗口）
                this.stringRedisTemplate.expire(key, KEY_TTL);
            }
            if (used != null && used > this.dailyTokenLimit) {
                log.warn("[TokenQuota] daily quota exceeded: userId={}, used={}, limit={}",
                        userId, used, this.dailyTokenLimit);
                throw new BadRequestException("今日 AI 使用额度已用完，请明天再来");
            }
        } catch (BadRequestException e) {
            // 超限拒绝需要向上传播，不能被下面的 fail-open 吞掉
            throw e;
        } catch (Exception e) {
            // fail-open：Redis 抖动 / 不可用时放行，绝不阻塞正常对话
            log.warn("[TokenQuota] quota check skipped due to redis error (fail-open): {}", e.getMessage());
        }
    }
}
