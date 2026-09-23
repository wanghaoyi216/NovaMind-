package com.novamind.exam.mapper;

import com.novamind.api.dto.IdAndNumDTO;
import com.novamind.exam.domain.po.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 题目 Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
public interface QuestionMapper extends BaseMapper<Question> {

    List<IdAndNumDTO> countQuestionOfCreater(@Param("createrIds") List<Long> createrIds);

}
