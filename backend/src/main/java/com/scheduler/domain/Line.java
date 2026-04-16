package com.scheduler.domain;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class Line {
    private String lineId;
    private Integer parallelCapacity;
    private Integer shiftHoursPerDay;
    private String defaultStart;   // "HH:mm"
    private String defaultEnd;     // "HH:mm"
    private List<ShiftOverride> shiftOverrides = new ArrayList<>();
    private List<Break> breaks = new ArrayList<>();
    private List<DowntimeEvent> downtimeEvents = new ArrayList<>();
}