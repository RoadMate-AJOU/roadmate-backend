package ajou.roadmate.poi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "POI 검색 요청 데이터")
public class POISearchRequest {

    @NotBlank(message = "목적지 정보가 필요합니다")
    @Schema(description = "검색할 목적지 키워드", example = "스타벅스", required = true)
    private String destination;

    @NotNull(message = "현재 위치의 위도가 필요합니다")
    @Schema(description = "현재 위치의 위도", example = "37.2816", required = true)
    private Double currentLat;

    @NotNull(message = "현재 위치의 경도가 필요합니다")
    @Schema(description = "현재 위치의 경도", example = "127.0453", required = true)
    private Double currentLon;
}