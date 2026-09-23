package com.novamind.exam.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.api.dto.exam.QuestionDTO;
import com.novamind.common.domain.dto.PageDTO;
import com.novamind.exam.domain.dto.QuestionFormDTO;
import com.novamind.exam.domain.po.Question;
import com.novamind.exam.domain.query.QuestionPageQuery;
import com.novamind.exam.domain.vo.QuestionDetailVO;
import com.novamind.exam.domain.vo.QuestionPageVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 题目 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-09-02
 */
public interface IQuestionService extends IService<Question> {

    void addQuestion(QuestionFormDTO questionFormDTO);

    void updateQuestion(QuestionFormDTO questionDTO);

    void deleteQuestionById(Long id);

    PageDTO<QuestionPageVO> queryQuestionByPage(QuestionPageQuery query);

    QuestionDetailVO queryQuestionDetailById(Long id);

    List<QuestionDTO> queryQuestionByIds(List<Long> ids);

    Map<Long, Integer> countQuestionNumOfCreater(List<Long> createrIds);

    List<QuestionDTO> queryQuestionByBizId(Long bizId);

    Boolean checkNameValid(String name);

    Map<Long, Integer> queryQuestionScores(List<Long> ids);
}
