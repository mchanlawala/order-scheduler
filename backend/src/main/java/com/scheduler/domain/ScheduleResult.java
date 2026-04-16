package com.scheduler.domain;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ScheduleResult {
    private List<Order> orders;
    private LocalDateTime generatedAt;
    private String aiInsights;
    private ScheduleSummary summary;

    @Data
    public static class ScheduleSummary {
        private int totalOrders;
        private int totalPanels;
        private long avgLeadTimeHours;
        private LocalDateTime allCompleteBy;
        private int linesUtilized;
    }
}