package com.scheduler.controller;

import com.scheduler.agent.SchedulingAgent;
import com.scheduler.domain.*;
import com.scheduler.service.ExcelParserService;
import com.scheduler.service.ProductionOrderService;
import com.scheduler.service.SchedulingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/schedule")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"})
@RequiredArgsConstructor
public class ScheduleController {

    private final ExcelParserService excelParser;
    private final SchedulingEngine schedulingEngine;
    private final SchedulingAgent schedulingAgent;
    private final ProductionOrderService productionOrderService;

    // In-memory store (replace with DB for production)
    private ScheduleResult lastResult;
    private ParsedExcelData lastParsed;

    /**
     * Upload Excel file, parse, schedule, and return full result.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadAndSchedule(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        try {
            log.info("Processing uploaded file: {}", file.getOriginalFilename());
            ParsedExcelData parsed = excelParser.parse(file);
            lastParsed = parsed;

            ScheduleResult result = schedulingEngine.generateSchedule(
                    parsed.getOrders(),
                    parsed.getProcessSteps(),
                    parsed.getLinesMap()
            );
            String insights = schedulingAgent.analyseSchedule(result);
            result.setAiInsights(insights);
            lastResult = result;

            log.info("Scheduled {} orders successfully", result.getOrders().size());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Upload/schedule failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Schedule using built-in default data (demo mode).
     */
    @PostMapping("/demo")
    public ResponseEntity<ScheduleResult> runDemo() {
        List<ProcessStep> steps = excelParser.getDefaultProcessSteps();
        Map<String, Line> lines = excelParser.getDefaultLines();
        List<Order> orders = buildDemoOrders();

        ScheduleResult result = schedulingEngine.generateSchedule(orders, steps, lines);
        String insights = schedulingAgent.analyseSchedule(result);
        result.setAiInsights(insights);
        lastResult = result;
        return ResponseEntity.ok(result);
    }

    /**
     * Get all scheduled orders.
     */
    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders() {
        if (lastResult == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(lastResult.getOrders());
    }

    /**
     * Get full schedule result.
     */
    @GetMapping("/result")
    public ResponseEntity<?> getResult() {
        if (lastResult == null) {
            return ResponseEntity.ok(Map.of("message", "No schedule generated yet. POST /api/schedule/demo or upload a file."));
        }
        return ResponseEntity.ok(lastResult);
    }

    /**
     * Get steps for a specific order (for Gantt chart).
     */
    @GetMapping("/gantt/{orderId}")
    public ResponseEntity<?> getGanttData(@PathVariable String orderId) {
        if (lastResult == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return lastResult.getOrders().stream()
                .filter(o -> o.getOrderId().equals(orderId))
                .findFirst()
                .map(o -> ResponseEntity.ok((Object) o.getSteps()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Dashboard aggregated data.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardData> getDashboard() {
        DashboardData data = new DashboardData();

        if (lastResult != null && lastResult.getOrders() != null) {
            // Orders by status
            Map<String, Integer> statusCount = new LinkedHashMap<>();
            for (Order o : lastResult.getOrders()) {
                statusCount.merge(o.getStatus(), 1, Integer::sum);
            }
            data.setOrdersByStatus(statusCount);

            // Line utilisation (hours per line)
            Map<String, Double> lineHours = new LinkedHashMap<>();
            for (Order o : lastResult.getOrders()) {
                if (o.getSteps() == null) continue;
                for (StepExecution s : o.getSteps()) {
                    lineHours.merge(s.getLineId(),
                            s.getDurationMinutes() != null ? s.getDurationMinutes() / 60.0 : 0.0,
                            Double::sum);
                }
            }
            // Normalise to percentage (assume 8h = 100%)
            double maxH = lineHours.values().stream().mapToDouble(d -> d).max().orElse(1);
            Map<String, Double> utilPct = new LinkedHashMap<>();
            lineHours.forEach((k, v) -> utilPct.put(k, Math.min(100.0, Math.round((v / maxH) * 1000.0) / 10.0)));
            data.setLineUtilisation(utilPct);

            // Delay summary
            Map<String, Integer> delays = new LinkedHashMap<>();
            for (Order o : lastResult.getOrders()) {
                if (o.getSteps() == null) continue;
                for (StepExecution s : o.getSteps()) {
                    if (Boolean.TRUE.equals(s.getIsDelayed()) && s.getDelayReason() != null) {
                        String reason = s.getDelayReason().length() > 30
                                ? s.getDelayReason().substring(0, 30) : s.getDelayReason();
                        delays.merge(reason, 1, Integer::sum);
                    }
                }
            }
            data.setDelaySummary(delays);
            data.setRecentOrders(lastResult.getOrders().stream().limit(5).collect(Collectors.toList()));
            data.setSummary(lastResult.getSummary());
        } else {
            data.setOrdersByStatus(Map.of("No data", 0));
            data.setLineUtilisation(Map.of("No data", 0.0));
            data.setDelaySummary(Map.of());
        }
        return ResponseEntity.ok(data);
    }

    /**
     * AI insights endpoint.
     */
    @GetMapping("/insights")
    public ResponseEntity<Map<String, String>> getInsights() {
        if (lastResult == null) {
            return ResponseEntity.ok(Map.of("insights", "No schedule generated yet."));
        }
        return ResponseEntity.ok(Map.of("insights",
                lastResult.getAiInsights() != null ? lastResult.getAiInsights() : "No insights available."));
    }

    // ---- Demo data builder ----

    private List<Order> buildDemoOrders() {
        String[][] data = {
                {"ORD-001", "Monocrystalline 400W", "400", "500", "1", "false"},
                {"ORD-002", "Bifacial 550W", "550", "300", "2", "true"},
                {"ORD-003", "Monocrystalline 320W", "320", "800", "3", "false"},
                {"ORD-004", "Polycrystalline 250W", "250", "200", "1", "false"},
                {"ORD-005", "Bifacial 450W", "450", "400", "4", "true"},
                {"ORD-006", "Monocrystalline 500W", "500", "150", "2", "false"},
        };
        java.time.LocalDateTime base = java.time.LocalDateTime.now().withHour(8).withMinute(0);
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            String[] d = data[i];
            Order o = new Order();
            o.setOrderId(d[0]);
            o.setPanelType(d[1]);
            o.setWattage(Integer.parseInt(d[2]));
            o.setQuantity(Integer.parseInt(d[3]));
            o.setPriority(Integer.parseInt(d[4]));
            o.setCustomSetup(Boolean.parseBoolean(d[5]));
            o.setOrderReceived(base.plusDays(i / 2));
            o.setDueDate(base.plusDays(14 + i));
            o.setStatus("PENDING");
            orders.add(o);
        }
        return orders;
    }

    /**
     * Get all steps assigned to a specific line (sorted by planned start).
     */
    @GetMapping("/line/{lineId}")
    public ResponseEntity<?> getLineSchedule(@PathVariable String lineId) {
        if (lastResult == null || lastResult.getOrders() == null) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<StepExecution> lineSteps = new ArrayList<>();
        for (Order order : lastResult.getOrders()) {
            if (order.getSteps() == null) continue;
            for (StepExecution step : order.getSteps()) {
                if (step.getLineId().equals(lineId)) {
                    lineSteps.add(step);
                }
            }
        }
        lineSteps.sort(Comparator.comparing(StepExecution::getPlannedStart));
        return ResponseEntity.ok(lineSteps);
    }

    /**
     * Get summary for all lines (total steps, hours, delayed steps, etc.).
     */
    @GetMapping("/lines/summary")
    public ResponseEntity<Map<String, Object>> getLinesSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (lastResult == null || lastResult.getOrders() == null) {
            return ResponseEntity.ok(summary);
        }
        Map<String, List<StepExecution>> stepsByLine = new LinkedHashMap<>();
        for (Order order : lastResult.getOrders()) {
            if (order.getSteps() == null) continue;
            for (StepExecution step : order.getSteps()) {
                stepsByLine.computeIfAbsent(step.getLineId(), k -> new ArrayList<>()).add(step);
            }
        }
        for (Map.Entry<String, List<StepExecution>> entry : stepsByLine.entrySet()) {
            String lineId = entry.getKey();
            List<StepExecution> steps = entry.getValue();
            long totalMinutes = steps.stream().mapToLong(StepExecution::getDurationMinutes).sum();
            double totalHours = totalMinutes / 60.0;
            long delayedSteps = steps.stream().filter(s -> Boolean.TRUE.equals(s.getIsDelayed())).count();
            LocalDateTime firstStart = steps.stream().map(StepExecution::getPlannedStart).min(LocalDateTime::compareTo).orElse(null);
            LocalDateTime lastEnd = steps.stream().map(StepExecution::getPlannedEnd).max(LocalDateTime::compareTo).orElse(null);
            Map<String, Object> lineData = new LinkedHashMap<>();
            lineData.put("totalSteps", steps.size());
            lineData.put("totalHours", Math.round(totalHours * 10) / 10.0);
            lineData.put("delayedSteps", delayedSteps);
            lineData.put("firstStart", firstStart);
            lineData.put("lastEnd", lastEnd);
            summary.put(lineId, lineData);
        }
        return ResponseEntity.ok(summary);
    }


    @GetMapping("/production-orders")
    public ResponseEntity<List<Order>> getProductionOrders() {
        return ResponseEntity.ok(productionOrderService.getAllOrders());
    }

    @GetMapping("/original-leadtime/{orderId}")
    public ResponseEntity<Map<String, Long>> getOriginalLeadTime(@PathVariable String orderId) {
        Long lead = productionOrderService.getOriginalLeadTime(orderId);
        return ResponseEntity.ok(Map.of("originalLeadTimeMinutes", lead != null ? lead : 0L));
    }
}