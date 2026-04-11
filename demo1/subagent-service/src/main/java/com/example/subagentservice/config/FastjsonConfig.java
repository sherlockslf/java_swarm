package com.example.subagentservice.config;

import com.example.common.json.Fastjson2HttpMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FastjsonConfig {
    @Bean
    public Fastjson2HttpMessageConverter fastjson2HttpMessageConverter() {
        return new Fastjson2HttpMessageConverter();
    }
}
