package ajou.roadmate.gpt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatRequest {

    private List<Message> messages;
}
