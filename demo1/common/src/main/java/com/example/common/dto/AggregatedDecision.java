package com.example.common.dto;

import java.util.List;
import java.util.Map;

public class AggregatedDecision {
    private String bestAgentId;
    private String bestAnswer;
    private String justification;
    private List<AlternativeSummary> alternatives;
    private Map<String, Object> debug;

    public String getBestAgentId() {
        return bestAgentId;
    }

    public void setBestAgentId(String bestAgentId) {
        this.bestAgentId = bestAgentId;
    }

    public String getBestAnswer() {
        return bestAnswer;
    }

    public void setBestAnswer(String bestAnswer) {
        this.bestAnswer = bestAnswer;
    }

    public String getJustification() {
        return justification;
    }

    public void setJustification(String justification) {
        this.justification = justification;
    }

    public List<AlternativeSummary> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(List<AlternativeSummary> alternatives) {
        this.alternatives = alternatives;
    }

    public Map<String, Object> getDebug() {
        return debug;
    }

    public void setDebug(Map<String, Object> debug) {
        this.debug = debug;
    }
}
