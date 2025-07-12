package ajou.roadmate.gpt.controller;

import ajou.roadmate.gpt.dto.ChatContext;
import ajou.roadmate.gpt.dto.LocationInfo;
import ajou.roadmate.gpt.dto.NlpRequestDto;
import ajou.roadmate.gpt.dto.NlpResponseDto;
import ajou.roadmate.gpt.service.ContextService;
import ajou.roadmate.gpt.service.NlpOrchestrationService;
import ajou.roadmate.gpt.service.OpenAiNlpService;
import ajou.roadmate.route.dto.RouteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

@RestController
@RequestMapping("/nlp")
@RequiredArgsConstructor
public class NlpController {

    private final NlpOrchestrationService orchestrationService;

    @PostMapping("/chat")
    public ResponseEntity<NlpResponseDto> handleChat(@RequestBody NlpRequestDto requestDto) {
        if (requestDto.getSessionId() == null || requestDto.getSessionId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    NlpResponseDto.builder()
                            .responseMessage("sessionId는 필수입니다.")
                            .status(NlpResponseDto.Status.ERROR)
                            .build());
        }
        NlpResponseDto response = orchestrationService.orchestrate(requestDto);
        return ResponseEntity.ok(response);
    }

}