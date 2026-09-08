package com.auknowlog.backend.feedback.controller;

import com.auknowlog.backend.feedback.dto.QuestionFeedbackRequest;
import com.auknowlog.backend.feedback.dto.QuestionFeedbackResponse;
import com.auknowlog.backend.feedback.service.QuestionFeedbackService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/question-feedback")
public class QuestionFeedbackController {

    private final QuestionFeedbackService questionFeedbackService;

    public QuestionFeedbackController(QuestionFeedbackService questionFeedbackService) {
        this.questionFeedbackService = questionFeedbackService;
    }

    @PutMapping
    public QuestionFeedbackResponse save(@Valid @RequestBody QuestionFeedbackRequest request) {
        return questionFeedbackService.save(request);
    }
}
