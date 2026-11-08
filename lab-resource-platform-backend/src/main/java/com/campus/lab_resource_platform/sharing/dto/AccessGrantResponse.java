package com.campus.lab_resource_platform.sharing.dto;

import com.campus.lab_resource_platform.sharing.entity.GrantStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
public class AccessGrantResponse {

    private Long id;
    private Long agreementId;
    private Long userId;
    private String userName;
    private String userEmail;
    private GrantStatus status;
    private Instant grantedAt;
    private Long revokedByUserId;
    private Instant revokedAt;
    private String revocationReason;
}

