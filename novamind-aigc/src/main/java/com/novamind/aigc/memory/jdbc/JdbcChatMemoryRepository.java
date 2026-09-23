package com.novamind.aigc.memory.jdbc;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.novamind.aigc.entity.ChatRecord;
import com.novamind.aigc.memory.MessageUtil;
import com.novamind.aigc.memory.MyChatMemoryRepository;
import com.novamind.aigc.service.ChatRecordService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;

import java.util.List;
import java.util.Objects;

public class JdbcChatMemoryRepository implements MyChatMemoryRepository {

    @Resource
    private ChatRecordService chatRecordService;

    @Override
    public List<String> findConversationIds() {
        var queryWrapper = new QueryWrapper<ChatRecord>();
        queryWrapper.select("DISTINCT conversationId");
        var chatRecordList = this.chatRecordService.list(queryWrapper);
        return CollStreamUtil.toList(chatRecordList, ChatRecord::getConversationId);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        var chatRecordList = this.chatRecordService.lambdaQuery()
                .eq(ChatRecord::getConversationId, conversationId)
                .orderByAsc(ChatRecord::getCreateTime)
                .list();
        return CollStreamUtil.toList(chatRecordList, chatRecord -> MessageUtil.toMessage(chatRecord.getData()));
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        deleteByConversationId(conversationId);
        var userId = Convert.toLong(StrUtil.subBefore(conversationId, '_', true));

        var chatRecordList = CollStreamUtil.toList(messages, message -> ChatRecord.builder()
                .data(MessageUtil.toJson(message))
                .conversationId(conversationId)
                .creater(userId)
                .updater(userId)
                .build());
        this.chatRecordService.saveBatch(chatRecordList);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        var queryWrapper = Wrappers.<ChatRecord>lambdaQuery()
                .eq(ChatRecord::getConversationId, conversationId);
        this.chatRecordService.remove(queryWrapper);
    }

    @Override
    public void optimization(String conversationId) {
        var records = this.chatRecordService.lambdaQuery()
                .eq(ChatRecord::getConversationId, conversationId)
                .orderByDesc(ChatRecord::getCreateTime)
                .last("LIMIT 2")
                .list();
        if (records == null || records.isEmpty()) {
            return;
        }
        var ids = records.stream()
                .map(ChatRecord::getId)
                .filter(Objects::nonNull)
                .toList();
        if (!ids.isEmpty()) {
            this.chatRecordService.removeByIds(ids);
        }
    }

}
