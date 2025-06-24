package ajou.roadmate.gpt.controller;

import ajou.roadmate.gpt.dto.ExtractedRouteResponse;
import ajou.roadmate.gpt.dto.GPTRequest;
import ajou.roadmate.gpt.service.GPTService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/qpt")
@RequiredArgsConstructor
public class GPTController {

    private final GPTService gptService;

    @PostMapping("/parse")
    public ResponseEntity<ExtractedRouteResponse> parseUserInput(@RequestBody GPTRequest request) {

        ExtractedRouteResponse response = gptService.extractLocation(request);
        return ResponseEntity.ok(response);
    }

}
