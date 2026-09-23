package com.novamind.aigc.controller;

import cn.hutool.core.collection.CollStreamUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.print.Doc;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    // ES向量数据库
    private final VectorStore vectorStore;

    // 向量化操作，一般是用户问题和知识
    @PostMapping
    public void saveVectorStore(@RequestParam("messages") List<String> messages) {
        log.info("保存到向量数据库中，消息数据：{}", messages);

        var documentList = CollStreamUtil.toList(messages, message -> Document.builder()
                .text(message)
                .build());
        this.vectorStore.add(documentList);

        log.info("保存到向量数据库成功, 数量：{}", messages.size());
    }
}