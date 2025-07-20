package ajou.roadmate.gpt.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.GPTErrorCode;
import ajou.roadmate.gpt.dto.ChatContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Service;

import java.time.Duration;

@RequiredArgsConstructor
@Service
@Slf4j
public class ContextService {

    private final RedisTemplate<String, ChatContext> chatContextRedisTemplate;

    private static final String CONTEXT_PREFIX = "nlp_context:";
    private static final Duration CONTEXT_TTL = Duration.ofMinutes(360);

    public ChatContext getContext(String sessionId) {
        try {
            ChatContext context = chatContextRedisTemplate.opsForValue().get(CONTEXT_PREFIX + sessionId);

            if (context == null) {
                throw new CustomException(GPTErrorCode.CONTEXT_NOT_FOUND);
            }

            return context;

        } catch (SerializationException e) {
            log.error("ChatContext 역직렬화 실패 - 세션: {}", sessionId, e);
            deleteContext(sessionId);
            throw new CustomException(GPTErrorCode.CONTEXT_DESERIALIZE_FAIL);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("ChatContext 조회 중 오류 - 세션: {}", sessionId, e);
            throw new CustomException(GPTErrorCode.CONTEXT_LOOKUP_ERROR);
        }
    }

    public void saveContext(ChatContext context) {
        try {
            chatContextRedisTemplate.opsForValue().set(CONTEXT_PREFIX + context.getSessionId(), context, CONTEXT_TTL);
            log.debug("컨텍스트 저장 완료: sessionId={}", context.getSessionId());
        } catch (Exception e) {
            log.error("컨텍스트 저장 실패: sessionId={}", context.getSessionId(), e);
            throw new RuntimeException("컨텍스트 저장 중 오류가 발생했습니다.", e);
        }
    }

    public void deleteContext(String sessionId) {
        try {
            Boolean deleted = chatContextRedisTemplate.delete(CONTEXT_PREFIX + sessionId);
            log.debug("컨텍스트 삭제: sessionId={}, deleted={}", sessionId, deleted);
        } catch (Exception e) {
            log.error("컨텍스트 삭제 실패: sessionId={}", sessionId, e);
        }
    }
}
