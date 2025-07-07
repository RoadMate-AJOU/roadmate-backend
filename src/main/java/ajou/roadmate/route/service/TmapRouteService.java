package ajou.roadmate.route.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.RouteErrorCode;
import ajou.roadmate.route.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TmapRouteService {

    @Value("${tmap.api.key}")
    private String tmapApiKey;

    @Value("${tmap.route.api.url:https://apis.openapi.sk.com/transit/routes}")
    private String tmapRouteApiUrl;

    @Qualifier("tmapRestTemplate")
    private final RestTemplate restTemplate;

    private final AccessibilityService accessibilityService;

    public RouteResponse searchRoute(RouteRequest request) {
        try {
            validateRequest(request);

            log.info("=== T맵 경로 탐색 시작 ===");
            log.info("출발지: {} ({}, {})", request.getStartName(), request.getStartLat(), request.getStartLon());
            log.info("목적지: {} ({}, {})", request.getEndName(), request.getEndLat(), request.getEndLon());

            TmapRouteResponse tmapResponse = callTmapRouteAPI(request);
            RouteResponse response = processTmapRouteResponse(tmapResponse);

            log.info("경로 탐색 완료 - 총 거리: {}m, 총 시간: {}초",
                    response.getTotalDistance(), response.getTotalTime());

            return response;

        } catch (CustomException e) {
            log.error("CustomException 발생: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("경로 탐색 중 예상치 못한 오류 발생: ", e);
            throw new CustomException(RouteErrorCode.TMAP_ROUTE_API_ERROR);
        }
    }

    private void validateRequest(RouteRequest request) {
        if (request.getStartLat() == null || request.getStartLon() == null) {
            throw new CustomException(RouteErrorCode.INVALID_START_LOCATION);
        }
        if (request.getEndLat() == null || request.getEndLon() == null) {
            throw new CustomException(RouteErrorCode.INVALID_END_LOCATION);
        }
        if (tmapApiKey == null || tmapApiKey.trim().isEmpty()) {
            log.error("T맵 API 키가 설정되지 않았습니다");
            throw new CustomException(RouteErrorCode.TMAP_ROUTE_API_ERROR);
        }
    }

    private TmapRouteResponse callTmapRouteAPI(RouteRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");
        headers.set("appKey", tmapApiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("startX", String.valueOf(request.getStartLon()));
        requestBody.put("startY", String.valueOf(request.getStartLat()));
        requestBody.put("endX", String.valueOf(request.getEndLon()));
        requestBody.put("endY", String.valueOf(request.getEndLat()));
        requestBody.put("count", 5); // 최대 5개 경로 요청
        requestBody.put("lang", 0);
        requestBody.put("format", "json");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("T맵 경로 탐색 API 호출: {}", tmapRouteApiUrl);
            log.debug("요청 바디: {}", requestBody);

            ResponseEntity<TmapRouteResponse> response = restTemplate.exchange(
                    tmapRouteApiUrl, HttpMethod.POST, entity, TmapRouteResponse.class);

            log.info("T맵 API 응답 상태: {}", response.getStatusCode());

            if (response.getBody() == null) {
                log.warn("T맵 API 응답이 비어있습니다.");
                throw new CustomException(RouteErrorCode.ROUTE_NOT_FOUND);
            }

            // 응답 구조 확인을 위한 로깅
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String responseJson = mapper.writeValueAsString(response.getBody());
                log.info("T맵 API 응답 내용 (처음 500자): {}",
                        responseJson.length() > 500 ? responseJson.substring(0, 500) + "..." : responseJson);
            } catch (Exception e) {
                log.warn("응답 JSON 변환 실패: {}", e.getMessage());
            }

            return response.getBody();

        } catch (Exception e) {
            log.error("T맵 경로 탐색 API 호출 실패");
            log.error("Exception Type: {}", e.getClass().getSimpleName());
            log.error("Exception Message: {}", e.getMessage());
            log.error("요청 URL: {}", tmapRouteApiUrl);
            log.error("요청 바디: {}", requestBody);

            if (e instanceof org.springframework.web.client.HttpClientErrorException) {
                org.springframework.web.client.HttpClientErrorException httpError =
                        (org.springframework.web.client.HttpClientErrorException) e;
                log.error("HTTP Status Code: {}", httpError.getStatusCode());
                log.error("HTTP Response Body: {}", httpError.getResponseBodyAsString());
            }

            throw new CustomException(RouteErrorCode.TMAP_ROUTE_API_ERROR);
        }
    }

    private RouteResponse processTmapRouteResponse(TmapRouteResponse tmapResponse) {
        if (tmapResponse == null) {
            throw new CustomException(RouteErrorCode.ROUTE_NOT_FOUND);
        }

        try {
            // T맵 API 응답을 JSON으로 변환하여 실제 구조 확인
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonString = mapper.writeValueAsString(tmapResponse);

            log.info("=== T맵 API 전체 응답 ===");
            log.info(jsonString);
            log.info("=== 응답 끝 ===");

            // Map으로 변환하여 동적으로 접근
            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = mapper.readValue(jsonString, Map.class);

            if (!responseMap.containsKey("metaData")) {
                log.warn("metaData가 없습니다. 사용 가능한 키들: {}", responseMap.keySet());
                return createFallbackResponse();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> metaData = (Map<String, Object>) responseMap.get("metaData");

            if (!metaData.containsKey("plan")) {
                log.warn("plan이 없습니다. metaData 키들: {}", metaData.keySet());
                return createFallbackResponse();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> plan = (Map<String, Object>) metaData.get("plan");

            if (!plan.containsKey("itineraries")) {
                log.warn("itineraries가 없습니다. plan 키들: {}", plan.keySet());
                return createFallbackResponse();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itineraries = (List<Map<String, Object>>) plan.get("itineraries");

            if (itineraries.isEmpty()) {
                log.warn("경로가 없습니다.");
                return createFallbackResponse();
            }

            // 경로 분석 및 선택
            List<RouteCandidate> routeCandidates = analyzeRoutes(itineraries);
            Map<String, Object> bestRoute = selectBestRoute(routeCandidates);
            RouteCandidate selectedCandidate = routeCandidates.stream()
                    .filter(c -> c.getRouteData() == bestRoute)
                    .findFirst()
                    .orElse(routeCandidates.get(0));

            // 선택된 경로의 상세 정보 처리
            return buildRouteResponse(bestRoute, selectedCandidate.getAccessibilityScore());

        } catch (Exception e) {
            log.error("T맵 경로 응답 파싱 중 오류 발생: ", e);
            return createFallbackResponse();
        }
    }

    private List<RouteCandidate> analyzeRoutes(List<Map<String, Object>> itineraries) {
        log.info("=== 찾은 경로 수: {} ===", itineraries.size());
        List<RouteCandidate> routeCandidates = new ArrayList<>();

        for (int i = 0; i < itineraries.size(); i++) {
            Map<String, Object> route = itineraries.get(i);
            Integer totalTime = getIntegerValue(route, "totalTime", 0);
            Integer totalWalkTime = getIntegerValue(route, "totalWalkTime", 0);
            Integer totalDistance = getIntegerValue(route, "totalDistance", 0);
            Integer transferCount = getIntegerValue(route, "transferCount", 0);

            // 경로의 역 목록 추출
            List<String> stationNames = extractStationNames(route);

            // 접근성 점수 계산
            AccessibilityService.RouteAccessibilityScore accessibilityScore =
                    accessibilityService.calculateRouteAccessibilityScore(stationNames, totalWalkTime);

            RouteCandidate candidate = RouteCandidate.builder()
                    .routeIndex(i)
                    .routeData(route)
                    .totalTime(totalTime)
                    .totalWalkTime(totalWalkTime)
                    .totalDistance(totalDistance)
                    .transferCount(transferCount)
                    .stationNames(stationNames)
                    .accessibilityScore(accessibilityScore)
                    .build();

            routeCandidates.add(candidate);

            log.info("경로 {}: 총시간={}분, 도보시간={}분, 총거리={}m, 환승={}회",
                    i + 1, totalTime/60, totalWalkTime/60, totalDistance, transferCount);
            log.info("  접근성: 엘리베이터={}개/{}, 에스컬레이터={}개/{}, 접근성점수={:.1f}",
                    accessibilityScore.getElevatorCount(), accessibilityScore.getTotalStations(),
                    accessibilityScore.getEscalatorCount(), accessibilityScore.getTotalStations(),
                    accessibilityScore.getTotalScore());
            log.info("  경유역: {}", String.join(" → ", stationNames));
        }

        return routeCandidates;
    }

    private Map<String, Object> selectBestRoute(List<RouteCandidate> candidates) {
        log.info("=== 경로 선택 알고리즘 시작 ===");

        // 접근성 점수 기준 정렬 (높은 순)
        candidates.sort((a, b) -> Double.compare(
                b.getAccessibilityScore().getTotalScore(),
                a.getAccessibilityScore().getTotalScore()));

        // 상위 후보들 로그 출력
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            RouteCandidate candidate = candidates.get(i);
            log.info("순위 {}: 접근성점수={:.1f}, 도보시간={}분, 엘리베이터={}개",
                    i + 1,
                    candidate.getAccessibilityScore().getTotalScore(),
                    candidate.getTotalWalkTime() / 60,
                    candidate.getAccessibilityScore().getElevatorCount());
        }

        RouteCandidate selected = candidates.get(0);
        log.info("최종 선택: 접근성 점수 {:.1f}점으로 선택됨",
                selected.getAccessibilityScore().getTotalScore());

        return selected.getRouteData();
    }

    private RouteResponse buildRouteResponse(Map<String, Object> selectedRoute,
                                             AccessibilityService.RouteAccessibilityScore accessibilityScore) {

        List<RouteResponse.GuideInfo> guides = new ArrayList<>();

        // 기본 정보 추출
        Integer totalDistance = getIntegerValue(selectedRoute, "totalDistance", 0);
        Integer totalTime = getIntegerValue(selectedRoute, "totalTime", 0);
        Integer totalWalkTime = getIntegerValue(selectedRoute, "totalWalkTime", 0);
        Integer totalFare = extractTotalFare(selectedRoute);

        // 출발지/도착지 좌표 추출
        RouteResponse.Location startLocation = extractStartLocation(selectedRoute);
        RouteResponse.Location endLocation = extractEndLocation(selectedRoute);

        // 경로 구간(legs) 처리
        processRouteLegs(selectedRoute, guides);

        log.info("=== 최종 결과 ===");
        log.info("총 거리: {}m, 총 시간: {}분, 도보 시간: {}분, 요금: {}원",
                totalDistance, totalTime/60, totalWalkTime/60, totalFare);
        log.info("접근성: 엘리베이터 {}개, 에스컬레이터 {}개, 접근성 비율: {:.1f}%",
                accessibilityScore.getElevatorCount(),
                accessibilityScore.getEscalatorCount(),
                accessibilityScore.getAccessibilityRate());
        log.info("길안내 수: {}", guides.size());

        return RouteResponse.builder()
                .totalDistance(totalDistance)
                .totalTime(totalTime)
                .totalFare(totalFare)
                .taxiFare(null)
                .startLocation(startLocation)
                .endLocation(endLocation)
                .guides(guides)
                .accessibilityInfo(RouteResponse.AccessibilityInfo.builder()
                        .totalScore(accessibilityScore.getTotalScore())
                        .elevatorStationCount(accessibilityScore.getElevatorCount())
                        .escalatorStationCount(accessibilityScore.getEscalatorCount())
                        .totalStationCount(accessibilityScore.getTotalStations())
                        .accessibilityRate(accessibilityScore.getAccessibilityRate())
                        .walkTimeMinutes(accessibilityScore.getWalkTimeMinutes())
                        .build())
                .build();
    }

    private void processRouteLegs(Map<String, Object> selectedRoute, List<RouteResponse.GuideInfo> guides) {

        if (!selectedRoute.containsKey("legs")) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> legs = (List<Map<String, Object>>) selectedRoute.get("legs");

        log.info("=== 경로 구간 상세 정보 ===");
        for (int i = 0; i < legs.size(); i++) {
            Map<String, Object> leg = legs.get(i);
            String mode = (String) leg.get("mode");
            Integer sectionTime = getIntegerValue(leg, "sectionTime", 0);
            Integer distance = getIntegerValue(leg, "distance", 0);

            String startName = getLocationName(leg, "start");
            String endName = getLocationName(leg, "end");
            String route = (String) leg.get("route");
            String routeId = (String) leg.get("routeId");

            log.info("구간 {}: {} | {}→{} | {}분, {}m | 노선: {}",
                    i + 1, mode, startName, endName, sectionTime/60, distance, route);

            // 시작/끝 위치 정보 생성
            RouteResponse.Location startLocation = createLocationFromLeg(leg, "start");
            RouteResponse.Location endLocation = createLocationFromLeg(leg, "end");

            // 경로 linestring 추출
            String lineString = extractLineString(leg, guides, i, startLocation, endLocation);

            // 구간별 주요 길안내 정보 생성
            addMainGuideInfo(leg, guides, distance, sectionTime, route, routeId,
                    startLocation, endLocation, lineString, mode, startName);
        }
    }

    private String extractLineString(Map<String, Object> leg, List<RouteResponse.GuideInfo> guides,
                                     int segmentIndex, RouteResponse.Location startLocation, RouteResponse.Location endLocation) {

        String mode = (String) leg.get("mode");
        String lineString = "";

        if ("WALK".equals(mode) && leg.containsKey("steps")) {
            // 도보 구간 처리
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) leg.get("steps");

            for (Map<String, Object> step : steps) {
                String stepLinestring = (String) step.get("linestring");
                String description = (String) step.get("description");
                String streetName = (String) step.get("streetName");
                Integer stepDistance = getIntegerValue(step, "distance", 0);

                if (stepLinestring != null) {
                    lineString += stepLinestring + " ";
                }

                // 상세 도보 안내 추가
                if (description != null && !description.trim().isEmpty()) {
                    String detailedGuidance = streetName != null && !streetName.trim().isEmpty()
                            ? streetName + ": " + description : description;

                    guides.add(RouteResponse.GuideInfo.builder()
                            .guidance(detailedGuidance)
                            .distance(stepDistance)
                            .time(0)
                            .transportType("WALK")
                            .routeName(streetName)
                            .startLocation(startLocation)
                            .endLocation(endLocation)
                            .lineString(stepLinestring)
                            .build());
                }
            }
        } else if (leg.containsKey("passShape")) {
            // 대중교통 구간 처리
            @SuppressWarnings("unchecked")
            Map<String, Object> passShape = (Map<String, Object>) leg.get("passShape");
            lineString = (String) passShape.get("linestring");
        }

        return lineString != null ? lineString.trim() : "";
    }

    private void addMainGuideInfo(Map<String, Object> leg, List<RouteResponse.GuideInfo> guides,
                                  Integer distance, Integer sectionTime, String route, String routeId,
                                  RouteResponse.Location startLocation, RouteResponse.Location endLocation,
                                  String lineString, String mode, String startName) {

        String mainGuidance = createDetailedGuidanceText(leg);
        String busNumber = extractBusNumber(route);

        // 역 접근성 정보 (대중교통 구간만)
        RouteResponse.StationAccessibility stationAccessibility = null;
        if (!"WALK".equals(mode)) {
            AccessibilityService.StationAccessibility accessibility =
                    accessibilityService.getStationAccessibility(startName);

            stationAccessibility = RouteResponse.StationAccessibility.builder()
                    .stationName(startName)
                    .hasElevator(accessibility.isHasElevator())
                    .hasEscalator(accessibility.isHasEscalator())
                    .elevatorExits(accessibility.getElevatorExits())
                    .escalatorExits(accessibility.getEscalatorExits())
                    .accessibleExitInfo(accessibility.getAccessibleExitInfo())
                    .build();
        }

        if (mainGuidance != null && !mainGuidance.trim().isEmpty()) {
            guides.add(RouteResponse.GuideInfo.builder()
                    .guidance(mainGuidance)
                    .distance(distance)
                    .time(sectionTime)
                    .transportType(mode)
                    .routeName(route)
                    .busNumber(busNumber)
                    .busRouteId(routeId)
                    .startLocation(startLocation)
                    .endLocation(endLocation)
                    .lineString(lineString)
                    .stationAccessibility(stationAccessibility)
                    .build());
        }
    }

    // Helper methods
    private List<String> extractStationNames(Map<String, Object> route) {
        List<String> stationNames = new ArrayList<>();

        if (route.containsKey("legs")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> legs = (List<Map<String, Object>>) route.get("legs");

            for (Map<String, Object> leg : legs) {
                String mode = (String) leg.get("mode");

                if (!"WALK".equals(mode)) {
                    String startName = getLocationName(leg, "start");
                    String endName = getLocationName(leg, "end");

                    if (!startName.isEmpty() && !stationNames.contains(startName)) {
                        stationNames.add(startName);
                    }
                    if (!endName.isEmpty() && !stationNames.contains(endName)) {
                        stationNames.add(endName);
                    }
                }
            }
        }

        return stationNames;
    }

    private Integer extractTotalFare(Map<String, Object> selectedRoute) {
        if (selectedRoute.containsKey("fare")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fare = (Map<String, Object>) selectedRoute.get("fare");
            if (fare.containsKey("regular")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> regular = (Map<String, Object>) fare.get("regular");
                return getIntegerValue(regular, "totalFare", 0);
            }
        }
        return 0;
    }

    private RouteResponse.Location extractStartLocation(Map<String, Object> selectedRoute) {
        if (selectedRoute.containsKey("legs")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> legs = (List<Map<String, Object>>) selectedRoute.get("legs");

            if (!legs.isEmpty()) {
                Map<String, Object> firstLeg = legs.get(0);
                return createLocationFromLeg(firstLeg, "start");
            }
        }
        return RouteResponse.Location.builder().name("출발지").lat(0.0).lon(0.0).build();
    }

    private RouteResponse.Location extractEndLocation(Map<String, Object> selectedRoute) {
        if (selectedRoute.containsKey("legs")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> legs = (List<Map<String, Object>>) selectedRoute.get("legs");

            if (!legs.isEmpty()) {
                Map<String, Object> lastLeg = legs.get(legs.size() - 1);
                return createLocationFromLeg(lastLeg, "end");
            }
        }
        return RouteResponse.Location.builder().name("도착지").lat(0.0).lon(0.0).build();
    }

    private void addRoutePointsFromLinestring(List<RouteResponse.RoutePoint> routePoints, String linestring, int segmentIndex) {
        if (linestring == null || linestring.trim().isEmpty()) {
            return;
        }

        try {
            String[] coordinates = linestring.split(" ");
            for (String coord : coordinates) {
                String[] lonLat = coord.split(",");
                if (lonLat.length >= 2) {
                    Double lon = Double.parseDouble(lonLat[0]);
                    Double lat = Double.parseDouble(lonLat[1]);
                    routePoints.add(RouteResponse.RoutePoint.builder()
                            .lon(lon)
                            .lat(lat)
                            .segmentIndex(segmentIndex)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("좌표 파싱 실패: {}", linestring, e);
        }
    }

    private RouteResponse.Location createLocationFromLeg(Map<String, Object> leg, String locationType) {
        if (leg.containsKey(locationType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> location = (Map<String, Object>) leg.get(locationType);

            return RouteResponse.Location.builder()
                    .name((String) location.get("name"))
                    .lat(getDoubleValue(location, "lat", 0.0))
                    .lon(getDoubleValue(location, "lon", 0.0))
                    .build();
        }
        return RouteResponse.Location.builder().name("").lat(0.0).lon(0.0).build();
    }

    private String createDetailedGuidanceText(Map<String, Object> leg) {
        if (leg == null) return null;

        String mode = (String) leg.get("mode");
        String startName = getLocationName(leg, "start");
        String endName = getLocationName(leg, "end");
        Integer distance = getIntegerValue(leg, "distance", 0);
        Integer time = getIntegerValue(leg, "sectionTime", 0);
        String route = (String) leg.get("route");
        String routeColor = (String) leg.get("routeColor");

        if ("WALK".equals(mode)) {
            return String.format("🚶 %s에서 %s까지 도보 %dm (%d분)",
                    startName, endName, distance, time / 60);
        } else if ("BUS".equals(mode)) {
            String busInfo = route != null ? route : "버스";
            String colorInfo = routeColor != null ? " (#" + routeColor + ")" : "";
            return String.format("🚌 %s에서 %s%s 탑승 → %s (%d분, %dm)",
                    startName, busInfo, colorInfo, endName, time / 60, distance);
        } else if ("SUBWAY".equals(mode)) {
            String subwayInfo = route != null ? route : "지하철";
            String colorInfo = routeColor != null ? " (#" + routeColor + ")" : "";
            return String.format("🚇 %s에서 %s%s 탑승 → %s (%d분, %dm)",
                    startName, subwayInfo, colorInfo, endName, time / 60, distance);
        } else if ("TRAIN".equals(mode)) {
            String trainInfo = route != null ? route : "기차";
            return String.format("🚄 %s에서 %s 탑승 → %s (%d분, %dm)",
                    startName, trainInfo, endName, time / 60, distance);
        } else if ("EXPRESSBUS".equals(mode)) {
            String busInfo = route != null ? route : "고속버스";
            return String.format("🚐 %s에서 %s 탑승 → %s (%d분, %dm)",
                    startName, busInfo, endName, time / 60, distance);
        }

        return String.format("🚗 %s에서 %s까지 %s 이용 (%d분, %dm)",
                startName, endName, mode, time / 60, distance);
    }

    private String extractBusNumber(String routeString) {
        if (routeString == null || routeString.trim().isEmpty()) {
            return null;
        }

        // "간선:13-4" → "13-4"
        if (routeString.contains(":")) {
            return routeString.split(":")[1].trim();
        }

        return routeString.trim();
    }

    private String getLocationName(Map<String, Object> leg, String locationType) {
        if (leg.containsKey(locationType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> location = (Map<String, Object>) leg.get(locationType);
            return (String) location.get("name");
        }
        return "";
    }

    private RouteResponse createFallbackResponse() {
        List<RouteResponse.RoutePoint> fallbackPoints = new ArrayList<>();
        fallbackPoints.add(RouteResponse.RoutePoint.builder().lat(37.2816).lon(127.0453).segmentIndex(0).build());
        fallbackPoints.add(RouteResponse.RoutePoint.builder().lat(37.2798).lon(127.0435).segmentIndex(0).build());

        List<RouteResponse.GuideInfo> fallbackGuides = new ArrayList<>();
        fallbackGuides.add(RouteResponse.GuideInfo.builder()
                .guidance("기본 경로 정보")
                .distance(1000)
                .time(600)
                .build());

        return RouteResponse.builder()
                .totalDistance(1000)
                .totalTime(600)
                .totalFare(0)
                .taxiFare(null)
                .startLocation(RouteResponse.Location.builder().name("출발지").lat(37.2816).lon(127.0453).build())
                .endLocation(RouteResponse.Location.builder().name("도착지").lat(37.2798).lon(127.0435).build())
                .guides(fallbackGuides)
                .accessibilityInfo(RouteResponse.AccessibilityInfo.builder()
                        .totalScore(0.0)
                        .elevatorStationCount(0)
                        .escalatorStationCount(0)
                        .totalStationCount(0)
                        .accessibilityRate(0.0)
                        .walkTimeMinutes(10)
                        .build())
                .build();
    }

    private Integer getIntegerValue(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;

        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } else if (value instanceof Double) {
            return ((Double) value).intValue();
        }

        return defaultValue;
    }

    private Double getDoubleValue(Map<String, Object> map, String key, Double defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;

        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } else if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        }

        return defaultValue;
    }

    // 경로 후보 클래스
    private static class RouteCandidate {
        private int routeIndex;
        private Map<String, Object> routeData;
        private int totalTime;
        private int totalWalkTime;
        private int totalDistance;
        private int transferCount;
        private List<String> stationNames;
        private AccessibilityService.RouteAccessibilityScore accessibilityScore;

        public static RouteCandidateBuilder builder() {
            return new RouteCandidateBuilder();
        }

        // Getters
        public int getRouteIndex() { return routeIndex; }
        public Map<String, Object> getRouteData() { return routeData; }
        public int getTotalTime() { return totalTime; }
        public int getTotalWalkTime() { return totalWalkTime; }
        public int getTotalDistance() { return totalDistance; }
        public int getTransferCount() { return transferCount; }
        public List<String> getStationNames() { return stationNames; }
        public AccessibilityService.RouteAccessibilityScore getAccessibilityScore() { return accessibilityScore; }

        public static class RouteCandidateBuilder {
            private RouteCandidate candidate = new RouteCandidate();

            public RouteCandidateBuilder routeIndex(int routeIndex) {
                candidate.routeIndex = routeIndex;
                return this;
            }

            public RouteCandidateBuilder routeData(Map<String, Object> routeData) {
                candidate.routeData = routeData;
                return this;
            }

            public RouteCandidateBuilder totalTime(int totalTime) {
                candidate.totalTime = totalTime;
                return this;
            }

            public RouteCandidateBuilder totalWalkTime(int totalWalkTime) {
                candidate.totalWalkTime = totalWalkTime;
                return this;
            }

            public RouteCandidateBuilder totalDistance(int totalDistance) {
                candidate.totalDistance = totalDistance;
                return this;
            }

            public RouteCandidateBuilder transferCount(int transferCount) {
                candidate.transferCount = transferCount;
                return this;
            }

            public RouteCandidateBuilder stationNames(List<String> stationNames) {
                candidate.stationNames = stationNames;
                return this;
            }

            public RouteCandidateBuilder accessibilityScore(AccessibilityService.RouteAccessibilityScore accessibilityScore) {
                candidate.accessibilityScore = accessibilityScore;
                return this;
            }

            public RouteCandidate build() {
                return candidate;
            }
        }
    }
}