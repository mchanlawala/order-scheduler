package com.scheduler.domain;

import lombok.Data;

@Data
public class ProcessStep {
    private Integer stepSeq;
    private String processName;
    private String lineId;
    private Integer setupTimeMin;
    private Double processingTimeMinPerUnit;
    private Integer batchSize;
    private Integer precedingStep;
    private Integer reconfigPenaltyMin;
}