package ajou.roadmate.gpt.service;

import ajou.roadmate.route.dto.RouteResponse;
import ajou.roadmate.route.dto.RouteResponse.GuideInfo;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RouteInfoService {

    public String getAnswerForIntent(String intent, RouteResponse routeResponse, Map<String, String> entities) {
        if (routeResponse == null) {
            return "경로 정보가 없습니다. 경로를 먼저 설정해주세요.";
        }

        switch (intent) {
            case "total_route_time":
                return getTotalTime(routeResponse);
            case "total_route_distance":
                return getTotalDistance(routeResponse);
            case "total_fare":
                return getTotalFare(routeResponse);
            case "estimated_arrival_time":
                return getEstimatedArrivalTime(routeResponse);
            case "section_time_by_mode":
                return getSectionTime(routeResponse, entities);
            default:
                return "지원하지 않는 정보 요청입니다.";
        }
    }

    private String getTotalTime(RouteResponse route) {
        int totalTime = route.getTotalTime();
        int minutes = totalTime / 60;
        return String.format("총 소요 시간은 약 %d분입니다.", minutes);
    }

    private String getTotalDistance(RouteResponse route) {
        double km = route.getTotalDistance() / 1000.0;
        return String.format("총 거리는 약 %.1fkm입니다.", km);
    }

    private String getTotalFare(RouteResponse route) {
        if(route.getTotalFare()==null)
            return "현재 경로에 요금이 존재하지 않습니다.";
        return "총 요금은 약 " + route.getTotalFare() + "원입니다.";
    }

    private String getSectionTime(RouteResponse route, Map<String, String> entities) {
        if (entities == null || !entities.containsKey("transportType")) {
            return "교통 수단 정보를 찾을 수 없습니다.";
        }

        String transportType = entities.get("transportType").toUpperCase();
        List<GuideInfo> filtered = route.getGuides().stream()
                .filter(g -> transportType.equals(g.getTransportType()))
                .collect(Collectors.toList());

        if (filtered.isEmpty()) return "해당 교통수단에 대한 구간 정보가 없습니다.";

        int totalSeconds = filtered.stream().mapToInt(GuideInfo::getTime).sum();
        int minutes = totalSeconds / 60;

        return String.format("%s 구간의 총 소요 시간은 약 %d분입니다.", transportType, minutes);
    }

    private String getEstimatedArrivalTime(RouteResponse route) {
        return String.format("출발시간 기준 %d분 후에 도착 예정입니다.", route.getTotalTime()/60);
    }
}
