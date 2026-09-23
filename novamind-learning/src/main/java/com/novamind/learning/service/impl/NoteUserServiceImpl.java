package com.novamind.learning.service.impl;

import com.novamind.learning.domain.po.NoteUser;
import com.novamind.learning.mapper.NoteUserMapper;
import com.novamind.learning.service.INoteUserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Service
public class NoteUserServiceImpl extends ServiceImpl<NoteUserMapper, NoteUser> implements INoteUserService {

}
