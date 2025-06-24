package ajou.roadmate.poi.dto;

import ajou.roadmate.poi.dto.POIItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "POI 검색 응답 데이터")
public class POISearchResponse {

    @Schema(description = "검색된 장소 목록", required = true)
    private List<POIItem> places;

    @Schema(description = "전체 검색 결과 수", example = "3800", required = true)
    private int totalCount;
}