package de.bsi.openai.chatgpt;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.bsi.openai.FormInputDTO;
import de.bsi.openai.OpenAiApiClient;
import de.bsi.openai.OpenAiApiClient.OpenAiService;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
public class ChatGptController {

	private ApplicationEventPublisher eventPublisher;

	@Autowired private ObjectMapper jsonMapper;
	@Autowired private OpenAiApiClient client;

	private String chatWithGpt3(String message) throws Exception {
		CompletionRequest.Message userMessage = new CompletionRequest.Message("user", message);
		List<CompletionRequest.Message> messages = List.of(userMessage);
		CompletionRequest request = new CompletionRequest("gpt-3.5-turbo", messages, 0.7);

		var postBodyJson = jsonMapper.writeValueAsString(request);
		//var responseBody = client.postToOpenAiApi(postBodyJson, OpenAiService.GPT_3);

		ObjectMapper mapper = new ObjectMapper();
		String assistantResponse = null;
		try {
			ChatCompletionResponse response = mapper.readValue("", ChatCompletionResponse.class);
			assistantResponse = response.getChoices()[0].getMessage().getContent();
			System.out.println("Assistant: " + assistantResponse);
		} catch (Exception e) {
			e.printStackTrace();

		}
		return  assistantResponse;
	}

	@PostMapping(path = "/chat")
	public ResponseEntity<String> chat(@RequestBody FormInputDTO dto) {
		try {
			String response =  chatWithGpt3(dto.prompt());
			System.out.println("response: " + response);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error in communication with OpenAI ChatGPT API.");
		}
	}

	@GetMapping(path = "/ping")
	public String ping() {
        return "Application is alive.";
		}

}
