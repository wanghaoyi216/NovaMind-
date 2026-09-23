package com.novamind.aigc.vo;

import com.novamind.aigc.enums.KnowledgeDocumentStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库文档 VO（返回给前端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVO {

    /**
     * 文档唯一标识
     */
    private String documentId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档分类
     */
    private String category;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 文档状态
     */
    private KnowledgeDocumentStatusEnum status;

    /**
     * 切片数量
     */
    private Integer chunkCount;

    /**
     * 向量数量
     */
    private Integer vectorCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
