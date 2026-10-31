package com.campus.lab_resource_platform.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "technician_access_requests",
        indexes = {
                @Index(
                        name = "idx_technician_request_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_technician_request_email",
                        columnList = "email"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class TechnicianAccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(length = 150)
    private String institution;

    @Column(length = 150)
    private String department;

    @Column(name = "college_id")
    private Long collegeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_role", nullable = false, length = 40)
    private User.Role requestedRole = User.Role.LAB_TECHNICIAN;

    @Column(length = 1000)
    private String reason;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (requestedRole == null) {
            requestedRole = User.Role.LAB_TECHNICIAN;
        }
    }

    public enum RequestStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
