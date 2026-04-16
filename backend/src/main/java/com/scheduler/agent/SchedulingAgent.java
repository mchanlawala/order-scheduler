package com.scheduler.agent;

import com.scheduler.domain.Order;
import com.scheduler.domain.ScheduleResult;
import com.scheduler.domain.StepExecution;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SchedulingAgent {

    private final ChatLanguageModel llm;
    private final boolean aiEnabled;

    public SchedulingAgent(@Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
                           @Value("${ollama.model:llama3.2}") String modelName,
                           @Value("${ollama.temperature:0.2}") double temperature,
                           @Value("${ollama.max-tokens:800}") int maxTokens) {
        ChatLanguageModel tmpLlm = null;
        boolean tmpEnabled = false;
        try {
            tmpLlm = OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(temperature)
                    // .maxTokens(maxTokens) // uncomment if your version supports it
                    .build();
            tmpEnabled = true;
            log.info("AI agent enabled with Ollama model: {} at {}", modelName, baseUrl);
        } catch (Exception e) {
            log.warn("Failed to initialize Ollama: {}.AI analysis failed AI insights disabled.", e.getMessage());
        }
        this.llm = tmpLlm;
        this.aiEnabled = tmpEnabled;
    }

    public String analyseSchedule(ScheduleResult schedule) {
        if (!aiEnabled || llm == null) {
            return buildRuleBasedInsights(schedule);
        }
        try {
            return runAgentWorkflow(schedule);
        } catch (Exception e) {
            log.error("AI analysis failed: {}", e.getMessage());
            return buildRuleBasedInsights(schedule);
        }
    }

    private String runAgentWorkflow(ScheduleResult schedule) {
        String orderSummary = buildOrderSummary(schedule);
        String orderAnalysis = chat(
                "You are a manufacturing scheduling expert for solar panels.",
                "Analyse these scheduled orders and identify the top 3 risks and recommendations:\n" + orderSummary
        );
        String capacityData = buildCapacityData(schedule);
        String capacityReport = chat(
                "You are a production capacity analyst.",
                "Review line utilisation and identify bottlenecks:\n" + capacityData + "\nPrevious analysis:\n" + orderAnalysis
        );
        String delayPrediction = chat(
                "You are a manufacturing delay prediction specialist.",
                "Based on the schedule, predict which orders are at risk of delay and why:\n" + orderSummary + "\nCapacity:\n" + capacityReport
        );
        String optimisation = chat(
                "You are a production scheduling optimiser.",
                "Provide 3 specific, actionable recommendations to improve this schedule:\n" +
                        "Orders at risk:\n" + delayPrediction + "\nFocus on reducing lead time and balancing line load."
        );

        return "=== ORDER ANALYSIS ===\n" + orderAnalysis +
                "\n\n=== CAPACITY REPORT ===\n" + capacityReport +
                "\n\n=== DELAY PREDICTIONS ===\n" + delayPrediction +
                "\n\n=== OPTIMISATION RECOMMENDATIONS ===\n" + optimisation;
    }

    private String chat(String systemPrompt, String userMessage) {
        Response<AiMessage> response = llm.generate(
                List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userMessage)
                )
        );
        return response.content().text();
    }

    private String buildOrderSummary(ScheduleResult schedule) {
        if (schedule.getOrders() == null) return "No orders.";
        StringBuilder sb = new StringBuilder();
        for (Order o : schedule.getOrders()) {
            sb.append(String.format("Order %s: %s, Qty=%d, Priority=%d, LeadTime=%dh, Status=%s, DueDate=%s, EstCompletion=%s%n",
                    o.getOrderId(), o.getPanelType(), o.getQuantity(), o.getPriority(),
                    o.getTotalLeadTimeMinutes() != null ? o.getTotalLeadTimeMinutes() / 60 : 0,
                    o.getStatus(), o.getDueDate(), o.getEstimatedCompletion()));
        }
        return sb.toString();
    }

    private String buildCapacityData(ScheduleResult schedule) {
        if (schedule.getOrders() == null) return "No data.";
        Map<String, Long> lineBusyMin = new java.util.LinkedHashMap<>();
        for (Order o : schedule.getOrders()) {
            if (o.getSteps() == null) continue;
            for (StepExecution s : o.getSteps()) {
                lineBusyMin.merge(s.getLineId(), s.getDurationMinutes() != null ? s.getDurationMinutes() : 0L, Long::sum);
            }
        }
        StringBuilder sb = new StringBuilder();
        lineBusyMin.forEach((line, min) ->
                sb.append(String.format("Line %s: %.1f hours scheduled%n", line, min / 60.0)));
        return sb.toString();
    }

    private String buildRuleBasedInsights(ScheduleResult schedule) {
        if (schedule.getOrders() == null || schedule.getOrders().isEmpty()) {
            return "No orders to analyse.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== SCHEDULING INSIGHTS (Rule-Based) ===\n\n");

        long delayedCount = schedule.getOrders().stream()
                .filter(o -> "DELAYED".equals(o.getStatus())).count();
        if (delayedCount > 0) {
            sb.append(String.format("⚠ WARNING: %d order(s) flagged as DELAYED — check due dates vs estimated completion.\n\n", delayedCount));
        }

        Map<String, Long> lineBusy = new java.util.HashMap<>();
        for (Order o : schedule.getOrders()) {
            if (o.getSteps() == null) continue;
            for (StepExecution s : o.getSteps()) {
                lineBusy.merge(s.getLineId(), s.getDurationMinutes() != null ? s.getDurationMinutes() : 0L, Long::sum);
            }
        }
        lineBusy.entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .ifPresent(e -> sb.append(String.format("🔴 Bottleneck line: %s (%.1f hours total load)\n\n", e.getKey(), e.getValue() / 60.0)));

        sb.append("📋 RECOMMENDATIONS:\n");
        sb.append("1. Prioritise orders with earliest due dates.\n");
        sb.append("2. Consider adding shift hours to the bottleneck line.\n");
        sb.append("3. Review custom-setup orders — they add reconfiguration time.\n");
        sb.append("4. Batch small quantity orders together where possible.\n");

        if (schedule.getSummary() != null) {
            sb.append(String.format("\n📊 SUMMARY: %d orders, %d panels, avg lead time %dh, all complete by %s",
                    schedule.getSummary().getTotalOrders(),
                    schedule.getSummary().getTotalPanels(),
                    schedule.getSummary().getAvgLeadTimeHours(),
                    schedule.getSummary().getAllCompleteBy()));
        }
        return sb.toString();
    }
}