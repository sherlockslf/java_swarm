package com.example.psu.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.psu.dto.request.TestDatasetCreateRequest;
import com.example.psu.entity.TestDataset;
import com.example.psu.service.TestDatasetService;

import jakarta.validation.Valid;

/**
 * 测试数据集管理控制器
 */
@RestController
@RequestMapping("/api/test-datasets")
public class TestDatasetController {
    
    @Autowired
    private TestDatasetService testDatasetService;
    
    /**
     * 获取PSU的测试数据集列表
     * @param psuId PSU ID
     * @return 测试数据集列表
     */
    @GetMapping
    public ResponseEntity<List<TestDataset>> getTestDatasets(@RequestParam Long psuId) {
        List<TestDataset> datasets = testDatasetService.getTestDatasets(psuId);
        return ResponseEntity.ok(datasets);
    }
    
    /**
     * 创建测试数据集
     * @param psuId PSU ID
     * @param request 创建请求
     * @return 创建的测试数据集
     */
    @PostMapping
    public ResponseEntity<TestDataset> createTestDataset(
            @RequestParam Long psuId, 
            @Valid @RequestBody TestDatasetCreateRequest request) {
        TestDataset dataset = testDatasetService.createTestDataset(psuId, request);
        return ResponseEntity.ok(dataset);
    }
    
    /**
     * 更新测试数据集
     * @param id 数据集ID
     * @param request 更新请求
     * @return 更新后的数据集
     */
    @PutMapping("/{id}")
    public ResponseEntity<TestDataset> updateTestDataset(
            @PathVariable Long id, 
            @Valid @RequestBody TestDatasetCreateRequest request) {
        TestDataset dataset = testDatasetService.updateTestDataset(id, request);
        return ResponseEntity.ok(dataset);
    }
    
    /**
     * 删除测试数据集
     * @param id 数据集ID
     * @return 无内容
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTestDataset(@PathVariable Long id) {
        testDatasetService.deleteTestDataset(id);
        return ResponseEntity.ok().build();
    }
}
