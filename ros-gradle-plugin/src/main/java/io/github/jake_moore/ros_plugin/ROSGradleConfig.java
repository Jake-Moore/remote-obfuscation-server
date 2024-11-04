package io.github.jake_moore.ros_plugin;

import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;

public abstract class ROSGradleConfig {
    // Properties for API URL and file locations
    public abstract Property<String> getApiUrl();
    public abstract Property<String> getConfigFilePath();

    public @NotNull String getObfuscationEndpoint() {
        String base = getApiUrl().get();
        if (!base.endsWith("/")) {
            base += "/";
        }
        return base + "api/obfuscate";
    }

    public @NotNull String getWatermarkEndpoint() {
        String base = getApiUrl().get();
        if (!base.endsWith("/")) {
            base += "/";
        }
        return base + "api/watermark";
    }

    public @NotNull String getStackTraceEndpoint() {
        String base = getApiUrl().get();
        if (!base.endsWith("/")) {
            base += "/";
        }
        return base + "api/stacktrace";
    }
}
