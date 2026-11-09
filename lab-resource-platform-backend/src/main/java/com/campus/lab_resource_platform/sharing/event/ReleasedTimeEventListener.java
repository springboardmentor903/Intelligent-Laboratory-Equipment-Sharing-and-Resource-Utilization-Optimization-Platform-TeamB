package com.campus.lab_resource_platform.sharing.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ReleasedTimeEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReleasedTimeEventListener.class);

    @EventListener
    public void onReleasedTime(ReleasedTimeEvent event) {
        log.info("Released time slot for equipment {}: {} to {}. Cancelled booking ID: {}. Reason: {}. Waitlist check triggered.",
                event.getEquipmentId(),
                event.getStartTime(),
                event.getEndTime(),
                event.getCancelledBookingId(),
                event.getReason());
        // In session 3 waitlist processing, this event wakes up any waitlisted reservations
    }
}

