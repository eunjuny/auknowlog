package com.auknowlog.backend.source.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

@Component
public class UrlSourceFetcher {

    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);

    private final UrlSafetyValidator urlSafetyValidator;
    private final int connectTimeoutMillis;
    private final int maxBytes;
    private final int requestTimeoutMillis;
    private final int maxRedirects;

    public UrlSourceFetcher(
            UrlSafetyValidator urlSafetyValidator,
            @Value("${auknowlog.source.url.connect-timeout:3s}") Duration connectTimeout,
            @Value("${auknowlog.source.url.request-timeout:8s}") Duration requestTimeout,
            @Value("${auknowlog.source.url.max-bytes:5242880}") int maxBytes,
            @Value("${auknowlog.source.url.max-redirects:3}") int maxRedirects) {
        this.urlSafetyValidator = urlSafetyValidator;
        this.connectTimeoutMillis = Math.toIntExact(connectTimeout.toMillis());
        this.maxBytes = maxBytes;
        this.requestTimeoutMillis = Math.toIntExact(requestTimeout.toMillis());
        this.maxRedirects = maxRedirects;
    }

    public FetchedSource fetch(String rawUrl) {
        URI current = urlSafetyValidator.validate(rawUrl);
        URI first = current;

        for (int redirectCount = 0; ; redirectCount++) {
            // 요청 직전에 다시 DNS를 확인해 설정 변경이나 단순한 rebinding 시도를 한 번 더 차단한다.
            current = urlSafetyValidator.validate(current.toString());
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) current.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(connectTimeoutMillis);
                connection.setReadTimeout(requestTimeoutMillis);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "AuknowlogSourcePreview/1.0");
                connection.setRequestProperty("Accept", "text/html, application/xhtml+xml, text/plain, text/markdown, application/pdf");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setUseCaches(false);

                int status = connection.getResponseCode();
                if (REDIRECT_STATUSES.contains(status)) {
                    if (redirectCount >= maxRedirects) {
                        throw new IllegalArgumentException("URL 리다이렉트 횟수가 너무 많습니다.");
                    }
                    String location = connection.getHeaderField("location");
                    if (location == null || location.isBlank()) {
                        throw new IllegalArgumentException("이동할 URL이 없는 리다이렉트 응답입니다.");
                    }
                    URI redirected = current.resolve(location);
                    if (current.getScheme().equals("https") && "http".equalsIgnoreCase(redirected.getScheme())) {
                        throw new IllegalArgumentException("HTTPS에서 HTTP로 이동하는 URL은 사용할 수 없습니다.");
                    }
                    current = urlSafetyValidator.validate(redirected.toString());
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new IllegalArgumentException("URL 원문 서버가 정상 응답하지 않았습니다. (HTTP " + status + ")");
                }

                String encoding = connection.getHeaderField("content-encoding");
                encoding = encoding == null ? "identity" : encoding;
                if (!encoding.equalsIgnoreCase("identity")) {
                    throw new IllegalArgumentException("압축된 URL 응답은 안전한 크기 검증을 위해 지원하지 않습니다.");
                }
                long declaredLength = connection.getContentLengthLong();
                if (declaredLength > maxBytes) {
                    throw new IllegalArgumentException("URL 원문은 최대 " + maxBytes / 1024 / 1024 + "MB까지 가져올 수 있습니다.");
                }

                byte[] body;
                try (InputStream stream = connection.getInputStream()) {
                    body = stream.readNBytes(maxBytes + 1);
                }
                if (body.length > maxBytes) {
                    throw new IllegalArgumentException("URL 원문은 최대 " + maxBytes / 1024 / 1024 + "MB까지 가져올 수 있습니다.");
                }
                if (body.length == 0) {
                    throw new IllegalArgumentException("URL에서 가져온 내용이 비어 있습니다.");
                }
                String headerContentType = connection.getContentType();
                String contentType = headerContentType == null
                        ? "application/octet-stream"
                        : headerContentType.toLowerCase(Locale.ROOT);
                return new FetchedSource(current, body, contentType, suggestedName(current, first));
            } catch (IOException exception) {
                throw new IllegalArgumentException("URL 원문을 제한 시간 안에 가져오지 못했습니다.");
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    private String suggestedName(URI finalUri, URI firstUri) {
        String path = finalUri.getPath();
        if (path != null && !path.isBlank() && !path.endsWith("/")) {
            String candidate = path.substring(path.lastIndexOf('/') + 1);
            if (!candidate.isBlank()) {
                return candidate.substring(0, Math.min(candidate.length(), 255));
            }
        }
        return firstUri.getHost();
    }
}
