package com.campus.lab_resource_platform.institution.service;

import com.campus.lab_resource_platform.institution.entity.Institution;
import com.campus.lab_resource_platform.institution.repository.InstitutionRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InstitutionService {

    private final InstitutionRepository institutionRepository;
    private final Map<Long, String> cache = new ConcurrentHashMap<>();

    public InstitutionService(InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
    }

    public String getInstitutionName(Long institutionId) {
        if (institutionId == null) {
            return "Unknown Institution";
        }
        return cache.computeIfAbsent(institutionId, id ->
                institutionRepository.findById(id)
                        .map(Institution::getName)
                        .orElse("Institution #" + id)
        );
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

