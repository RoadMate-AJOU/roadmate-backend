package ajou.roadmate.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum RouteErrorCode implements ErrorCode {
    INVALID_START_LOCATION(HttpStatus.BAD_REQUEST, "출발지 정보가 올바르지 않습니다"),
    INVALID_END_LOCATION(HttpStatus.BAD_REQUEST, "목적지 정보가 올바르지 않습니다"),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "경로를 찾을 수 없습니다"),
    TMAP_ROUTE_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "T맵 경로 탐색 API 호출 중 오류가 발생했습니다"),
    ROUTE_PARSE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "경로 데이터 파싱 중 오류가 발생했습니다");

    private final HttpStatus status;
    private final String message;
}