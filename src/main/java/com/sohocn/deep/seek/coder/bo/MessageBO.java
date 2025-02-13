package com.sohocn.deep.seek.coder.bo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class MessageBO {
    @JsonProperty("id")
    private String id;

    @JsonProperty("object")
    private String object;

    @JsonProperty("created")
    private Integer created;

    @JsonProperty("model")
    private String model;

    @JsonProperty("choices")
    private List<Choices> choices;

    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    @JsonProperty("usage")
    private Usage usage;

    @Data
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    @Data
    public static class Choices {
        @JsonProperty("index")
        private Integer index;

        @JsonProperty("delta")
        private Delta delta;

        @JsonProperty("finish_reason")
        private Object finishReason;

        @JsonProperty("content_filter_results")
        private ContentFilterResults contentFilterResults;
    }

    @Data
    public static class Delta {
        @JsonProperty("content")
        private String content;

        @JsonProperty("reasoning_content")
        private String reasoningContent;

        @JsonProperty("role")
        private String role;
    }

    @Data
    public static class ContentFilterResults {
        @JsonProperty("hate")
        private Hate hate;

        @JsonProperty("self_harm")
        private SelfHarm selfHarm;

        @JsonProperty("sexual")
        private Sexual sexual;

        @JsonProperty("violence")
        private Violence violence;

    }

    @Data
    public static class Hate {
        @JsonProperty("filtered")
        private Boolean filtered;
    }

    @Data
    public static class SelfHarm {
        @JsonProperty("filtered")
        private Boolean filtered;
    }

    @Data
    public static class Sexual {
        @JsonProperty("filtered")
        private Boolean filtered;
    }

    @Data
    public static class Violence {
        @JsonProperty("filtered")
        private Boolean filtered;
    }
}
