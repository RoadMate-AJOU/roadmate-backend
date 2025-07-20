package ajou.roadmate.gpt.controller;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.UserErrorCode;
import ajou.roadmate.global.utils.UserContext;
import ajou.roadmate.gpt.dto.NlpRequestDto;
import ajou.roadmate.gpt.dto.NlpResponseDto;
import ajou.roadmate.gpt.service.NlpOrchestrationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/nlp")
@RequiredArgsConstructor
public class NlpController {

    private final NlpOrchestrationService orchestrationService;
    private final UserContext userContext;

    @PostMapping("/chat")
    public ResponseEntity<NlpResponseDto> handleChat(HttpServletRequest request, @RequestBody NlpRequestDto requestDto) {

        String userId = userContext.resolveUserId(request);
        if(userId==null){
            throw new CustomException(UserErrorCode.SESSION_NOT_FOUND);
        }

        NlpResponseDto response = orchestrationService.orchestrate(requestDto, userId);
        return ResponseEntity.ok(response);
    }

}