package com.imagelab.data;

public class HistoryEntry {
    private int id;
    private String operation;
    private String timestamp;
    private String sourceImage;
    private String details;

    public HistoryEntry() {}

    public HistoryEntry(String operation, String timestamp, String sourceImage, String details) {
        this.operation = operation;
        this.timestamp = timestamp;
        this.sourceImage = sourceImage;
        this.details = details;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getSourceImage() { return sourceImage; }
    public void setSourceImage(String sourceImage) { this.sourceImage = sourceImage; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}