package com.novamind.aigc.memory.mongodb;

import cn.hutool.core.collection.CollStreamUtil;
import com.novamind.aigc.memory.MessageUtil;
import com.novamind.aigc.memory.MyChatMemoryRepository;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

public class MongoDBChatMemoryRepository implements MyChatMemoryRepository {

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public List<String> findConversationIds() {
        var chatRecordList = this.mongoTemplate.findAll(ChatRecord.class);
        return CollStreamUtil.toList(chatRecordList, ChatRecord::getConversationId);
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        var query = Query.query(Criteria.where("conversationId").is(conversationId));
        var chatRecord = this.mongoTemplate.findOne(query, ChatRecord.class);
        if (null == chatRecord) {
            return List.of();
        }
        return CollStreamUtil.toList(chatRecord.getMessages(), MessageUtil::toMessage);
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        deleteByConversationId(conversationId);
        var chatRecord = ChatRecord.builder()
                .conversationId(conversationId)
                .messages(CollStreamUtil.toList(messages, MessageUtil::toJson))
                .build();
        this.mongoTemplate.save(chatRecord);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        var query = Query.query(Criteria.where("conversationId").is(conversationId));
        this.mongoTemplate.remove(query, ChatRecord.class);
    }

    @Override
    public void optimization(String conversationId) {
        var query = Query.query(Criteria.where("conversationId").is(conversationId));
        var chatRecord = this.mongoTemplate.findOne(query, ChatRecord.class);
        if (chatRecord == null || chatRecord.getMessages() == null || chatRecord.getMessages().isEmpty()) {
            return;
        }
        var messages = chatRecord.getMessages();
        int fromIndex = Math.max(0, messages.size() - 2);
        messages.subList(fromIndex, messages.size()).clear();
        if (messages.isEmpty()) {
            this.mongoTemplate.remove(query, ChatRecord.class);
            return;
        }
        this.mongoTemplate.save(chatRecord);
    }

}
