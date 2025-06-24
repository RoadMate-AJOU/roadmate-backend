package ajou.roadmate.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(
            CustomException e, WebRequest request) {
        log.error("CustomException: {}", e.getMessage(), e);

        ErrorResponse errorResponse = e.getDetails() != null
                ? ErrorResponse.of(e.getErrorCode(), request.getDescription(false), e.getDetails())
                : ErrorResponse.of(e.getErrorCode(), request.getDescription(false));

        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException e, WebRequest request) {
        log.error("Validation error: {}", e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST)
                .error("VALIDATION_ERROR")
                .message("입력값이 올바르지 않습니다")
                .path(request.getDescription(false))
                .details(e.getBindingResult().getFieldErrors())
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleHttpClientErrorException(
            HttpClientErrorException e, WebRequest request) {
        log.error("HTTP Client Error: {}", e.getMessage());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status((HttpStatus) e.getStatusCode())
                .error("EXTERNAL_API_ERROR")
                .message("외부 API 호출 중 오류가 발생했습니다")
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.status(e.getStatusCode()).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception e, WebRequest request) {
        log.error("Unexpected error: {}", e.getMessage(), e);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .error("INTERNAL_SERVER_ERROR")
                .message("서버 내부 오류가 발생했습니다")
                .path(request.getDescription(false))
                .build();

        return ResponseEntity.internalServerError().body(errorResponse);
    }
}