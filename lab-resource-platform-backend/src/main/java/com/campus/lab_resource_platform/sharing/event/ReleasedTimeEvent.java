package com.campus.lab_resource_platform.sharing.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

@Getter
public class ReleasedTimeEvent extends ApplicationEvent {

    private final Long equipmentId;
    private final Long cancelledBookingId;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String reason;

    public ReleasedTimeEvent(Object source, Long equipmentId, Long cancelledBookingId,
                             LocalDateTime startTime, LocalDateTime endTime, String reason) {
        super(source);
        this.equipmentId = equipmentId;
        this.cancelledBookingId = cancelledBookingId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
    }
}

