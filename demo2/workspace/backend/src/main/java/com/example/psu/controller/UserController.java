package com.example.psu.controller;

import com.example.psu.dto.UserCreateRequest;
import com.example.psu.entity.User;
import com.example.psu.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器（仅管理员使用）
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 获取用户列表（仅管理员）
     * @return 用户列表
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<User>> getUsers() {
        List<User> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
    
    /**
     * 创建用户（仅管理员）
     * @param request 用户创建请求
     * @return 创建的用户
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> createUser(@Valid @RequestBody UserCreateRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setRole(User.UserRole.valueOf(request.getRole().toUpperCase()));
        user.setEnabled(request.getEnabled());
        
        User createdUser = userService.createUser(user);
        return ResponseEntity.ok(createdUser);
    }
    
    /**
     * 切换用户启用状态（仅管理员）
     * @param id 用户ID
     * @return 更新后的用户
     */
    @PutMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<User> toggleUserStatus(@PathVariable Long id) {
        User user = userService.toggleUserStatus(id);
        return ResponseEntity.ok(user);
    }
}