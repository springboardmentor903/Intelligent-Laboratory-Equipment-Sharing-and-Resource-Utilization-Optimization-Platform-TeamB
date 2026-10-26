package com.campus.lab_resource_platform.equipment.repository;

import com.campus.lab_resource_platform.equipment.entity.CalibrationRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CalibrationRecordRepository
        extends JpaRepository<CalibrationRecord, Long> {

    List<CalibrationRecord> findByEquipmentIdOrderByCalibrationDateDesc(
            Long equipmentId);

    Optional<CalibrationRecord> findByIdAndEquipmentId(
            Long id, Long equipmentId);
}