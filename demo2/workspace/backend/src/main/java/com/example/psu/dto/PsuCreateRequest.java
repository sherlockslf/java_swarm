package com.example.psu.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * PSU创建请求DTO
 */
@Data
public class PsuCreateRequest {
    
    @NotBlank(message = "PSU ID不能为空")
    private String psuId;
    
    @NotBlank(message = "PSU名称不能为空")
    private String name;
    
    private String description;
}