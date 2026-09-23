package com.novamind.aigc.memory;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

public class RedisChatMemoryRepository implements MyChatMemoryRepository {

    public static final String DEFAULT_PREFIX = "CHAT:";

    private final String prefix;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public RedisChatMemoryRepository() {
        this(DEFAULT_PREFIX);
    }

    public RedisChatMemoryRepository(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public List<String> findConversationIds() {
        Set<String> keys = this.stringRedisTemplate.keys(this.prefix + "*");
        if (null == keys) {
            return List.of();
        }
        return StreamUtil.of(keys)
                .map(key -> StrUtil.replace(key, this.prefix, ""))
                .toList();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        var redisKey = getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);
        var messages = listOps.range(0, -1);
        return CollStreamUtil.toList(messages, MessageUtil::toMessage);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        var redisKey = getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);
        deleteByConversationId(conversationId);
        messages.forEach(message -> listOps.rightPush(MessageUtil.toJson(message)));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        var redisKey = getKey(conversationId);
        this.stringRedisTemplate.delete(redisKey);
    }

    @Override
    public void optimization(String conversationId) {
        var redisKey = getKey(conversationId);
        var listOps = this.stringRedisTemplate.boundListOps(redisKey);
        listOps.rightPop(2);
    }

    private String getKey(String conversationId) {
        return this.prefix + conversationId;
    }

}
