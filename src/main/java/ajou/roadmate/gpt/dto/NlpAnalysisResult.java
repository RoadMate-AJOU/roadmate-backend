package ajou.roadmate.gpt.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NlpAnalysisResult {
    private String intent;
    private Map<String, String> entities;
    private String responseText;
}