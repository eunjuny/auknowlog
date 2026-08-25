package com.auknowlog.backend.source.controller;

import com.auknowlog.backend.source.dto.SourceCreateRequest;
import com.auknowlog.backend.source.dto.SourceCreateResponse;
import com.auknowlog.backend.source.service.SourceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping
    public SourceCreateResponse create(@Valid @RequestBody SourceCreateRequest request) {
        return sourceService.create(request);
    }
}
