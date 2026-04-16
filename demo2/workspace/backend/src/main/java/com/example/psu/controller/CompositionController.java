package com.example.psu.controller;

import com.example.psu.dto.request.CompositionRenderRequest;
import com.example.psu.dto.request.CompositionSaveRequest;
import com.example.psu.dto.response.CompositionRenderResponse;
import com.example.psu.dto.response.CompositionResponse;
import com.example.psu.dto.response.CompositionValidateResponse;
import com.example.psu.entity.User;
import com.example.psu.repository.UserRepository;
import com.example.psu.service.CompositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 编排控制器
 */
@RestController
@RequestMapping("/api/compositions")
public class CompositionController {

    @Autowired
    private CompositionService compositionService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<CompositionResponse> getComposition(@RequestParam Long psuId) {
        CompositionResponse response = compositionService.getDraft(psuId);
        return ResponseEntity.ok(response);
    }

    @PutMapping
    public ResponseEntity<CompositionResponse> saveDraft(
        @RequestParam Long psuId,
        @RequestBody CompositionSaveRequest request
    ) {
        CompositionResponse response = compositionService.saveDraft(psuId, request, getCurrentUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate")
    public ResponseEntity<CompositionValidateResponse> validate(
        @RequestParam Long psuId,
        @RequestBody CompositionSaveRequest request
    ) {
        CompositionValidateResponse response = compositionService.validate(psuId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/render")
    public ResponseEntity<CompositionRenderResponse> render(
        @RequestParam Long psuId,
        @RequestBody CompositionRenderRequest request
    ) {
        CompositionRenderResponse response = compositionService.render(psuId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/submit")
    public ResponseEntity<CompositionResponse> submit(@RequestParam Long psuId) {
        CompositionResponse response = compositionService.submit(psuId, getCurrentUserId());
        return ResponseEntity.ok(response);
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
