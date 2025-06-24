package ajou.roadmate.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RoadMate API")
                        .description("음성 인식 기반 T맵 POI 검색 API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("RoadMate Team")
                                .email("roadmate@ajou.ac.kr")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버"),
                        new Server()
                                .url("https://api.roadmate.com")
                                .description("운영 서버")
                ));
    }
}