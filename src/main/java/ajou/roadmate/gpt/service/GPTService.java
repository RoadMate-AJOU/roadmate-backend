package ajou.roadmate.gpt.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.GPTErrorCode;
import ajou.roadmate.gpt.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
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
        String systemPrompt = """
                사용자의 입력에서 출발지와 도착지를 추출하여 JSON 형식으로 응답해주세요.
                
                규칙:
                1. 출발지가 명시된 경우: 해당 장소를 추출
                2. "지금", "여기서", "현 위치", "현재 위치" 등의 표현이 있으면: 출발지를 null로 설정
                3. 도착지는 항상 추출
                4. 응답은 반드시 {"출발지": "값", "도착지": "값"} 형식의 JSON만 출력
                
                예시:
                - "강남역에서 서울대병원 가고 싶어" → {"출발지": "강남역", "도착지": "서울대병원"}
                - "지금 여기서 아주대학교로 가려면?" → {"출발지": null, "도착지": "아주대학교"}
                - "홍대에서 강남역까지 부탁해" → {"출발지": "홍대", "도착지": "강남역"}
                - "나 여기서 서울숲 가고 싶어" → {"출발지": null, "도착지": "서울숲"}
                """;

        List<Message> messages = List.of(
                new Message("system", systemPrompt),
                new Message("user", userInput)
        );

        return new ChatRequest(messages, model, 150, 0.1);
    }

    private ExtractedRouteResponse parseGPTResponse(GPTResponse gptResponse) {
        if (gptResponse.getChoices().isEmpty()) {
            throw new CustomException(GPTErrorCode.RESPONSE_NOT_FOUND);
        }

        log.info("llloooggg : " + gptResponse);

        String content = gptResponse.getChoices().get(0).getMessage().getContent();

        log.info("llloooggg : " + content);

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