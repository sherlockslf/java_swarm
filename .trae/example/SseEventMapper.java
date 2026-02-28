package com.aim.mall.ai.util;

import org.springframework.http.codec.ServerSentEvent;

public class SseEventMapper {

    private static final String STR_BLANK = "&#32;";
    private static final String STR_NEWLINE = "&#92n";

    public static ServerSentEvent<String> text(String content) {
        if (content == null) {
            return ServerSentEvent.<String>builder().event("text").data("").build();
        }
        String replace = content.replace(" ", STR_BLANK).replace("\n", STR_NEWLINE);
        return ServerSentEvent.<String>builder().event("text").data(replace).build();
    }

    public static ServerSentEvent<String> card(String json) {
        return ServerSentEvent.<String>builder().event("card").data(json).build();
    }

    public static ServerSentEvent<String> guess(String json) {
        return ServerSentEvent.<String>builder().event("guess").data(json).build();
    }

    public static ServerSentEvent<String> heartbeat(String ts) {
        return ServerSentEvent.<String>builder().event("heartbeat").data(ts).build();
    }

    public static ServerSentEvent<String> thinking(String ts) {
        return ServerSentEvent.<String>builder().event("thinking").data(ts).build();
    }

    public static ServerSentEvent<String> phase(String ts) {
        return ServerSentEvent.<String>builder().event("phase").data(ts).build();
    }
}
