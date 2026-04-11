package com.example.common.llm.qwen;

import java.util.List;

public class QwenApiModels {
    public static class Message {
        public String role;
        public String content;
        public Message() {}
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
    public static class ChatRequest {
        public String model;
        public List<Message> messages;
        public Double temperature;
        public Boolean stream = Boolean.FALSE;
    }
    public static class ChatResponse {
        public List<Choice> choices;
        public Usage usage;
        public static class Choice {
            public Message message;
            public String finish_reason;
        }
        public static class Usage {
            public Integer prompt_tokens;
            public Integer completion_tokens;
            public Integer total_tokens;
        }
    }
}
