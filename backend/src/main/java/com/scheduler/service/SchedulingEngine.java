package com.scheduler.service;

import com.scheduler.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SchedulingEngine {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b([01]?[0-9]|2[0-3]):[0-5][0-9]\\b");

    private static final String[] STEP_COLORS = {
            "#378ADD", "#1D9E75", "#D85A30", "#9C27B0", "#D4537E",
            "#BA7517", "#185FA5", "#3B6D11", "#639922", "#993C1D",
            "#0F6E56", "#D85A30", "#533AB7", "#0F6E56"
    };

    public ScheduleResult generateSchedule(List<Order> orders,
                                           List<ProcessStep> processSteps,
                                           Map<String, Line> linesMap) {

        log.info("Scheduling {} orders across {} process steps on {} lines",
                orders.size(), processSteps.size(), linesMap.size());

        // Sort orders: priority ASC (1 = highest), then received date
        List<Order> sorted = new ArrayList<>(orders);
        sorted.sort(Comparator
                .comparingInt(Order::getPriority)
                .thenComparing(Order::getOrderReceived));

        // Track next-available slot per line
        Map<String, PriorityQueue<LocalDateTime>> lineSlots = new ConcurrentHashMap<>();
        for (Map.Entry<String, Line> e : linesMap.entrySet()) {
            PriorityQueue<LocalDateTime> slots = new PriorityQueue<>();
            int cap = e.getValue().getParallelCapacity();
            for (int i = 0; i < cap; i++) {
                slots.add(LocalDateTime.now().withHour(8).withMinute(0));
            }
            lineSlots.put(e.getKey(), slots);
        }

        List<Order> scheduledOrders = new ArrayList<>();

        for (Order order : sorted) {
            LocalDateTime prevStepEnd = order.getOrderReceived();
            List<StepExecution> executions = new ArrayList<>();

            for (int i = 0; i < processSteps.size(); i++) {
                ProcessStep step = processSteps.get(i);
                Line line = linesMap.get(step.getLineId());

                if (line == null) {
                    log.warn("Line '{}' not found for step '{}'", step.getLineId(), step.getProcessName());
                    continue;
                }

                PriorityQueue<LocalDateTime> slots = lineSlots.get(step.getLineId());
                LocalDateTime lineEarliest = slots.peek();

                LocalDateTime candidateStart = laterOf(prevStepEnd, lineEarliest);
                LocalDateTime adjustedStart = adjustToWorkingTime(candidateStart, line);

                // Setup + Reconfig penalty
                long setupMin = step.getSetupTimeMin();
                if (Boolean.TRUE.equals(order.getCustomSetup()) && step.getReconfigPenaltyMin() != null) {
                    setupMin += step.getReconfigPenaltyMin();
                }

                // Processing time
                int batches = (int) Math.ceil((double) order.getQuantity() / step.getBatchSize());
                long procMin = (long) (batches * step.getBatchSize() * step.getProcessingTimeMinPerUnit());

                LocalDateTime afterSetup = scheduleWork(adjustedStart, setupMin, line);
                LocalDateTime stepEnd = scheduleWork(afterSetup, procMin, line);

                // Apply downtime
                boolean delayed = false;
                String delayReason = null;
                LocalDateTime finalEnd = applyDowntime(adjustedStart, stepEnd, line);
                if (finalEnd.isAfter(stepEnd)) {
                    delayed = true;
                    delayReason = "Downtime on " + step.getLineId();
                    stepEnd = finalEnd;
                }

                // FIXED: compare stepEnd directly with dueDate (LocalDateTime), no .atStartOfDay()
                if (order.getDueDate() != null && stepEnd.isAfter(order.getDueDate())) {
                    delayed = true;
                    delayReason = (delayReason != null ? delayReason + "; " : "") + "Due date breach";
                }

                StepExecution exec = new StepExecution();
                exec.setStepSeq(step.getStepSeq());
                exec.setProcessName(step.getProcessName());
                exec.setLineId(step.getLineId());
                exec.setPlannedStart(adjustedStart);
                exec.setPlannedEnd(stepEnd);
                exec.setDurationMinutes(Duration.between(adjustedStart, stepEnd).toMinutes());
                exec.setIsDelayed(delayed);
                exec.setDelayReason(delayReason);
                exec.setColor(STEP_COLORS[i % STEP_COLORS.length]);
                executions.add(exec);

                slots.poll();
                slots.add(stepEnd.plusMinutes(5)); // small buffer

                prevStepEnd = stepEnd;
            }

            order.setSteps(executions);
            order.setEstimatedCompletion(prevStepEnd);
            order.setTotalLeadTimeMinutes(Duration.between(order.getOrderReceived(), prevStepEnd).toMinutes());
            order.setStatus(executions.stream().anyMatch(e -> Boolean.TRUE.equals(e.getIsDelayed())) ? "DELAYED" : "SCHEDULED");

            scheduledOrders.add(order);
        }

        ScheduleResult result = new ScheduleResult();
        result.setOrders(scheduledOrders);
        result.setGeneratedAt(LocalDateTime.now());
        result.setSummary(buildSummary(scheduledOrders));
        return result;
    }

    // ==================== SAFER TIME PARSING ====================

    /**
     * Robustly parse a time string (HH:mm) from various Excel garbage inputs.
     * Returns default 08:00 if parsing fails.
     */
    private LocalTime parseTime(String input) {
        if (input == null || input.isBlank()) {
            return LocalTime.of(8, 0);
        }

        String trimmed = input.trim().toLowerCase();

        // Reject obvious non-time strings (e.g. "1 day, 0:00:00")
        if (trimmed.contains("day") || trimmed.contains("hour") || trimmed.matches(".*\\d+\\s+day.*")) {
            log.warn("Ignoring non‑time value: '{}', using 08:00", input);
            return LocalTime.of(8, 0);
        }

        // Handle bad Excel full-date strings like "1899-12-31 08:00:00"
        if (trimmed.contains("1899") || trimmed.contains("1900")) {
            int colonIndex = trimmed.indexOf(':');
            if (colonIndex >= 2 && colonIndex + 6 <= trimmed.length()) {
                String timePart = trimmed.substring(colonIndex - 2, colonIndex + 6);
                try {
                    return LocalTime.parse(timePart, TIME_FMT);
                } catch (Exception ignored) {
                }
            }
            return LocalTime.of(8, 0);
        }

        // Try to extract HH:mm from anywhere in the string using regex
        Matcher m = TIME_PATTERN.matcher(trimmed);
        if (m.find()) {
            try {
                return LocalTime.parse(m.group(), TIME_FMT);
            } catch (DateTimeParseException ignored) {
            }
        }

        // Last resort: assume first 5 chars are HH:mm
        try {
            if (trimmed.length() >= 5) {
                return LocalTime.parse(trimmed.substring(0, 5), TIME_FMT);
            }
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse time: '{}'", trimmed);
        }

        return LocalTime.of(8, 0);
    }

    /**
     * Validate a time string before returning it to shift logic.
     * Returns a safe default (08:00) for invalid values.
     */
    private String validateTimeString(String timeStr) {
        if (timeStr == null) return "08:00";
        String lower = timeStr.toLowerCase();
        if (lower.contains("day") || lower.contains("hour")) {
            log.warn("Invalid time '{}' – using default 08:00", timeStr);
            return "08:00";
        }
        return timeStr;
    }

    private LocalDateTime adjustToWorkingTime(LocalDateTime dt, Line line) {
        LocalDateTime cur = dt;
        int safety = 0;
        while (safety++ < 30) {
            LocalTime t = cur.toLocalTime();
            LocalTime start = parseTime(getShiftStart(cur.toLocalDate(), line));
            LocalTime end = parseTime(getShiftEnd(cur.toLocalDate(), line));

            if (t.isBefore(start)) {
                cur = cur.with(start);
                continue;
            }
            if (!t.isBefore(end)) {
                cur = cur.plusDays(1).with(start);
                continue;
            }

            // Check breaks
            for (Break brk : line.getBreaks()) {
                LocalTime bs = parseTime(brk.getBreakStart());
                LocalTime be = parseTime(brk.getBreakEnd());
                if (!t.isBefore(bs) && t.isBefore(be)) {
                    cur = cur.with(be);
                    break;
                }
            }
            break; // if no break, we are good
        }
        return cur;
    }

    private LocalDateTime scheduleWork(LocalDateTime start, long durationMin, Line line) {
        if (durationMin <= 0) return start;
        LocalDateTime cur = adjustToWorkingTime(start, line);
        long remaining = durationMin;
        int safety = 0;

        while (remaining > 0 && safety++ < 2000) {
            LocalTime t = cur.toLocalTime();
            LocalTime shiftEnd = parseTime(getShiftEnd(cur.toLocalDate(), line));

            // Find next break
            LocalTime nextBreakStart = null;
            LocalTime nextBreakEnd = null;
            for (Break brk : line.getBreaks()) {
                LocalTime bs = parseTime(brk.getBreakStart());
                if (t.isBefore(bs) && (nextBreakStart == null || bs.isBefore(nextBreakStart))) {
                    nextBreakStart = bs;
                    nextBreakEnd = parseTime(brk.getBreakEnd());
                }
            }

            LocalTime segEnd = (nextBreakStart != null && nextBreakStart.isBefore(shiftEnd)) ? nextBreakStart : shiftEnd;

            long segMin = Duration.between(t, segEnd).toMinutes();
            if (segMin <= 0) {
                cur = cur.plusDays(1).with(parseTime(getShiftStart(cur.plusDays(1).toLocalDate(), line)));
                cur = adjustToWorkingTime(cur, line);
                continue;
            }

            if (remaining <= segMin) {
                cur = cur.plusMinutes(remaining);
                remaining = 0;
            } else {
                remaining -= segMin;
                cur = cur.with(segEnd);
                if (nextBreakStart != null && t.isBefore(nextBreakStart)) {
                    cur = cur.with(nextBreakEnd);
                } else {
                    cur = cur.plusDays(1).with(parseTime(getShiftStart(cur.plusDays(1).toLocalDate(), line)));
                }
                cur = adjustToWorkingTime(cur, line);
            }
        }
        return cur;
    }

    private LocalDateTime applyDowntime(LocalDateTime start, LocalDateTime stepEnd, Line line) {
        if (line.getDowntimeEvents() == null) return stepEnd;
        LocalDateTime result = stepEnd;
        for (DowntimeEvent event : line.getDowntimeEvents()) {
            if (event.getStartDateTime().isBefore(result) && event.getEndDateTime().isAfter(start)) {
                long delay = Duration.between(laterOf(event.getStartDateTime(), start), event.getEndDateTime()).toMinutes();
                result = result.plusMinutes(delay);
            }
        }
        return result;
    }

    private String getShiftStart(LocalDate date, Line line) {
        for (ShiftOverride ov : line.getShiftOverrides()) {
            if (ov.getDate().equals(date)) return validateTimeString(ov.getStartTime());
        }
        return validateTimeString(line.getDefaultStart() != null ? line.getDefaultStart() : "08:00");
    }

    private String getShiftEnd(LocalDate date, Line line) {
        for (ShiftOverride ov : line.getShiftOverrides()) {
            if (ov.getDate().equals(date)) return validateTimeString(ov.getEndTime());
        }
        return validateTimeString(line.getDefaultEnd() != null ? line.getDefaultEnd() : "17:00");
    }

    private LocalDateTime laterOf(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private ScheduleResult.ScheduleSummary buildSummary(List<Order> orders) {
        ScheduleResult.ScheduleSummary s = new ScheduleResult.ScheduleSummary();
        s.setTotalOrders(orders.size());
        s.setTotalPanels(orders.stream().mapToInt(Order::getQuantity).sum());
        s.setAvgLeadTimeHours((long) orders.stream()
                .mapToLong(o -> o.getTotalLeadTimeMinutes() / 60)
                .average().orElse(0));
        orders.stream().map(Order::getEstimatedCompletion)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .ifPresent(s::setAllCompleteBy);
        return s;
    }
}