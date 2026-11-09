package com.campus.lab_resource_platform.sharing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharingTrendResponse {

    private Long institutionId;
    private String institutionName;
    private String period; // "WEEKLY" or "MONTHLY"

    @Builder.Default
    private List<SharingTrendItem> items = new ArrayList<>();
}

