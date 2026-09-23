package com.novamind.aigc.controller;

import com.novamind.aigc.service.UserGraphService;
import com.novamind.common.domain.R;
import com.novamind.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/graph")
@RequiredArgsConstructor
public class GraphController {

    private final UserGraphService userGraphService;

    @GetMapping("/visualization")
    public R<Map<String, Object>> getVisualization() {
        Long userId = UserContext.getUser();
        Map<String, Object> data = userGraphService.getGraphVisualization(userId);
        return R.ok(data);
    }

    @GetMapping("/status")
    public R<Map<String, Object>> getStatus() {
        Map<String, Object> status = userGraphService.status();
        return R.ok(status);
    }
}
