package com.novamind.aigc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.novamind.aigc.entity.ChatRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatRecordMapper extends BaseMapper<ChatRecord> {
}
