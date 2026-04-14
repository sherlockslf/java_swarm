package com.example.psu.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理所有RuntimeException
     * @param e 运行时异常
     * @return 错误响应
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", e.getMessage());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        
        String message = e.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        
        // 根据异常消息判断合适的HTTP状态码
        if (message.contains("not found") || message.contains("Not found")) {
            status = HttpStatus.NOT_FOUND;
        } else if (message.contains("Only developers") || message.contains("Only business") || 
                   message.contains("can only be edited") || message.contains("cannot edit")) {
            status = HttpStatus.FORBIDDEN;
        } else if (message.contains("already exists") || message.contains("Already exists")) {
            status = HttpStatus.CONFLICT;
        } else if (message.contains("Invalid") || message.contains("invalid")) {
            status = HttpStatus.BAD_REQUEST;
        }
        
        response.put("status", status.value());
        return ResponseEntity.status(status).body(response);
    }
}
