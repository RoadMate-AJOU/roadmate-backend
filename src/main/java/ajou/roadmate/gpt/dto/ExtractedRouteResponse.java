package ajou.roadmate.gpt.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ExtractedRouteResponse {
    private String departure;
    private String destination;

    public boolean hasCurrentLocation() {
        return departure == null;
    }

}
