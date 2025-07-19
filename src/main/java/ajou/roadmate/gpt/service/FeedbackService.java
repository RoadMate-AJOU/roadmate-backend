package ajou.roadmate.gpt.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.GPTErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final RedisTemplate<String, String> stringRedisTemplate;

    public void submitFeedback(String userId, String feedbackCategory) {
        Set<String> validCategories = Set.of("walk", "transfer", "totalTime", "elevator", "escalator");

        if (!validCategories.contains(feedbackCategory)) {
            throw new CustomException(GPTErrorCode.INVALID_FEEDBACK_CATEGORY);
        }

        String key = "feedback_count:" + userId;
        stringRedisTemplate.opsForHash().increment(key, feedbackCategory, 1);
    }

    public Map<String, Integer> getFeedbackCounts(String userId) {
        String key = "feedback_count:" + userId;
        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(key);

        Map<String, Integer> result = new HashMap<>();
        List<String> allCategories = List.of("walk", "transfer", "totalTime", "elevator", "escalator");

        for (String category : allCategories) {
            Object value = raw.get(category);
            int count = 0;
            if (value != null) {
                try {
                    count = Integer.parseInt(value.toString());
                } catch (NumberFormatException e) {
                    count = 0;
                }
            }
            result.put(category, count);
        }
        return result;
    }
}
