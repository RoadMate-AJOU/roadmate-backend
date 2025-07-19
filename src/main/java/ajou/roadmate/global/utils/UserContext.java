package ajou.roadmate.global.utils;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.UserErrorCode;
import ajou.roadmate.user.domain.User;
import ajou.roadmate.user.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserContext {

    private final AuthService authService;

    public String resolveUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && !token.isEmpty()) {
            User user = authService.getUserBySession(token);
            return user.getId();
        } else {
            String guestId = request.getHeader("X-Guest-Id");
            if (guestId == null || guestId.isEmpty()) {
                throw new CustomException(UserErrorCode.MISSING_GUEST_ID);
            }
            return guestId;
        }
    }
}
