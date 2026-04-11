package com.example.common.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.alibaba.fastjson2.JSON;

public class JsonUtils {
    public static <T> T readObject(Path path, Class<T> clazz) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return JSON.parseObject(content, clazz);
    }

    public static <T> List<T> readArray(Path path, Class<T> clazz) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        return JSON.parseArray(content, clazz);
    }

    public static String readString(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public static void writeString(Path path, Object value) throws IOException {
        String s = JSON.toJSONString(value);
        Files.createDirectories(path.getParent());
        Files.writeString(path, s, StandardCharsets.UTF_8);
    }
}
