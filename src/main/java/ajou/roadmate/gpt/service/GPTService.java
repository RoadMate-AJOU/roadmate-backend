package ajou.roadmate.gpt.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.GPTErrorCode;
import ajou.roadmate.gpt.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GPTService {

    private final RestTemplate restTemplate;

    @Value("${gpt.model}")
    private String model;

    @Value("${gpt.api.url}")
    private String apiURL;

    public ExtractedRouteResponse extractLocation(GPTRequest request) {
        ChatRequest chatRequest = createChatRequest(request.getQuery());

        GPTResponse gptResponse = restTemplate.postForObject(
                apiURL,
                chatRequest,
                GPTResponse.class
        );

        return parseGPTResponse(gptResponse);
    }

    private ChatRequest createChatRequest(String userInput) {
        List<Message> messages = List.of(
                new Message("user", userInput)
        );

        return new ChatRequest(messages, model, 1, 0.3);
    }

    private ExtractedRouteResponse parseGPTResponse(GPTResponse gptResponse) {
        if (gptResponse.getChoices().isEmpty()) {
            throw new CustomException(GPTErrorCode.RESPONSE_NOT_FOUND);
        }

        String content = gptResponse.getChoices().get(0).getMessage().getContent();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(content);

            String departure = jsonNode.has("출발지") && !jsonNode.get("출발지").isNull()
                    ? jsonNode.get("출발지").asText() : null;
            String destination = jsonNode.has("도착지") && !jsonNode.get("도착지").isNull()
                    ? jsonNode.get("도착지").asText() : null;

            return new ExtractedRouteResponse(departure, destination);

        } catch (Exception e) {
            throw new CustomException(GPTErrorCode.ERROR_WHILE_PARSE);
        }
    }
}