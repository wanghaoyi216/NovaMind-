package com.novamind.user.service;

import com.novamind.common.domain.dto.PageDTO;
import com.novamind.user.domain.dto.StudentFormDTO;
import com.novamind.user.domain.query.UserPageQuery;
import com.novamind.user.domain.vo.StudentPageVo;

/**
 * <p>
 * 学员详情表 服务类
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
public interface IStudentService {

    void saveStudent(StudentFormDTO studentFormDTO);

    void updateMyPassword(StudentFormDTO studentFormDTO);

    PageDTO<StudentPageVo> queryStudentPage(UserPageQuery pageQuery);
}
