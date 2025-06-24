package ajou.roadmate.poi.controller;

import ajou.roadmate.poi.dto.POISearchRequest;
import ajou.roadmate.poi.dto.POISearchResponse;
import ajou.roadmate.poi.service.TmapPOIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/poi")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "POI 검색", description = "T맵 기반 관심지점(POI) 검색 API")
public class POIController {

    private final TmapPOIService tmapPOIService;

    @Operation(
            summary = "POI 검색",
            description = "사용자의 현재 위치를 기준으로 주변 관심지점을 검색합니다. T맵 API를 활용하여 거리순으로 정렬된 결과를 제공합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "검색 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = POISearchResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 - 필수 파라미터 누락"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 내부 오류"
            )
    })
    @PostMapping("/search")
    public ResponseEntity<POISearchResponse> searchPOI(
            @Parameter(description = "POI 검색 요청 정보", required = true)
            @Valid @RequestBody POISearchRequest request) {

        log.info("POI 검색 요청 - 목적지: {}, 현재위치: ({}, {})",
                request.getDestination(), request.getCurrentLat(), request.getCurrentLon());

        POISearchResponse response = tmapPOIService.searchPOI(request);
        return ResponseEntity.ok(response);
    }
}