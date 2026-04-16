package com.scheduler.domain;

import lombok.Data;

@Data
public class Break {
    private String breakStart;   // "HH:mm"
    private String breakEnd;     // "HH:mm"
    private Integer dailyOccurrence;
}