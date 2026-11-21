package com.campus.lab_resource_platform.maintenance.entity;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "work_orders",
        indexes = {
                @Index(name = "idx_wo_num", columnList = "work_order_number", unique = true),
                @Index(name = "idx_wo_equip", columnList = "equipment_id"),
                @Index(name = "idx_wo_tech", columnList = "assigned_technician_id"),
                @Index(name = "idx_wo_status", columnList = "status"),
                @Index(name = "idx_wo_type", columnList = "type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_order_number", nullable = false, unique = true, length = 40)
    private String workOrderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WorkOrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private WorkOrderStatus status = WorkOrderStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "maintenance_request_id")
    private MaintenanceRequest maintenanceRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preventive_schedule_id")
    private PreventiveSchedule preventiveSchedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_technician_id")
    private User assignedTechnician;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private MaintenancePriority priority = MaintenancePriority.MEDIUM;

    @Column(name = "expected_start_date")
    private Instant expectedStartDate;

    @Column(name = "expected_end_date")
    private Instant expectedEndDate;

    @Column(name = "actual_start_date")
    private Instant actualStartDate;

    @Column(name = "actual_end_date")
    private Instant actualEndDate;

    @Column(name = "cost", precision = 12, scale = 2)
    private BigDecimal cost;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("timestamp ASC")
    private List<WorkOrderServiceLog> serviceLogs = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public void addServiceLog(WorkOrderServiceLog log) {
        if (serviceLogs == null) {
            serviceLogs = new ArrayList<>();
        }
        serviceLogs.add(log);
        log.setWorkOrder(this);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = WorkOrderStatus.OPEN;
        }
        if (priority == null) {
            priority = MaintenancePriority.MEDIUM;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
