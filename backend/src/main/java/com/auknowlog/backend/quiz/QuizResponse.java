package com.auknowlog.backend.quiz;

import java.util.List;

public record QuizResponse(String quizTitle, List<Question> questions) {}


