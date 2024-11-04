package com.kamikazejam.gradle.plugin.util;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@Data
@RequiredArgsConstructor
public class WatermarkData {
    private final @NotNull String requestId;
    private final @NotNull String requestUser;
}
