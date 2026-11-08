package com.campus.lab_resource_platform.utilization.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.sharing.service.FeeCalculationService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.utilization.dto.CheckInRequest;
import com.campus.lab_resource_platform.utilization.dto.CheckOutRequest;
import com.campus.lab_resource_platform.utilization.dto.UtilizationResponse;
import com.campus.lab_resource_platform.utilization.entity.UsageType;
import com.campus.lab_resource_platform.utilization.entity.Utilization;
import com.campus.lab_resource_platform.utilization.repository.UtilizationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class UtilizationService {

    private final UtilizationRepository utilizationRepository;
    private final BookingRepository bookingRepository;
    private final FeeCalculationService feeCalculationService;
    private final UserRepository userRepository;

    public UtilizationService(
            UtilizationRepository utilizationRepository,
            BookingRepository bookingRepository,
            FeeCalculationService feeCalculationService,
            UserRepository userRepository) {
        this.utilizationRepository = utilizationRepository;
        this.bookingRepository = bookingRepository;
        this.feeCalculationService = feeCalculationService;
        this.userRepository = userRepository;
    }

    /**
     * Requirement: Ensure that external usage is recorded with the usage type SHARED during check-in.
     */
    @Transactional
    public UtilizationResponse checkIn(CheckInRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + request.getBookingId()));

        User user = getAuthenticatedUser();
        authorizeBookingOperation(booking, user);

        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot check in to a cancelled or rejected booking");
        }

        if (booking.getStatus() == BookingStatus.IN_USE || booking.getStatus() == BookingStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking has already been checked in");
        }

        LocalDateTime checkInTime = request.getCheckInTime() != null ? request.getCheckInTime() : LocalDateTime.now();

        // Update booking status
        booking.setStatus(BookingStatus.IN_USE);
        bookingRepository.save(booking);

        Utilization utilization = new Utilization();
        utilization.setBooking(booking);
        utilization.setEquipment(booking.getEquipment());
        utilization.setUser(booking.getUser());
        // Requirement: External usage recorded as SHARED
        utilization.setUsageType(booking.isExternal() ? UsageType.SHARED : UsageType.INTERNAL);
        utilization.setCheckInTime(checkInTime);
        utilization.setNotes(request.getNotes());

        Utilization saved = utilizationRepository.save(utilization);
        return toResponse(saved);
    }

    @Transactional
    public UtilizationResponse checkOut(CheckOutRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found: " + request.getBookingId()));

        User user = getAuthenticatedUser();
        authorizeBookingOperation(booking, user);

        Utilization utilization = utilizationRepository.findByBookingId(booking.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No check-in record found for this booking"));

        if (utilization.getCheckOutTime() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booking has already been checked out");
        }

        LocalDateTime checkOutTime = request.getCheckOutTime() != null ? request.getCheckOutTime() : LocalDateTime.now();
        if (checkOutTime.isBefore(utilization.getCheckInTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Check-out time cannot be before check-in time");
        }

        long usedMinutes = Math.max(0, Duration.between(utilization.getCheckInTime(), checkOutTime).toMinutes());
        utilization.setCheckOutTime(checkOutTime);
        utilization.setUsedMinutes((int) usedMinutes);

        // Actual fee calculation
        BigDecimal feePerHour = booking.getFeePerHour() != null ? booking.getFeePerHour() : BigDecimal.ZERO;
        BigDecimal actualFee = feeCalculationService.calculateActualFee(usedMinutes, feePerHour);
        utilization.setActualFee(actualFee);

        if (request.getNotes() != null) {
            utilization.setNotes(request.getNotes());
        }

        // Update booking
        booking.setStatus(BookingStatus.COMPLETED);
        booking.setActualFee(actualFee);
        bookingRepository.save(booking);

        Utilization saved = utilizationRepository.save(utilization);
        return toResponse(saved);
    }

    private void authorizeBookingOperation(Booking booking, User user) {
        if (user.getRole() == User.Role.SYSTEM_ADMINISTRATOR) {
            return;
        }
        if (booking.getUser().getId().equals(user.getId())) {
            return;
        }
        if (user.getRole() == User.Role.LAB_TECHNICIAN || user.getRole() == User.Role.LAB_MANAGER) {
            if (user.getInstitutionId() != null && user.getInstitutionId().equals(booking.getEquipment().getInstitutionId())) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to check in/out for this booking");
    }

    public UtilizationResponse toResponse(Utilization u) {
        UtilizationResponse resp = new UtilizationResponse();
        resp.setId(u.getId());
        resp.setBookingId(u.getBooking() != null ? u.getBooking().getId() : null);
        resp.setEquipmentId(u.getEquipment().getId());
        resp.setEquipmentName(u.getEquipment().getName());
        resp.setUserId(u.getUser().getId());
        resp.setUserName(u.getUser().getFullName());
        resp.setUsageType(u.getUsageType());
        resp.setCheckInTime(u.getCheckInTime());
        resp.setCheckOutTime(u.getCheckOutTime());
        resp.setUsedMinutes(u.getUsedMinutes());
        resp.setActualFee(u.getActualFee());
        resp.setNotes(u.getNotes());
        return resp;
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || authentication.getName().equals("anonymousUser")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user was not found"));
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is inactive");
        }
        return user;
    }
}
