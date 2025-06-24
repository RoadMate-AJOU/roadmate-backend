package ajou.roadmate.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean("tmapRestTemplate")
    public RestTemplate tmapRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        // T맵 API용 공통 설정
        restTemplate.getInterceptors().add((request, body, execution) -> {
            // 공통 헤더 설정 등
            request.getHeaders().add("Accept", "application/json");
            return execution.execute(request, body);
        });

        return restTemplate;
    }
}