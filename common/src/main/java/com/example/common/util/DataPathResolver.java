package com.example.common.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class DataPathResolver {
    public static Optional<Path> resolveDirectory(String envName, String... relativeCandidates) {
        Path env = readEnvPath(envName);
        if (env != null && Files.isDirectory(env)) {
            return Optional.of(env);
        }
        Path base = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && base != null; i++) {
            for (String rel : relativeCandidates) {
                if (rel == null || rel.isBlank()) {
                    continue;
                }
                Path candidate = base.resolve(rel);
                if (Files.isDirectory(candidate)) {
                    return Optional.of(candidate);
                }
            }
            base = base.getParent();
        }
        return Optional.empty();
    }

    public static Optional<Path> resolveFile(String envName, String... relativeCandidates) {
        Path env = readEnvPath(envName);
        if (env != null && Files.isRegularFile(env)) {
            return Optional.of(env);
        }
        Path base = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 6 && base != null; i++) {
            for (String rel : relativeCandidates) {
                if (rel == null || rel.isBlank()) {
                    continue;
                }
                Path candidate = base.resolve(rel);
                if (Files.isRegularFile(candidate)) {
                    return Optional.of(candidate);
                }
            }
            base = base.getParent();
        }
        return Optional.empty();
    }

    private static Path readEnvPath(String envName) {
        if (envName == null || envName.isBlank()) {
            return null;
        }
        String v = System.getenv(envName);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Path.of(v);
        } catch (Exception ignored) {
            return null;
        }
    }
}
