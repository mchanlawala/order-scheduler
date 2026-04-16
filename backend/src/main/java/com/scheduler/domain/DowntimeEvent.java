package com.scheduler.domain;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DowntimeEvent {
    private String eventId;
    private String lineId;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String reason;
    private String type;  // "Planned" or "Unplanned"
}