package com.example.subagentservice.model;

public class SubAgentDef {
    private String id;
    private String name;
    private String type;
    private String promptPath;
    private boolean enabled;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPromptPath() { return promptPath; }
    public void setPromptPath(String promptPath) { this.promptPath = promptPath; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
