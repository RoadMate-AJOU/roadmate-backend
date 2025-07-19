package ajou.roadmate.user.dto;

import ajou.roadmate.user.domain.User;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SignUpResponse {

    private String id;
    private String name;

    public static SignUpResponse of(User user){
        return SignUpResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .build();
    }
}
