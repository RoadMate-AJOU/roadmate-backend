package ajou.roadmate.gpt.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.GPTErrorCode;
import ajou.roadmate.gpt.dto.*;
import ajou.roadmate.route.dto.RouteResponse.GuideInfo;
import ajou.roadmate.route.dto.RouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NlpOrchestrationService {

    private final ContextService contextService;
    private final OpenAiNlpService openAiNlpService;
    private final RouteInfoService routeInfoService;
    private final FeedbackService feedbackService;

    public NlpResponseDto orchestrate(NlpRequestDto request, String userId) {
        ChatContext context;

        try {
            context = contextService.getContext(userId);
        } catch (CustomException e) {
            if (e.getErrorCode() == GPTErrorCode.CONTEXT_NOT_FOUND) {
                context = new ChatContext(userId);
            } else {
                throw e;
            }
        }

        NlpAnalysisResult analysis = openAiNlpService.analyze(context.getConversationHistory(), request.getText());

        String intent = analysis.getIntent();
        NlpResponseDto response;

        if (isRouteQuery(intent)) {
            response = handleRouteExtraction(context, analysis);
        } else if (isInfoQuery(intent)) {
            response = handleInfoRequest(context, analysis);
        } else if (isRealTimeQuery(intent)) {
            response = handleRealTimeRequest(context, analysis);

        } else if (isGuidanceQuery(intent)) {
            response = handleGuidanceInfo(context, analysis);
        } else if ("current_location".equals(intent)) {
            response = handleCurrentLocation(context);
        } else if ("feedback".equals(intent)) {
            response = handleFeedback(context, analysis);
        } else {
            response = NlpResponseDto.builder()
                    .sessionId(context.getSessionId())
                    .intent("other_inquiries")
                    .status(NlpResponseDto.Status.COMPLETE)
                    .responseMessage(analysis.getResponseText())
                    .build();
        }

        updateAndSaveContext(context, request.getText(), response);
        return response;
    }

    private boolean isRouteQuery(String intent) {
        return "extract_route".equals(intent) || "research_route".equals(intent);
    }

    private boolean isInfoQuery(String intent) {
        return intent.startsWith("total_") || intent.startsWith("section_") || intent.equals("estimated_arrival_time");
    }

    private boolean isRealTimeQuery(String intent) {
        return intent.startsWith("real_time_");
    }

    private boolean isGuidanceQuery(String intent) {
        return switch (intent) {
            case "bus_number_info", "subway_line_info", "bus_station_info",
                    "subway_station_info", "accessibility_info" -> true;
            default -> false;
        };
    }

    private NlpResponseDto handleRouteExtraction(ChatContext context, NlpAnalysisResult analysis) {
        if ("research_route".equals(analysis.getIntent())) {
            context.setRouteResponse(null);
        }

        LocationInfo newLocations = new LocationInfo(
                analysis.getEntities().get("origin"),
                analysis.getEntities().get("destination")
        );

        if (context.getExtractedLocations() != null) {
            if (newLocations.getOrigin() == null) newLocations.setOrigin(context.getExtractedLocations().getOrigin());
            if (newLocations.getDestination() == null) newLocations.setDestination(context.getExtractedLocations().getDestination());
        }

        context.setExtractedLocations(newLocations);


        if (newLocations.getOrigin() == null && newLocations.getDestination() != null) {
            return NlpResponseDto.builder()
                    .sessionId(context.getSessionId())
                    .intent(analysis.getIntent())
                    .responseMessage(String.format("현재 위치에서 %s까지 경로를 탐색합니다.",newLocations.getDestination()))
                    .status(NlpResponseDto.Status.API_REQUIRED)
                    .data(newLocations)
                    .build();
        }

        if (newLocations.getOrigin() != null && newLocations.getDestination() != null) {
            return NlpResponseDto.builder()
                    .sessionId(context.getSessionId())
                    .intent(analysis.getIntent())
                    .responseMessage(String.format("%s에서 %s까지 경로를 탐색합니다.", newLocations.getOrigin(), newLocations.getDestination()))
                    .status(NlpResponseDto.Status.API_REQUIRED)
                    .data(newLocations)
                    .build();
        }

        return NlpResponseDto.builder()
                .sessionId(context.getSessionId())
                .intent(analysis.getIntent())
                .responseMessage("도착지를 말씀해주세요.")
                .status(NlpResponseDto.Status.INCOMPLETE)
                .data(newLocations)
                .build();
    }

    private NlpResponseDto handleInfoRequest(ChatContext context, NlpAnalysisResult analysis) {
        String answer = routeInfoService.getAnswerForIntent(analysis.getIntent(), context.getRouteResponse(), analysis.getEntities());

        return NlpResponseDto.builder()
                .sessionId(context.getSessionId())
                .intent(analysis.getIntent())
                .responseMessage(answer)
                .status(NlpResponseDto.Status.COMPLETE)
                .build();
    }

    private NlpResponseDto handleRealTimeRequest(ChatContext context, NlpAnalysisResult analysis) {
        Map<?, ?> apiRequestData = analysis.getEntities();
        String responseMessage;

        if (apiRequestData == null || apiRequestData.isEmpty()) {
            responseMessage = "죄송합니다, 어떤 버스나 지하철의 정보가 필요하신지 명확히 말씀해주시겠어요?";
        } else {
            responseMessage = analysis.getResponseText();
        }

        return NlpResponseDto.builder()
                .sessionId(context.getSessionId())
                .intent(analysis.getIntent())
                .responseMessage(responseMessage)
                .status(NlpResponseDto.Status.API_REQUIRED)
                .data(apiRequestData)
                .build();
    }

    private NlpResponseDto handleGuidanceInfo(ChatContext context, NlpAnalysisResult analysis) {
        String intent = analysis.getIntent();
        RouteResponse route = context.getRouteResponse();
        Map<String, String> entities = analysis.getEntities();
        String answer;

        if (route == null) {
            answer = "먼저 출발지와 도착지를 알려주셔야 경로 안내가 가능합니다.";
            return NlpResponseDto.builder()
                    .sessionId(context.getSessionId())
                    .intent(intent)
                    .responseMessage(answer)
                    .status(NlpResponseDto.Status.INCOMPLETE)
                    .build();
        }

        switch (intent) {
            case "bus_number_info":        answer = getBusNumber(route); break;
            case "subway_line_info":       answer = getSubwayLine(route); break;
            case "bus_station_info":       answer = getBusStationInfo(route, entities); break;
            case "subway_station_info":    answer = getSubwayStationInfo(route, entities); break;
            case "accessibility_info":     answer = getAccessibilityInfo(route); break;
            default:                       answer = "요청하신 정보를 이해하지 못했어요."; break;
        }

        return NlpResponseDto.builder()
                .sessionId(context.getSessionId())
                .intent(intent)
                .responseMessage(answer)
                .status(NlpResponseDto.Status.COMPLETE)
                .build();
    }

    private NlpResponseDto handleFeedback(ChatContext context, NlpAnalysisResult analysis) {
        Map<String, String> entities = analysis.getEntities();
        String category = entities != null ? entities.get("category") : null;

        if (category == null) {
            return NlpResponseDto.builder()
                    .sessionId(context.getSessionId())
                    .intent("feedback")
                    .responseMessage("어떤 항목에 대한 피드백인지 자세히 알 수 있을까요? 카테고리로는 걷는 거리, 환승, 소요 시간, 엘리베이터, 에스컬레이터 여부가 있어요!")
                    .status(NlpResponseDto.Status.INCOMPLETE)
                    .build();
        }

        try {
            feedbackService.submitFeedback(context.getSessionId(), category);
        } catch (IllegalArgumentException e) {
            return NlpResponseDto.builder()
                    .sessionId(context.getSessionId())
                    .intent("feedback")
                    .responseMessage("올바르지 않은 피드백 항목입니다. 걷는 거리, 환승, 소요 시간, 엘리베이터, 에스컬레이터 중 하나를 사용해주세요.")
                    .status(NlpResponseDto.Status.ERROR)
                    .build();
        }

        return NlpResponseDto.builder()
                .sessionId(context.getSessionId())
                .intent("feedback")
                .responseMessage("피드백이 이후 경로 추천에 반영될 예정입니다.")
                .status(NlpResponseDto.Status.COMPLETE)
                .build();
    }

    private NlpResponseDto handleCurrentLocation(ChatContext context){
        return NlpResponseDto.builder()
                .sessionId(context.getSessionId())
                .intent("current_location")
                .responseMessage("현재 위치 정보를 탐색합니다.")
                .status(NlpResponseDto.Status.COMPLETE)
                .build();
    }

    private String getBusNumber(RouteResponse route) {
        List<String> busNumbers = route.getGuides().stream()
                .filter(g -> "BUS".equalsIgnoreCase(g.getTransportType()) && g.getBusNumber() != null)
                .map(GuideInfo::getBusNumber)
                .distinct()
                .toList();

        if (busNumbers.isEmpty()) {
            return "현재 경로에는 버스 관련 정보가 없습니다.";
        }

        return "버스 번호는 " + String.join(", ", busNumbers) + "입니다.";
    }

    private String getSubwayLine(RouteResponse route) {
        List<String> subwayLines = route.getGuides().stream()
                .filter(g -> "SUBWAY".equalsIgnoreCase(g.getTransportType()) && g.getRouteName() != null)
                .map(GuideInfo::getRouteName)
                .distinct()
                .toList();

        if(subwayLines.isEmpty()){
            return "현재 경로에는 지하철 관련 정보가 없습니다.";
        }

        return "지하철 노선은 " + String.join(", ", subwayLines) + "입니다. 승차역과 하차역은 추가로 질문해주세요.";
    }

    private String getBusStationInfo(RouteResponse route, Map<String, String> entities){
        if (entities == null || entities.isEmpty()) {
            return "버스 정류장 정보를 확인할 수 없습니다. 출발 또는 도착 여부를 명시해주세요.";
        }

        if ("start".equalsIgnoreCase(entities.get("position"))) {
            return getStartBusStationInfo(route);
        } else if ("end".equalsIgnoreCase(entities.get("position"))) {
            return getEndBusStationInfo(route);
        } else {
            return "버스 정류장 정보를 확인할 수 없습니다. 승차 정류장 또는 하차 정류장 여부를 명시하여 다시 말씀해주세요.";
        }
    }

    private String getStartBusStationInfo(RouteResponse route) {
        List<String> stationDescriptions = route.getGuides().stream()
                .filter(g -> "BUS".equalsIgnoreCase(g.getTransportType()) && g.getStartLocation() != null)
                .map(g -> String.format("%s에서 승차", g.getStartLocation().getName()))
                .distinct()
                .toList();

        if (stationDescriptions.isEmpty()) {
            return "현재 경로에서 이용 가능한 버스 승차 위치 정보를 찾을 수 없습니다.";
        }

        return "버스 승차 정류장은 " + String.join(", ", stationDescriptions) + "입니다.";
    }

    private String getEndBusStationInfo(RouteResponse route) {
        List<String> stationDescriptions = route.getGuides().stream()
                .filter(g -> "BUS".equalsIgnoreCase(g.getTransportType()) && g.getEndLocation() != null)
                .map(g -> String.format("%s에서 하차", g.getEndLocation().getName()))
                .distinct()
                .toList();

        if (stationDescriptions.isEmpty()) {
            return "현재 경로에서 이용 가능한 버스 하차 위치 정보를 찾을 수 없습니다.";
        }

        return "버스 하차 정류장은 " + String.join(", ", stationDescriptions) + "입니다.";
    }

    private String getSubwayStationInfo(RouteResponse route, Map<String, String> entities){
        if (entities == null || entities.isEmpty()) {
            return "지하철 정보를 확인할 수 없습니다. 출발 또는 도착 여부를 명시해주세요.";
        }

        if ("start".equalsIgnoreCase(entities.get("position"))) {
            return getStartSubwayStationInfo(route);
        } else if ("end".equalsIgnoreCase(entities.get("position"))) {
            return getEndSubwayStationInfo(route);
        } else {
            return "지하철 승하차 정보를 확인할 수 없습니다. 승차역 또는 하차역 여부를 명시하여 다시 말씀해주세요.";
        }
    }

    private String getStartSubwayStationInfo(RouteResponse route) {
        List<String> stationDescriptions = route.getGuides().stream()
                .filter(g -> "SUBWAY".equalsIgnoreCase(g.getTransportType()) && g.getStartLocation() != null)
                .map(g -> String.format("지하철은 %s역에서 탑승합니다.", g.getStartLocation().getName()))
                .distinct()
                .toList();

        if (stationDescriptions.isEmpty()) {
            return "현재 경로에서 이용 가능한 지하철 승차 위치 정보를 찾을 수 없습니다.";
        }

        return "지하철 승차역은 " + String.join(", ", stationDescriptions) + "입니다.";
    }

    private String getEndSubwayStationInfo(RouteResponse route) {
        List<String> stationDescriptions = route.getGuides().stream()
                .filter(g -> "SUBWAY".equalsIgnoreCase(g.getTransportType()) && g.getEndLocation() != null)
                .map(g -> String.format("지하철은 %s역에서 하차합니다.", g.getEndLocation().getName()))
                .distinct()
                .toList();

        if (stationDescriptions.isEmpty()) {
            return "현재 경로에서 이용 가능한 지하철 하차 위치 정보를 찾을 수 없습니다.";
        }

        return "지하철 하차역은 " + String.join(", ", stationDescriptions) + "입니다.";
    }

    private String getAccessibilityInfo(RouteResponse route) {
        Map<String, String> stationToDescription = route.getGuides().stream()
                .filter(g -> g.getStationAccessibility() != null && g.getStartLocation() != null)
                .collect(Collectors.toMap(
                        g -> g.getStartLocation().getName(),
                        g -> {
                            var acc = g.getStationAccessibility();
                            return String.format(
                                    "%s역에는 엘리베이터가 %s, 에스컬레이터가 %s",
                                    g.getStartLocation().getName(),
                                    acc.getHasElevator() ? "있습니다." : "없습니다.",
                                    acc.getHasEscalator() ? "있습니다." : "없습니다."
                            );
                        },
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        if (stationToDescription.isEmpty()) {
            return "역의 접근성 정보가 없습니다.";
        }

        return String.join("\n", stationToDescription.values());
    }

    private void updateAndSaveContext(ChatContext context, String userText, NlpResponseDto response) {
        context.addMessage(new Message("user", userText));
        context.addMessage(new Message("assistant", response.getResponseMessage()));
        contextService.saveContext(context);
    }

}