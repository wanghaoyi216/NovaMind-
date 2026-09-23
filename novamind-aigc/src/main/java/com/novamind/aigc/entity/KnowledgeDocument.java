package com.novamind.aigc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.novamind.aigc.enums.KnowledgeDocumentStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_document")
public class KnowledgeDocument {

    /**
     * 主键自增
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 文档唯一标识
     */
    private String documentId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 文档分类
     */
    private String category;

    /**
     * 标签，JSON数组格式
     */
    private List<String> tags;

    /**
     * 文档状态：PENDING-待处理，PROCESSING-处理中，PROCESS-已处理，FAILED-处理失败
     */
    @Builder.Default
    private KnowledgeDocumentStatusEnum status = KnowledgeDocumentStatusEnum.PENDING;

    /**
     * 切片数量
     */
    private Integer chunkCount;

    /**
     * 向量数量
     */
    private Integer vectorCount;

    /**
     * 错误信息（处理失败时记录）
     */
    private String errorMessage;

    /**
     * 创建人
     */
    private Long creator;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private Long updater;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否被删除
     */
    @TableLogic // 标记字段为逻辑删除字段
    private Boolean isDelete;
}
