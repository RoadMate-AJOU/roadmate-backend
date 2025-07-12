package ajou.roadmate.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
@Getter
public enum GPTErrorCode implements ErrorCode{
    RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, "응답 결과를 찾을 수 없습니다."),
    ERROR_WHILE_PARSE(HttpStatus.BAD_REQUEST, "파싱 중 오류가 발생했습니다."),
    UNKNOWN_QUERY(HttpStatus.BAD_REQUEST, "이해할 수 없는 질문입니다."),
    CONTEXT_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 세션의 컨텍스트가 존재하지 않습니다."),
    CONTEXT_DESERIALIZE_FAIL(HttpStatus.BAD_REQUEST, "컨텍스트 역직렬화 실패"),
    CONTEXT_LOOKUP_ERROR(HttpStatus.BAD_REQUEST, "컨텍스트 조회 중 오류 발생"),
    GPT_ANALYSIS_FAIL(HttpStatus.BAD_REQUEST, "GPT 분석 실패");

    private final HttpStatus status;
    private final String message;
}
