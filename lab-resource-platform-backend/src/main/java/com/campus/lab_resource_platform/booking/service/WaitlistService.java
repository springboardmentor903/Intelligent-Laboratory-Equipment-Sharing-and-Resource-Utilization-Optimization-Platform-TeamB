package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.dto.WaitlistRequest;
import com.campus.lab_resource_platform.booking.dto.WaitlistResponse;
import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.entity.WaitlistEntry;
import com.campus.lab_resource_platform.booking.entity.WaitlistStatus;
import com.campus.lab_resource_platform.booking.repository.BookingHistoryRepository;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.booking.repository.WaitlistRepository;
import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.common.exception.ResourceNotFoundException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistRepository waitlistRepository;
    private final EquipmentRepository equipmentRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public WaitlistService(
            WaitlistRepository waitlistRepository,
            EquipmentRepository equipmentRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository
    ) {
        this.waitlistRepository = waitlistRepository;
        this.equipmentRepository = equipmentRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public WaitlistResponse joinWaitlist(WaitlistRequest request, String userEmail) {
        User user = getUserByEmail(userEmail);

        if (request.getDesiredStartTime().isBefore(Instant.now())) {
            throw new BadRequestException("Desired start time must be in the future");
        }
        if (!request.getDesiredEndTime().isAfter(request.getDesiredStartTime())) {
            throw new BadRequestException("Desired end time must be after start time");
        }

        Equipment equipment = equipmentRepository.findById(request.getEquipmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Equipment not found with ID: " + request.getEquipmentId()));

        if (waitlistRepository.existsByEquipmentIdAndUserIdAndStatus(equipment.getId(), user.getId(), WaitlistStatus.WAITING)) {
            throw new BadRequestException("You are already on the active waitlist for this equipment");
        }

        WaitlistEntry entry = WaitlistEntry.builder()
                .equipment(equipment)
                .user(user)
                .desiredStartTime(request.getDesiredStartTime())
                .desiredEndTime(request.getDesiredEndTime())
                .purpose(request.getPurpose())
                .status(WaitlistStatus.WAITING)
                .priority(1)
                .build();

        WaitlistEntry saved = waitlistRepository.save(entry);
        long queuePos = waitlistRepository.calculateQueuePosition(equipment.getId(), saved.getPriority(), saved.getCreatedAt());

        log.info("User {} joined waitlist for equipment {} at position {}", user.getEmail(), equipment.getName(), queuePos);
        return WaitlistResponse.fromEntity(saved, queuePos);
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> getMyWaitlist(String userEmail) {
        User user = getUserByEmail(userEmail);
        List<WaitlistEntry> list = waitlistRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        return list.stream().map(w -> {
            long queuePos = 0;
            if (w.getStatus() == WaitlistStatus.WAITING) {
                queuePos = waitlistRepository.calculateQueuePosition(w.getEquipment().getId(), w.getPriority(), w.getCreatedAt());
            }
            return WaitlistResponse.fromEntity(w, queuePos);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> getEquipmentWaitlist(Long equipmentId) {
        List<WaitlistEntry> list = waitlistRepository.findByEquipmentIdAndStatusOrderByPriorityAscCreatedAtAsc(equipmentId, WaitlistStatus.WAITING);

        long pos = 1;
        List<WaitlistResponse> results = new java.util.ArrayList<>();
        for (WaitlistEntry w : list) {
            results.add(WaitlistResponse.fromEntity(w, pos++));
        }
        return results;
    }

    @Transactional
    public void leaveWaitlist(Long id, String userEmail) {
        WaitlistEntry entry = waitlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Waitlist entry not found with ID: " + id));

        User user = getUserByEmail(userEmail);
        boolean isOwner = entry.getUser().getId().equals(user.getId());
        boolean isManager = user.getRole() == User.Role.SYSTEM_ADMINISTRATOR
                || user.getRole() == User.Role.LAB_MANAGER
                || user.getRole() == User.Role.DEPARTMENT_HEAD
                || user.getRole() == User.Role.INSTITUTION_ADMINISTRATOR;

        if (!isOwner && !isManager) {
            throw new ForbiddenException("You cannot cancel another user's waitlist entry");
        }

        entry.setStatus(WaitlistStatus.CANCELLED);
        waitlistRepository.save(entry);
        log.info("Waitlist entry #{} cancelled by {}", id, user.getEmail());
    }

    /**
     * Called whenever a booking is cancelled to automatically promote the top waitlisted student
     */
    @Transactional
    public void checkAndPromoteWaitlistedCandidate(Long equipmentId, Instant freedStart, Instant freedEnd) {
        try {
            List<WaitlistEntry> candidates = waitlistRepository.findEligibleWaitlistCandidates(equipmentId, freedStart, freedEnd);
            if (candidates.isEmpty()) {
                return;
            }

            WaitlistEntry topCandidate = candidates.get(0);

            // Double check for conflict with any remaining active booking
            List<BookingStatus> blocking = List.of(BookingStatus.CONFIRMED, BookingStatus.IN_USE, BookingStatus.PENDING_APPROVAL);
            List<Booking> conflicts = bookingRepository.findConflictingBookings(
                    equipmentId,
                    topCandidate.getDesiredStartTime(),
                    topCandidate.getDesiredEndTime(),
                    blocking
            );

            if (!conflicts.isEmpty()) {
                log.info("Top waitlist candidate #{} still conflicts with another booking; skipping auto-promote", topCandidate.getId());
                return;
            }

            // Convert to booking in PENDING_APPROVAL status so manager can review
            Booking newBooking = Booking.builder()
                    .equipment(topCandidate.getEquipment())
                    .user(topCandidate.getUser())
                    .startTime(topCandidate.getDesiredStartTime())
                    .endTime(topCandidate.getDesiredEndTime())
                    .purpose("[Waitlist Auto-Promoted] " + (topCandidate.getPurpose() != null ? topCandidate.getPurpose() : ""))
                    .status(BookingStatus.PENDING_APPROVAL)
                    .build();

            Booking savedBooking = bookingRepository.save(newBooking);

            topCandidate.setStatus(WaitlistStatus.CONVERTED);
            topCandidate.setConvertedBookingId(savedBooking.getId());
            topCandidate.setNotifiedAt(Instant.now());
            waitlistRepository.save(topCandidate);

            log.info("Auto-promoted waitlist entry #{} to Booking #{} for user {}",
                    topCandidate.getId(), savedBooking.getId(), topCandidate.getUser().getEmail());

        } catch (Exception e) {
            log.error("Failed to auto-promote waitlist candidate for equipment #{}: {}", equipmentId, e.getMessage(), e);
        }
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}

