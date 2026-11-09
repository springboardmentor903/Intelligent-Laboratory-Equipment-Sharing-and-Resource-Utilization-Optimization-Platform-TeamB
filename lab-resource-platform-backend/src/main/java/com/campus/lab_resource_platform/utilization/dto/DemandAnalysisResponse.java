package com.campus.lab_resource_platform.utilization.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemandAnalysisResponse {

    private List<DemandAnalysisItemDto> items;
    private Long totalItems;
    private Integer page;
    private Integer size;
    private Integer totalPages;
    private Long highDemandCount;
    private Double thresholdPercent;
}
