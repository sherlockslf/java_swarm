package com.example.common.json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.NonNull;

import com.alibaba.fastjson2.JSON;

public class Fastjson2HttpMessageConverter extends AbstractHttpMessageConverter<Object> {
    public Fastjson2HttpMessageConverter() {
        super(MediaType.APPLICATION_JSON, new MediaType("application", "*+json"), MediaType.TEXT_PLAIN);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    @NonNull
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        inputMessage.getBody().transferTo(bos);
        String content = bos.toString(StandardCharsets.UTF_8);
        if (clazz == String.class) { 
            return content != null ? content : "";
        }
        if (content == null || content.isBlank()) {
            throw new HttpMessageNotReadableException("Empty JSON content", inputMessage);
        }
        try {
            Object obj = JSON.parseObject(content, clazz);
            if (obj == null) {
                throw new HttpMessageNotReadableException("Parsed JSON is null for type: " + clazz.getName(), inputMessage);
            }
            return obj;
        } catch (Exception ex) {
            throw new HttpMessageNotReadableException("JSON parse error: " + ex.getMessage(), ex, inputMessage);
        }
    }

    @Override
    protected void writeInternal(Object o, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
        String json = (o instanceof String s) ? s : JSON.toJSONString(o);
        outputMessage.getBody().write(json.getBytes(StandardCharsets.UTF_8));
    }
}
