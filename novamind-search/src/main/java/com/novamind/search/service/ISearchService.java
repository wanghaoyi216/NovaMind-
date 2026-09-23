package com.novamind.search.service;

import com.novamind.common.domain.dto.PageDTO;
import com.novamind.search.domain.query.CoursePageQuery;
import com.novamind.search.domain.vo.CourseVO;

import java.util.List;

public interface ISearchService {

    List<CourseVO> queryCourseByCateId(Long cateLv2Id);

    List<CourseVO> queryBestTopN();

    List<CourseVO> queryNewTopN();

    List<CourseVO> queryFreeTopN();

    PageDTO<CourseVO> queryCoursesForPortal(CoursePageQuery query);

    List<Long> queryCoursesIdByName(String keyword);
}
