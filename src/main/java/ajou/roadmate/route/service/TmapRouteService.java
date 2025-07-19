package ajou.roadmate.route.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.RouteErrorCode;
import ajou.roadmate.gpt.dto.ChatContext;
import ajou.roadmate.gpt.service.ContextService;
import ajou.roadmate.gpt.service.FeedbackService;
import ajou.roadmate.route.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ContextService contextService;

    // FeedbackService를 Optional로 처리
    @Autowired(required = false)
    private FeedbackService feedbackService;

    public RouteResponse searchRoute(RouteRequest request, String userId) {
        try {
            validateRequest(request);

            log.info("=== T맵 경로 탐색 시작 ===");
            log.info("출발지: {} ({}, {})", request.getStartName(), request.getStartLat(), request.getStartLon());
            log.info("목적지: {} ({}, {})", request.getEndName(), request.getEndLat(), request.getEndLon());

            TmapRouteResponse tmapResponse = callTmapRouteAPI(request);
            RouteResponse response = processTmapRouteResponse(tmapResponse, request.getSessionId(), userId);

            log.info("경로 탐색 완료 - 총 거리: {}m, 총 시간: {}초",
                    response.getTotalDistance(), response.getTotalTime());

            // ChatContext 처리를 Optional로 변경
            try {
                ChatContext context;
                try {
                    context = contextService.getContext(request.getSessionId());
                } catch (CustomException e) {
                    // 컨텍스트가 없으면 새로 생성
                    context = new ChatContext();
                    context.setSessionId(request.getSessionId());
                }

                context.setRouteResponse(response);
                contextService.saveContext(context);
            } catch (Exception e) {
                log.warn("컨텍스트 저장 실패 (무시하고 계속 진행): {}", e.getMessage());
            }

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
        requestBody.put("searchOption", request.getSearchOption() != null ? request.getSearchOption() : "0");

        // 교통수단 포함 설정 (지하철 우선)
        requestBody.put("subwayBusCount", 5); // 지하철+버스 조합 경로
        requestBody.put("subwayCount", 3);    // 지하철 전용 경로
        requestBody.put("busCount", 2);       // 버스 전용 경로

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<TmapRouteResponse> response = restTemplate.exchange(
                    tmapRouteApiUrl, HttpMethod.POST, entity, TmapRouteResponse.class);

            if (response.getBody() == null) {
                log.warn("T맵 API 응답이 비어있습니다.");
                throw new CustomException(RouteErrorCode.ROUTE_NOT_FOUND);
            }

            return response.getBody();

        } catch (Exception e) {
            log.error("T맵 경로 탐색 API 호출 실패: {}", e.getMessage());
            throw new CustomException(RouteErrorCode.TMAP_ROUTE_API_ERROR);
        }
    }

    private RouteResponse processTmapRouteResponse(TmapRouteResponse tmapResponse, String sessionId, String userId) {
        if (tmapResponse == null) {
            throw new CustomException(RouteErrorCode.ROUTE_NOT_FOUND);
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String jsonString = mapper.writeValueAsString(tmapResponse);

            @SuppressWarnings("unchecked")
            Map<String, Object> responseMap = mapper.readValue(jsonString, Map.class);

            if (!responseMap.containsKey("metaData")) {
                return createFallbackResponse();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> metaData = (Map<String, Object>) responseMap.get("metaData");

            if (!metaData.containsKey("plan")) {
                return createFallbackResponse();
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> plan = (Map<String, Object>) metaData.get("plan");

            if (!plan.containsKey("itineraries")) {
                return createFallbackResponse();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> itineraries = (List<Map<String, Object>>) plan.get("itineraries");

            if (itineraries.isEmpty()) {
                return createFallbackResponse();
            }

            List<RouteCandidate> routeCandidates = analyzeRoutes(itineraries);
            Map<String, Object> bestRoute = selectBestRoute(routeCandidates, userId);
            RouteCandidate selectedCandidate = routeCandidates.stream()
                    .filter(c -> c.getRouteData() == bestRoute)
                    .findFirst()
                    .orElse(routeCandidates.get(0));

            return buildRouteResponse(bestRoute, selectedCandidate.getAccessibilityScore());

        } catch (Exception e) {
            log.error("T맵 경로 응답 파싱 중 오류 발생: ", e);
            return createFallbackResponse();
        }
    }

    private List<RouteCandidate> analyzeRoutes(List<Map<String, Object>> itineraries) {
        List<RouteCandidate> routeCandidates = new ArrayList<>();

        for (int i = 0; i < itineraries.size(); i++) {
            Map<String, Object> route = itineraries.get(i);
            Integer totalTime = getIntegerValue(route, "totalTime", 0);
            Integer totalWalkTime = getIntegerValue(route, "totalWalkTime", 0);
            Integer totalDistance = getIntegerValue(route, "totalDistance", 0);
            Integer transferCount = getIntegerValue(route, "transferCount", 0);

            List<String> stationNames = extractStationNames(route);

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
        }

        return routeCandidates;
    }

    private Map<String, Object> selectBestRoute(List<RouteCandidate> candidates, String userId) {
        Map<String, Integer> feedbackCounts = new HashMap<>();

        if (feedbackService != null) {
            try {
                feedbackCounts = feedbackService.getFeedbackCounts(userId);
            } catch (Exception e) {
                log.warn("FeedbackService 호출 실패, 기본값 사용: {}", e.getMessage());
            }
        }

        int walkWeight = feedbackCounts.getOrDefault("walk", 2);
        int transferWeight = feedbackCounts.getOrDefault("transfer", 0);
        int totalTimeWeight = feedbackCounts.getOrDefault("totalTime", 3);
        int elevatorWeight = feedbackCounts.getOrDefault("elevator", 2);
        int escalatorWeight = feedbackCounts.getOrDefault("escalator", 2);

        for (RouteCandidate candidate : candidates) {
            double score = calculateRouteScore(candidate, walkWeight, transferWeight, totalTimeWeight, elevatorWeight, escalatorWeight);
            candidate.setWeightedScore(score);
        }

        candidates.sort((a, b) -> Double.compare(a.getWeightedScore(), b.getWeightedScore()));

        RouteCandidate selected = candidates.get(0);

        return selected.getRouteData();
    }

    private double calculateRouteScore(RouteCandidate candidate, int walkWeight, int transferWeight,
                                       int totalTimeWeight, int elevatorWeight, int escalatorWeight) {
        double maxWalkTime = 1800.0;
        double maxTotalTime = 7200.0;
        double maxTransferCount = 5.0;

        double normalizedWalkTime = candidate.getTotalWalkTime() / maxWalkTime;
        double walkScore = normalizedWalkTime * walkWeight;

        double normalizedTransferCount = candidate.getTransferCount() / maxTransferCount;
        double transferScore = normalizedTransferCount * transferWeight;

        double normalizedTotalTime = candidate.getTotalTime() / maxTotalTime;
        double timeScore = normalizedTotalTime * totalTimeWeight;

        double elevatorRatio = 0.0;
        if (candidate.getAccessibilityScore().getTotalStations() > 0) {
            elevatorRatio = (double) candidate.getAccessibilityScore().getElevatorCount() /
                    candidate.getAccessibilityScore().getTotalStations();
        }
        double elevatorScore = -(elevatorRatio * elevatorWeight);

        double escalatorRatio = 0.0;
        if (candidate.getAccessibilityScore().getTotalStations() > 0) {
            escalatorRatio = (double) candidate.getAccessibilityScore().getEscalatorCount() /
                    candidate.getAccessibilityScore().getTotalStations();
        }
        double escalatorScore = -(escalatorRatio * escalatorWeight);

        return walkScore + transferScore + timeScore + elevatorScore + escalatorScore;
    }

    private RouteResponse buildRouteResponse(Map<String, Object> selectedRoute,
                                             AccessibilityService.RouteAccessibilityScore accessibilityScore) {

        List<RouteResponse.GuideInfo> guides = new ArrayList<>();

        Integer totalDistance = getIntegerValue(selectedRoute, "totalDistance", 0);
        Integer totalTime = getIntegerValue(selectedRoute, "totalTime", 0);
        Integer totalFare = extractTotalFare(selectedRoute);

        RouteResponse.Location startLocation = extractStartLocation(selectedRoute);
        RouteResponse.Location endLocation = extractEndLocation(selectedRoute);

        processRouteLegs(selectedRoute, guides);

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

        for (int i = 0; i < legs.size(); i++) {
            Map<String, Object> leg = legs.get(i);
            String mode = (String) leg.get("mode");

            RouteResponse.Location startLocation = createLocationFromLeg(leg, "start");
            RouteResponse.Location endLocation = createLocationFromLeg(leg, "end");

            if ("WALK".equals(mode) && leg.containsKey("steps")) {
                processWalkSteps(leg, guides, startLocation, endLocation);
            } else {
                addTransportGuideInfo(leg, guides, startLocation, endLocation, mode);
            }
        }
    }

    private void processWalkSteps(Map<String, Object> leg, List<RouteResponse.GuideInfo> guides,
                                  RouteResponse.Location legStartLocation, RouteResponse.Location legEndLocation) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) leg.get("steps");

        for (Map<String, Object> step : steps) {
            String description = (String) step.get("description");
            String streetName = (String) step.get("streetName");
            Integer stepDistance = getIntegerValue(step, "distance", 0);
            String stepLinestring = (String) step.get("linestring");

            if (description != null && !description.trim().isEmpty()) {
                // T맵 description에서 출구 정보 포맷팅
                String formattedDescription = formatDescriptionWithExit(description);

                guides.add(RouteResponse.GuideInfo.builder()
                        .guidance(formattedDescription)  // 포맷팅된 description 사용
                        .distance(stepDistance)
                        .time(0)
                        .transportType("WALK")
                        .routeName(streetName)
                        .color(null)
                        .startLocation(legStartLocation)
                        .endLocation(legEndLocation)
                        .lineString(stepLinestring)
                        .build());
            }
        }
    }

    private String formatDescriptionWithExit(String description) {
        if (description == null) {
            return description;
        }

        // "불광역  6번출구 에서 직진 후" → "불광역 6번 출구 에서 직진 후"
        if (description.contains("번출구")) {
            return description.replaceAll("(\\S+?)(\\d+)번출구", "$1 $2번 출구");
        }

        return description;
    }

    private void addTransportGuideInfo(Map<String, Object> leg, List<RouteResponse.GuideInfo> guides,
                                       RouteResponse.Location startLocation, RouteResponse.Location endLocation, String mode) {

        Integer distance = getIntegerValue(leg, "distance", 0);
        Integer sectionTime = getIntegerValue(leg, "sectionTime", 0);
        String route = (String) leg.get("route");
        String routeId = (String) leg.get("routeId");
        String routeColor = (String) leg.get("routeColor");
        String lineString = extractLineString(leg);

        String busNumber = extractBusNumber(route);
        String guidance = createSimpleGuidanceText(leg);

        RouteResponse.StationAccessibility stationAccessibility = null;
        if (!"WALK".equals(mode)) {
            String startName = getLocationName(leg, "start");
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

        if (guidance != null && !guidance.trim().isEmpty()) {
            guides.add(RouteResponse.GuideInfo.builder()
                    .guidance(guidance)
                    .distance(distance)
                    .time(sectionTime)
                    .transportType(mode)
                    .routeName(route)
                    .busNumber(busNumber)
                    .busRouteId(routeId)
                    .color(routeColor)
                    .startLocation(startLocation)
                    .endLocation(endLocation)
                    .lineString(lineString)
                    .stationAccessibility(stationAccessibility)
                    .build());
        }
    }

    private String[] extractStationAndExit(String guidance) {
        String stationName = null;
        String exitInfo = null;

        if (guidance == null) {
            return new String[]{null, null};
        }

        // "상왕십리역에서 간선:463 탑승 → 역삼역6번출구 (35분, 7599m)" 패턴 분석
        // "→" 뒤의 부분에서 역명과 출구 정보 추출
        if (guidance.contains("→")) {
            String[] parts = guidance.split("→");
            if (parts.length > 1) {
                String destination = parts[1].trim();

                // "역삼역6번출구 (35분, 7599m)" 에서 역명과 출구 추출
                if (destination.contains("번출구")) {
                    // 정규표현식: (역명)(숫자)번출구
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(.+?)(\\d+)번출구");
                    java.util.regex.Matcher matcher = pattern.matcher(destination);

                    if (matcher.find()) {
                        stationName = matcher.group(1).trim();  // "역삼역"
                        String exitNumber = matcher.group(2);   // "6"
                        exitInfo = exitNumber + "번 출구";       // "6번 출구"

                        log.debug("역명/출구 추출: '{}' → 역명='{}', 출구='{}'", destination, stationName, exitInfo);
                    }
                } else {
                    // 출구 정보가 없는 경우, 괄호 앞까지가 역명
                    if (destination.contains("(")) {
                        stationName = destination.substring(0, destination.indexOf("(")).trim();
                    } else {
                        stationName = destination.trim();
                    }
                }
            }
        }

        return new String[]{stationName, exitInfo};
    }

    private String extractExitInfoFromDescription(String description) {
        if (description == null) return null;

        if (description.contains("번출구")) {
            String exitNumber = description.replaceAll(".*?(\\d+)번출구.*", "$1");
            if (!exitNumber.equals(description)) {
                return exitNumber + "번 출구";
            }
        }

        return null;
    }

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

    private String extractLineString(Map<String, Object> leg) {
        String mode = (String) leg.get("mode");
        String lineString = "";

        if ("WALK".equals(mode) && leg.containsKey("steps")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> steps = (List<Map<String, Object>>) leg.get("steps");

            for (Map<String, Object> step : steps) {
                String stepLinestring = (String) step.get("linestring");
                if (stepLinestring != null) {
                    lineString += stepLinestring + " ";
                }
            }
        } else if (leg.containsKey("passShape")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> passShape = (Map<String, Object>) leg.get("passShape");
            lineString = (String) passShape.get("linestring");
        }

        return lineString != null ? lineString.trim() : "";
    }

    private String createSimpleGuidanceText(Map<String, Object> leg) {
        if (leg == null) return null;

        String mode = (String) leg.get("mode");
        String startName = getLocationName(leg, "start");
        String endName = getLocationName(leg, "end");
        Integer distance = getIntegerValue(leg, "distance", 0);
        Integer time = getIntegerValue(leg, "sectionTime", 0);
        String route = (String) leg.get("route");

        if ("WALK".equals(mode)) {
            return String.format("%s에서 %s까지 도보 %dm (%d분)",
                    startName, endName, distance, time / 60);
        } else if ("BUS".equals(mode)) {
            String busInfo = route != null ? route : "버스";
            String formattedEndName = formatStationNameWithExit(endName);
            return String.format("%s에서 %s 탑승 → %s (%d분, %dm)",
                    startName, busInfo, formattedEndName, time / 60, distance);
        } else if ("SUBWAY".equals(mode)) {
            String subwayInfo = route != null ? route : "지하철";
            String formattedEndName = formatStationNameWithExit(endName);
            return String.format("%s에서 %s 탑승 → %s (%d분, %dm)",
                    startName, subwayInfo, formattedEndName, time / 60, distance);
        }

        String formattedEndName = formatStationNameWithExit(endName);
        return String.format("%s에서 %s까지 %s 이용 (%d분, %dm)",
                startName, formattedEndName, mode, time / 60, distance);
    }

    private String formatStationNameWithExit(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return locationName;
        }

        // "역삼역6번출구" → "역삼역 6번 출구"
        if (locationName.contains("번출구")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(.+?)(\\d+)번출구");
            java.util.regex.Matcher matcher = pattern.matcher(locationName);

            if (matcher.find()) {
                String stationName = matcher.group(1).trim();  // "역삼역"
                String exitNumber = matcher.group(2);          // "6"
                return stationName + " " + exitNumber + "번 출구";  // "역삼역 6번 출구"
            }
        }

        return locationName;
    }

    private String extractBusNumber(String routeString) {
        if (routeString == null || routeString.trim().isEmpty()) {
            return null;
        }

        if (routeString.contains(":")) {
            return routeString.split(":")[1].trim();
        }

        return routeString.trim();
    }

    private String extractExitInfo(String locationName) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return null;
        }

        if (locationName.contains("번출구")) {
            String[] parts = locationName.split("번출구");
            if (parts.length > 0) {
                String exitNumber = parts[0].replaceAll(".*?([0-9]+)$", "$1");
                if (!exitNumber.isEmpty()) {
                    return exitNumber + "번 출구";
                }
            }
        }

        return null;
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
        private double weightedScore;

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
        public double getWeightedScore() { return weightedScore; }

        // Setter for weighted score
        public void setWeightedScore(double weightedScore) { this.weightedScore = weightedScore; }

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