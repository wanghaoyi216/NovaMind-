package com.novamind.aigc.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.aigc.dto.DocumentDTO;
import com.novamind.aigc.entity.KnowledgeDocument;
import com.novamind.aigc.vo.DocumentVO;

import java.util.List;

/**
 * 知识库文档服务接口
 */
public interface DocumentService extends IService<KnowledgeDocument> {

    /**
     * 批量上传文档
     *
     * @param documentDTO 文档DTO
     * @return 上传结果
     */
    Boolean createDocument(DocumentDTO documentDTO);

    /**
     * 查询文档列表（分页）
     *
     * @param page     页码
     * @param size     每页数量
     * @param category 分类（可选）
     * @return 文档列表
     */
    List<DocumentVO> listDocuments(Integer page, Integer size, String category);

    /**
     * 删除文档
     *
     * @param documentId 文档ID
     * @return 删除结果
     */
    Boolean deleteDocument(String documentId);

    /**
     * 根据ID查询文档详情
     *
     * @param documentId 文档ID
     * @return 文档详情
     */
    DocumentVO getDocumentById(String documentId);

    /**
     * 更新文档状态
     *
     * @param documentId 文档ID
     * @param status     新状态
     * @param errorMessage 错误信息（处理失败时）
     * @return 更新结果
     */
    Boolean updateDocumentStatus(String documentId, String status, String errorMessage);

    /**
     * 异步处理文档（切片和向量化）
     *
     * @param documentId 文档ID
     */
    void processDocumentAsync(String documentId);
}
