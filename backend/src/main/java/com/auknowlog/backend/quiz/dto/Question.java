package com.auknowlog.backend.quiz.dto;

import java.util.List;

public record Question(String questionText, List<String> options, String correctAnswer, String explanation) {}


