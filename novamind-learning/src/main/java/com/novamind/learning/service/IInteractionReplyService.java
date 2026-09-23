package com.novamind.learning.service;

import com.novamind.common.domain.dto.PageDTO;
import com.novamind.learning.domain.dto.ReplyDTO;
import com.novamind.learning.domain.po.InteractionReply;
import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.learning.domain.query.ReplyPageQuery;
import com.novamind.learning.domain.vo.ReplyVO;

/**
 * <p>
 * 互动问题的回答或评论 服务类
 * </p>
 *
 * @author 虎哥
 */
public interface IInteractionReplyService extends IService<InteractionReply> {

    void saveReply(ReplyDTO replyDTO);

    PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery pageQuery, boolean isStudent);

    void hiddenReply(Long id, Boolean hidden);

    ReplyVO queryReplyById(Long id);
}
