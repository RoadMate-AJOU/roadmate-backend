package ajou.roadmate.route.dto;

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
@Schema(description = "경로 탐색 응답 데이터")
public class RouteResponse {

    @Schema(description = "총 거리(미터)", example = "12500", required = true)
    private Integer totalDistance;

    @Schema(description = "총 소요시간(초)", example = "1200", required = true)
    private Integer totalTime;

    @Schema(description = "요금 정보(원)", example = "3000")
    private Integer totalFare;

    @Schema(description = "택시 요금 정보(원) - 대중교통 경로에서는 null", example = "15000")
    private Integer taxiFare;

    @Schema(description = "출발지 좌표", required = true)
    private Location startLocation;

    @Schema(description = "도착지 좌표", required = true)
    private Location endLocation;

    @Schema(description = "상세 길안내 정보 (구간별 lineString 포함)", required = true)
    private List<GuideInfo> guides;

    @Schema(description = "접근성 정보")
    private AccessibilityInfo accessibilityInfo;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "위치 정보")
    public static class Location {
        @Schema(description = "위도", example = "37.2816", required = true)
        private Double lat;

        @Schema(description = "경도", example = "127.0453", required = true)
        private Double lon;

        @Schema(description = "위치명", example = "아주대학교")
        private String name;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "경로 좌표 정보")
    public static class RoutePoint {
        @Schema(description = "위도", example = "37.2816", required = true)
        private Double lat;

        @Schema(description = "경도", example = "127.0453", required = true)
        private Double lon;

        @Schema(description = "해당 구간 인덱스", example = "0")
        private Integer segmentIndex;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "길안내 정보")
    public static class GuideInfo {
        @Schema(description = "안내 문구", example = "신촌에서 지하철 탑승 → 역삼역 6번 출구", required = true)
        private String guidance;

        @Schema(description = "거리(미터)", example = "500", required = true)
        private Integer distance;

        @Schema(description = "소요시간(초)", example = "60", required = true)
        private Integer time;

        @Schema(description = "교통수단 타입", example = "WALK", allowableValues = {"WALK", "BUS", "SUBWAY", "TRAIN"})
        private String transportType;

        @Schema(description = "도로명 또는 노선명", example = "수도권2호선")
        private String routeName;

        @Schema(description = "버스 번호 (정제된)", example = "13-4")
        private String busNumber;

        @Schema(description = "버스 노선 ID", example = "11504001")
        private String busRouteId;

        @Schema(description = "노선 색상 코드", example = "009D3E")
        private String color;

        @Schema(description = "시작 위치")
        private Location startLocation;

        @Schema(description = "끝 위치")
        private Location endLocation;

        @Schema(description = "해당 구간의 경로 좌표 (linestring)")
        private String lineString;

        @Schema(description = "역 접근성 정보 (지하철/버스정류장)")
        private StationAccessibility stationAccessibility;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "역 접근성 정보")
    public static class StationAccessibility {
        @Schema(description = "역명", example = "강남역")
        private String stationName;

        @Schema(description = "엘리베이터 유무", example = "true")
        private Boolean hasElevator;

        @Schema(description = "에스컬레이터 유무", example = "true")
        private Boolean hasEscalator;

        @Schema(description = "엘리베이터 출구 번호", example = "3번,5번,7번")
        private String elevatorExits;

        @Schema(description = "에스컬레이터 출구 번호", example = "1번,2번,4번")
        private String escalatorExits;

        @Schema(description = "접근 가능한 출구 정보", example = "3번 출구 (엘리베이터), 5번 출구 (엘리베이터)")
        private String accessibleExitInfo;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "전체 경로 접근성 정보")
    public static class AccessibilityInfo {
        @Schema(description = "총 접근성 점수", example = "87.5")
        private Double totalScore;

        @Schema(description = "엘리베이터 설치 역 수", example = "3")
        private Integer elevatorStationCount;

        @Schema(description = "에스컬레이터 설치 역 수", example = "4")
        private Integer escalatorStationCount;

        @Schema(description = "전체 역 수", example = "5")
        private Integer totalStationCount;

        @Schema(description = "접근성 비율(%)", example = "80.0")
        private Double accessibilityRate;

        @Schema(description = "도보 시간(분)", example = "7")
        private Integer walkTimeMinutes;
    }
}