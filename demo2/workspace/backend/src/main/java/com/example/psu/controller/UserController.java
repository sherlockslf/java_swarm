package com.example.psu.controller;

import com.example.psu.dto.UserCreateRequest;
import com.example.psu.entity.User;
import com.example.psu.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器（仅管理员使用）
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 创建用户（允许任何人创建初始用户）
     * @param request 用户创建请求
     * @return 创建的用户
     */
    @PostMapping
    public ResponseEntity<User> createUser(@Valid @RequestBody UserCreateRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setRole(User.UserRole.valueOf(request.getRole().toUpperCase()));
        user.setEnabled(request.getEnabled());
        
        User createdUser = userService.createUser(user);
        return ResponseEntity.ok(createdUser);
    }
}