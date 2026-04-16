package com.example.psu.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.psu.dto.UserCreateRequest;
import com.example.psu.dto.response.UserResponse;
import com.example.psu.entity.User;
import com.example.psu.enums.UserRole;
import com.example.psu.service.UserService;

import jakarta.validation.Valid;

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
    public ResponseEntity<List<UserResponse>> getUsers() {
        List<User> users = userService.getAllUsers();
        List<UserResponse> responses = users.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }
    
    /**
     * 创建用户（仅管理员）
     * @param request 用户创建请求
     * @return 创建的用户
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());
        user.setRole(UserRole.fromCode(request.getRole().toUpperCase()));
        user.setEnabled(request.getEnabled());
        
        User createdUser = userService.createUser(user);
        UserResponse response = convertToResponse(createdUser);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 切换用户启用状态（仅管理员）
     * @param id 用户ID
     * @return 更新后的用户
     */
    @PutMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> toggleUserStatus(@PathVariable Long id) {
        User user = userService.toggleUserStatus(id);
        UserResponse response = convertToResponse(user);
        return ResponseEntity.ok(response);
    }
    
    /**
     * 将用户实体转换为响应DTO
     */
    private UserResponse convertToResponse(User user) {
        UserResponse response = new UserResponse();
        BeanUtils.copyProperties(user, response);
        response.setRole(user.getRole().getCode());
        return response;
    }
}