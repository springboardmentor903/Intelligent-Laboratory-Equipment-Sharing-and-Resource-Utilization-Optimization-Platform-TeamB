
package com.campus.lab_resource_platform.utilization.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.sharing.service.FeeCalculationService;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import com.campus.lab_resource_platform.utilization.dto.CheckInRequest;
import com.campus.lab_resource_platform.utilization.dto.CheckOutRequest;
import com.campus.lab_resource_platform.utilization.dto.UtilizationResponse;
import com.campus.lab_resource_platform.utilization.entity.UsageType;
import com.campus.lab_resource_platform.utilization.entity.Utilization;
import com.campus.lab_resource_platform.utilization.repository.UtilizationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UtilizationServiceTest {

    @Mock
    private UtilizationRepository utilizationRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private FeeCalculationService feeCalculationService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UtilizationService utilizationService;

    private User user;
    private Equipment equipment;
    private Booking externalBooking;
    private Booking internalBooking;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(5L);
        user.setEmail("user@inst.edu");
        user.setFullName("Lab User");
        user.setActive(true);

        equipment = new Equipment();
        equipment.setId(10L);
        equipment.setName("Spectrometer");

        externalBooking = new Booking();
        externalBooking.setId(101L);
        externalBooking.setEquipment(equipment);
        externalBooking.setUser(user);
        externalBooking.setExternal(true);
        externalBooking.setFeePerHour(new BigDecimal("100.00"));
        externalBooking.setStatus(BookingStatus.CONFIRMED);

        internalBooking = new Booking();
        internalBooking.setId(102L);
        internalBooking.setEquipment(equipment);
        internalBooking.setUser(user);
        internalBooking.setExternal(false);
        internalBooking.setStatus(BookingStatus.CONFIRMED);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        Collections.emptyList()
                )
        );

        lenient().when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCheckIn_ExternalBookingRecordedAsShared() {
        when(bookingRepository.findById(101L))
                .thenReturn(Optional.of(externalBooking));

        when(utilizationRepository.save(any(Utilization.class))).thenAnswer(i -> {
            Utilization u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        CheckInRequest req = new CheckInRequest();
        req.setBookingId(101L);
        req.setCheckInTime(LocalDateTime.now());

        UtilizationResponse resp = utilizationService.checkIn(req);

        assertNotNull(resp);
        assertEquals(UsageType.SHARED, resp.getUsageType());
        assertEquals(BookingStatus.IN_USE, externalBooking.getStatus());
    }

    @Test
    void testCheckIn_InternalBookingRecordedAsInternal() {
        when(bookingRepository.findById(102L))
                .thenReturn(Optional.of(internalBooking));

        when(utilizationRepository.save(any(Utilization.class))).thenAnswer(i -> {
            Utilization u = i.getArgument(0);
            u.setId(2L);
            return u;
        });

        CheckInRequest req = new CheckInRequest();
        req.setBookingId(102L);

        UtilizationResponse resp = utilizationService.checkIn(req);

        assertNotNull(resp);
        assertEquals(UsageType.INTERNAL, resp.getUsageType());
    }

    @Test
    void testCheckOut_ComputesActualFee() {
        LocalDateTime checkIn = LocalDateTime.of(2026, 11, 10, 10, 0);
        LocalDateTime checkOut = LocalDateTime.of(2026, 11, 10, 11, 30);

        Utilization util = new Utilization();
        util.setId(1L);
        util.setBooking(externalBooking);
        util.setEquipment(equipment);
        util.setUser(user);
        util.setCheckInTime(checkIn);

        when(bookingRepository.findById(101L))
                .thenReturn(Optional.of(externalBooking));

        when(utilizationRepository.findByBookingId(101L))
                .thenReturn(Optional.of(util));

        when(feeCalculationService.calculateActualFee(
                90, new BigDecimal("100.00")))
                .thenReturn(new BigDecimal("150.00"));

        when(utilizationRepository.save(any(Utilization.class)))
                .thenAnswer(i -> i.getArgument(0));

        CheckOutRequest req = new CheckOutRequest();
        req.setBookingId(101L);
        req.setCheckOutTime(checkOut);

        UtilizationResponse resp = utilizationService.checkOut(req);

        assertEquals(90, resp.getUsedMinutes());
        assertEquals(new BigDecimal("150.00"), resp.getActualFee());
        assertEquals(BookingStatus.COMPLETED, externalBooking.getStatus());
        assertEquals(new BigDecimal("150.00"), externalBooking.getActualFee());
    }
}
