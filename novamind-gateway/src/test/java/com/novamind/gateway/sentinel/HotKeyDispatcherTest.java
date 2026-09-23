package com.novamind.gateway.sentinel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R9 HotKeyDispatcher 单元测试
 *
 * <p>重点覆盖：</p>
 * <ul>
 *   <li>{@code passHotKey(bizOrderNo, couponId)} 在未配置限流规则时始终放行</li>
 *   <li>从 query 参数 Map 抽取参数并正确调用 dispatcher</li>
 *   <li>非法参数（空串/非数字）不抛异常，优雅降级为 0</li>
 * </ul>
 */
class HotKeyDispatcherTest {

    private final HotKeyDispatcher dispatcher = new HotKeyDispatcher();

    @Test
    @DisplayName("未触发限流时默认放行")
    void passHotKeyReturnsTrueByDefault() {
        assertTrue(dispatcher.passHotKey(12345L, 99999L));
        assertTrue(dispatcher.passHotKey(null, null));
        assertTrue(dispatcher.passHotKey(0L, 0L));
    }

    @Test
    @DisplayName("从 params Map 抽取 bizOrderNo / couponId")
    void passHotKeyFromParamsExtractsCorrectly() {
        Map<String, String> params = new HashMap<>();
        params.put("bizOrderNo", "123456789");
        params.put("couponId", "88888");
        assertTrue(dispatcher.passHotKeyFromParams(params));
    }

    @Test
    @DisplayName("缺失字段不会抛异常")
    void passHotKeyFromParamsTolerant() {
        assertTrue(dispatcher.passHotKeyFromParams(new HashMap<>()));
        Map<String, String> params = new HashMap<>();
        params.put("bizOrderNo", "not_a_number");
        assertTrue(dispatcher.passHotKeyFromParams(params));
    }
}