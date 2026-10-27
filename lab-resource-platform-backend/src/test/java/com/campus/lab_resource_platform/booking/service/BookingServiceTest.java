package com.campus.lab_resource_platform.booking.service;

import com.campus.lab_resource_platform.booking.dto.BookingRequest;
import com.campus.lab_resource_platform.booking.dto.BookingResponse;
import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingHistoryRepository;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.common.exception.BadRequestException;
import com.campus.lab_resource_platform.common.exception.BookingConflictException;
import com.campus.lab_resource_platform.common.exception.ForbiddenException;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.user.entity.User;
import com.campus.lab_resource_platform.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingHistoryRepository bookingHistoryRepository;

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BookingService bookingService;

    private User studentUser;
    private User managerUser;
    private Equipment autoConfirmEquipment;
    private Equipment approvalRequiredEquipment;

    @BeforeEach
    void setUp() {
        studentUser = new User();
        studentUser.setId(1L);
        studentUser.setEmail("student@campus.edu");
        studentUser.setFullName("Student User");
        studentUser.setRole(User.Role.RESEARCHER_STUDENT);
        studentUser.setInstitutionId(10L);

        managerUser = new User();
        managerUser.setId(2L);
        managerUser.setEmail("manager@campus.edu");
        managerUser.setFullName("Lab Manager");
        managerUser.setRole(User.Role.LAB_MANAGER);
        managerUser.setInstitutionId(10L);

        autoConfirmEquipment = Equipment.builder()
                .id(100L)
                .name("Spectrophotometer")
                .status(EquipmentStatus.AVAILABLE)
                .maxBookingHours(4)
                .requiresApproval(false)
                .institutionId(10L)
                .build();

        approvalRequiredEquipment = Equipment.builder()
                .id(200L)
                .name("Transmission Electron Microscope")
                .status(EquipmentStatus.AVAILABLE)
                .maxBookingHours(2)
                .requiresApproval(true)
                .institutionId(10L)
                .build();
    }

    @Test
    void createBooking_directConfirmation_whenApprovalNotRequired() {
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        BookingRequest request = BookingRequest.builder()
                .equipmentId(100L)
                .startTime(start)
                .endTime(end)
                .purpose("Photometric analysis")
                .build();

        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(studentUser));
        when(equipmentRepository.findByIdWithLock(100L)).thenReturn(Optional.of(autoConfirmEquipment));
        when(bookingRepository.findConflictingBookings(eq(100L), any(), any(), any())).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(501L);
            return b;
        });

        BookingResponse response = bookingService.createBooking(request, "student@campus.edu");

        assertNotNull(response);
        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertEquals("Spectrophotometer", response.getEquipmentName());
        verify(bookingRepository, times(1)).save(any(Booking.class));
        verify(bookingHistoryRepository, times(1)).save(any());
    }

    @Test
    void createBooking_pendingApproval_whenApprovalRequired() {
        Instant start = Instant.now().plus(3, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = BookingRequest.builder()
                .equipmentId(200L)
                .startTime(start)
                .endTime(end)
                .purpose("Nanostructure imaging")
                .build();

        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(studentUser));
        when(equipmentRepository.findByIdWithLock(200L)).thenReturn(Optional.of(approvalRequiredEquipment));
        when(bookingRepository.findConflictingBookings(eq(200L), any(), any(), any())).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(502L);
            return b;
        });

        BookingResponse response = bookingService.createBooking(request, "student@campus.edu");

        assertNotNull(response);
        assertEquals(BookingStatus.PENDING_APPROVAL, response.getStatus());
        assertEquals("Transmission Electron Microscope", response.getEquipmentName());
    }

    @Test
    void createBooking_exceedsMaxDuration_fractionalHours_throwsBadRequest() {
        // Equipment max is 2 hours. User books 2 hours + 15 minutes.
        // Truncation bug would see toHours() == 2 and pass. Fixed logic must throw BadRequestException!
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = start.plus(135, ChronoUnit.MINUTES); // 2 hrs 15 mins
        BookingRequest request = BookingRequest.builder()
                .equipmentId(200L)
                .startTime(start)
                .endTime(end)
                .purpose("Extended run")
                .build();

        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(studentUser));
        when(equipmentRepository.findByIdWithLock(200L)).thenReturn(Optional.of(approvalRequiredEquipment));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> bookingService.createBooking(request, "student@campus.edu"));

        assertTrue(ex.getMessage().contains("exceeds the maximum allowed limit of 2 hours"));
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void createBooking_conflictingBooking_throwsConflictException() {
        Instant start = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = BookingRequest.builder()
                .equipmentId(100L)
                .startTime(start)
                .endTime(end)
                .purpose("Test conflict")
                .build();

        Booking existing = Booking.builder()
                .id(999L)
                .equipment(autoConfirmEquipment)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(studentUser));
        when(equipmentRepository.findByIdWithLock(100L)).thenReturn(Optional.of(autoConfirmEquipment));
        when(bookingRepository.findConflictingBookings(eq(100L), any(), any(), any())).thenReturn(List.of(existing));

        assertThrows(BookingConflictException.class,
                () -> bookingService.createBooking(request, "student@campus.edu"));

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void createBooking_pastStartTime_throwsBadRequest() {
        Instant start = Instant.now().minus(10, ChronoUnit.MINUTES);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        BookingRequest request = BookingRequest.builder()
                .equipmentId(100L)
                .startTime(start)
                .endTime(end)
                .build();

        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(studentUser));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> bookingService.createBooking(request, "student@campus.edu"));

        assertTrue(ex.getMessage().contains("future"));
    }

    @Test
    void cancelBooking_byOwner_success() {
        Instant start = Instant.now().plus(5, ChronoUnit.HOURS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Booking booking = Booking.builder()
                .id(777L)
                .user(studentUser)
                .equipment(autoConfirmEquipment)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(777L)).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(studentUser));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse response = bookingService.cancelBooking(777L, "Change of research schedule", "student@campus.edu");

        assertNotNull(response);
        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        assertEquals("Change of research schedule", response.getCancellationReason());
    }

    @Test
    void cancelBooking_afterStartTime_throwsBadRequest() {
        Instant start = Instant.now().minus(30, ChronoUnit.MINUTES);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        Booking booking = Booking.builder()
                .id(778L)
                .user(studentUser)
                .equipment(autoConfirmEquipment)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.IN_USE)
                .build();

        when(bookingRepository.findById(778L)).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(studentUser));

        assertThrows(BadRequestException.class,
                () -> bookingService.cancelBooking(778L, "Too late to cancel", "student@campus.edu"));
    }

    @Test
    void approveBooking_byAuthorizedManager_success() {
        Instant start = Instant.now().plus(4, ChronoUnit.HOURS);
        Instant end = start.plus(1, ChronoUnit.HOURS);
        Booking booking = Booking.builder()
                .id(888L)
                .user(studentUser)
                .equipment(approvalRequiredEquipment)
                .startTime(start)
                .endTime(end)
                .status(BookingStatus.PENDING_APPROVAL)
                .build();

        when(bookingRepository.findById(888L)).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("manager@campus.edu")).thenReturn(Optional.of(managerUser));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse response = bookingService.approveBooking(888L, "manager@campus.edu");

        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertEquals(2L, response.getApproverId());
        assertNotNull(response.getApprovedAt());
    }

    @Test
    void approveBooking_byUnauthorizedStudent_throwsForbidden() {
        Booking booking = Booking.builder()
                .id(889L)
                .user(studentUser)
                .equipment(approvalRequiredEquipment)
                .status(BookingStatus.PENDING_APPROVAL)
                .build();

        when(bookingRepository.findById(889L)).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(studentUser));

        assertThrows(ForbiddenException.class,
                () -> bookingService.approveBooking(889L, "student@campus.edu"));
    }

    @Test
    void rejectBooking_byAuthorizedManager_success() {
        Booking booking = Booking.builder()
                .id(890L)
                .user(studentUser)
                .equipment(approvalRequiredEquipment)
                .status(BookingStatus.PENDING_APPROVAL)
                .build();

        when(bookingRepository.findById(890L)).thenReturn(Optional.of(booking));
        when(userRepository.findByEmail("manager@campus.edu")).thenReturn(Optional.of(managerUser));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingResponse response = bookingService.rejectBooking(890L, "Equipment reserved for calibration", "manager@campus.edu");

        assertEquals(BookingStatus.CANCELLED, response.getStatus());
        assertEquals("Equipment reserved for calibration", response.getRejectionReason());
    }

    @Test
    void getBookingsForApproval_byManager_success() {
        Booking pending = Booking.builder()
                .id(901L)
                .equipment(approvalRequiredEquipment)
                .user(studentUser)
                .status(BookingStatus.PENDING_APPROVAL)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByEmail("manager@campus.edu")).thenReturn(Optional.of(managerUser));
        when(bookingRepository.findPendingApprovalsByInstitution(eq(BookingStatus.PENDING_APPROVAL), eq(10L), any()))
                .thenReturn(new PageImpl<>(List.of(pending), pageable, 1));

        Page<BookingResponse> result = bookingService.getBookingsForApproval("manager@campus.edu", null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(901L, result.getContent().get(0).getId());
    }

    @Test
    void getBookingsForApproval_byStudent_throwsForbidden() {
        Pageable pageable = PageRequest.of(0, 10);
        when(userRepository.findByEmail("student@campus.edu")).thenReturn(Optional.of(studentUser));

        assertThrows(ForbiddenException.class,
                () -> bookingService.getBookingsForApproval("student@campus.edu", null, pageable));
    }
}
