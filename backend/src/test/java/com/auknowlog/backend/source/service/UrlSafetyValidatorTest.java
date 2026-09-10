package com.auknowlog.backend.source.service;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlSafetyValidatorTest {

    @Test
    void acceptsOnlyPublicHttpAddress() throws Exception {
        UrlSafetyValidator validator = new UrlSafetyValidator(
                host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")}
        );

        assertThat(validator.validate("https://docs.example.com/guide?q=java#section").toString())
                .isEqualTo("https://docs.example.com/guide?q=java");
    }

    @Test
    void rejectsLocalAndPrivateAddressesBeforeFetch() {
        UrlSafetyValidator literalValidator = new UrlSafetyValidator();
        UrlSafetyValidator privateDnsValidator = new UrlSafetyValidator(host -> new InetAddress[]{
                InetAddress.getByName("10.20.30.40")
        });

        assertThatThrownBy(() -> literalValidator.validate("http://localhost/admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("내부 네트워크");
        assertThatThrownBy(() -> literalValidator.validate("http://127.0.0.1/admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("내부 네트워크");
        assertThatThrownBy(() -> literalValidator.validate("http://[::1]/admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("내부 네트워크");
        assertThatThrownBy(() -> privateDnsValidator.validate("https://private.example/resource"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("내부 네트워크");
    }

    @Test
    void rejectsUnsafeSchemesCredentialsAndPorts() throws Exception {
        UrlSafetyValidator validator = new UrlSafetyValidator(
                host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")}
        );

        assertThatThrownBy(() -> validator.validate("file:///etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP");
        assertThatThrownBy(() -> validator.validate("https://user:secret@docs.example.com/page"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("인증정보");
        assertThatThrownBy(() -> validator.validate("https://docs.example.com:8443/page"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("443");
    }
}
