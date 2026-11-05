package com.campus.lab_resource_platform.utilization.service;

import com.campus.lab_resource_platform.booking.entity.Booking;
import com.campus.lab_resource_platform.booking.entity.BookingStatus;
import com.campus.lab_resource_platform.booking.repository.BookingRepository;
import com.campus.lab_resource_platform.booking.repository.WaitlistRepository;
import com.campus.lab_resource_platform.college.entity.College;
import com.campus.lab_resource_platform.college.repository.CollegeRepository;
import com.campus.lab_resource_platform.department.entity.Department;
import com.campus.lab_resource_platform.department.repository.DepartmentRepository;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentOperatingHours;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.EquipmentOperatingHoursRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import com.campus.lab_resource_platform.utilization.dto.*;
import com.campus.lab_resource_platform.utilization.entity.Utilization;
import com.campus.lab_resource_platform.utilization.entity.UtilizationSnapshot;
import com.campus.lab_resource_platform.utilization.repository.UtilizationRepository;
import com.campus.lab_resource_platform.utilization.repository.UtilizationSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UtilizationAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(UtilizationAnalyticsService.class);
    public static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Kolkata");

    private static final LocalTime DEFAULT_OPEN = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(17, 0);
    private static final double DEFAULT_HIGH_DEMAND_THRESHOLD = 80.0;
    private static final double DEFAULT_TARGET_PERCENT = 75.0;

    private static final List<BookingStatus> BOOKED_STATUSES = List.of(
            BookingStatus.PENDING_APPROVAL,
            BookingStatus.CONFIRMED,
            BookingStatus.IN_USE,
            BookingStatus.COMPLETED
    );

    private final EquipmentRepository equipmentRepository;
    private final EquipmentOperatingHoursRepository operatingHoursRepository;
    private final UtilizationRepository utilizationRepository;
    private final UtilizationSnapshotRepository snapshotRepository;
    private final BookingRepository bookingRepository;
    private final WaitlistRepository waitlistRepository;
    private final DepartmentRepository departmentRepository;
    private final CollegeRepository collegeRepository;

    public UtilizationAnalyticsService(
            EquipmentRepository equipmentRepository,
            EquipmentOperatingHoursRepository operatingHoursRepository,
            UtilizationRepository utilizationRepository,
            UtilizationSnapshotRepository snapshotRepository,
            BookingRepository bookingRepository,
            WaitlistRepository waitlistRepository,
            DepartmentRepository departmentRepository,
            CollegeRepository collegeRepository) {
        this.equipmentRepository = equipmentRepository;
        this.operatingHoursRepository = operatingHoursRepository;
        this.utilizationRepository = utilizationRepository;
        this.snapshotRepository = snapshotRepository;
        this.bookingRepository = bookingRepository;
        this.waitlistRepository = waitlistRepository;
        this.departmentRepository = departmentRepository;
        this.collegeRepository = collegeRepository;
    }

    // =========================================================================
    // A. Operating Hours Management
    // =========================================================================

    @Transactional(readOnly = true)
    public List<OperatingHoursDto> getOperatingHours(Long equipmentId) {
        ensureEquipmentExists(equipmentId);
        List<EquipmentOperatingHours> hours = operatingHoursRepository.findByEquipmentIdOrderByDayOfWeekAsc(equipmentId);
        if (hours.isEmpty()) {
            return getDefaultOperatingHoursDtoList();
        }
        return hours.stream().map(h -> OperatingHoursDto.builder()
                .id(h.getId())
                .dayOfWeek(h.getDayOfWeek())
                .dayName(DayOfWeek.of(h.getDayOfWeek()).name())
                .openTime(h.getOpenTime())
                .closeTime(h.getCloseTime())
                .build()
        ).toList();
    }

    @Transactional
    public List<OperatingHoursDto> updateOperatingHours(Long equipmentId, List<OperatingHoursDto> dtos) {
        Equipment equipment = ensureEquipmentExists(equipmentId);
        operatingHoursRepository.deleteByEquipmentId(equipmentId);

        List<EquipmentOperatingHours> entities = new ArrayList<>();
        if (dtos != null) {
            for (OperatingHoursDto dto : dtos) {
                if (dto.getOpenTime().isAfter(dto.getCloseTime()) || dto.getOpenTime().equals(dto.getCloseTime())) {
                    throw new IllegalArgumentException("Open time must be before close time for day " + dto.getDayOfWeek());
                }
                entities.add(EquipmentOperatingHours.builder()
                        .equipment(equipment)
                        .dayOfWeek(dto.getDayOfWeek())
                        .openTime(dto.getOpenTime())
                        .closeTime(dto.getCloseTime())
                        .build());
            }
        }
        List<EquipmentOperatingHours> saved = operatingHoursRepository.saveAll(entities);
        return saved.stream().map(h -> OperatingHoursDto.builder()
                .id(h.getId())
                .dayOfWeek(h.getDayOfWeek())
                .dayName(DayOfWeek.of(h.getDayOfWeek()).name())
                .openTime(h.getOpenTime())
                .closeTime(h.getCloseTime())
                .build()
        ).toList();
    }

    private List<OperatingHoursDto> getDefaultOperatingHoursDtoList() {
        List<OperatingHoursDto> defaults = new ArrayList<>();
        for (int day = 1; day <= 5; day++) {
            defaults.add(OperatingHoursDto.builder()
                    .dayOfWeek(day)
                    .dayName(DayOfWeek.of(day).name())
                    .openTime(DEFAULT_OPEN)
                    .closeTime(DEFAULT_CLOSE)
                    .build());
        }
        return defaults;
    }

    // =========================================================================
    // B. Utilization Summary API
    // =========================================================================

    @Transactional(readOnly = true)
    public UtilizationAnalyticsSummaryDto getUtilizationSummary(
            String scope, Long scopeId, String period, LocalDate startDate, LocalDate endDate) {

        DateRange range = resolveDateRange(period, startDate, endDate);
        List<Equipment> equipments = resolveEquipmentsInScope(scope, scopeId);

        if (equipments.isEmpty()) {
            return buildEmptySummary(scope, scopeId, period, range);
        }

        Map<Long, Map<Integer, OpenWindow>> equipmentHoursMap = loadOperatingHoursForEquipments(equipments);

        // Bulk load utilization logs in range
        Instant rangeStartUtc = range.startDate.atStartOfDay(PLATFORM_ZONE).toInstant();
        Instant rangeEndUtc = range.endDate.plusDays(1).atStartOfDay(PLATFORM_ZONE).toInstant();

        List<Long> equipmentIds = equipments.stream().map(Equipment::getId).toList();
        List<Utilization> utilizations = utilizationRepository.findUtilizationsInRangeForEquipments(
                equipmentIds, rangeStartUtc, rangeEndUtc);

        long totalAvailableMins = 0;
        long totalUsedMins = 0;
        long sharedUsedMins = 0;

        Instant now = Instant.now();

        // Calculate available minutes across all equipments
        for (Equipment eq : equipments) {
            Map<Integer, OpenWindow> hours = equipmentHoursMap.get(eq.getId());
            totalAvailableMins += calculateAvailableMinutes(hours, range.startDate, range.endDate);
        }

        // Calculate used minutes clipped to operating hours
        for (Utilization u : utilizations) {
            Equipment eq = u.getEquipment();
            Map<Integer, OpenWindow> hours = equipmentHoursMap.get(eq.getId());
            long used = calculateClippedUsedMinutes(u, hours, range.startDate, range.endDate, now);
            totalUsedMins += used;

            // Shared usage check
            if (isSharedUsage(u)) {
                sharedUsedMins += used;
            }
        }

        Double rate = totalAvailableMins > 0
                ? Math.min(100.0, Math.round(((double) totalUsedMins / totalAvailableMins) * 1000.0) / 10.0)
                : null;

        Double sharedPercent = totalUsedMins > 0
                ? Math.round(((double) sharedUsedMins / totalUsedMins) * 1000.0) / 10.0
                : 0.0;

        Double targetPercent = resolveTargetPercent(scope, scopeId);
        Double targetGap = (rate != null && targetPercent != null)
                ? Math.round((rate - targetPercent) * 10.0) / 10.0
                : null;

        String targetStatus = (rate == null) ? "NOT_SET" : (rate >= targetPercent ? "ON_TRACK" : "NEEDS_ATTENTION");

        return UtilizationAnalyticsSummaryDto.builder()
                .scope(scope)
                .scopeId(scopeId)
                .scopeName(resolveScopeName(scope, scopeId, equipments))
                .period(period)
                .startDate(range.startDate)
                .endDate(range.endDate)
                .platformTimeZone(PLATFORM_ZONE.getId())
                .totalAvailableMinutes(totalAvailableMins)
                .totalAvailableHours(roundHours(totalAvailableMins))
                .totalUsedMinutes(totalUsedMins)
                .totalUsedHours(roundHours(totalUsedMins))
                .utilizationRate(rate)
                .sharedUsageMinutes(sharedUsedMins)
                .sharedUsagePercent(sharedPercent)
                .targetUtilizationPercent(targetPercent)
                .targetGap(targetGap)
                .targetStatus(targetStatus)
                .equipmentCount(equipments.size())
                .build();
    }

    // =========================================================================
    // C. Utilization Benchmark API
    // =========================================================================

    @Transactional(readOnly = true)
    public UtilizationBenchmarkDto getBenchmark(
            String scope, Long scopeId, String period, LocalDate startDate, LocalDate endDate) {

        DateRange currentRange = resolveDateRange(period, startDate, endDate);
        List<Equipment> equipments = resolveEquipmentsInScope(scope, scopeId);

        if (equipments.isEmpty()) {
            return UtilizationBenchmarkDto.builder()
                    .currentPeriodUtilization(null)
                    .benchmarkPeriodUtilization(null)
                    .differencePercentagePoints(0.0)
                    .comparisonStatus("STABLE")
                    .currentStartDate(currentRange.startDate)
                    .currentEndDate(currentRange.endDate)
                    .build();
        }

        // Preceding 28 days benchmark: [currentRange.startDate - 28 days, currentRange.startDate - 1 day]
        LocalDate benchmarkEnd = currentRange.startDate.minusDays(1);
        LocalDate benchmarkStart = benchmarkEnd.minusDays(27);

        Map<Long, Map<Integer, OpenWindow>> hoursMap = loadOperatingHoursForEquipments(equipments);

        Double currentRate = calculateRangeUtilization(equipments, hoursMap, currentRange.startDate, currentRange.endDate);
        Double benchmarkRate = calculateRangeUtilization(equipments, hoursMap, benchmarkStart, benchmarkEnd);

        Double diff = null;
        String status = "STABLE";

        if (currentRate != null && benchmarkRate != null) {
            diff = Math.round((currentRate - benchmarkRate) * 10.0) / 10.0;
            if (diff > 1.0) status = "HIGHER";
            else if (diff < -1.0) status = "LOWER";
        } else if (currentRate != null) {
            diff = currentRate;
            status = "HIGHER";
        }

        return UtilizationBenchmarkDto.builder()
                .currentPeriodUtilization(currentRate)
                .benchmarkPeriodUtilization(benchmarkRate)
                .differencePercentagePoints(diff != null ? diff : 0.0)
                .comparisonStatus(status)
                .currentStartDate(currentRange.startDate)
                .currentEndDate(currentRange.endDate)
                .benchmarkStartDate(benchmarkStart)
                .benchmarkEndDate(benchmarkEnd)
                .build();
    }

    // =========================================================================
    // D. Demand Analysis API
    // =========================================================================

    @Transactional(readOnly = true)
    public DemandAnalysisResponse getDemandAnalysis(
            String scope, Long scopeId, String period, LocalDate startDate, LocalDate endDate,
            Boolean highDemandOnly, Integer page, Integer size) {

        DateRange range = resolveDateRange(period, startDate, endDate);
        List<Equipment> equipments = resolveEquipmentsInScope(scope, scopeId);

        int p = (page != null && page >= 0) ? page : 0;
        int s = (size != null && size > 0) ? size : 20;

        if (equipments.isEmpty()) {
            return DemandAnalysisResponse.builder()
                    .items(Collections.emptyList())
                    .totalItems(0L)
                    .page(p)
                    .size(s)
                    .totalPages(0)
                    .highDemandCount(0L)
                    .thresholdPercent(DEFAULT_HIGH_DEMAND_THRESHOLD)
                    .build();
        }

        Map<Long, Map<Integer, OpenWindow>> hoursMap = loadOperatingHoursForEquipments(equipments);
        List<Long> equipmentIds = equipments.stream().map(Equipment::getId).toList();

        Instant rangeStartUtc = range.startDate.atStartOfDay(PLATFORM_ZONE).toInstant();
        Instant rangeEndUtc = range.endDate.plusDays(1).atStartOfDay(PLATFORM_ZONE).toInstant();

        // Bulk load all bookings for equipment in range
        List<Booking> allBookings = bookingRepository.findBookingsInDateRangeForEquipments(
                equipmentIds, rangeStartUtc, rangeEndUtc);

        Map<Long, List<Booking>> bookingsByEquipment = allBookings.stream()
                .collect(Collectors.groupingBy(b -> b.getEquipment().getId()));

        // Active waitlist counts
        Map<Long, Long> waitlistCounts = new HashMap<>();
        try {
            List<Object[]> rows = waitlistRepository.countActiveGroupedByEquipment();
            for (Object[] row : rows) {
                waitlistCounts.put((Long) row[0], ((Number) row[1]).longValue());
            }
        } catch (Exception ignored) {}

        List<DemandAnalysisItemDto> allItems = new ArrayList<>();
        long highDemandCount = 0;

        for (Equipment eq : equipments) {
            Map<Integer, OpenWindow> hours = hoursMap.get(eq.getId());
            long availableMins = calculateAvailableMinutes(hours, range.startDate, range.endDate);

            List<Booking> eqBookings = bookingsByEquipment.getOrDefault(eq.getId(), Collections.emptyList());
            long totalBookings = eqBookings.size();

            long bookedMins = 0;
            long noShowCount = 0;
            long cancelledCount = 0;
            long completedOrInUseCount = 0;

            for (Booking b : eqBookings) {
                BookingStatus st = b.getStatus();
                if (st == BookingStatus.NO_SHOW) {
                    noShowCount++;
                } else if (st == BookingStatus.CANCELLED) {
                    cancelledCount++;
                } else if (BOOKED_STATUSES.contains(st)) {
                    if (st == BookingStatus.COMPLETED || st == BookingStatus.IN_USE) {
                        completedOrInUseCount++;
                    }
                    bookedMins += calculateClippedBookingMinutes(b, hours, range.startDate, range.endDate);
                }
            }

            double bookingRate = availableMins > 0
                    ? Math.min(100.0, Math.round(((double) bookedMins / availableMins) * 1000.0) / 10.0)
                    : 0.0;

            long noShowDenominator = completedOrInUseCount + noShowCount;
            double noShowRate = noShowDenominator > 0
                    ? Math.round(((double) noShowCount / noShowDenominator) * 1000.0) / 10.0
                    : 0.0;

            double cancellationRate = totalBookings > 0
                    ? Math.round(((double) cancelledCount / totalBookings) * 1000.0) / 10.0
                    : 0.0;

            boolean isHighDemand = bookingRate >= DEFAULT_HIGH_DEMAND_THRESHOLD;
            if (isHighDemand) highDemandCount++;

            long waitCount = waitlistCounts.getOrDefault(eq.getId(), 0L);

            allItems.add(DemandAnalysisItemDto.builder()
                    .equipmentId(eq.getId())
                    .equipmentName(eq.getName())
                    .assetTag(eq.getAssetTag() != null ? eq.getAssetTag() : "EQ-" + eq.getId())
                    .category(eq.getCategory())
                    .location(eq.getLocation())
                    .totalBookings(totalBookings)
                    .bookedMinutes(bookedMins)
                    .availableMinutes(availableMins)
                    .bookingRate(bookingRate)
                    .noShowRate(noShowRate)
                    .cancellationRate(cancellationRate)
                    .waitlistCount(waitCount)
                    .highDemand(isHighDemand)
                    .thresholdPercent(DEFAULT_HIGH_DEMAND_THRESHOLD)
                    .build());
        }

        // Sort by booking rate descending
        allItems.sort((a, b) -> Double.compare(b.getBookingRate(), a.getBookingRate()));

        // Filter high demand if requested
        List<DemandAnalysisItemDto> filtered = (Boolean.TRUE.equals(highDemandOnly))
                ? allItems.stream().filter(DemandAnalysisItemDto::getHighDemand).toList()
                : allItems;

        int totalFiltered = filtered.size();
        int fromIndex = Math.min(p * s, totalFiltered);
        int toIndex = Math.min(fromIndex + s, totalFiltered);
        List<DemandAnalysisItemDto> pagedItems = filtered.subList(fromIndex, toIndex);

        int totalPages = (int) Math.ceil((double) totalFiltered / s);

        return DemandAnalysisResponse.builder()
                .items(pagedItems)
                .totalItems((long) totalFiltered)
                .page(p)
                .size(s)
                .totalPages(totalPages)
                .highDemandCount(highDemandCount)
                .thresholdPercent(DEFAULT_HIGH_DEMAND_THRESHOLD)
                .build();
    }

    // =========================================================================
    // E. Idle Time & Alerts APIs
    // =========================================================================

    @Transactional(readOnly = true)
    public IdleTimeAnalysisDto getIdleTimeAnalysis(
            String scope, Long scopeId, String period, LocalDate startDate, LocalDate endDate) {

        DateRange range = resolveDateRange(period, startDate, endDate);
        List<Equipment> equipments = resolveEquipmentsInScope(scope, scopeId);

        if (equipments.isEmpty()) {
            return IdleTimeAnalysisDto.builder()
                    .totalAvailableMinutes(0L)
                    .totalBookedMinutes(0L)
                    .totalIdleMinutes(0L)
                    .totalAvailableHours(0.0)
                    .totalBookedHours(0.0)
                    .totalIdleHours(0.0)
                    .idlePercentage(0.0)
                    .dailyBreakdown(Collections.emptyList())
                    .build();
        }

        Map<Long, Map<Integer, OpenWindow>> hoursMap = loadOperatingHoursForEquipments(equipments);
        List<Long> equipmentIds = equipments.stream().map(Equipment::getId).toList();

        Instant rangeStartUtc = range.startDate.atStartOfDay(PLATFORM_ZONE).toInstant();
        Instant rangeEndUtc = range.endDate.plusDays(1).atStartOfDay(PLATFORM_ZONE).toInstant();

        List<Booking> bookings = bookingRepository.findBookingsInDateRangeForEquipments(
                equipmentIds, rangeStartUtc, rangeEndUtc);

        // Daily breakdown calculation
        List<IdleTimeAnalysisDto.DailyIdleDto> dailyBreakdown = new ArrayList<>();
        long totalAvail = 0;
        long totalBooked = 0;

        LocalDate cur = range.startDate;
        while (!cur.isAfter(range.endDate)) {
            final LocalDate day = cur;
            long dayAvail = 0;
            long dayBooked = 0;

            for (Equipment eq : equipments) {
                Map<Integer, OpenWindow> h = hoursMap.get(eq.getId());
                dayAvail += calculateAvailableMinutes(h, day, day);
            }

            for (Booking b : bookings) {
                if (BOOKED_STATUSES.contains(b.getStatus())) {
                    Equipment eq = b.getEquipment();
                    Map<Integer, OpenWindow> h = hoursMap.get(eq.getId());
                    dayBooked += calculateClippedBookingMinutes(b, h, day, day);
                }
            }

            long dayIdle = Math.max(0, dayAvail - dayBooked);
            double dayIdlePct = dayAvail > 0
                    ? Math.round(((double) dayIdle / dayAvail) * 1000.0) / 10.0
                    : 0.0;

            dailyBreakdown.add(IdleTimeAnalysisDto.DailyIdleDto.builder()
                    .date(day)
                    .dayName(day.getDayOfWeek().name())
                    .availableMinutes(dayAvail)
                    .bookedMinutes(dayBooked)
                    .idleMinutes(dayIdle)
                    .availableHours(roundHours(dayAvail))
                    .bookedHours(roundHours(dayBooked))
                    .idleHours(roundHours(dayIdle))
                    .idlePercentage(dayIdlePct)
                    .build());

            totalAvail += dayAvail;
            totalBooked += dayBooked;

            cur = cur.plusDays(1);
        }

        long totalIdle = Math.max(0, totalAvail - totalBooked);
        double totalIdlePct = totalAvail > 0
                ? Math.round(((double) totalIdle / totalAvail) * 1000.0) / 10.0
                : 0.0;

        return IdleTimeAnalysisDto.builder()
                .totalAvailableMinutes(totalAvail)
                .totalBookedMinutes(totalBooked)
                .totalIdleMinutes(totalIdle)
                .totalAvailableHours(roundHours(totalAvail))
                .totalBookedHours(roundHours(totalBooked))
                .totalIdleHours(roundHours(totalIdle))
                .idlePercentage(totalIdlePct)
                .dailyBreakdown(dailyBreakdown)
                .build();
    }

    @Transactional(readOnly = true)
    public List<IdleAlertDto> getIdleAlerts(String scope, Long scopeId) {
        List<Equipment> equipments = resolveEquipmentsInScope(scope, scopeId);

        // Filter: only Available or Booked; exclude Under Maintenance, Out of Service, Retired
        List<Equipment> eligible = equipments.stream()
                .filter(e -> e.getStatus() == EquipmentStatus.AVAILABLE || e.getStatus() == EquipmentStatus.IN_USE)
                .toList();

        if (eligible.isEmpty()) {
            return Collections.emptyList();
        }

        // Last 7 completed days ending yesterday
        LocalDate yesterday = LocalDate.now(PLATFORM_ZONE).minusDays(1);
        LocalDate sevenDaysAgo = yesterday.minusDays(6);

        Map<Long, Map<Integer, OpenWindow>> hoursMap = loadOperatingHoursForEquipments(eligible);
        List<Long> equipmentIds = eligible.stream().map(Equipment::getId).toList();

        Instant rangeStartUtc = sevenDaysAgo.atStartOfDay(PLATFORM_ZONE).toInstant();
        Instant rangeEndUtc = yesterday.plusDays(1).atStartOfDay(PLATFORM_ZONE).toInstant();

        List<Booking> bookings = bookingRepository.findBookingsInDateRangeForEquipments(
                equipmentIds, rangeStartUtc, rangeEndUtc);
        Map<Long, List<Booking>> bookingsByEq = bookings.stream()
                .collect(Collectors.groupingBy(b -> b.getEquipment().getId()));

        Map<Long, Department> deptMap = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(Department::getId, Function.identity(), (a, b) -> a));
        Map<Long, College> colMap = collegeRepository.findAll().stream()
                .collect(Collectors.toMap(College::getId, Function.identity(), (a, b) -> a));

        List<IdleAlertDto> alerts = new ArrayList<>();

        for (Equipment eq : eligible) {
            Map<Integer, OpenWindow> h = hoursMap.get(eq.getId());
            long availMins = calculateAvailableMinutes(h, sevenDaysAgo, yesterday);
            if (availMins == 0) continue;

            List<Booking> eqBookings = bookingsByEq.getOrDefault(eq.getId(), Collections.emptyList());
            long bookedMins = 0;
            for (Booking b : eqBookings) {
                if (BOOKED_STATUSES.contains(b.getStatus())) {
                    bookedMins += calculateClippedBookingMinutes(b, h, sevenDaysAgo, yesterday);
                }
            }

            long idleMins = Math.max(0, availMins - bookedMins);
            double idlePct = Math.round(((double) idleMins / availMins) * 1000.0) / 10.0;

            if (idlePct >= 90.0) {
                // Find end date of last completed booking
                Instant lastBookingEnd = null;
                List<Booking> recent = bookingRepository.findRecentCompletedOrConfirmedBookings(eq.getId());
                if (!recent.isEmpty()) {
                    lastBookingEnd = recent.get(0).getEndTime();
                }

                String deptName = eq.getDepartmentId() != null && deptMap.containsKey(eq.getDepartmentId())
                        ? deptMap.get(eq.getDepartmentId()).getName() : "General";
                Long cId = eq.getCollegeId() != null ? eq.getCollegeId() : eq.getInstitutionId();
                String colName = cId != null && colMap.containsKey(cId) ? colMap.get(cId).getName() : "Institution";

                alerts.add(IdleAlertDto.builder()
                        .equipmentId(eq.getId())
                        .equipmentName(eq.getName())
                        .assetTag(eq.getAssetTag() != null ? eq.getAssetTag() : "EQ-" + eq.getId())
                        .model(eq.getModel())
                        .category(eq.getCategory())
                        .departmentId(eq.getDepartmentId())
                        .departmentName(deptName)
                        .collegeId(cId)
                        .collegeName(colName)
                        .idlePercentage(idlePct)
                        .idleHours(roundHours(idleMins))
                        .availableHours(roundHours(availMins))
                        .bookedHours(roundHours(bookedMins))
                        .lastBookingEndDate(lastBookingEnd)
                        .build());
            }
        }

        alerts.sort((a, b) -> Double.compare(b.getIdlePercentage(), a.getIdlePercentage()));
        return alerts;
    }

    // =========================================================================
    // F. Peak Usage & Heatmap APIs
    // =========================================================================

    @Transactional(readOnly = true)
    public PeakUsageDto getPeakUsage(
            String scope, Long scopeId, String period, LocalDate startDate, LocalDate endDate) {

        DateRange range = resolveDateRange(period, startDate, endDate);
        List<Equipment> equipments = resolveEquipmentsInScope(scope, scopeId);

        if (equipments.isEmpty()) {
            return PeakUsageDto.builder()
                    .busiestDayOfWeek("NONE")
                    .dayOfWeekUsage(Collections.emptyMap())
                    .peakHoursOfDay(Collections.emptyList())
                    .hourlyUsage(Collections.emptyMap())
                    .top5Slots(Collections.emptyList())
                    .build();
        }

        Map<Long, Map<Integer, OpenWindow>> hoursMap = loadOperatingHoursForEquipments(equipments);
        List<Long> equipmentIds = equipments.stream().map(Equipment::getId).toList();

        Instant rangeStartUtc = range.startDate.atStartOfDay(PLATFORM_ZONE).toInstant();
        Instant rangeEndUtc = range.endDate.plusDays(1).atStartOfDay(PLATFORM_ZONE).toInstant();

        List<Utilization> utilizations = utilizationRepository.findUtilizationsInRangeForEquipments(
                equipmentIds, rangeStartUtc, rangeEndUtc);

        Instant now = Instant.now();

        // Accumulators
        Map<Integer, Long> usedMinsByDayOfWeek = new HashMap<>(); // 1..7
        Map<Integer, Long> availMinsByDayOfWeek = new HashMap<>();
        Map<Integer, Long> usedMinsByHour = new HashMap<>();       // 0..23
        Map<Integer, Long> availMinsByHour = new HashMap<>();
        Map<String, Long> usedMinsByDayHour = new HashMap<>();    // "d-h"
        Map<String, Long> availMinsByDayHour = new HashMap<>();

        for (int d = 1; d <= 7; d++) {
            usedMinsByDayOfWeek.put(d, 0L);
            availMinsByDayOfWeek.put(d, 0L);
        }
        for (int h = 0; h < 24; h++) {
            usedMinsByHour.put(h, 0L);
            availMinsByHour.put(h, 0L);
        }
        for (int d = 1; d <= 7; d++) {
            for (int h = 0; h < 24; h++) {
                usedMinsByDayHour.put(d + "-" + h, 0L);
                availMinsByDayHour.put(d + "-" + h, 0L);
            }
        }

        // Calculate available capacity per day and hour
        LocalDate cur = range.startDate;
        while (!cur.isAfter(range.endDate)) {
            int d = cur.getDayOfWeek().getValue();
            for (Equipment eq : equipments) {
                Map<Integer, OpenWindow> hMap = hoursMap.get(eq.getId());
                OpenWindow win = hMap != null ? hMap.get(d) : null;
                if (win != null) {
                    for (int h = 0; h < 24; h++) {
                        long slotOpenMins = win.getOpenMinutesForHour(h);
                        if (slotOpenMins > 0) {
                            availMinsByHour.put(h, availMinsByHour.get(h) + slotOpenMins);
                            availMinsByDayOfWeek.put(d, availMinsByDayOfWeek.get(d) + slotOpenMins);
                            String key = d + "-" + h;
                            availMinsByDayHour.put(key, availMinsByDayHour.get(key) + slotOpenMins);
                        }
                    }
                }
            }
            cur = cur.plusDays(1);
        }

        // Clip usage logs to day and hour
        for (Utilization u : utilizations) {
            Equipment eq = u.getEquipment();
            Map<Integer, OpenWindow> hMap = hoursMap.get(eq.getId());
            Instant start = u.getCheckInTime();
            Instant end = u.getCheckOutTime() != null ? u.getCheckOutTime() : now;
            if (end.isBefore(start)) continue;

            LocalDate logDate = range.startDate;
            while (!logDate.isAfter(range.endDate)) {
                int d = logDate.getDayOfWeek().getValue();
                OpenWindow win = hMap != null ? hMap.get(d) : null;
                if (win != null) {
                    for (int h = 0; h < 24; h++) {
                        long slotOpenMins = win.getOpenMinutesForHour(h);
                        if (slotOpenMins > 0) {
                            Instant slotStart = logDate.atTime(h, 0).atZone(PLATFORM_ZONE).toInstant();
                            Instant slotEnd = logDate.atTime(h, 59, 59).plusSeconds(1).atZone(PLATFORM_ZONE).toInstant();

                            Instant clipS = start.isAfter(slotStart) ? start : slotStart;
                            Instant clipE = end.isBefore(slotEnd) ? end : slotEnd;

                            if (clipE.isAfter(clipS)) {
                                long mins = Math.min(slotOpenMins, Duration.between(clipS, clipE).toMinutes());
                                usedMinsByHour.put(h, usedMinsByHour.get(h) + mins);
                                usedMinsByDayOfWeek.put(d, usedMinsByDayOfWeek.get(d) + mins);
                                String key = d + "-" + h;
                                usedMinsByDayHour.put(key, usedMinsByDayHour.get(key) + mins);
                            }
                        }
                    }
                }
                logDate = logDate.plusDays(1);
            }
        }

        // Busiest day of week
        String busiestDay = "MONDAY";
        long maxDayUsage = -1;
        Map<String, Long> dayOfWeekUsageNamed = new LinkedHashMap<>();
        for (int d = 1; d <= 7; d++) {
            String name = DayOfWeek.of(d).name();
            long usage = usedMinsByDayOfWeek.get(d);
            dayOfWeekUsageNamed.put(name, usage);
            if (usage > maxDayUsage) {
                maxDayUsage = usage;
                busiestDay = name;
            }
        }

        // Peak hours of day (top 3 hours)
        List<Integer> peakHours = usedMinsByHour.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        // Top 5 slots (DayOfWeek + Hour) sorted by utilization %, used minutes, earlier day, earlier hour
        List<PeakUsageDto.BusiestSlotDto> slotList = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            for (int h = 0; h < 24; h++) {
                String key = d + "-" + h;
                long used = usedMinsByDayHour.get(key);
                long avail = availMinsByDayHour.get(key);
                if (avail > 0 && used > 0) {
                    double pct = Math.min(100.0, Math.round(((double) used / avail) * 1000.0) / 10.0);
                    slotList.add(PeakUsageDto.BusiestSlotDto.builder()
                            .dayOfWeek(d)
                            .dayName(DayOfWeek.of(d).name())
                            .hour(h)
                            .timeLabel(String.format("%02d:00 - %02d:00", h, (h + 1) % 24))
                            .usedMinutes(used)
                            .availableMinutes(avail)
                            .utilizationPercent(pct)
                            .build());
                }
            }
        }

        slotList.sort((a, b) -> {
            int comp = Double.compare(b.getUtilizationPercent(), a.getUtilizationPercent());
            if (comp != 0) return comp;
            comp = Long.compare(b.getUsedMinutes(), a.getUsedMinutes());
            if (comp != 0) return comp;
            comp = Integer.compare(a.getDayOfWeek(), b.getDayOfWeek());
            if (comp != 0) return comp;
            return Integer.compare(a.getHour(), b.getHour());
        });

        List<PeakUsageDto.BusiestSlotDto> top5 = slotList.stream().limit(5).toList();

        return PeakUsageDto.builder()
                .busiestDayOfWeek(busiestDay)
                .dayOfWeekUsage(dayOfWeekUsageNamed)
                .peakHoursOfDay(peakHours)
                .hourlyUsage(usedMinsByHour)
                .top5Slots(top5)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UtilizationHeatmapSlotDto> getHeatmap(
            String scope, Long scopeId, String period, LocalDate startDate, LocalDate endDate) {

        DateRange range = resolveDateRange(period, startDate, endDate);
        List<Equipment> equipments = resolveEquipmentsInScope(scope, scopeId);

        Map<Long, Map<Integer, OpenWindow>> hoursMap = loadOperatingHoursForEquipments(equipments);
        List<Long> equipmentIds = equipments.stream().map(Equipment::getId).toList();

        Instant rangeStartUtc = range.startDate.atStartOfDay(PLATFORM_ZONE).toInstant();
        Instant rangeEndUtc = range.endDate.plusDays(1).atStartOfDay(PLATFORM_ZONE).toInstant();

        List<Utilization> utilizations = equipmentIds.isEmpty() ? Collections.emptyList() :
                utilizationRepository.findUtilizationsInRangeForEquipments(equipmentIds, rangeStartUtc, rangeEndUtc);

        Instant now = Instant.now();

        // 168 slots: 7 days x 24 hours
        Map<String, Long> availMap = new HashMap<>();
        Map<String, Long> usedMap = new HashMap<>();

        for (int d = 1; d <= 7; d++) {
            for (int h = 0; h < 24; h++) {
                String k = d + "-" + h;
                availMap.put(k, 0L);
                usedMap.put(k, 0L);
            }
        }

        LocalDate cur = range.startDate;
        while (!cur.isAfter(range.endDate)) {
            int d = cur.getDayOfWeek().getValue();
            for (Equipment eq : equipments) {
                Map<Integer, OpenWindow> hMap = hoursMap.get(eq.getId());
                OpenWindow win = hMap != null ? hMap.get(d) : null;
                if (win != null) {
                    for (int h = 0; h < 24; h++) {
                        long mins = win.getOpenMinutesForHour(h);
                        if (mins > 0) {
                            String k = d + "-" + h;
                            availMap.put(k, availMap.get(k) + mins);
                        }
                    }
                }
            }
            cur = cur.plusDays(1);
        }

        for (Utilization u : utilizations) {
            Equipment eq = u.getEquipment();
            Map<Integer, OpenWindow> hMap = hoursMap.get(eq.getId());
            Instant start = u.getCheckInTime();
            Instant end = u.getCheckOutTime() != null ? u.getCheckOutTime() : now;
            if (end.isBefore(start)) continue;

            LocalDate logDate = range.startDate;
            while (!logDate.isAfter(range.endDate)) {
                int d = logDate.getDayOfWeek().getValue();
                OpenWindow win = hMap != null ? hMap.get(d) : null;
                if (win != null) {
                    for (int h = 0; h < 24; h++) {
                        long slotOpenMins = win.getOpenMinutesForHour(h);
                        if (slotOpenMins > 0) {
                            Instant slotStart = logDate.atTime(h, 0).atZone(PLATFORM_ZONE).toInstant();
                            Instant slotEnd = logDate.atTime(h, 59, 59).plusSeconds(1).atZone(PLATFORM_ZONE).toInstant();

                            Instant clipS = start.isAfter(slotStart) ? start : slotStart;
                            Instant clipE = end.isBefore(slotEnd) ? end : slotEnd;

                            if (clipE.isAfter(clipS)) {
                                long mins = Math.min(slotOpenMins, Duration.between(clipS, clipE).toMinutes());
                                String k = d + "-" + h;
                                usedMap.put(k, usedMap.get(k) + mins);
                            }
                        }
                    }
                }
                logDate = logDate.plusDays(1);
            }
        }

        List<UtilizationHeatmapSlotDto> result = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            for (int h = 0; h < 24; h++) {
                String k = d + "-" + h;
                long avail = availMap.get(k);
                long used = usedMap.get(k);

                Double pct = null;
                if (avail > 0) {
                    pct = Math.min(100.0, Math.round(((double) used / avail) * 1000.0) / 10.0);
                }

                result.add(UtilizationHeatmapSlotDto.builder()
                        .dayOfWeek(d)
                        .dayName(DayOfWeek.of(d).name())
                        .hour(h)
                        .timeLabel(String.format("%02d:00", h))
                        .availableMinutes(avail)
                        .usedMinutes(used)
                        .utilizationPercent(pct)
                        .build());
            }
        }

        return result;
    }

    // =========================================================================
    // G. Daily Snapshot Scheduled Job & Backfill (Cron: 12:30 AM)
    // =========================================================================

    @Scheduled(cron = "0 30 0 * * ?", zone = "Asia/Kolkata")
    @Transactional
    public void scheduledDailySnapshots() {
        LocalDate yesterday = LocalDate.now(PLATFORM_ZONE).minusDays(1);
        log.info("Running scheduled daily utilization snapshot job for date: {}", yesterday);
        generateSnapshotsForDate(yesterday);
    }

    @Transactional
    public void backfillSnapshots(int maxDays) {
        int days = Math.min(90, Math.max(1, maxDays));
        LocalDate today = LocalDate.now(PLATFORM_ZONE);
        for (int i = 1; i <= days; i++) {
            LocalDate d = today.minusDays(i);
            generateSnapshotsForDate(d);
        }
        log.info("Completed historical backfill of snapshots for {} days.", days);
    }

    private void generateSnapshotsForDate(LocalDate date) {
        List<Equipment> equipments = equipmentRepository.findAll();
        Map<Long, Map<Integer, OpenWindow>> hoursMap = loadOperatingHoursForEquipments(equipments);

        Instant dayStartUtc = date.atStartOfDay(PLATFORM_ZONE).toInstant();
        Instant dayEndUtc = date.plusDays(1).atStartOfDay(PLATFORM_ZONE).toInstant();

        List<Long> equipmentIds = equipments.stream().map(Equipment::getId).toList();
        List<Utilization> dayUtilizations = utilizationRepository.findUtilizationsInRangeForEquipments(
                equipmentIds, dayStartUtc, dayEndUtc);
        Map<Long, List<Utilization>> byEquipment = dayUtilizations.stream()
                .collect(Collectors.groupingBy(u -> u.getEquipment().getId()));

        Instant now = Instant.now();

        for (Equipment eq : equipments) {
            Map<Integer, OpenWindow> h = hoursMap.get(eq.getId());
            long avail = calculateAvailableMinutes(h, date, date);

            List<Utilization> logs = byEquipment.getOrDefault(eq.getId(), Collections.emptyList());
            long used = 0;
            long shared = 0;

            for (Utilization u : logs) {
                long m = calculateClippedUsedMinutes(u, h, date, date, now);
                used += m;
                if (isSharedUsage(u)) {
                    shared += m;
                }
            }

            Double pct = avail > 0
                    ? Math.min(100.0, Math.round(((double) used / avail) * 1000.0) / 10.0)
                    : null;

            Optional<UtilizationSnapshot> existing = snapshotRepository.findByEquipmentIdAndSnapshotDate(eq.getId(), date);
            UtilizationSnapshot snap = existing.orElseGet(() -> UtilizationSnapshot.builder()
                    .equipment(eq)
                    .snapshotDate(date)
                    .build());

            snap.setAvailableMinutes((int) avail);
            snap.setUsedMinutes((int) used);
            snap.setSharedMinutes((int) shared);
            snap.setUtilizationPercent(pct);

            snapshotRepository.save(snap);
        }
    }

    // =========================================================================
    // Scope & Helper Calculations
    // =========================================================================

    public List<Equipment> resolveEquipmentsInScope(String scope, Long scopeId) {
        if ("EQUIPMENT".equalsIgnoreCase(scope)) {
            if (scopeId == null) {
                return equipmentRepository.findAll().stream().findFirst().map(List::of).orElse(Collections.emptyList());
            }
            return equipmentRepository.findById(scopeId).map(List::of).orElse(Collections.emptyList());
        } else if ("DEPARTMENT".equalsIgnoreCase(scope)) {
            if (scopeId == null) {
                List<Equipment> deptEqs = equipmentRepository.findAll().stream()
                        .filter(e -> e.getDepartmentId() != null)
                        .toList();
                return deptEqs.isEmpty() ? equipmentRepository.findAll() : deptEqs;
            }
            return equipmentRepository.findAll().stream()
                    .filter(e -> scopeId.equals(e.getDepartmentId()))
                    .toList();
        } else if ("INSTITUTION".equalsIgnoreCase(scope) || "COLLEGE".equalsIgnoreCase(scope)) {
            if (scopeId == null) {
                return equipmentRepository.findAll();
            }
            return equipmentRepository.findAll().stream()
                    .filter(e -> scopeId.equals(e.getCollegeId()) || scopeId.equals(e.getInstitutionId()))
                    .toList();
        }
        throw new IllegalArgumentException("Invalid scope: " + scope + ". Must be EQUIPMENT, DEPARTMENT, or INSTITUTION");
    }

    private Equipment ensureEquipmentExists(Long equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new NoSuchElementException("Equipment not found: " + equipmentId));
    }

    private Double calculateRangeUtilization(
            List<Equipment> equipments, Map<Long, Map<Integer, OpenWindow>> hoursMap, LocalDate start, LocalDate end) {
        long avail = 0;
        for (Equipment eq : equipments) {
            avail += calculateAvailableMinutes(hoursMap.get(eq.getId()), start, end);
        }
        if (avail == 0) return null;

        Instant sUtc = start.atStartOfDay(PLATFORM_ZONE).toInstant();
        Instant eUtc = end.plusDays(1).atStartOfDay(PLATFORM_ZONE).toInstant();
        List<Long> eqIds = equipments.stream().map(Equipment::getId).toList();

        List<Utilization> utils = utilizationRepository.findUtilizationsInRangeForEquipments(eqIds, sUtc, eUtc);
        Instant now = Instant.now();

        long used = 0;
        for (Utilization u : utils) {
            used += calculateClippedUsedMinutes(u, hoursMap.get(u.getEquipment().getId()), start, end, now);
        }

        return Math.min(100.0, Math.round(((double) used / avail) * 1000.0) / 10.0);
    }

    private Map<Long, Map<Integer, OpenWindow>> loadOperatingHoursForEquipments(List<Equipment> equipments) {
        List<Long> ids = equipments.stream().map(Equipment::getId).toList();
        List<EquipmentOperatingHours> rows = ids.isEmpty() ? Collections.emptyList() :
                operatingHoursRepository.findByEquipmentIdIn(ids);

        Map<Long, List<EquipmentOperatingHours>> byEq = rows.stream()
                .collect(Collectors.groupingBy(h -> h.getEquipment().getId()));

        Map<Long, Map<Integer, OpenWindow>> result = new HashMap<>();

        for (Equipment eq : equipments) {
            List<EquipmentOperatingHours> configured = byEq.get(eq.getId());
            Map<Integer, OpenWindow> dayMap = new HashMap<>();

            if (configured == null || configured.isEmpty()) {
                // Default schedule: Mon - Fri (1..5), 09:00 - 17:00
                for (int d = 1; d <= 5; d++) {
                    dayMap.put(d, new OpenWindow(DEFAULT_OPEN, DEFAULT_CLOSE));
                }
            } else {
                // Configured schedule: ONLY configured days are open
                for (EquipmentOperatingHours h : configured) {
                    dayMap.put(h.getDayOfWeek(), new OpenWindow(h.getOpenTime(), h.getCloseTime()));
                }
            }
            result.put(eq.getId(), dayMap);
        }
        return result;
    }

    private long calculateAvailableMinutes(Map<Integer, OpenWindow> hours, LocalDate start, LocalDate end) {
        if (hours == null || hours.isEmpty()) return 0;
        long mins = 0;
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            int d = cur.getDayOfWeek().getValue();
            OpenWindow win = hours.get(d);
            if (win != null) {
                mins += win.getDurationMinutes();
            }
            cur = cur.plusDays(1);
        }
        return mins;
    }

    private long calculateClippedUsedMinutes(
            Utilization u, Map<Integer, OpenWindow> hours, LocalDate start, LocalDate end, Instant now) {
        if (hours == null || hours.isEmpty()) return 0;
        Instant s = u.getCheckInTime();
        Instant e = u.getCheckOutTime() != null ? u.getCheckOutTime() : now;
        if (e.isBefore(s)) return 0;

        long clippedMins = 0;
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            int d = cur.getDayOfWeek().getValue();
            OpenWindow win = hours.get(d);
            if (win != null) {
                Instant openInstant = cur.atTime(win.openTime).atZone(PLATFORM_ZONE).toInstant();
                Instant closeInstant = cur.atTime(win.closeTime).atZone(PLATFORM_ZONE).toInstant();

                Instant overlapStart = s.isAfter(openInstant) ? s : openInstant;
                Instant overlapEnd = e.isBefore(closeInstant) ? e : closeInstant;

                if (overlapEnd.isAfter(overlapStart)) {
                    clippedMins += Duration.between(overlapStart, overlapEnd).toMinutes();
                }
            }
            cur = cur.plusDays(1);
        }
        return clippedMins;
    }

    private long calculateClippedBookingMinutes(
            Booking b, Map<Integer, OpenWindow> hours, LocalDate start, LocalDate end) {
        if (hours == null || hours.isEmpty()) return 0;
        Instant s = b.getStartTime();
        Instant e = b.getEndTime();
        if (s == null || e == null || e.isBefore(s)) return 0;

        long clippedMins = 0;
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            int d = cur.getDayOfWeek().getValue();
            OpenWindow win = hours.get(d);
            if (win != null) {
                Instant openInstant = cur.atTime(win.openTime).atZone(PLATFORM_ZONE).toInstant();
                Instant closeInstant = cur.atTime(win.closeTime).atZone(PLATFORM_ZONE).toInstant();

                Instant overlapStart = s.isAfter(openInstant) ? s : openInstant;
                Instant overlapEnd = e.isBefore(closeInstant) ? e : closeInstant;

                if (overlapEnd.isAfter(overlapStart)) {
                    clippedMins += Duration.between(overlapStart, overlapEnd).toMinutes();
                }
            }
            cur = cur.plusDays(1);
        }
        return clippedMins;
    }

    private boolean isSharedUsage(Utilization u) {
        if (u.getBooking() != null) {
            // Check if user is from different institution or college
            if (u.getUser() != null && u.getEquipment() != null) {
                Long userCol = u.getUser().getCollegeId() != null ? u.getUser().getCollegeId() : u.getUser().getInstitutionId();
                Long eqCol = u.getEquipment().getCollegeId() != null ? u.getEquipment().getCollegeId() : u.getEquipment().getInstitutionId();
                if (userCol != null && eqCol != null && !userCol.equals(eqCol)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Double resolveTargetPercent(String scope, Long scopeId) {
        if ("DEPARTMENT".equalsIgnoreCase(scope) && scopeId != null) {
            return departmentRepository.findById(scopeId)
                    .map(Department::getUtilizationTargetPercent)
                    .orElse(DEFAULT_TARGET_PERCENT);
        }
        return DEFAULT_TARGET_PERCENT;
    }

    private String resolveScopeName(String scope, Long scopeId, List<Equipment> equipments) {
        if ("EQUIPMENT".equalsIgnoreCase(scope)) {
            if (!equipments.isEmpty()) {
                return equipments.get(0).getName();
            }
            return scopeId != null ? "Equipment #" + scopeId : "All Equipment";
        } else if ("DEPARTMENT".equalsIgnoreCase(scope)) {
            if (scopeId != null) {
                return departmentRepository.findById(scopeId).map(Department::getName).orElse("Department #" + scopeId);
            }
            return "All Departments (" + equipments.size() + " items)";
        } else if ("INSTITUTION".equalsIgnoreCase(scope) || "COLLEGE".equalsIgnoreCase(scope)) {
            if (scopeId != null) {
                return collegeRepository.findById(scopeId).map(College::getName).orElse("Institution #" + scopeId);
            }
            return "All Institutions (" + equipments.size() + " items)";
        }
        return scope + " (" + equipments.size() + " items)";
    }

    private UtilizationAnalyticsSummaryDto buildEmptySummary(String scope, Long scopeId, String period, DateRange range) {
        return UtilizationAnalyticsSummaryDto.builder()
                .scope(scope)
                .scopeId(scopeId)
                .scopeName(scope + " (Empty)")
                .period(period)
                .startDate(range.startDate)
                .endDate(range.endDate)
                .platformTimeZone(PLATFORM_ZONE.getId())
                .totalAvailableMinutes(0L)
                .totalAvailableHours(0.0)
                .totalUsedMinutes(0L)
                .totalUsedHours(0.0)
                .utilizationRate(null)
                .sharedUsageMinutes(0L)
                .sharedUsagePercent(0.0)
                .targetUtilizationPercent(DEFAULT_TARGET_PERCENT)
                .targetGap(null)
                .targetStatus("NOT_SET")
                .equipmentCount(0)
                .build();
    }

    public DateRange resolveDateRange(String period, LocalDate start, LocalDate end) {
        LocalDate today = LocalDate.now(PLATFORM_ZONE);

        if ("TODAY".equalsIgnoreCase(period)) {
            return new DateRange(today, today);
        } else if ("LAST_7_DAYS".equalsIgnoreCase(period)) {
            return new DateRange(today.minusDays(6), today);
        } else if ("LAST_30_DAYS".equalsIgnoreCase(period)) {
            return new DateRange(today.minusDays(29), today);
        } else if ("CUSTOM".equalsIgnoreCase(period) || (start != null && end != null)) {
            if (start == null || end == null) {
                throw new IllegalArgumentException("Start date and end date are both required for custom period");
            }
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("End date cannot be before start date");
            }
            long days = ChronoUnit.DAYS.between(start, end) + 1;
            if (days > 90) {
                throw new IllegalArgumentException("Date range exceeds the maximum limit of 90 days. Selected: " + days + " days");
            }
            return new DateRange(start, end);
        }
        // Default to last 7 days
        return new DateRange(today.minusDays(6), today);
    }

    private Double roundHours(long minutes) {
        return Math.round((minutes / 60.0) * 10.0) / 10.0;
    }

    public static class DateRange {
        public final LocalDate startDate;
        public final LocalDate endDate;

        public DateRange(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    public static class OpenWindow {
        public final LocalTime openTime;
        public final LocalTime closeTime;

        public OpenWindow(LocalTime openTime, LocalTime closeTime) {
            this.openTime = openTime;
            this.closeTime = closeTime;
        }

        public long getDurationMinutes() {
            return Duration.between(openTime, closeTime).toMinutes();
        }

        public long getOpenMinutesForHour(int hour) {
            LocalTime hourStart = LocalTime.of(hour, 0);
            LocalTime hourEnd = (hour == 23) ? LocalTime.of(23, 59, 59) : LocalTime.of(hour + 1, 0);

            if (openTime.isAfter(hourEnd) || closeTime.isBefore(hourStart) || closeTime.equals(hourStart)) {
                return 0;
            }
            LocalTime winS = openTime.isAfter(hourStart) ? openTime : hourStart;
            LocalTime winE = closeTime.isBefore(hourEnd) ? closeTime : hourEnd;
            return Math.max(0, Duration.between(winS, winE).toMinutes());
        }
    }
}
