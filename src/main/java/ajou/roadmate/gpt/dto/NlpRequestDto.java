package ajou.roadmate.gpt.dto;

import lombok.Data;

@Data
public class NlpRequestDto {
    private String sessionId;
    private String text;
}