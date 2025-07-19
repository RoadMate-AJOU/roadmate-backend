package ajou.roadmate.route.controller;

import ajou.roadmate.global.utils.UserContext;
import ajou.roadmate.route.dto.RouteRequest;
import ajou.roadmate.route.dto.RouteResponse;
import ajou.roadmate.route.service.TmapRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "경로 탐색", description = "T맵 기반 경로 탐색 API")
public class RouteController {

    private final TmapRouteService tmapRouteService;
    private final UserContext userContext;

    @Operation(
            summary = "경로 탐색",
            description = "출발지와 목적지 좌표를 기반으로 최적의 경로를 탐색합니다. T맵 API를 활용하여 상세한 경로 정보와 길안내를 제공합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "경로 탐색 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RouteResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 - 필수 파라미터 누락 또는 잘못된 좌표 정보"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "경로를 찾을 수 없음"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류 또는 T맵 API 오류"
            )
    })
    @PostMapping("/search")
    public ResponseEntity<RouteResponse> searchRoute(
            @Parameter(description = "경로 탐색 요청 정보", required = true)
            @Valid @RequestBody RouteRequest request,
            HttpServletRequest httpRequest) {

        String resolvedUserId = userContext.resolveUserId(httpRequest);

        log.info("경로 탐색 요청 - 사용자: {}, 출발지: {} ({}, {}), 목적지: {} ({}, {})",
                resolvedUserId, request.getStartName(), request.getStartLat(), request.getStartLon(),
                request.getEndName(), request.getEndLat(), request.getEndLon());

        RouteResponse response = tmapRouteService.searchRoute(request, resolvedUserId);

        log.info("경로 탐색 완료 - 총 거리: {}m, 총 시간: {}초, 길안내 수: {}",
                response.getTotalDistance(), response.getTotalTime(),
                response.getGuides() != null ? response.getGuides().size() : 0);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Health Check", description = "경로 탐색 서비스 상태 확인")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Route service is healthy");
    }
}