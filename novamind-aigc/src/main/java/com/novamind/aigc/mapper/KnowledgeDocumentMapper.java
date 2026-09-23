package com.novamind.aigc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novamind.aigc.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库文档 Mapper 接口
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
