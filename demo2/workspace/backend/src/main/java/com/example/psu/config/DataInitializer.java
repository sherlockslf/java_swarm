package com.example.psu.config;

import com.example.psu.entity.PromptFragment;
import com.example.psu.entity.PsuUnit;
import com.example.psu.entity.User;
import com.example.psu.entity.User.UserRole;
import com.example.psu.repository.PromptFragmentRepository;
import com.example.psu.repository.PsuRepository;
import com.example.psu.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 数据初始化器 - 确保系统中有默认用户和Prompt片段
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PsuRepository psuRepository;
    
    @Autowired
    private PromptFragmentRepository promptFragmentRepository;

    @Override
    public void run(String... args) throws Exception {
        // 启动时统一对齐默认账号，避免历史库里残留错误密码导致无法登录
        ensureDefaultUser("admin_user", "Admin@123", UserRole.ADMIN);
        ensureDefaultUser("dev_user", "Dev@123", UserRole.DEVELOPER);
        ensureDefaultUser("bus_user", "Bus@123", UserRole.BUSINESS);
        
        // 初始化默认Prompt片段
        ensureDefaultPromptFragments();
    }

    private void ensureDefaultUser(String username, String rawPassword, UserRole role) {
        // 查询默认账号是否已存在
        Optional<User> existingOpt = userRepository.findByUsername(username);
        if (existingOpt.isEmpty()) {
            // 不存在则直接创建默认账号
            User user = new User();
            user.setUsername(username);
            user.setPassword(rawPassword);
            user.setRole(role);
            user.setEnabled(true);
            userRepository.save(user);
            System.out.println("已创建默认用户: " + username);
            return;
        }

        // 存在则校验关键字段，不一致时自动修正
        User existing = existingOpt.get();
        boolean needUpdate = false;

        if (!rawPassword.equals(existing.getPassword())) {
            existing.setPassword(rawPassword);
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
    
    /**
     * 确保每个PSU都有默认的Prompt片段
     */
    private void ensureDefaultPromptFragments() {
        // 获取所有PSU
        java.util.List<PsuUnit> allPsus = psuRepository.findAll();
        
        for (PsuUnit psu : allPsus) {
            // 检查是否已有core_rules片段
            Optional<PromptFragment> existingOpt = promptFragmentRepository.findByPsuIdAndFragmentKey(
                psu.getId(), "core_rules");
            
            if (existingOpt.isEmpty()) {
                // 创建默认Prompt片段
                PromptFragment fragment = new PromptFragment();
                fragment.setPsuId(psu.getId());
                fragment.setFragmentKey("core_rules");
                fragment.setContent("你是一个专业的购物助手，请根据用户需求推荐商品。");
                fragment.setEditable(true);
                fragment.setType(PromptFragment.FragmentType.CORE_RULES);
                fragment.setSortOrder(1);
                fragment.setCreatedAt(LocalDateTime.now());
                fragment.setUpdatedAt(LocalDateTime.now());
                promptFragmentRepository.save(fragment);
                System.out.println("已为PSU " + psu.getName() + " 创建默认Prompt片段");
            }
        }
    }
}
