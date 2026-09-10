package com.auknowlog.backend.source.controller;

import com.auknowlog.backend.source.dto.SourceCreateRequest;
import com.auknowlog.backend.source.dto.SourceCreateResponse;
import com.auknowlog.backend.source.dto.SourcePreviewResponse;
import com.auknowlog.backend.source.dto.TextSourcePreviewRequest;
import com.auknowlog.backend.source.dto.SourceSummaryResponse;
import com.auknowlog.backend.source.dto.UrlSourcePreviewRequest;
import com.auknowlog.backend.source.service.SourcePreviewService;
import com.auknowlog.backend.source.service.SourceService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceService sourceService;
    private final SourcePreviewService sourcePreviewService;

    public SourceController(SourceService sourceService, SourcePreviewService sourcePreviewService) {
        this.sourceService = sourceService;
        this.sourcePreviewService = sourcePreviewService;
    }

    @PostMapping
    public SourceCreateResponse create(@Valid @RequestBody SourceCreateRequest request) {
        return sourceService.create(request);
    }

    @GetMapping
    public List<SourceSummaryResponse> sources() {
        return sourceService.getSources();
    }

    @PostMapping(value = "/previews/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SourcePreviewResponse previewFile(@RequestParam("file") MultipartFile file) {
        return sourcePreviewService.previewFile(file);
    }

    @PostMapping("/previews/url")
    public SourcePreviewResponse previewUrl(@Valid @RequestBody UrlSourcePreviewRequest request) {
        return sourcePreviewService.previewUrl(request.url());
    }

    @PostMapping("/previews/text")
    public SourcePreviewResponse previewText(@Valid @RequestBody TextSourcePreviewRequest request) {
        return sourcePreviewService.previewText(request.title(), request.content());
    }
}
