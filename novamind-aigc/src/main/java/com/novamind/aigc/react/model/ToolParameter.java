package com.novamind.aigc.react.model;

import lombok.Builder;
import lombok.Data;

/**
 * Parameter definition for a tool
 */
@Data
@Builder
public class ToolParameter {
    /**
     * Parameter name
     */
    private String name;
    
    /**
     * Parameter type (string, integer, boolean, etc.)
     */
    private String type;
    
    /**
     * Parameter description
     */
    private String description;
    
    /**
     * Whether this parameter is required
     */
    @Builder.Default
    private boolean required = false;
    
    /**
     * Default value if not provided
     */
    private Object defaultValue;
}
