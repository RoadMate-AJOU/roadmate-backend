package ajou.roadmate.gpt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class GPTResponse {

    private List<Choice> choices;

    @Getter
    @AllArgsConstructor
    public static class Choice{
        private int index;
        private Message message;
    }
}
