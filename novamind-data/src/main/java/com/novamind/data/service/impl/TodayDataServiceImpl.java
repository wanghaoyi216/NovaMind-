package com.novamind.data.service.impl;

import com.novamind.common.utils.BeanUtils;
import com.novamind.common.utils.JsonUtils;
import com.novamind.data.constants.RedisConstants;
import com.novamind.data.model.dto.TodayDataDTO;
import com.novamind.data.model.po.TodayDataInfo;
import com.novamind.data.model.vo.TodayDataVO;
import com.novamind.data.service.TodayDataService;
import com.novamind.data.utils.DataUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * @ClassName TodayDataServiceImpl
 * @Author wusongsong
 * @Date 2022/10/13 9:28
 * @Version
 **/
@Service
public class TodayDataServiceImpl implements TodayDataService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public TodayDataVO get() {
        // 1.数据redis存储key
        String key = RedisConstants.KEY_TODAY + DataUtils.getVersion(1);
        // 2.获取数据
        Object originData = redisTemplate.opsForValue().get(key);
        // 2.1.数据判空
        if (originData == null) {
            return new TodayDataVO();
        }
        return JsonUtils.toBean(originData.toString(), TodayDataVO.class);
    }

    @Override
    public void set(TodayDataDTO todayDataDTO) {
        // 1.数据redis存储key
        String key = RedisConstants.KEY_TODAY + todayDataDTO.getVersion();
        // 2.数据转化
        TodayDataInfo todayDataInfo = BeanUtils.toBean(todayDataDTO, TodayDataInfo.class);
        // 3.数据存储
        redisTemplate.opsForValue().set(key, JsonUtils.toJsonStr(todayDataInfo));
    }
}
