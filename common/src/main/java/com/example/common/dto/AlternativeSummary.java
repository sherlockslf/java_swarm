package com.example.common.dto;

public class AlternativeSummary {
    private String agentId;
    private String summary;

    public AlternativeSummary() {}

    public AlternativeSummary(String agentId, String summary) {
        this.agentId = agentId;
        this.summary = summary;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
