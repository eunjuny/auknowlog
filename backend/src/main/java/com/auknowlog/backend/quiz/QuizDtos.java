package com.auknowlog.backend.quiz;

import java.util.List;

// 프론트엔드에서 토픽을 받기 위한 DTO
record QuizRequest(String topic) {}

// 퀴즈 응답을 위한 DTO
record QuizResponse(String quizTitle, List<Question> questions) {}

// 개별 질문을 위한 DTO
record Question(String questionText, List<String> options, String correctAnswer, String explanation) {}

// Gemini API 요청을 위한 DTO
record GeminiRequest(List<Content> contents) {}

record Content(List<Part> parts) {}

record Part(String text) {}

// Gemini API 응답을 위한 DTO
record GeminiResponse(List<Candidate> candidates) {}

record Candidate(Content content) {}
