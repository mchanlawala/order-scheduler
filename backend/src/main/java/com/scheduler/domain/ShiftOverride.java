package com.scheduler.domain;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ShiftOverride {
    private LocalDate date;
    private String startTime;
    private String endTime;
}