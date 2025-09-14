package com.auknowlog.backend.document.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotionService {

    private final WebClient notionClient;
    private final ObjectMapper objectMapper;

    private final String defaultParentPageId;

    public NotionService(
            WebClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${auknowlog.notion.api.key}") String notionApiKey,
            @Value("${auknowlog.notion.version:2022-06-28}") String notionVersion,
            @Value("${auknowlog.notion.parent.page-id:}") String defaultParentPageId
    ) {
        this.objectMapper = objectMapper;
        this.defaultParentPageId = defaultParentPageId == null ? "" : defaultParentPageId.trim();

        this.notionClient = builder
                .baseUrl("https://api.notion.com/v1")
                .defaultHeader("Authorization", "Bearer " + notionApiKey)
                .defaultHeader("Notion-Version", notionVersion)
                .build();
    }

    public Mono<String> createPageWithMarkdown(
            String title,
            String markdown,
            String parentPageId,
            String databaseId,
            String databaseTitleProperty
    ) {
        String effectiveTitle = (title == null || title.isBlank()) ? "퀴즈 결과" : title.trim();
        String effectiveDbTitleProp = (databaseTitleProperty == null || databaseTitleProperty.isBlank()) ? "Name" : databaseTitleProperty.trim();

        Map<String, Object> body = new HashMap<>();

        Map<String, Object> parent = new HashMap<>();
        boolean useDatabase = StringUtils.hasText(databaseId);
        if (useDatabase) {
            parent.put("type", "database_id");
            parent.put("database_id", databaseId);
        } else {
            String effectiveParent = StringUtils.hasText(parentPageId) ? parentPageId : this.defaultParentPageId;
            if (!StringUtils.hasText(effectiveParent)) {
                return Mono.error(new IllegalStateException("Notion parentPageId가 설정되어 있지 않습니다. 요청 또는 설정으로 제공해주세요."));
            }
            parent.put("type", "page_id");
            parent.put("page_id", effectiveParent);
        }
        body.put("parent", parent);

        Map<String, Object> properties = new HashMap<>();
        if (useDatabase) {
            properties.put(effectiveDbTitleProp, titleProperty(effectiveTitle));
        } else {
            properties.put("title", titleProperty(effectiveTitle));
        }
        body.put("properties", properties);

        List<Map<String, Object>> children = convertMarkdownToBlocks(markdown == null ? "" : markdown);
        if (!children.isEmpty()) {
            body.put("children", children);
        }

        return notionClient.post()
                .uri("/pages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(json -> {
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        JsonNode urlNode = root.get("url");
                        if (urlNode != null && urlNode.isTextual()) {
                            return Mono.just(urlNode.asText());
                        }
                        JsonNode idNode = root.get("id");
                        if (idNode != null && idNode.isTextual()) {
                            return Mono.just(idNode.asText());
                        }
                        return Mono.just("(created, but no url in response)");
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }

    private Map<String, Object> titleProperty(String title) {
        Map<String, Object> text = new HashMap<>();
        Map<String, Object> textObj = new HashMap<>();
        textObj.put("content", title);
        text.put("text", textObj);

        Map<String, Object> titleContent = new HashMap<>();
        titleContent.put("type", "text");
        titleContent.put("text", textObj);

        List<Map<String, Object>> titleArray = new ArrayList<>();
        titleArray.add(titleContent);

        Map<String, Object> titleProp = new HashMap<>();
        titleProp.put("title", titleArray);
        return titleProp;
    }

    private List<Map<String, Object>> convertMarkdownToBlocks(String markdown) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (markdown == null) return blocks;

        String[] lines = markdown.split("\r?\n");
        for (String line : lines) {
            String trimmed = line == null ? "" : line;
            if (trimmed.startsWith("### ")) {
                blocks.add(headingBlock(3, trimmed.substring(4)));
            } else if (trimmed.startsWith("## ")) {
                blocks.add(headingBlock(2, trimmed.substring(3)));
            } else if (trimmed.startsWith("# ")) {
                blocks.add(headingBlock(1, trimmed.substring(2)));
            } else if (trimmed.startsWith("- ")) {
                blocks.add(bulletedListItemBlock(trimmed.substring(2)));
            } else if (trimmed.isBlank()) {
                blocks.add(paragraphBlock(""));
            } else {
                blocks.add(paragraphBlock(trimmed));
            }
        }
        return blocks;
    }

    private Map<String, Object> headingBlock(int level, String text) {
        String type = switch (level) {
            case 1 -> "heading_1";
            case 2 -> "heading_2";
            default -> "heading_3";
        };
        Map<String, Object> block = new HashMap<>();
        block.put("object", "block");
        block.put("type", type);
        Map<String, Object> payload = new HashMap<>();
        payload.put("rich_text", richTextArray(text));
        block.put(type, payload);
        return block;
    }

    private Map<String, Object> bulletedListItemBlock(String text) {
        Map<String, Object> block = new HashMap<>();
        block.put("object", "block");
        block.put("type", "bulleted_list_item");
        Map<String, Object> payload = new HashMap<>();
        payload.put("rich_text", richTextArray(text));
        block.put("bulleted_list_item", payload);
        return block;
    }

    private Map<String, Object> paragraphBlock(String text) {
        Map<String, Object> block = new HashMap<>();
        block.put("object", "block");
        block.put("type", "paragraph");
        Map<String, Object> payload = new HashMap<>();
        payload.put("rich_text", richTextArray(text));
        block.put("paragraph", payload);
        return block;
    }

    private List<Map<String, Object>> richTextArray(String text) {
        List<Map<String, Object>> arr = new ArrayList<>();
        if (text == null) text = "";
        // Notion single text content is limited (approx 2000 chars). Chunk defensively.
        final int limit = 1800; // use safe margin
        List<String> chunks = chunkText(text, limit);
        for (String chunk : chunks) {
            Map<String, Object> t = new HashMap<>();
            t.put("type", "text");
            Map<String, Object> textObj = new HashMap<>();
            textObj.put("content", chunk);
            t.put("text", textObj);
            arr.add(t);
        }
        if (arr.isEmpty()) {
            Map<String, Object> t = new HashMap<>();
            t.put("type", "text");
            Map<String, Object> textObj = new HashMap<>();
            textObj.put("content", "");
            t.put("text", textObj);
            arr.add(t);
        }
        return arr;
    }

    private List<String> chunkText(String text, int maxChars) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;
        if (text.length() <= maxChars) {
            list.add(text);
            return list;
        }
        // Split on UTF-8 byte-safe boundaries by chars (simple, safe for BMP).
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxChars);
            list.add(text.substring(start, end));
            start = end;
        }
        return list;
    }
}


