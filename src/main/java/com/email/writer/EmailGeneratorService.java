package com.email.writer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class EmailGeneratorService {

    private final WebClient webClient;
    private final String apiKey;

    public EmailGeneratorService(WebClient.Builder webClientBuilder,
                                 @Value("${gemini.api.url}") String baseUrl,
                                 @Value("${gemini.api.key}") String geminiApiKey) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = geminiApiKey;
    }

    public String generateEmailReply(EmailRequest emailRequest) {
        // Build prompt
        String prompt = buildPrompt(emailRequest);

        // Prepare raw JSON body
        String requestBody = String.format("""
                {
                    "contents": [
                      {
                        "parts": [
                          {
                            "text": "%s"
                          }
                        ]
                      }
                    ]
                }
                """, prompt.replace("\"", "\\\"")); // Escape quotes if needed

        // Send request
        String response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("v1beta/models/gemini-2.5-flash:generateContent")
                        .build())
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // Extract single clean email reply
        return extractResponseContent(response);
    }

    private String extractResponseContent(String response) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode root = mapper.readTree(response);
            return root.path("candidates")
                    .get(0)
                    .path("content")
                    .path("parts")
                    .get(0)
                    .path("text")
                    .asText();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error while parsing the response from Gemini API", e);
        }
    }

    private String buildPrompt(EmailRequest emailRequest) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are a professional email writer,with no plagarism and make sure you pass through plagarism and make sure email reply looks like a human and reply should be obviously professional and well structured");
        prompt.append("Generate only the final formatted email reply using this structure:\n");
        prompt.append("Dont Add Subject in Reply mail because Subject is already there from the recieved mail");
        prompt.append("Greeting (e.g., Dear Mr./Ms. ...)\n");
        prompt.append("Introduction (state the purpose briefly)\n");
        prompt.append("Body (details or main message)\n");
        prompt.append("Conclusion (summary or call to action)\n");
        prompt.append("Closing and signature (e.g., Regards, [Your Name])\n\n");
        prompt.append("Do not include any explanations, notes, or comments. ");
        prompt.append("Write only the final email content, concise and grammatically correct.\n\n");

        // Tone handling
        if (emailRequest.getTone() != null && !emailRequest.getTone().isBlank()) {
            prompt.append("Tone of the email: ")
                    .append(emailRequest.getTone())
                    .append(".\n\n");
        } else {
            prompt.append("Use a neutral and professional tone.\n\n");
        }

        // Important: instruct the model to reply
        prompt.append("Write a reply to the following email:\n");
        prompt.append(emailRequest.getEmailContent());

        return prompt.toString();
    }


}