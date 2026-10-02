package com.zoner.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Loads key-value pairs from a root or backend .env file into Spring's environment if present.
 * Priority: System environment variables > .env properties > application.yml defaults.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path cwd = Path.of(System.getProperty("user.dir", "."));
        Path[] candidates = new Path[] {
            cwd.resolve(".env"),
            cwd.resolve("../.env"),
            Path.of(".env"),
            Path.of("../.env")
        };

        for (Path candidate : candidates) {
            Path normalized = candidate.normalize().toAbsolutePath();
            if (Files.exists(normalized) && Files.isRegularFile(normalized)) {
                loadEnvFile(normalized, environment);
                return;
            }
        }
    }

    private void loadEnvFile(Path path, ConfigurableEnvironment environment) {
        try {
            List<String> lines = Files.readAllLines(path);
            Map<String, Object> properties = new HashMap<>();

            for (String rawLine : lines) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separatorIndex = line.indexOf('=');
                if (separatorIndex > 0) {
                    String key = line.substring(0, separatorIndex).trim();
                    String value = line.substring(separatorIndex + 1).trim();

                    if ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }

                    properties.put(key, value);
                }
            }

            if (!properties.isEmpty()) {
                // Add right after system environment variables so .env overrides application.yml defaults
                if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                    environment.getPropertySources().addAfter(
                            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            new MapPropertySource("dotenvProperties", properties));
                } else {
                    environment.getPropertySources().addFirst(new MapPropertySource("dotenvProperties", properties));
                }
            }
        } catch (IOException ignored) {
            // If .env is unreadable, continue with standard properties
        }
    }
}
