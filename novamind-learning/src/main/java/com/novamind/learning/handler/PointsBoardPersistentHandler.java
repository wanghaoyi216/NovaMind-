package com.novamind.learning.handler;

import com.novamind.common.utils.CollUtils;
import com.novamind.common.utils.DateUtils;
import com.novamind.learning.constants.RedisConstants;
import com.novamind.learning.domain.po.PointsBoard;
import com.novamind.learning.service.IPointsBoardSeasonService;
import com.novamind.learning.service.IPointsBoardService;
import com.novamind.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

import static com.novamind.learning.constants.LearningConstants.POINTS_BOARD_TABLE_PREFIX;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService seasonService;

    private final IPointsBoardService pointsBoardService;

    private final StringRedisTemplate redisTemplate;

    // 定时创建历史赛季表
    @XxlJob("createTableJob") // 定义定时任务，需要启动web UI填写执行器和任务管理
    public void createPointsBoardTableOfLastSeason(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.查询赛季id
        Integer season = seasonService.querySeasonByTime(time);
        if (season == null) {
            // 赛季不存在
            return;
        }
        // 3.创建表
        pointsBoardService.createPointsBoardTableBySeason(season);
    }

    // 持久化赛季数据到数据库中
    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);

        // 2.计算动态表名
        // 2.1.查询赛季信息
        Integer season = seasonService.querySeasonByTime(time);
        // 2.2.存入ThreadLocal
        TableInfoContext.setInfo(POINTS_BOARD_TABLE_PREFIX + season);

        // 3.查询榜单数据
        // 3.1.拼接KEY
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 3.2.查询数据
        int index = XxlJobHelper.getShardIndex(); // 多实例分片-片号
        int total = XxlJobHelper.getShardTotal(); // 多实例分片-总片数
        int pageNo = index + 1; // 起始编号 | 页码
        int pageSize = 10; // 分页查询页数
        while (true) {
            // 分页查询
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if (CollUtils.isEmpty(boardList)) {
                break;
            }
            // 4.持久化到数据库
            // 4.1.把排名信息写入id
            boardList.forEach(b -> {
                b.setId(b.getRank().longValue());
                b.setRank(null);
            });
            // 4.2.持久化
            pointsBoardService.saveBatch(boardList);
            // 5.翻页
            pageNo+=total;
        }
        // 移除掉 ThreadLocal
        TableInfoContext.remove();
    }

    // 删除Redis中的赛季数据
    @XxlJob("clearPointsBoardFromRedis")
    public void clearPointsBoardFromRedis(){
        // 1.获取上月时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 3.删除
        redisTemplate.unlink(key);
    }
}
