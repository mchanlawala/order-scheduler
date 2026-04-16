package com.scheduler.domain;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Order {
    private String orderId;
    private String panelType;
    private Integer wattage;
    private Integer quantity;
    private LocalDateTime orderReceived;
    private LocalDateTime dueDate;
    private Integer priority;
    private Boolean customSetup;

    // Scheduling results (populated after scheduling)
    private LocalDateTime estimatedCompletion;
    private String status;
    private Long totalLeadTimeMinutes;
    private List<StepExecution> steps;
}