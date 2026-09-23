package com.novamind.common.autoconfigure.mvc.decorator;

import com.novamind.common.utils.UserContext;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.lang.NonNull;

import java.util.Map;

/**
 * 异步线程任务装饰器：在线程池执行时透传 MDC（TraceId/RequestId）与 UserContext
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    @NonNull
    public Runnable decorate(@NonNull Runnable runnable) {
        // 捕获主线程的 MDC 上下文和用户上下文
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        Long userId = UserContext.getUser();

        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                if (userId != null) {
                    UserContext.setUser(userId);
                }
                runnable.run();
            } finally {
                MDC.clear();
                UserContext.removeUser();
            }
        };
    }
}
