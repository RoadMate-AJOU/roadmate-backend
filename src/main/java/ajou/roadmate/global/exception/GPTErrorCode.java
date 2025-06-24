package ajou.roadmate.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum GPTErrorCode implements ErrorCode{
    RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, "응답 결과를 찾을 수 없습니다."),
    ERROR_WHILE_PARSE(HttpStatus.BAD_REQUEST, "파싱 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
