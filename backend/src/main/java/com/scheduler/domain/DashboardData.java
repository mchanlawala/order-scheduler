package com.scheduler.domain;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class DashboardData {
    private Map<String, Integer> ordersByStatus;
    private Map<String, Double> lineUtilisation;
    private Map<String, Integer> delaySummary;
    private List<Order> recentOrders;
    private ScheduleResult.ScheduleSummary summary;
}