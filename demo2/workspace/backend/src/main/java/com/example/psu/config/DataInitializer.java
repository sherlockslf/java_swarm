package com.example.psu.config;

import com.example.psu.entity.User;
import com.example.psu.entity.User.UserRole;
import com.example.psu.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 数据初始化器 - 确保系统中有默认用户
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // 启动时统一对齐默认账号，避免历史库里残留错误密码导致无法登录
        ensureDefaultUser("admin_user", "Admin@123", UserRole.ADMIN);
        ensureDefaultUser("dev_user", "Dev@123", UserRole.DEVELOPER);
        ensureDefaultUser("bus_user", "Bus@123", UserRole.BUSINESS);
    }

    private void ensureDefaultUser(String username, String rawPassword, UserRole role) {
        // 查询默认账号是否已存在
        Optional<User> existingOpt = userRepository.findByUsername(username);
        if (existingOpt.isEmpty()) {
            // 不存在则直接创建默认账号
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            user.setEnabled(true);
            userRepository.save(user);
            System.out.println("已创建默认用户: " + username);
            return;
        }

        // 存在则校验关键字段，不一致时自动修正
        User existing = existingOpt.get();
        boolean needUpdate = false;

        if (!passwordEncoder.matches(rawPassword, existing.getPassword())) {
            existing.setPassword(passwordEncoder.encode(rawPassword));
            needUpdate = true;
        }
        if (existing.getRole() != role) {
            existing.setRole(role);
            needUpdate = true;
        }
        if (!Boolean.TRUE.equals(existing.getEnabled())) {
            existing.setEnabled(true);
            needUpdate = true;
        }

        if (needUpdate) {
            userRepository.save(existing);
            System.out.println("已修正默认用户: " + username);
        }
    }
}
