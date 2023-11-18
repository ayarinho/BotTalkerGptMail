package de.bsi.openai.chatgpt;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ChatCompletionResponse {

	@JsonProperty("system_fingerprint")
	private String system_fingerprint;

	@JsonProperty("id")
	private String id;

	@JsonProperty("object")
	private String object;

	@JsonProperty("created")
	private long created;

	@JsonProperty("model")
	private String model;

	@JsonProperty("choices")
	private Choice[] choices;

	@JsonProperty("usage")
	private Usage usage;

	public Choice[] getChoices() {
		return choices;
	}

	public static class Choice {

		@JsonProperty("index")
		private int index;

		@JsonProperty("message")
		private Message message;

		@JsonProperty("finish_reason")
		private String finishReason;

		public Message getMessage() {
			return message;
		}
	}

	public static class Message {
		@JsonProperty("role")
		private String role;

		@JsonProperty("content")
		private String content;

		public String getContent() {
			return content;
		}
	}

	public static class Usage {
		@JsonProperty("prompt_tokens")
		private int promptTokens;

		@JsonProperty("completion_tokens")
		private int completionTokens;

		@JsonProperty("total_tokens")
		private int totalTokens;
	}
}
