package ajou.roadmate.gpt.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.GPTErrorCode;
import ajou.roadmate.gpt.dto.*;
import ajou.roadmate.route.dto.RouteResponse.GuideInfo;
import ajou.roadmate.route.dto.RouteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NlpOrchestrationService {

    private final ContextService contextService;
    private final OpenAiNlpService openAiNlpService;
    private final RouteInfoService routeInfoService;

    public NlpResponseDto orchestrate(NlpRequestDto request) {
        ChatContext context;

        try {
            context = contextService.getContext(request.getSessionId());
        } catch (CustomException e) {
            if (e.getErrorCode() == GPTErrorCode.CONTEXT_NOT_FOUND) {
                context = new ChatContext(request.getSessionId());
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
        }else {
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
        Object apiRequestData = analysis.getEntities();
        String responseMessage;

        if (apiRequestData == null || ((java.util.Map<?, ?>) apiRequestData).isEmpty()) {
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
            case "bus_station_info":       answer = getBusStationInfo(route); break;
            case "subway_station_info":    answer = getSubwayStationInfo(route); break;
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

    private String getBusNumber(RouteResponse route) {
        return route.getGuides().stream()
                .filter(g -> "BUS".equalsIgnoreCase(g.getTransportType()) && g.getBusNumber() != null)
                .map(GuideInfo::getBusNumber)
                .distinct()
                .collect(Collectors.joining(", ", "버스 번호는 ", "입니다."));
    }

    private String getSubwayLine(RouteResponse route) {
        return route.getGuides().stream()
                .filter(g -> "SUBWAY".equalsIgnoreCase(g.getTransportType()) && g.getRouteName() != null)
                .map(GuideInfo::getRouteName)
                .distinct()
                .collect(Collectors.joining(", ", "이용할 지하철 노선은 ", "입니다."));
    }

    private String getBusStationInfo(RouteResponse route) {
        return route.getGuides().stream()
                .filter(g -> "BUS".equalsIgnoreCase(g.getTransportType()) && g.getStartLocation() != null)
                .map(g -> String.format("버스 승차는 %s에서 시작합니다.", g.getStartLocation().getName()))
                .findFirst()
                .orElse("버스 승차 위치 정보를 찾을 수 없습니다.");
    }

    private String getSubwayStationInfo(RouteResponse route) {
        return route.getGuides().stream()
                .filter(g -> "SUBWAY".equalsIgnoreCase(g.getTransportType()) && g.getStartLocation() != null)
                .map(g -> String.format("지하철은 %s역에서 탑승합니다.", g.getStartLocation().getName()))
                .findFirst()
                .orElse("지하철 승차 위치 정보를 찾을 수 없습니다.");
    }

    private String getAccessibilityInfo(RouteResponse route) {
        return route.getGuides().stream()
                .filter(g -> g.getStationAccessibility() != null)
                .map(g -> {
                    RouteResponse.StationAccessibility acc = g.getStationAccessibility();
                    return String.format(
                            "%s역에는 엘리베이터가 %s, 에스컬레이터가 %s",
                            g.getStartLocation().getName(),
                            acc.getHasElevator() ? "있습니다." : "없습니다.",
                            acc.getHasEscalator() ? "있습니다." : "없습니다."
                    );
                })
                .collect(Collectors.joining("\n", "", ""));
    }

    private void updateAndSaveContext(ChatContext context, String userText, NlpResponseDto response) {
        context.addMessage(new Message("user", userText));
        context.addMessage(new Message("assistant", response.getResponseMessage()));
        contextService.saveContext(context);
    }

}