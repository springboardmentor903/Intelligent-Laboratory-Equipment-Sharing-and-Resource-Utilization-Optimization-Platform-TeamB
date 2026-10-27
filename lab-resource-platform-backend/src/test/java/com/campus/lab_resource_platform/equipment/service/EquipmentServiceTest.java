package com.campus.lab_resource_platform.equipment.service;

import com.campus.lab_resource_platform.equipment.entity.Equipment;
import com.campus.lab_resource_platform.equipment.entity.EquipmentStatus;
import com.campus.lab_resource_platform.equipment.repository.EquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EquipmentServiceTest {

    private EquipmentRepository equipmentRepository;
    private EquipmentService equipmentService;

    @BeforeEach
    void setUp() {
        equipmentRepository = mock(EquipmentRepository.class);
        equipmentService = new EquipmentService(equipmentRepository);
    }

    @Test
    void createEquipment_persistsTagsCorrectly() {
        Equipment equipment = Equipment.builder()
                .name("Spectrophotometer UV-Vis")
                .category("Spectroscopy")
                .tags(Set.of("Optics", "Absorption", "Analysis"))
                .status(EquipmentStatus.AVAILABLE)
                .maxBookingHours(4)
                .build();

        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(i -> i.getArgument(0));

        Equipment created = equipmentService.createEquipment(equipment);

        assertNotNull(created);
        assertEquals(3, created.getTags().size());
        assertTrue(created.getTags().contains("Optics"));
        assertTrue(created.getTags().contains("Absorption"));
        verify(equipmentRepository).save(equipment);
    }

    @Test
    void updateEquipment_updatesTagsAndFields() {
        Equipment existing = Equipment.builder()
                .id(1L)
                .name("Old Name")
                .category("Category A")
                .tags(Set.of("LegacyTag"))
                .status(EquipmentStatus.AVAILABLE)
                .maxBookingHours(2)
                .build();

        when(equipmentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(i -> i.getArgument(0));

        Equipment updatedPayload = Equipment.builder()
                .name("New Name")
                .category("Category B")
                .tags(Set.of("NewTag1", "NewTag2"))
                .status(EquipmentStatus.UNDER_MAINTENANCE)
                .maxBookingHours(6)
                .build();

        Equipment result = equipmentService.updateEquipment(1L, updatedPayload);

        assertEquals("New Name", result.getName());
        assertEquals("Category B", result.getCategory());
        assertEquals(EquipmentStatus.UNDER_MAINTENANCE, result.getStatus());
        assertEquals(6, result.getMaxBookingHours());
        assertEquals(2, result.getTags().size());
        assertTrue(result.getTags().contains("NewTag1"));
        assertTrue(result.getTags().contains("NewTag2"));
        assertFalse(result.getTags().contains("LegacyTag"));
    }

    @Test
    void updateEquipment_preservesExistingTagsWhenUpdatedTagsNull() {
        Equipment existing = Equipment.builder()
                .id(2L)
                .name("Microscope")
                .tags(Set.of("Microscopy", "Fluorescence"))
                .status(EquipmentStatus.AVAILABLE)
                .maxBookingHours(8)
                .build();

        when(equipmentRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(equipmentRepository.save(any(Equipment.class))).thenAnswer(i -> i.getArgument(0));

        Equipment updatedPayload = Equipment.builder()
                .name("Microscope Pro")
                .tags(null)
                .status(EquipmentStatus.AVAILABLE)
                .maxBookingHours(8)
                .build();

        Equipment result = equipmentService.updateEquipment(2L, updatedPayload);

        assertEquals("Microscope Pro", result.getName());
        assertNotNull(result.getTags());
        assertEquals(2, result.getTags().size());
        assertTrue(result.getTags().contains("Microscopy"));
    }

    @Test
    void getAllTags_delegatesToRepository() {
        when(equipmentRepository.findAllDistinctTags()).thenReturn(List.of("Centrifuge", "Microscopy", "Optics"));

        List<String> tags = equipmentService.getAllTags();

        assertEquals(3, tags.size());
        assertEquals(List.of("Centrifuge", "Microscopy", "Optics"), tags);
        verify(equipmentRepository).findAllDistinctTags();
    }
}
