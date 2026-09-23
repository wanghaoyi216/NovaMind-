package com.novamind.aigc.react.config;

import com.novamind.aigc.react.model.Tool;
import com.novamind.aigc.react.model.ToolCategory;
import com.novamind.aigc.react.model.ToolParameter;
import com.novamind.aigc.react.registry.ToolRegistry;
import com.novamind.aigc.tools.CourseTools;
import com.novamind.api.client.course.CourseClient;
import com.novamind.api.client.user.UserClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * Configuration for registering business service tools
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ToolRegistrationConfig {
    
    private final ToolRegistry toolRegistry;
    private final CourseClient courseClient;
    private final UserClient userClient;
    
    @Bean
    public CommandLineRunner registerTools() {
        return args -> {
            log.info("Registering business service tools...");
            
            // Register course search tool
            registerCourseSearchTool();
            
            // Register course info tool
            registerCourseInfoTool();
            
            // Register user profile tool
            registerUserProfileTool();
            
            log.info("Tool registration completed");
        };
    }
    
    private void registerCourseSearchTool() {
        Tool tool = Tool.builder()
                .name("search_courses")
                .description("Search for courses by keyword, category, or difficulty level")
                .category(ToolCategory.COURSE_QUERY)
                .requiresAuth(false)
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("keyword")
                                .type("string")
                                .description("Search keyword for course name or description")
                                .required(true)
                                .build(),
                        ToolParameter.builder()
                                .name("category")
                                .type("string")
                                .description("Course category (e.g., Java, Python, Frontend)")
                                .required(false)
                                .build(),
                        ToolParameter.builder()
                                .name("difficulty")
                                .type("string")
                                .description("Difficulty level (beginner, intermediate, advanced)")
                                .required(false)
                                .build()
                ))
                .executor(params -> {
                    // Simple implementation - in production, call actual search API
                    String keyword = (String) params.get("keyword");
                    log.info("Searching courses with keyword: {}", keyword);
                    
                    // TODO: Implement actual course search via CourseClient
                    return Map.of(
                            "courses", List.of(),
                            "total", 0,
                            "message", "Course search functionality to be implemented"
                    );
                })
                .build();
        
        toolRegistry.registerTool(tool);
        log.info("Registered tool: search_courses");
    }
    
    private void registerCourseInfoTool() {
        Tool tool = Tool.builder()
                .name("get_course_info")
                .description("Get detailed information about a specific course by ID")
                .category(ToolCategory.COURSE_QUERY)
                .requiresAuth(false)
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("courseId")
                                .type("integer")
                                .description("Course ID")
                                .required(true)
                                .build()
                ))
                .executor(params -> {
                    Object courseIdObj = params.get("courseId");
                    Long courseId;
                    
                    if (courseIdObj instanceof Integer) {
                        courseId = ((Integer) courseIdObj).longValue();
                    } else if (courseIdObj instanceof Long) {
                        courseId = (Long) courseIdObj;
                    } else if (courseIdObj instanceof String) {
                        courseId = Long.parseLong((String) courseIdObj);
                    } else {
                        return Map.of("error", "Invalid courseId type");
                    }
                    
                    log.info("Getting course info for courseId: {}", courseId);
                    
                    try {
                        var courseInfo = courseClient.baseInfo(courseId, true);
                        return courseInfo != null ? courseInfo : Map.of("error", "Course not found");
                    } catch (Exception e) {
                        log.error("Error getting course info", e);
                        return Map.of("error", "Failed to get course info: " + e.getMessage());
                    }
                })
                .build();
        
        toolRegistry.registerTool(tool);
        log.info("Registered tool: get_course_info");
    }
    
    private void registerUserProfileTool() {
        Tool tool = Tool.builder()
                .name("get_user_profile")
                .description("Get user profile information and learning progress")
                .category(ToolCategory.USER_QUERY)
                .requiresAuth(true)
                .parameters(List.of(
                        ToolParameter.builder()
                                .name("userId")
                                .type("string")
                                .description("User ID")
                                .required(true)
                                .build()
                ))
                .executor(params -> {
                    String userId = (String) params.get("userId");
                    log.info("Getting user profile for userId: {}", userId);
                    
                    try {
                        // TODO: Implement actual user profile retrieval via UserClient
                        return Map.of(
                                "userId", userId,
                                "message", "User profile functionality to be implemented"
                        );
                    } catch (Exception e) {
                        log.error("Error getting user profile", e);
                        return Map.of("error", "Failed to get user profile: " + e.getMessage());
                    }
                })
                .build();
        
        toolRegistry.registerTool(tool);
        log.info("Registered tool: get_user_profile");
    }
}
