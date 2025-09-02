package com.auknowlog.backend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class GeminiPropertyOverridePostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SOURCE_NAME = "geminiFileOverride";
    private static final String FILE_NAME = "application-gemini.properties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Resource resource = new FileSystemResource(FILE_NAME);
        if (!resource.exists()) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream is = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            // If we can't read it, silently ignore to avoid breaking startup
            return;
        }

        if (!properties.isEmpty()) {
            PropertiesPropertySource pps = new PropertiesPropertySource(SOURCE_NAME, properties);
            // Add as the first source so it takes precedence over env vars and others
            environment.getPropertySources().addFirst(pps);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}


