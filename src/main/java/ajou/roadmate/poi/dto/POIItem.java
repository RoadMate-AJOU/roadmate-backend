package ajou.roadmate.poi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "개별 장소 정보")
public class POIItem {

    @Schema(description = "장소명", example = "스타벅스 아주대점", required = true)
    private String name;

    @Schema(description = "주소", example = "경기 수원시 팔달구 우만동", required = true)
    private String address;

    @Schema(description = "위도", example = "37.27979418", required = true)
    private Double latitude;

    @Schema(description = "경도", example = "127.04346763", required = true)
    private Double longitude;

    @Schema(description = "현재 위치로부터의 거리(미터)", example = "258.07", required = true)
    private Double distance;

    @Schema(description = "카테고리", example = "생활편의 > 카페 > 커피전문점")
    private String category;

    @Schema(description = "전화번호", example = "1522-3232")
    private String tel;
}