package ajou.roadmate.route.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "경로 탐색 요청 데이터")
public class RouteRequest {

    @NotNull(message = "출발지 위도가 필요합니다")
    @Schema(description = "출발지 위도", example = "37.2816", required = true)
    private Double startLat;

    @NotNull(message = "출발지 경도가 필요합니다")
    @Schema(description = "출발지 경도", example = "127.0453", required = true)
    private Double startLon;

    @Schema(description = "출발지 명칭", example = "아주대학교")
    private String startName;

    @NotNull(message = "목적지 위도가 필요합니다")
    @Schema(description = "목적지 위도", example = "37.27979418", required = true)
    private Double endLat;

    @NotNull(message = "목적지 경도가 필요합니다")
    @Schema(description = "목적지 경도", example = "127.04346763", required = true)
    private Double endLon;

    @Schema(description = "목적지 명칭", example = "스타벅스 아주대점", required = true)
    private String endName;

    @Schema(description = "경로 탐색 옵션", example = "0", allowableValues = {"0", "1", "2"})
    private String searchOption = "0"; // 0: 최적, 1: 최단거리, 2: 고속도로우선
}