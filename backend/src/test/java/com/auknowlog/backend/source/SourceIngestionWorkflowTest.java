package com.auknowlog.backend.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:source_ingestion;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.target=2",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:h2-learning-schema.sql",
        "spring.jpa.hibernate.ddl-auto=validate",
        "auknowlog.openai.embedding.enabled=false"
})
@AutoConfigureMockMvc
class SourceIngestionWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void previewsMarkdownThenStoresMetadataAndReusesSameContent() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "kubernetes.md",
                "text/markdown",
                "# Kubernetes Service\nService는 여러 Pod에 안정적인 주소를 제공합니다.".getBytes(StandardCharsets.UTF_8)
        );

        String previewBody = mockMvc.perform(multipart("/api/sources/previews/file").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("FILE"))
                .andExpect(jsonPath("$.originalName").value("kubernetes.md"))
                .andExpect(jsonPath("$.mimeType").value("text/markdown"))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andReturn().getResponse().getContentAsString();

        JsonNode preview = objectMapper.readTree(previewBody);
        String createBody = objectMapper.createObjectNode()
                .put("title", preview.path("title").asText())
                .put("content", preview.path("content").asText())
                .put("sourceType", preview.path("sourceType").asText())
                .put("originalName", preview.path("originalName").asText())
                .put("mimeType", preview.path("mimeType").asText())
                .toString();

        String firstSave = mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceType").value("FILE"))
                .andExpect(jsonPath("$.reused").value(false))
                .andExpect(jsonPath("$.chunkCount").value(1))
                .andReturn().getResponse().getContentAsString();

        long firstId = objectMapper.readTree(firstSave).path("sourceId").asLong();
        mockMvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceId").value(firstId))
                .andExpect(jsonPath("$[0].title").value(preview.path("title").asText()))
                .andExpect(jsonPath("$[0].contentLength").value(preview.path("content").asText().length()))
                .andExpect(jsonPath("$[0].chunkCount").value(1));

        String secondSave = mockMvc.perform(post("/api/sources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reused").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(secondSave).path("sourceId").asLong()).isEqualTo(firstId);
    }

    @Test
    void rejectsLoopbackUrlWithoutFetchingIt() throws Exception {
        mockMvc.perform(post("/api/sources/previews/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://127.0.0.1:8080/actuator/env\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("내부 네트워크")));
    }
}
