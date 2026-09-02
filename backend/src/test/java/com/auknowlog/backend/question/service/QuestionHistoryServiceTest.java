package com.auknowlog.backend.question.service;

import com.auknowlog.backend.question.repository.QuestionHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class QuestionHistoryServiceTest {

    private QuestionHistoryService service;

    @BeforeEach
    void setUp() {
        service = new QuestionHistoryService(mock(QuestionHistoryRepository.class), new ObjectMapper());
    }

    @Test
    void treatsCaseWhitespaceAndSentencePunctuationAsTheSameQuestion() {
        String original = service.generateHash("JVM의 역할은 무엇인가요?");
        String formatted = service.generateHash("  jvm 의 역할은 무엇인가요!!!  ");

        assertThat(formatted).isEqualTo(original);
    }

    @Test
    void keepsCppAndCSharpAsDifferentTechnologyNames() {
        String cpp = service.generateHash("C++의 주요 특징은 무엇인가요?");
        String csharp = service.generateHash("C#의 주요 특징은 무엇인가요?");

        assertThat(cpp).isNotEqualTo(csharp);
    }

    @Test
    void normalizesMeaningfulSymbolsBeforeRemovingOtherPunctuation() {
        assertThat(service.generateHash("C ++의 특징"))
                .isEqualTo(service.generateHash("c++의 특징"));
        assertThat(service.generateHash("A & B의 차이"))
                .isEqualTo(service.generateHash("A and B의 차이"));
        assertThat(service.generateHash("CPU 사용률 80%"))
                .isEqualTo(service.generateHash("CPU 사용률 80 percent"));
    }
}
