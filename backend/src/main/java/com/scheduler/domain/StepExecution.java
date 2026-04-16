package com.scheduler.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class StepExecution {
    private Integer stepSeq;
    private String processName;
    private String lineId;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private Long durationMinutes;
    private Boolean isDelayed;
    private String delayReason;
    private String color;
}