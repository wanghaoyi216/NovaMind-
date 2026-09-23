package com.novamind.aigc.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novamind.aigc.dto.DocumentDTO;
import com.novamind.aigc.entity.KnowledgeDocument;
import com.novamind.aigc.enums.KnowledgeDocumentStatusEnum;
import com.novamind.aigc.mapper.KnowledgeDocumentMapper;
import com.novamind.aigc.service.DocumentService;
import com.novamind.aigc.service.VectorStoreCleanupService;
import com.novamind.aigc.vo.DocumentVO;
import com.novamind.common.utils.ObjectUtils;
import com.novamind.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument> implements DocumentService {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final VectorStoreCleanupService  vectorStoreCleanupService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean createDocument(DocumentDTO documentDTO) {
        log.info("批量上传文档，数量: {}", documentDTO.getDocuments().size());

        Long userId = UserContext.getUser();

        // 遍历文档列表，逐个保存
        for (DocumentDTO.Document doc : documentDTO.getDocuments()) {
            KnowledgeDocument document = KnowledgeDocument.builder()
                    .documentId(IdUtil.fastSimpleUUID())
                    .title(doc.title())
                    .content(doc.content())
                    .category(doc.category())
                    .tags(doc.tags())
                    .status(KnowledgeDocumentStatusEnum.PENDING)
                    .chunkCount(0)
                    .vectorCount(0)
                    .creator(userId)
                    .createTime(LocalDateTime.now())
                    .updater(userId)
                    .updateTime(LocalDateTime.now())
                    .build();

            // 保存到数据库
            this.save(document);

            // 异步处理文档切片和向量化
            processDocumentAsync(document.getDocumentId());

            log.info("文档保存成功，已启动异步处理: {}", document.getTitle());
        }

        return true;
    }

    @Override
    public List<DocumentVO> listDocuments(Integer page, Integer size, String category) {
        log.info("查询文档列表: page={}, size={}, category={}", page, size, category);

        // 构建查询条件
        LambdaQueryWrapper<KnowledgeDocument> wrapper = Wrappers.<KnowledgeDocument>lambdaQuery()
                .eq(category != null && !category.isEmpty(), KnowledgeDocument::getCategory, category)
                .orderByDesc(KnowledgeDocument::getCreateTime)
                .last("LIMIT " + size + " OFFSET " + (page - 1) * size);

        List<KnowledgeDocument> documents = this.list(wrapper);

        // 转换为 VO
        return documents.stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
//    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteDocument(String documentId) {
        log.info("删除文档: {}", documentId);

        // 查询文档是否存在
        KnowledgeDocument document = this.lambdaQuery()
                .eq(KnowledgeDocument::getDocumentId, documentId)
                .one();

        if (document == null) {
            log.warn("文档不存在: {}", documentId);
            return false;
        }

        // 从数据库中删除 - 这里逻辑删除，为后续异步调用做准备
        boolean removed = this.lambdaUpdate()
                .eq(KnowledgeDocument::getDocumentId, documentId)
                .remove();

        log.info("数据库文档删除{}: {}", removed ? "成功" : "失败", documentId);

        // 异步从向量数据库中删除对应的向量
        // 注意！Async方法没有返回值，真正执行的时候需要维护一个全局的字段（维护表）把删除失败的 documentId 写进去
        vectorStoreCleanupService.deleteVectorsFromStore(documentId);
        return removed;
    }

    @Override
    public DocumentVO getDocumentById(String documentId) {
        log.info("查询文档详情: {}", documentId);

        KnowledgeDocument document = this.lambdaQuery()
                .eq(KnowledgeDocument::getDocumentId, documentId)
                .one();

        if (document == null) {
            log.warn("文档不存在: {}", documentId);
            return null;
        }

        return convertToVO(document);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateDocumentStatus(String documentId, String status, String errorMessage) {
        log.info("更新文档状态: documentId={}, status={}", documentId, status);

        KnowledgeDocumentStatusEnum statusEnum = KnowledgeDocumentStatusEnum.valueOf(status);

        boolean updated = this.lambdaUpdate()
                .eq(KnowledgeDocument::getDocumentId, documentId)
                .set(KnowledgeDocument::getStatus, statusEnum)
                .set(errorMessage != null, KnowledgeDocument::getErrorMessage, errorMessage)
                .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now())
                .update();

        log.info("文档状态更新{}: {}", updated ? "成功" : "失败", documentId);
        return updated;
    }

    /**
     * 将实体转换为 VO
     */
    private DocumentVO convertToVO(KnowledgeDocument document) {
        return DocumentVO.builder()
                .documentId(document.getDocumentId())
                .title(document.getTitle())
                .category(document.getCategory())
                .tags(document.getTags())
                .status(document.getStatus())
                .chunkCount(document.getChunkCount())
                .vectorCount(document.getVectorCount())
                .createTime(document.getCreateTime())
                .updateTime(document.getUpdateTime())
                .build();
    }

    /**
     * 异步处理文档（切片和向量化）
     *
     * @param documentId 文档ID
     */
    @Async
    @Override
    public void processDocumentAsync(String documentId) {
        log.info("开始异步处理文档: documentId={}", documentId);

        try {
            // 1. 更新状态为处理中
            updateDocumentStatus(documentId, KnowledgeDocumentStatusEnum.PROCESSING.name(), null);
            log.info("文档状态更新为 PROCESSING: documentId={}", documentId);

            // 2. 查询文档内容
            KnowledgeDocument document = this.lambdaQuery()
                    .eq(KnowledgeDocument::getDocumentId, documentId)
                    .one();

            if (document == null) {
                log.error("文档不存在: documentId={}", documentId);
                updateDocumentStatus(documentId, KnowledgeDocumentStatusEnum.FAILED.name(), "文档不存在");
                return;
            }

            String content = document.getContent();
            log.info("文档内容长度: {} 字符", content.length());

            // 3. 文档切片
            // TokenTextSplitter 构造函数参数：
            // 1. chunkSize: 切片大小
            // 2. minChunkSize: 最小切片大小
            // 3. maxChunkSize: 最大切片大小
            // 4. ??? 第4个参数（需要确认）
            // 5. keepSeparator: 是否保留分隔符
            TokenTextSplitter splitter = new TokenTextSplitter(
                    200,    // chunkSize
                    5,      // minChunkSize
                    10000,  // maxChunkSize
                    0,      // 第4个参数（暂时设为0）
                    true    // keepSeparator
            );

            // 创建 Spring AI 的 Document 对象
            Document springDoc = Document.builder()
                    .text(content)
                    .build();

            // 执行切片
            List<Document> chunks = splitter.apply(List.of(springDoc));
            log.info("文档切片完成: 切片数量={}", chunks.size());

            // 4. 为每个切片添加元数据
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);

                // 构建元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("documentId", documentId);
                metadata.put("chunkIndex", i);
                metadata.put("title", document.getTitle());
                metadata.put("category", document.getCategory());

                // 创建新的 Document 对象，包含元数据
                Document chunkWithMetadata = new Document(chunk.getFormattedContent(), metadata);
                chunks.set(i, chunkWithMetadata);
            }

            // 5. 批量存储到向量数据库（会自动进行向量化）
            vectorStore.add(chunks);
            log.info("向量存储完成: 向量数量={}", chunks.size());

            // 6. 更新状态为成功
            updateDocumentStatusAndCounts(documentId,
                    KnowledgeDocumentStatusEnum.PROCESS.name(),
                    null,
                    chunks.size(),
                    chunks.size());
            log.info("文档处理完成: documentId={}, chunkCount={}, vectorCount={}",
                    documentId, chunks.size(), chunks.size());

        } catch (Exception e) {
            log.error("文档处理失败: documentId={}", documentId, e);
            updateDocumentStatus(documentId, KnowledgeDocumentStatusEnum.FAILED.name(), e.getMessage());
        }
    }

    /**
     * 更新文档状态和切片/向量数量
     */
    private void updateDocumentStatusAndCounts(String documentId, String status, String errorMessage, Integer chunkCount, Integer vectorCount) {
        log.info("更新文档状态和数量: documentId={}, status={}, chunkCount={}, vectorCount={}",
                documentId, status, chunkCount, vectorCount);

        KnowledgeDocumentStatusEnum statusEnum = KnowledgeDocumentStatusEnum.valueOf(status);

        this.lambdaUpdate()
                .eq(KnowledgeDocument::getDocumentId, documentId)
                .set(KnowledgeDocument::getStatus, statusEnum)
                .set(errorMessage != null, KnowledgeDocument::getErrorMessage, errorMessage)
                .set(chunkCount != null, KnowledgeDocument::getChunkCount, chunkCount)
                .set(vectorCount != null, KnowledgeDocument::getVectorCount, vectorCount)
                .set(KnowledgeDocument::getUpdateTime, LocalDateTime.now())
                .update();
    }
}
