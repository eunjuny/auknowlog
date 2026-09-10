package com.auknowlog.backend.source.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

@Component
public class SourceContentExtractor {

    private static final Set<String> FILE_EXTENSIONS = Set.of("txt", "md", "markdown", "pdf");
    private static final Set<String> URL_MEDIA_TYPES = Set.of(
            "text/html", "application/xhtml+xml", "text/plain", "text/markdown", "text/x-markdown", "application/pdf"
    );

    private final SourceContentSupport contentSupport;
    private final Tika tika;

    public SourceContentExtractor(
            SourceContentSupport contentSupport,
            @Value("${auknowlog.source.max-content-characters:50000}") int maxContentCharacters) {
        this.contentSupport = contentSupport;
        this.tika = new Tika();
        this.tika.setMaxStringLength(maxContentCharacters + 1);
    }

    public ExtractedSource extractFile(String originalName, byte[] bytes) {
        String safeName = safeFileName(originalName);
        String extension = extensionOf(safeName);
        if (!FILE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("TXT, Markdown(.md), PDF 파일만 업로드할 수 있습니다.");
        }

        String detected = detect(bytes, safeName);
        if (extension.equals("pdf") && !detected.equals("application/pdf")) {
            throw new IllegalArgumentException("확장자는 PDF이지만 실제 PDF 파일이 아닙니다.");
        }
        if (!extension.equals("pdf") && !detected.startsWith("text/")) {
            throw new IllegalArgumentException("텍스트 파일로 위장된 다른 형식의 파일은 업로드할 수 없습니다.");
        }

        String content = extension.equals("pdf")
                ? parseWithTika(bytes)
                : decodeUtf8Text(bytes);
        String title = withoutExtension(safeName);
        return new ExtractedSource(limitTitle(title), contentSupport.normalize(content), normalizedMime(extension, detected));
    }

    public ExtractedSource extractUrl(FetchedSource fetched) {
        String mediaType = mediaType(fetched.contentType());
        if (mediaType.equals("application/octet-stream")) {
            mediaType = detect(fetched.body(), fetched.suggestedName());
        }
        if (!URL_MEDIA_TYPES.contains(mediaType)) {
            throw new IllegalArgumentException("URL에서는 HTML, 텍스트, Markdown, PDF 문서만 가져올 수 있습니다.");
        }

        ExtractedSource extracted;
        if (mediaType.equals("text/html") || mediaType.equals("application/xhtml+xml")) {
            extracted = extractHtml(fetched.body(), fetched.finalUri());
        } else if (mediaType.equals("application/pdf")) {
            extracted = new ExtractedSource(
                    limitTitle(withoutExtension(fetched.suggestedName())),
                    contentSupport.normalize(parseWithTika(fetched.body())),
                    "application/pdf"
            );
        } else {
            extracted = new ExtractedSource(
                    limitTitle(withoutExtension(fetched.suggestedName())),
                    contentSupport.normalize(decodeUtf8Text(fetched.body())),
                    mediaType
            );
        }
        if (extracted.content().length() < 80) {
            throw new IllegalArgumentException("학습 자료로 쓰기에 본문이 너무 짧습니다. 본문이 있는 문서 URL인지 확인해주세요.");
        }
        return extracted;
    }

    private ExtractedSource extractHtml(byte[] bytes, URI uri) {
        Document document;
        try {
            document = Jsoup.parse(new ByteArrayInputStream(bytes), null, uri.toString());
        } catch (IOException exception) {
            throw new IllegalArgumentException("HTML 문서에서 본문을 추출하지 못했습니다.");
        }
        document.select("script, style, noscript, iframe, svg, canvas, nav, header, footer, aside, form").remove();
        // body는 article/main을 포함하므로 같은 후보군에서 길이를 비교하면 항상 주변 추천·광고까지 선택된다.
        // 의미가 더 명확한 기사 본문을 우선하고, 없을 때만 넓은 컨테이너로 단계적으로 폴백한다.
        Element root = largestUsefulElement(document, "article, #dic_area, [itemprop=articleBody]");
        if (root == null) {
            root = largestUsefulElement(document, "main, [role=main]");
        }
        if (root == null) {
            root = document.body();
        }

        StringBuilder content = new StringBuilder();
        root.select("h1, h2, h3, h4, h5, h6, p, li, pre, blockquote, td, th").forEach(element -> {
            String text = element.text().trim();
            if (!text.isBlank()) {
                content.append(text).append('\n');
            }
        });
        if (content.isEmpty()) {
            content.append(root.text());
        }

        String title = firstNonBlank(
                document.select("meta[property=og:title]").attr("content"),
                document.select("h1").first() == null ? null : document.select("h1").first().text(),
                document.title(),
                uri.getHost()
        );
        return new ExtractedSource(limitTitle(title), contentSupport.normalize(content.toString()), "text/html");
    }

    private Element largestUsefulElement(Document document, String selector) {
        return document.select(selector).stream()
                .filter(element -> element.text().length() >= 80)
                .max(Comparator.comparingInt(element -> element.text().length()))
                .orElse(null);
    }

    private String detect(byte[] bytes, String name) {
        return mediaType(tika.detect(bytes, name));
    }

    private String parseWithTika(byte[] bytes) {
        try {
            return tika.parseToString(new ByteArrayInputStream(bytes), new Metadata());
        } catch (IOException | TikaException exception) {
            throw new IllegalArgumentException("문서에서 텍스트를 추출하지 못했습니다.");
        }
    }

    private String decodeUtf8Text(byte[] bytes) {
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            boolean containsBinaryControl = decoded.chars()
                    .anyMatch(value -> value < 0x20 && value != '\n' && value != '\r' && value != '\t');
            if (containsBinaryControl) {
                throw new IllegalArgumentException("텍스트 파일로 위장된 바이너리 파일은 업로드할 수 없습니다.");
            }
            return decoded;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("UTF-8 텍스트가 아닌 파일은 업로드할 수 없습니다.");
        }
    }

    private String safeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("파일 이름을 확인할 수 없습니다.");
        }
        String normalized = originalName.replace('\\', '/');
        String name = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (name.isBlank() || name.length() > 255) {
            throw new IllegalArgumentException("파일 이름은 255자 이하여야 합니다.");
        }
        return name;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String withoutExtension(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String value = dot > 0 ? name.substring(0, dot) : name;
        return value == null || value.isBlank() ? "가져온 학습 자료" : value;
    }

    private String normalizedMime(String extension, String detected) {
        if (extension.equals("md") || extension.equals("markdown")) {
            return "text/markdown";
        }
        return detected;
    }

    private String mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private String limitTitle(String title) {
        String normalized = firstNonBlank(title, "가져온 학습 자료").replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(normalized.length(), 255));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "가져온 학습 자료";
    }
}
