
package com.campus.lab_resource_platform.equipment.controller;

import com.campus.lab_resource_platform.equipment.dto.TagRequest;
import com.campus.lab_resource_platform.equipment.entity.Tag;
import com.campus.lab_resource_platform.equipment.repository.TagRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagRepository tagRepository;

    public TagController(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @GetMapping
    public List<Tag> getAllTags() {
        return tagRepository.findAll(
                org.springframework.data.domain.Sort.by("name"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tag createTag(@Valid @RequestBody TagRequest request) {

        String name = request.getName().trim();

        if (tagRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A tag with this name already exists"
            );
        }

        Tag tag = new Tag();
        tag.setName(name);
        tag.setDescription(request.getDescription());

        return tagRepository.save(tag);
    }
}
