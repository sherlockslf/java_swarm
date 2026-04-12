package com.example.psu.controller;

import com.example.psu.dto.AuthRequest;
import com.example.psu.dto.AuthResponse;
import com.example.psu.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 用户登录
     * @param authRequest 认证请求
     * @return 认证响应
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest authRequest) {
        AuthResponse response = userService.authenticate(authRequest);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取当前用户信息
     * @return 当前用户信息
     */
    @GetMapping("/profile")
    public ResponseEntity<AuthResponse> getProfile() {
        // 这里需要从SecurityContext获取当前用户信息
        // 简化实现，实际项目中需要完善
        return ResponseEntity.ok(new AuthResponse("", "current_user", "DEVELOPER"));
    }
}