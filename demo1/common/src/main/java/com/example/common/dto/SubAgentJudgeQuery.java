package com.example.common.dto;

public class SubAgentJudgeQuery {
    private ShoppingRequest request;
    private String replayJson;

    public ShoppingRequest getRequest() {
        return request;
    }

    public void setRequest(ShoppingRequest request) {
        this.request = request;
    }

    public String getReplayJson() {
        return replayJson;
    }

    public void setReplayJson(String replayJson) {
        this.replayJson = replayJson;
    }
}

