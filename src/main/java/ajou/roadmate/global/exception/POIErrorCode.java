package ajou.roadmate.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum POIErrorCode implements ErrorCode {
    INVALID_DESTINATION(HttpStatus.BAD_REQUEST, "목적지 정보가 필요합니다"),
    INVALID_LOCATION(HttpStatus.BAD_REQUEST, "현재 위치 정보가 필요합니다"),
    TMAP_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "T맵 API 호출 중 오류가 발생했습니다"),
    NO_RESULTS_FOUND(HttpStatus.NOT_FOUND, "검색 결과를 찾을 수 없습니다"),
    COORDINATE_PARSE_ERROR(HttpStatus.BAD_REQUEST, "좌표 정보 파싱 중 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;
}