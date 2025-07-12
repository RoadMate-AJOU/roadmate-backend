package ajou.roadmate.gpt.service;

import ajou.roadmate.gpt.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiNlpService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gpt.api.key}")
    private String openaiApiKey;
    @Value("${gpt.api.url}")
    private String openaiApiUrl;
    @Value("${gpt.model}")
    private String model;

    public NlpAnalysisResult analyze(List<Message> conversationHistory, String newUserInput) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", getSystemPrompt()));
        messages.addAll(conversationHistory);
        messages.add(new Message("user", newUserInput));

        try {
            return callOpenAiApi(messages);
        } catch (Exception e) {
            log.error("Error calling OpenAI API", e);
            NlpAnalysisResult errorResult = new NlpAnalysisResult();
            errorResult.setIntent("error");
            errorResult.setResponseText("죄송합니다, 요청을 이해하는 데 실패했습니다.");
            return errorResult;
        }
    }

    private NlpAnalysisResult callOpenAiApi(List<Message> messages) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
                "response_format", Map.of("type", "json_object")
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String response = restTemplate.postForObject(openaiApiUrl, entity, String.class);
        log.info("OpenAI API Response: {}", response);

        Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");

        Map<String, Object> messageMap = (Map<String, Object>) choices.get(0).get("message");

        String jsonContent = (String) messageMap.get("content");

        return objectMapper.readValue(jsonContent, NlpAnalysisResult.class);
    }

    private String getSystemPrompt() {
        return """
        You are a master assistant for a navigation service in South Korea. Your primary job is to understand a user's request, classify it into a specific "intent", extract necessary "entities", and generate a preliminary "responseText". Your response MUST be a valid JSON object with three keys: "intent", "entities", and "responseText".

    Possible intents are:
    - "extract_route"
    - "research_route"
    - "real_time_bus_arrival"
    - "real_time_subway_arrival"
    - "total_route_time"
    - "section_time_by_mode"
    - "estimated_arrival_time"
    - "total_fare"
    - "total_route_distance"
    - "bus_number_info"
    - "subway_line_info"
    - "bus_station_info"
    - "subway_station_info"
    - "accessibility_info"
    - "other_inquiries"

    For "section_time" the "mode" entity must be one of "WALK", "BUS", or "SUBWAY".

    Analyze the user's text and conversation history and extract the proper intent and relevant entities.

    Example 1 (New Route):
    User: "서울역에서 강남역 가는 길 알려줘"
    Assistant: {
        "intent": "extract_route",
        "entities": {
            "origin": "서울역",
            "destination": "강남역"
        }, 
        "responseText": "서울역에서 강남역까지의 경로를 찾아볼게요."
    }
    
    Example 1.1 (New Route):
    User: "강남역 가고싶어"
    Assistant: {
        "intent": "extract_route",
        "entities": {
            "origin": null,
            "destination": "강남역"
        }, 
        "responseText": "현재 위치에서 강남역까지의 경로를 찾아볼게요."
    }

    Example 2 (Real-time info):
    User: "500번 버스 언제 와?"
    Assistant: {
        "intent": "real_time_bus_arrival", 
        "entities": {
            "bus_number": "500"
        }, 
        "responseText": "500번 버스 도착 정보를 확인해볼게요."
    }

    Example 3 (Guidance):
    User: "어떤 버스 타야 해?"
    Assistant: {
        "intent": "bus_number_info", 
        "entities": {}, 
        "responseText": "타야 할 버스를 안내드릴게요."
    }

    Example 4 (Section time by mode):
    User: "지하철은 얼마나 타?"
    Assistant: {
        "intent": "section_time_by_mode", 
        "entities": {
            "mode": "SUBWAY"
        }, 
        "responseText": "지하철 이용 시간 정보를 알려드릴게요."
    }

    Example 5 (other_query):
    User: "배고파"
    Assistant: {
        "intent": "other_inquiries", 
        "entities": {}, 
        "responseText": "배가 고프시군요. 경로와 관련된 질문을 해주세요"
    }

    Always respond in strict JSON format.
    """;
    }
}