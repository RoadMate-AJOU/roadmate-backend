package ajou.roadmate.user.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.UserErrorCode;
import ajou.roadmate.user.domain.User;
import ajou.roadmate.user.dto.SignInRequest;
import ajou.roadmate.user.dto.SignInResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private static final String SESSION_PREFIX = "session:";
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> stringRedisTemplate;
    private final RedisTemplate<String, User> userRedisTemplate;

    public SignInResponse signIn(SignInRequest request) {
        for (int i = 1; ; i++) {
            User user = userRedisTemplate.opsForValue().get("user:" + i);
            if (user == null) break;

            if (user.getUsername().equals(request.getUsername()) &&
                    user.getPassword().equals(request.getPassword())) {

                String sessionToken = UUID.randomUUID().toString();
                stringRedisTemplate.opsForValue().set(SESSION_PREFIX + sessionToken, user.getId(), SESSION_TTL);
                return SignInResponse.builder()
                        .id(user.getId())
                        .token(sessionToken)
                        .build();
            }
        }
        throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }

    public void logout(String sessionToken) {
        stringRedisTemplate.delete(SESSION_PREFIX + sessionToken);
    }

    public User getUserBySession(String sessionToken) {
        log.info("session token ${}", sessionToken);
        String userId = stringRedisTemplate.opsForValue().get(SESSION_PREFIX + sessionToken);
        if (userId == null)
            throw new CustomException(UserErrorCode.USER_NOT_FOUND);
        return userRedisTemplate.opsForValue().get("user:" + userId);
    }
}
