package de.bsi.openai.chatgpt;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CompletionRequest {

	@JsonProperty("model")
	private String model;
	@JsonProperty("messages")

	private List<Message> messages;
	@JsonProperty("temperature")

	private double temperature;

	public CompletionRequest(String model, List<Message> messages, double temperature) {
		this.model = model;
		this.messages = messages;
		this.temperature = temperature;
	}

	public static class Message {
		@JsonProperty("role")
		private String role;
		@JsonProperty("content")
		private String content;

		public Message(String role, String content) {
			this.role = role;
			this.content = content;
		}

	}
}
