package ajou.roadmate.user.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignInResponse {

    private String id;
    private String token;
}
