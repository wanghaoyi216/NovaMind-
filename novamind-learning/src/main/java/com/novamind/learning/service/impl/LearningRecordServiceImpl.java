package com.novamind.learning.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novamind.api.client.course.CourseClient;
import com.novamind.api.dto.course.CourseFullInfoDTO;
import com.novamind.api.dto.leanring.LearningLessonDTO;
import com.novamind.api.dto.leanring.LearningRecordDTO;
import com.novamind.common.exceptions.BizIllegalException;
import com.novamind.common.exceptions.DbException;
import com.novamind.common.utils.BeanUtils;
import com.novamind.common.utils.UserContext;
import com.novamind.learning.domain.dto.LearningRecordFormDTO;
import com.novamind.learning.domain.po.LearningLesson;
import com.novamind.learning.domain.po.LearningRecord;
import com.novamind.learning.enums.LessonStatus;
import com.novamind.learning.enums.SectionType;
import com.novamind.learning.mapper.LearningRecordMapper;
import com.novamind.learning.service.ILearningLessonService;
import com.novamind.learning.service.ILearningRecordService;
import com.novamind.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-12-10
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;

    private final CourseClient courseClient;

    private final LearningRecordDelayTaskHandler taskHandler;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.查询课表
        LearningLesson lesson = lessonService.queryByUserAndCourseId(userId, courseId);
        // 用户没有这门课的课表（尚未报名/购买）时返回 null。
        // 调用方 CourseServiceImpl 对 null 有明确分支（"没有查询到课表信息，说明是免费试看"），
        // 这里不能直接 lesson.getId() —— 否则浏览任何未购课程都会 NPE -> 500。
        if (lesson == null) {
            return null;
        }
        // 3.查询学习记录
        // select * from xx where lesson_id = #{lessonId}
        List<LearningRecord> records = lambdaQuery().eq(LearningRecord::getLessonId, lesson.getId()).list();
        // 4.封装结果
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        dto.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));
        return dto;
    }

    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO recordDTO) {
        // 1.获取登录用户
        Long userId = UserContext.getUser();
        // 2.处理学习记录
        boolean finished = false;
        if (recordDTO.getSectionType() == SectionType.VIDEO) {
            // 2.1.处理视频
            finished = handleVideoRecord(userId, recordDTO);
        }else{
            // 2.2.处理考试
            finished = handleExamRecord(userId, recordDTO);
        }
        if(!finished){
            // 没有新学完的小节，无需更新课表中的学习进度
            return;
        }
        // 3.处理课表数据
        handleLearningLessonsChanges(recordDTO);
    }

    private void handleLearningLessonsChanges(LearningRecordFormDTO recordDTO) {
        // 1.查询课表
        LearningLesson lesson = lessonService.getById(recordDTO.getLessonId());
        if (lesson == null) {
            throw new BizIllegalException("课程不存在，无法更新数据！");
        }
        // 2.判断是否有新的完成小节
        boolean allLearned = false;

            // 3.如果有新完成的小节，则需要查询课程数据
            CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (cInfo == null) {
                throw new BizIllegalException("课程不存在，无法更新数据！");
            }
            // 4.比较课程是否全部学完：已学习小节 >= 课程总小节
            allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();

        // 5.更新课表
        lessonService.lambdaUpdate()
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .setSql("learned_sections = learned_sections + 1")
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    private boolean handleVideoRecord(Long userId, LearningRecordFormDTO recordDTO) {
        // 1.查询旧的学习记录
        LearningRecord old = queryOldRecord(recordDTO.getLessonId(), recordDTO.getSectionId());
        // 2.判断是否存在
        if (old == null) {
            // 3.不存在，则新增
            // 3.1.转换PO
            LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
            // 3.2.填充数据
            record.setUserId(userId);
            // 3.3.写入数据库
            boolean success = save(record);
            if (!success) {
                throw new DbException("新增学习记录失败！");
            }
            return false;
        }
        // 4.存在，则更新
        // 4.1.判断是否是第一次完成
        boolean finished = !old.getFinished() && recordDTO.getMoment() * 2 >= recordDTO.getDuration();
        if(!finished){
            LearningRecord record = new LearningRecord();
            record.setLessonId(recordDTO.getLessonId());
            record.setSectionId(recordDTO.getSectionId());
            record.setMoment(recordDTO.getMoment());
            record.setId(old.getId());
            record.setFinished(old.getFinished());
            taskHandler.addLearningRecordTask(record);
            return false;
        }
        // 4.2.更新数据
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, recordDTO.getMoment())
                .set(LearningRecord::getFinished, true)
                .set(LearningRecord::getFinishTime, recordDTO.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if(!success){
            throw new DbException("更新学习记录失败！");
        }
        // 4.3.清理缓存
        taskHandler.cleanRecordCache(recordDTO.getLessonId(), recordDTO.getSectionId());
        return true;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        // 1.查询缓存
        LearningRecord record = taskHandler.readRecordCache(lessonId, sectionId);
        // 2.如果命中，直接返回
        if (record != null) {
            return record;
        }
        // 3.未命中，查询数据库
        record = lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        // 4.写入缓存
        taskHandler.writeRecordCache(record);
        return record;
    }

    private boolean handleExamRecord(Long userId, LearningRecordFormDTO recordDTO) {
        // 1.转换DTO为PO
        LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
        // 2.填充数据
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(recordDTO.getCommitTime());
        // 3.写入数据库
        boolean success = save(record);
        if (!success) {
            throw new DbException("新增考试记录失败！");
        }
        return true;
    }
}
