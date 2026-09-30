package com.auknowlog.backend.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotionServiceTest {

    @Test
    void missingKeyDoesNotBlockStartupButRejectsExplicitExport() {
        NotionService service = new NotionService(RestClient.builder(), new ObjectMapper(), "", "2022-06-28", "");

        assertThatThrownBy(() -> service.createPageWithMarkdown("title", "content", "page", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Notion API 키");
    }
}
