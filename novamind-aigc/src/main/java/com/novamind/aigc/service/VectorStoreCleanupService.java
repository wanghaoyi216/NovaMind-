package com.novamind.aigc.service;

import com.novamind.common.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreCleanupService {

    private final VectorStore vectorStore;

    // 删除向量数据库中的切分块
    @Async
    public void deleteVectorsFromStore(String documentId){
        Filter.Expression filterExpression = new FilterExpressionBuilder()
                .eq("documentId", documentId)
                .build();
        try {
            vectorStore.delete(filterExpression);
        }
        catch (Exception e) {
            log.error("删除向量数据库出错：{}", e.getMessage());
        }
    }
}
