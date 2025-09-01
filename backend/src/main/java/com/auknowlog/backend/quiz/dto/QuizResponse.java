package com.auknowlog.backend.quiz.dto;

import java.util.List;

public record QuizResponse(String quizTitle, List<Question> questions) {}


