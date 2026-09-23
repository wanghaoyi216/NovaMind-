package com.novamind.common.autoconfigure.mvc;

import com.novamind.common.autoconfigure.mvc.aspects.CheckerAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ParamCheckerConfig {

    @Bean
    public CheckerAspect checkerAspect(){
        return new CheckerAspect();
    }
}
