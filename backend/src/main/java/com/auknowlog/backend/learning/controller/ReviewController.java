package com.auknowlog.backend.learning.controller;

import com.auknowlog.backend.learning.dto.ReviewAnswerRequest;
import com.auknowlog.backend.learning.dto.ReviewAnswerResponse;
import com.auknowlog.backend.learning.dto.ReviewQueueResponse;
import com.auknowlog.backend.learning.dto.ReviewRegistrationRequest;
import com.auknowlog.backend.learning.dto.ReviewRegistrationResponse;
import com.auknowlog.backend.learning.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ReviewQueueResponse queue() {
        return reviewService.getQueue();
    }

    @PostMapping
    public ReviewRegistrationResponse register(@Valid @RequestBody ReviewRegistrationRequest request) {
        return reviewService.registerAfterGrading(request);
    }

    @PostMapping("/{reviewScheduleId}/answer")
    public ReviewAnswerResponse answer(@PathVariable Long reviewScheduleId,
                                       @Valid @RequestBody ReviewAnswerRequest request) {
        return reviewService.answer(reviewScheduleId, request);
    }
}
