package com.novamind.aigc.memory;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.novamind.aigc.config.ToolResultHolder;
import com.novamind.aigc.constants.Constant;
import org.springframework.ai.chat.messages.*;

import java.util.Map;

public class MessageUtil {

    public static String toJson(Message message) {
        var myMessage = BeanUtil.toBean(message, MyMessage.class);
        myMessage.setTextContent(message.getText());

        if (message instanceof AssistantMessage assistantMessage) {
            myMessage.setToolCalls(assistantMessage.getToolCalls());
            var messageId = MapUtil.getStr(message.getMetadata(), Constant.ID);
            var requestId = Convert.toStr(ToolResultHolder.get(messageId, Constant.REQUEST_ID));
            var params = ToolResultHolder.get(requestId);
            if (ObjectUtil.isNotEmpty(params)) {
                myMessage.setParams(params);
            }
            ToolResultHolder.remove(messageId);
        }

        if (message instanceof ToolResponseMessage toolResponseMessage) {
            myMessage.setToolResponses(toolResponseMessage.getResponses());
        }

        return JSONUtil.toJsonStr(myMessage);
    }

    public static Message toMessage(String json) {
        var myMessage = JSONUtil.toBean(json, MyMessage.class);
        var messageType = MessageType.valueOf(myMessage.getMessageType());

        return switch (messageType) {
            case SYSTEM -> new SystemMessage(myMessage.getTextContent());
            case USER -> UserMessage.builder()
                    .text(myMessage.getTextContent())
                    .metadata(myMessage.getMetadata())
                    .media(myMessage.getMedia())
                    .build();
            case ASSISTANT -> new MyAssistantMessage(
                    myMessage.getTextContent(),
                    myMessage.getMetadata(),
                    myMessage.getToolCalls(),
                    myMessage.getMedia(),
                    myMessage.getParams()
            );
            case TOOL -> new ToolResponseMessage(myMessage.getToolResponses(), myMessage.getMetadata());
        };
    }

}