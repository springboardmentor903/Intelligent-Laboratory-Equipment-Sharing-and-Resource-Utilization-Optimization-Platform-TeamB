package com.campus.lab_resource_platform.institution.service;

import com.campus.lab_resource_platform.college.entity.College;
import com.campus.lab_resource_platform.college.repository.CollegeRepository;
import com.campus.lab_resource_platform.institution.entity.Institution;
import com.campus.lab_resource_platform.institution.repository.InstitutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InstitutionService {

    private final InstitutionRepository institutionRepository;
    private final CollegeRepository collegeRepository;
    private final Map<Long, String> cache = new ConcurrentHashMap<>();

    @Autowired
    public InstitutionService(InstitutionRepository institutionRepository,
                              @Autowired(required = false) CollegeRepository collegeRepository) {
        this.institutionRepository = institutionRepository;
        this.collegeRepository = collegeRepository;
    }

    public String getInstitutionName(Long institutionId) {
        if (institutionId == null) {
            return "Unknown Institution";
        }
        return cache.computeIfAbsent(institutionId, id -> {
            Optional<String> instName = institutionRepository.findById(id).map(Institution::getName);
            if (instName.isPresent()) {
                return instName.get();
            }
            if (collegeRepository != null) {
                Optional<String> collegeName = collegeRepository.findById(id).map(College::getName);
                if (collegeName.isPresent()) {
                    return collegeName.get();
                }
            }
            return "Institution #" + id;
        });
    }

    public Institution createOrGet(Long id, String name, String code) {
        if (id != null && institutionRepository.existsById(id)) {
            return institutionRepository.findById(id).get();
        }
        Institution inst = new Institution();
        inst.setId(id);
        inst.setName(name != null ? name : "Institution #" + id);
        inst.setCode(code);
        return institutionRepository.save(inst);
    }
}
