package com.novamind.message.service;

import com.novamind.message.domain.dto.NoticeTaskDTO;
import com.novamind.message.domain.dto.NoticeTaskFormDTO;
import com.novamind.message.domain.query.NoticeTaskPageQuery;
import com.novamind.common.domain.dto.PageDTO;
import com.novamind.message.domain.po.NoticeTask;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 系统通告的任务表，可以延期或定期发送通告 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-08-19
 */
public interface INoticeTaskService extends IService<NoticeTask> {

    Long saveNoticeTask(NoticeTaskFormDTO noticeTaskFormDTO);

    void handleTask(NoticeTask noticeTask);

    void updateNoticeTask(NoticeTaskFormDTO noticeTaskFormDTO);

    PageDTO<NoticeTaskDTO> queryNoticeTasks(NoticeTaskPageQuery pageQuery);

    NoticeTaskDTO queryNoticeTask(Long id);

    PageDTO<NoticeTask> queryTodoNoticeTaskByPage(int pageNo, int size);
}
