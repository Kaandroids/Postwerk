package com.postwerk.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wizard session state stored in Redis. Contains the full conversation,
 * virtual tool results, and automation plan data.
 */
public class WizardSession implements Serializable {

    private UUID id;
    private String lang;
    private List<Map<String, Object>> messages;
    private Map<String, Object> automationPlan;
    private List<Map<String, Object>> toolResults;
    private String phase;
    private String createdAt;
    private String ipAddress;
    private int turnCount;

    public WizardSession() {
        this.id = UUID.randomUUID();
        this.messages = new ArrayList<>();
        this.toolResults = new ArrayList<>();
        this.phase = "chatting";
        this.createdAt = java.time.Instant.now().toString();
        this.turnCount = 0;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getLang() { return lang; }
    public void setLang(String lang) { this.lang = lang; }

    public List<Map<String, Object>> getMessages() { return messages; }
    public void setMessages(List<Map<String, Object>> messages) { this.messages = messages; }

    public Map<String, Object> getAutomationPlan() { return automationPlan; }
    public void setAutomationPlan(Map<String, Object> automationPlan) { this.automationPlan = automationPlan; }

    public List<Map<String, Object>> getToolResults() { return toolResults; }
    public void setToolResults(List<Map<String, Object>> toolResults) { this.toolResults = toolResults; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public int getTurnCount() { return turnCount; }
    public void setTurnCount(int turnCount) { this.turnCount = turnCount; }
}
