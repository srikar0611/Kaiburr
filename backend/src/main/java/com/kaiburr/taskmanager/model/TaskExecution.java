package com.kaiburr.taskmanager.model;

import java.time.Instant;

public class TaskExecution {
    private Instant startTime;
    private Instant endTime;
    private String output;

    // ✅ Default Constructor (for MongoDB)
    public TaskExecution() {
    }

    // ✅ Constructor with Parameters
    public TaskExecution(Instant startTime, Instant endTime, String output) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.output = output;
    }

    // ✅ Getters & Setters
    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }
}
