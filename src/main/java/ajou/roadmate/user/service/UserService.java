package ajou.roadmate.user.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.UserErrorCode;
import ajou.roadmate.user.domain.User;
import ajou.roadmate.user.dto.SignUpRequest;
import ajou.roadmate.user.dto.SignUpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private static final String USER_KEY_PREFIX = "user:";
    private static final String USER_ID_SEQ_KEY = "user:id:seq";

    private final RedisTemplate<String, User> userRedisTemplate;

    @Autowired
    public UserService(RedisTemplate<String, User> userRedisTemplate) {
        this.userRedisTemplate = userRedisTemplate;
    }

    public SignUpResponse signUp(SignUpRequest request) {
        Long newId = userRedisTemplate.opsForValue().increment(USER_ID_SEQ_KEY);
        if (newId == null) {
            throw new CustomException(UserErrorCode.ID_GENERATE_FAIL);
        }

        String userId = newId.toString();
        User user = User.builder()
                .id(userId)
                .username(request.getUsername())
                .name(request.getName())
                .password(request.getPassword())
                .build();
        userRedisTemplate.opsForValue().set(USER_KEY_PREFIX + userId, user);

        return SignUpResponse.of(user);
    }

    public User getUserById(String userId) {
        return userRedisTemplate.opsForValue().get(USER_KEY_PREFIX + userId);
    }

    public void deleteUser(String userId) {
        userRedisTemplate.delete(USER_KEY_PREFIX + userId);
    }
}
