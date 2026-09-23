package com.novamind.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.novamind.learning.domain.po.PointsBoardSeason;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 */
public interface IPointsBoardSeasonService extends IService<PointsBoardSeason> {

    Integer querySeasonByTime(LocalDateTime time);
}
