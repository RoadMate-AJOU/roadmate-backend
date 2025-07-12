package ajou.roadmate.gpt.dto;

import ajou.roadmate.route.dto.RouteResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class ChatContext {
    private String sessionId;
    private LocationInfo extractedLocations;
    private List<Message> conversationHistory;
    private RouteResponse routeResponse;

    public void reset() {
        this.extractedLocations = null;
        this.routeResponse = null;
        if (this.conversationHistory != null) {
            this.conversationHistory.clear();
        }
    }

    public ChatContext(String sessionId) {
        this.sessionId = sessionId;
        this.extractedLocations = new LocationInfo();
        this.conversationHistory = new ArrayList<>();
    }

    public void addMessage(Message message) {
        if (this.conversationHistory == null) {
            this.conversationHistory = new ArrayList<>();
        }
        this.conversationHistory.add(message);
    }

}