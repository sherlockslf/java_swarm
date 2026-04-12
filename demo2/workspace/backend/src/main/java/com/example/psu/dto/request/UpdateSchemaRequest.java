package com.example.psu.dto.request;

/**
 * Schema更新请求类
 */
public class UpdateSchemaRequest {
    private String schemaContent;
    private String changeLog;
    
    public String getSchemaContent() {
        return schemaContent;
    }
    
    public void setSchemaContent(String schemaContent) {
        this.schemaContent = schemaContent;
    }
    
    public String getChangeLog() {
        return changeLog;
    }
    
    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }
}