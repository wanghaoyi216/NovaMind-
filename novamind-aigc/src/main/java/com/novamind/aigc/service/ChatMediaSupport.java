package com.novamind.aigc.service;

import cn.hutool.core.util.StrUtil;
import com.novamind.aigc.dto.ChatMediaDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.content.Media;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ChatMediaSupport {

    public String buildUserText(String question, List<ChatMediaDTO> mediaList) {
        var builder = new StringBuilder(StrUtil.blankToDefault(question, ""));
        if (mediaList == null || mediaList.isEmpty()) {
            return builder.toString();
        }
        for (ChatMediaDTO media : mediaList) {
            if (media == null || StrUtil.isBlank(media.getText())) {
                continue;
            }
            builder.append("\n\n[")
                    .append(StrUtil.blankToDefault(media.getType(), "media"))
                    .append(":")
                    .append(StrUtil.blankToDefault(media.getName(), "inline"))
                    .append("]\n")
                    .append(media.getText());
        }
        return builder.toString();
    }

    public List<Media> toSpringAiMedia(List<ChatMediaDTO> mediaList) {
        if (mediaList == null || mediaList.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<Media>();
        for (ChatMediaDTO media : mediaList) {
            if (media == null || StrUtil.isBlank(media.getUrl()) || StrUtil.isBlank(media.getMimeType())) {
                continue;
            }
            try {
                MimeType mimeType = MimeTypeUtils.parseMimeType(media.getMimeType());
                result.add(Media.builder()
                        .mimeType(mimeType)
                        .data(URI.create(media.getUrl()))
                        .name(StrUtil.blankToDefault(media.getName(), media.getType()))
                        .build());
            } catch (Exception e) {
                log.warn("Ignore invalid chat media. type={}, url={}, mimeType={}",
                        media.getType(), media.getUrl(), media.getMimeType(), e);
            }
        }
        return result;
    }
}
