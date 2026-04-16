package com.example.psu.controller;

import com.example.psu.dto.request.TestRunRequest;
import com.example.psu.dto.response.TestRunResponse;
import com.example.psu.entity.User;
import com.example.psu.repository.UserRepository;
import com.example.psu.service.TestRunService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试运行控制器
 */
@RestController
@RequestMapping("/api/test-runs")
public class TestRunController {

    @Autowired
    private TestRunService testRunService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<TestRunResponse> runDataset(
        @RequestParam Long psuId,
        @RequestParam Long datasetId,
        @RequestBody(required = false) TestRunRequest request
    ) {
        TestRunResponse response = testRunService.runDataset(psuId, datasetId, request, getCurrentUserId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{runId}")
    public ResponseEntity<TestRunResponse> getRun(@PathVariable Long runId) {
        return ResponseEntity.ok(testRunService.getRun(runId));
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
            return user.getId();
        }
        throw new RuntimeException("Unauthenticated");
    }
}
