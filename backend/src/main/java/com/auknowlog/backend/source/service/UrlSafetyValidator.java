package com.auknowlog.backend.source.service;

import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

@Component
public class UrlSafetyValidator {

    @FunctionalInterface
    interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final DnsResolver dnsResolver;

    public UrlSafetyValidator() {
        this(InetAddress::getAllByName);
    }

    UrlSafetyValidator(DnsResolver dnsResolver) {
        this.dnsResolver = dnsResolver;
    }

    public URI validate(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("가져올 URL을 입력해주세요.");
        }

        try {
            URI parsed = new URI(input.trim());
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException("HTTP 또는 HTTPS URL만 사용할 수 있습니다.");
            }
            if (parsed.getRawUserInfo() != null) {
                throw new IllegalArgumentException("사용자 인증정보가 포함된 URL은 사용할 수 없습니다.");
            }
            if (parsed.getHost() == null || parsed.getHost().isBlank()) {
                throw new IllegalArgumentException("호스트가 올바른 URL을 입력해주세요.");
            }

            String rawHost = parsed.getHost();
            boolean ipv6Literal = rawHost.indexOf(':') >= 0;
            String asciiHost = ipv6Literal
                    ? rawHost.toLowerCase(Locale.ROOT)
                    : IDN.toASCII(rawHost, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            if (asciiHost.equals("localhost") || asciiHost.endsWith(".localhost")
                    || (!ipv6Literal && !asciiHost.contains("."))) {
                throw new IllegalArgumentException("로컬 또는 내부 네트워크 주소는 사용할 수 없습니다.");
            }

            InetAddress[] addresses = dnsResolver.resolve(asciiHost);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("URL의 호스트 주소를 확인할 수 없습니다.");
            }
            for (InetAddress address : addresses) {
                if (isBlocked(address)) {
                    throw new IllegalArgumentException("로컬 또는 내부 네트워크 주소는 사용할 수 없습니다.");
                }
            }

            int port = parsed.getPort();
            if (port != -1 && !((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))) {
                throw new IllegalArgumentException("보안을 위해 HTTP 80, HTTPS 443 포트만 사용할 수 있습니다.");
            }

            return new URI(
                    scheme,
                    null,
                    asciiHost,
                    port,
                    parsed.getRawPath() == null || parsed.getRawPath().isBlank() ? "/" : parsed.getRawPath(),
                    parsed.getRawQuery(),
                    null
            ).normalize();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException("URL의 호스트 주소를 확인할 수 없습니다.");
        } catch (URISyntaxException | IllegalArgumentException exception) {
            if (exception instanceof IllegalArgumentException && exception.getMessage() != null
                    && !exception.getMessage().isBlank()) {
                throw (IllegalArgumentException) exception;
            }
            throw new IllegalArgumentException("올바른 URL을 입력해주세요.");
        }
    }

    private boolean isBlocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            return isReservedIpv4(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            byte[] bytes = address.getAddress();
            boolean uniqueLocal = (bytes[0] & 0xfe) == 0xfc;
            boolean documentation = bytes[0] == 0x20 && bytes[1] == 0x01
                    && bytes[2] == 0x0d && (bytes[3] & 0xff) == 0xb8;
            return uniqueLocal || documentation;
        }
        return true;
    }

    private boolean isReservedIpv4(byte[] bytes) {
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        int third = Byte.toUnsignedInt(bytes[2]);

        return first == 0
                || first == 10
                || first == 127
                || (first == 100 && second >= 64 && second <= 127)
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 0 && third == 0)
                || (first == 192 && second == 0 && third == 2)
                || (first == 192 && second == 168)
                || (first == 198 && (second == 18 || second == 19))
                || (first == 198 && second == 51 && third == 100)
                || (first == 203 && second == 0 && third == 113)
                || first >= 224;
    }
}
