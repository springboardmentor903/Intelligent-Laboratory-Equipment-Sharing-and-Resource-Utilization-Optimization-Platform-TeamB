package com.campus.lab_resource_platform.maintenance.entity;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "preventive_schedules",
        indexes = {
                @Index(name = "idx_prev_sched_equip", columnList = "equipment_id"),
                @Index(name = "idx_prev_sched_due", columnList = "next_due_date"),
                @Index(name = "idx_prev_sched_active", columnList = "is_active")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreventiveSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_period", nullable = false, length = 30)
    @Builder.Default
    private ScheduleRepeatPeriod repeatPeriod = ScheduleRepeatPeriod.MONTHLY;

    @Column(name = "custom_repeat_days")
    private Integer customRepeatDays;

    @Column(name = "last_completed_date")
    private LocalDate lastCompletedDate;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ScheduleStatus calculateStatus(LocalDate today) {
        if (!active || nextDueDate == null) {
            return ScheduleStatus.OK;
        }
        if (nextDueDate.isBefore(today)) {
            return ScheduleStatus.OVERDUE;
        }
        long daysUntilDue = ChronoUnit.DAYS.between(today, nextDueDate);
        if (daysUntilDue <= 7) {
            return ScheduleStatus.DUE_SOON;
        }
        return ScheduleStatus.OK;
    }

    public void advanceNextDueDate(LocalDate completionDate) {
        this.lastCompletedDate = completionDate;
        int days = customRepeatDays != null && customRepeatDays > 0
                ? customRepeatDays
                : (repeatPeriod != null ? repeatPeriod.getDays() : 30);
        this.nextDueDate = completionDate.plusDays(days);
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (nextDueDate == null) {
            int days = customRepeatDays != null && customRepeatDays > 0
                    ? customRepeatDays
                    : (repeatPeriod != null ? repeatPeriod.getDays() : 30);
            nextDueDate = LocalDate.now().plusDays(days);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
