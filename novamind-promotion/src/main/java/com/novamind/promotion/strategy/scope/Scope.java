package com.novamind.promotion.strategy.scope;

import com.novamind.api.dto.promotion.OrderCourseDTO;
import com.novamind.promotion.constants.ScopeType;

import java.util.List;

public interface Scope {

    boolean canUse(OrderCourseDTO course);

    ScopeType getType();

    List<Long> getScopeIds();
}
