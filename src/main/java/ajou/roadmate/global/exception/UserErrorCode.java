package ajou.roadmate.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum UserErrorCode implements ErrorCode{
    ID_GENERATE_FAIL(HttpStatus.BAD_REQUEST, "ID 생성에 실패했습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    MISSING_GUEST_ID(HttpStatus.BAD_REQUEST, "guest id를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;
}
