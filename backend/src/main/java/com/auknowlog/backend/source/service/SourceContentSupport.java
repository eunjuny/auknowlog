package com.auknowlog.backend.source.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class SourceContentSupport {

    private static final int CHUNK_SIZE = 1_200;

    private final int maxContentCharacters;

    public SourceContentSupport(
            @Value("${auknowlog.source.max-content-characters:50000}") int maxContentCharacters) {
        this.maxContentCharacters = maxContentCharacters;
    }

    public String normalize(String content) {
        if (content == null) {
            throw new IllegalArgumentException("학습 자료 내용이 비어 있습니다.");
        }

        String normalized = content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u0000', ' ')
                .lines()
                .map(line -> line.replaceAll("[\\t\\x0B\\f ]+", " ").trim())
                .reduce(new StringBuilder(), (builder, line) -> {
                    if (line.isBlank()) {
                        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '\n') {
                            builder.append('\n');
                        }
                    } else {
                        if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '\n') {
                            builder.append('\n');
                        }
                        builder.append(line);
                    }
                    return builder;
                }, (left, right) -> left.append(right))
                .toString()
                .trim();

        if (normalized.isBlank()) {
            throw new IllegalArgumentException("학습 자료에서 사용할 수 있는 텍스트를 찾지 못했습니다.");
        }
        if (normalized.length() > maxContentCharacters) {
            throw new IllegalArgumentException("추출된 본문은 " + maxContentCharacters + "자 이하여야 합니다.");
        }
        return normalized;
    }

    public String sha256(String normalizedContent) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizedContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    public List<String> splitIntoChunks(String normalizedContent) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : normalizedContent.split("\\R+")) {
            String normalized = paragraph.trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + normalized.length() + 1 > CHUNK_SIZE) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (normalized.length() > CHUNK_SIZE) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                for (int start = 0; start < normalized.length(); start += CHUNK_SIZE) {
                    chunks.add(normalized.substring(start, Math.min(normalized.length(), start + CHUNK_SIZE)));
                }
                continue;
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(normalized);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks.isEmpty() ? List.of(normalizedContent) : chunks;
    }

    public int estimateChunkCount(String normalizedContent) {
        return splitIntoChunks(normalizedContent).size();
    }
}
