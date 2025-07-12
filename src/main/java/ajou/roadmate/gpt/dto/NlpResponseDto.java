package ajou.roadmate.gpt.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonInclude;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NlpResponseDto {
    public enum Status {
        COMPLETE,
        INCOMPLETE,
        API_REQUIRED,
        ERROR
    }

    private String sessionId;
    private String responseMessage;
    private String intent;
    private Status status;
    private Object data;
}