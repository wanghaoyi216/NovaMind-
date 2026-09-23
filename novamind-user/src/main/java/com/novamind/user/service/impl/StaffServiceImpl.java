package com.novamind.user.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.novamind.api.cache.RoleCache;
import com.novamind.common.domain.dto.PageDTO;
import com.novamind.common.enums.UserType;
import com.novamind.common.utils.BeanUtils;
import com.novamind.user.domain.po.UserDetail;
import com.novamind.user.domain.query.UserPageQuery;
import com.novamind.user.domain.vo.StaffVO;
import com.novamind.user.service.IStaffService;
import com.novamind.user.service.IUserDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 员工详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-07-12
 */
@Service
@RequiredArgsConstructor
public class StaffServiceImpl implements IStaffService {

    private final IUserDetailService detailService;
    private final RoleCache roleCache;
    @Override
    public PageDTO<StaffVO> queryStaffPage(UserPageQuery query) {
        // 1.搜索
        Page<UserDetail> p = detailService.queryUserDetailByPage(query, UserType.STAFF);
        // 2.处理vo
        return PageDTO.of(p, u -> {
            StaffVO v = BeanUtils.toBean(u, StaffVO.class);
            v.setRoleName(roleCache.getRoleName(u.getRoleId()));
            return v;
        });
    }
}
