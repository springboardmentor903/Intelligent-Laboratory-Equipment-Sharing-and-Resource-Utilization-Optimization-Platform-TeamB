package com.campus.lab_resource_platform.equipment.controller;

import com.campus.lab_resource_platform.equipment.dto.CalibrationRecordRequest;
import com.campus.lab_resource_platform.equipment.entity.CalibrationRecord;
import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.repository.CalibrationRecordRepository;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.campus.lab_resource_platform.equipment.service.EquipmentService;

import java.util.List;

@RestController
@RequestMapping("/api/equipment/{equipmentId}/calibrations")
public class CalibrationRecordController {

    private final EquipmentRepository equipmentRepository;
    private final CalibrationRecordRepository calibrationRepository;
    private final EquipmentService equipmentService;
    public CalibrationRecordController(
        EquipmentRepository equipmentRepository,
        CalibrationRecordRepository calibrationRepository,
        EquipmentService equipmentService) {

    this.equipmentRepository = equipmentRepository;
    this.calibrationRepository = calibrationRepository;
    this.equipmentService = equipmentService;
}

    @GetMapping
    public List<CalibrationRecord> getCalibrationRecords(
            @PathVariable Long equipmentId) {

        findEquipment(equipmentId);

        return calibrationRepository
                .findByEquipmentIdOrderByCalibrationDateDesc(equipmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalibrationRecord createCalibrationRecord(
            @PathVariable Long equipmentId,
            @Valid @RequestBody CalibrationRecordRequest request) {

        equipmentService.authorizeEquipmentManagementFor(equipmentId);
        Equipment equipment = findEquipment(equipmentId);

        if (request.getNextCalibrationDate() != null
                && request.getNextCalibrationDate()
                .isBefore(request.getCalibrationDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Next calibration date cannot be before calibration date");
        }

        CalibrationRecord record = new CalibrationRecord();
        record.setEquipment(equipment);
        record.setCalibrationDate(request.getCalibrationDate());
        record.setNextCalibrationDate(request.getNextCalibrationDate());
        record.setResult(request.getResult());
        record.setPerformedBy(request.getPerformedBy());
        record.setNotes(request.getNotes());

        return calibrationRepository.save(record);
    }

    @GetMapping("/{recordId}")
    public CalibrationRecord getCalibrationRecord(
            @PathVariable Long equipmentId,
            @PathVariable Long recordId) {

        return calibrationRepository
                .findByIdAndEquipmentId(recordId, equipmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Calibration record not found for this equipment"));
    }

    private Equipment findEquipment(Long equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Equipment not found: " + equipmentId));
    }
}