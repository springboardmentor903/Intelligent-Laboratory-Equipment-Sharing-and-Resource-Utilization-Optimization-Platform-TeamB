package com.campus.lab_resource_platform.booking.entity;

import com.campus.lab_resource_platform.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "booking_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 40)
    private BookingStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 40)
    private BookingStatus newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_id", nullable = true)
    private User changedBy;

    @Column(name = "timestamp", nullable = false)
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Column(length = 1000)
    private String remarks;

    @Column(name = "changed_at", nullable = true)
    @Builder.Default
    private Instant changedAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (timestamp == null) {
            timestamp = now;
        }

        if (changedAt == null) {
            changedAt = now;
        }
    }
}
