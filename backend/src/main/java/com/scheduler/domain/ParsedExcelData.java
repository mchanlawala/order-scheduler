package com.scheduler.domain;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class ParsedExcelData {
    private List<Order> orders;
    private List<ProcessStep> processSteps;
    private Map<String, Line> linesMap;
}