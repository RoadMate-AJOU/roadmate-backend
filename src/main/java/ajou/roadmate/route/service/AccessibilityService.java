package ajou.roadmate.route.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.util.*;

@Service
@Slf4j
public class AccessibilityService {

    private Set<String> elevatorStations = new HashSet<>();
    private Set<String> escalatorStations = new HashSet<>();
    private Map<String, StationAccessibility> stationAccessibilityMap = new HashMap<>();
    private Map<String, List<String>> elevatorExitMap = new HashMap<>(); // 역별 엘리베이터 출구 정보
    private Map<String, List<String>> escalatorExitMap = new HashMap<>(); // 역별 에스컬레이터 출구 정보

    @PostConstruct
    public void loadAccessibilityData() {
        loadElevatorData();
        loadEscalatorData();
        log.info("접근성 데이터 로드 완료 - 엘리베이터: {}개 역, 에스컬레이터: {}개 역",
                elevatorStations.size(), escalatorStations.size());
    }

    private void loadElevatorData() {
        try {
            // 클래스패스에서 파일 읽기 시도
            var inputStream = getClass().getClassLoader().getResourceAsStream("data/elevator.csv");
            if (inputStream == null) {
                log.warn("엘리베이터 데이터 파일을 찾을 수 없습니다. 기본 데이터를 사용합니다.");
                loadDefaultElevatorData();
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(inputStream, Charset.forName("EUC-KR")))) {

                String line;
                boolean isHeader = true;
                while ((line = reader.readLine()) != null) {
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }

                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
                        String stationName = parts[2].trim(); // 역명
                        String lineName = parts[1].trim(); // 호선
                        String exitInfo = parts.length > 5 ? parts[5].trim() : ""; // 출입구번호

                        elevatorStations.add(stationName);
                        stationAccessibilityMap.computeIfAbsent(stationName,
                                k -> new StationAccessibility()).setHasElevator(true);

                        // 출구 정보 저장
                        if (!exitInfo.isEmpty() && !exitInfo.equals("")) {
                            elevatorExitMap.computeIfAbsent(stationName, k -> new ArrayList<>()).add(exitInfo);
                        }
                    }
                }

                log.info("엘리베이터 데이터 로드 완료: {}개 역", elevatorStations.size());

            }
        } catch (Exception e) {
            log.error("엘리베이터 데이터 로드 실패: {}", e.getMessage());
            loadDefaultElevatorData();
        }
    }

    private void loadEscalatorData() {
        try {
            var inputStream = getClass().getClassLoader().getResourceAsStream("data/escalator.csv");
            if (inputStream == null) {
                log.warn("에스컬레이터 데이터 파일을 찾을 수 없습니다. 기본 데이터를 사용합니다.");
                loadDefaultEscalatorData();
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(inputStream, Charset.forName("EUC-KR")))) {

                String line;
                boolean isHeader = true;
                while ((line = reader.readLine()) != null) {
                    if (isHeader) {
                        isHeader = false;
                        continue;
                    }

                    String[] parts = line.split(",");
                    if (parts.length >= 6) {
                        String stationName = parts[2].trim(); // 역명
                        String exitInfo = parts.length > 5 ? parts[5].trim() : ""; // 출입구번호

                        escalatorStations.add(stationName);
                        stationAccessibilityMap.computeIfAbsent(stationName,
                                k -> new StationAccessibility()).setHasEscalator(true);

                        // 출구 정보 저장
                        if (!exitInfo.isEmpty() && !exitInfo.equals("")) {
                            escalatorExitMap.computeIfAbsent(stationName, k -> new ArrayList<>()).add(exitInfo);
                        }
                    }
                }

                log.info("에스컬레이터 데이터 로드 완료: {}개 역", escalatorStations.size());

            }
        } catch (Exception e) {
            log.error("에스컬레이터 데이터 로드 실패: {}", e.getMessage());
            loadDefaultEscalatorData();
        }
    }

    private void loadDefaultElevatorData() {
        // 주요 역들의 엘리베이터 정보 (기본값)
        String[] majorStationsWithElevator = {
                "강남역", "홍대입구역", "신촌역", "명동역", "종각역", "시청역",
                "을지로입구역", "동대문역사문화공원역", "잠실역", "선릉역",
                "서울역", "용산역", "왕십리역", "건대입구역", "선정릉역"
        };

        for (String station : majorStationsWithElevator) {
            elevatorStations.add(station);
            stationAccessibilityMap.computeIfAbsent(station,
                    k -> new StationAccessibility()).setHasElevator(true);
        }
    }

    private void loadDefaultEscalatorData() {
        // 주요 역들의 에스컬레이터 정보 (기본값)
        String[] majorStationsWithEscalator = {
                "강남역", "홍대입구역", "신촌역", "명동역", "종각역", "시청역",
                "을지로입구역", "동대문역사문화공원역", "잠실역", "선릉역",
                "서울역", "용산역", "왕십리역", "건대입구역", "선정릉역",
                "신논현역", "논현역", "압구정역", "청담역", "삼성역"
        };

        for (String station : majorStationsWithEscalator) {
            escalatorStations.add(station);
            stationAccessibilityMap.computeIfAbsent(station,
                    k -> new StationAccessibility()).setHasEscalator(true);
        }
    }

    public boolean hasElevator(String stationName) {
        return elevatorStations.contains(normalizeStationName(stationName));
    }

    public boolean hasEscalator(String stationName) {
        return escalatorStations.contains(normalizeStationName(stationName));
    }

    public StationAccessibility getStationAccessibility(String stationName) {
        String normalizedName = normalizeStationName(stationName);
        StationAccessibility accessibility = stationAccessibilityMap.getOrDefault(normalizedName,
                new StationAccessibility());

        // 출구 정보 추가
        List<String> elevatorExits = elevatorExitMap.getOrDefault(normalizedName, new ArrayList<>());
        List<String> escalatorExits = escalatorExitMap.getOrDefault(normalizedName, new ArrayList<>());

        accessibility.setElevatorExits(String.join(",", elevatorExits));
        accessibility.setEscalatorExits(String.join(",", escalatorExits));

        // 접근 가능한 출구 정보 생성
        List<String> accessibleExits = new ArrayList<>();
        if (!elevatorExits.isEmpty()) {
            accessibleExits.addAll(elevatorExits.stream()
                    .map(exit -> exit + " (엘리베이터)")
                    .toList());
        }
        if (!escalatorExits.isEmpty()) {
            accessibleExits.addAll(escalatorExits.stream()
                    .map(exit -> exit + " (에스컬레이터)")
                    .toList());
        }
        accessibility.setAccessibleExitInfo(String.join(", ", accessibleExits));

        return accessibility;
    }

    public RouteAccessibilityScore calculateRouteAccessibilityScore(List<String> stationNames, int walkTime) {
        int elevatorCount = 0;
        int escalatorCount = 0;
        int totalStations = stationNames.size();

        for (String stationName : stationNames) {
            if (hasElevator(stationName)) {
                elevatorCount++;
            }
            if (hasEscalator(stationName)) {
                escalatorCount++;
            }
        }

        // 접근성 점수 계산 (높을수록 좋음)
        double accessibilityScore = 0.0;

        // 엘리베이터 가중치 (40%)
        if (totalStations > 0) {
            accessibilityScore += (double) elevatorCount / totalStations * 40;
        }

        // 에스컬레이터 가중치 (30%)
        if (totalStations > 0) {
            accessibilityScore += (double) escalatorCount / totalStations * 30;
        }

        // 도보시간 가중치 (30%) - 낮을수록 좋음
        double walkTimeScore = Math.max(0, 30 - (walkTime / 60.0)); // 분 단위로 변환
        accessibilityScore += walkTimeScore;

        return RouteAccessibilityScore.builder()
                .totalScore(accessibilityScore)
                .elevatorCount(elevatorCount)
                .escalatorCount(escalatorCount)
                .totalStations(totalStations)
                .walkTimeMinutes(walkTime / 60)
                .accessibilityRate((double) elevatorCount / Math.max(1, totalStations) * 100)
                .build();
    }

    private String normalizeStationName(String stationName) {
        if (stationName == null) return "";

        // 역명 정규화 (끝에 "역" 제거, 공백 제거 등)
        return stationName.replace("역", "")
                .replace(" ", "")
                .replace("지하철", "")
                .replace("1호선", "")
                .replace("2호선", "")
                .replace("3호선", "")
                .replace("4호선", "")
                .replace("5호선", "")
                .replace("6호선", "")
                .replace("7호선", "")
                .replace("8호선", "")
                .replace("9호선", "")
                .replace("(중)", "")
                .replace("(하)", "")
                .replace("(상)", "")
                .trim();
    }

    public static class StationAccessibility {
        private boolean hasElevator = false;
        private boolean hasEscalator = false;
        private String elevatorExits = "";
        private String escalatorExits = "";
        private String accessibleExitInfo = "";

        public boolean isHasElevator() { return hasElevator; }
        public void setHasElevator(boolean hasElevator) { this.hasElevator = hasElevator; }
        public boolean isHasEscalator() { return hasEscalator; }
        public void setHasEscalator(boolean hasEscalator) { this.hasEscalator = hasEscalator; }
        public String getElevatorExits() { return elevatorExits; }
        public void setElevatorExits(String elevatorExits) { this.elevatorExits = elevatorExits; }
        public String getEscalatorExits() { return escalatorExits; }
        public void setEscalatorExits(String escalatorExits) { this.escalatorExits = escalatorExits; }
        public String getAccessibleExitInfo() { return accessibleExitInfo; }
        public void setAccessibleExitInfo(String accessibleExitInfo) { this.accessibleExitInfo = accessibleExitInfo; }
    }

    public static class RouteAccessibilityScore {
        private double totalScore;
        private int elevatorCount;
        private int escalatorCount;
        private int totalStations;
        private int walkTimeMinutes;
        private double accessibilityRate;

        public static RouteAccessibilityScoreBuilder builder() {
            return new RouteAccessibilityScoreBuilder();
        }

        // Getters
        public double getTotalScore() { return totalScore; }
        public int getElevatorCount() { return elevatorCount; }
        public int getEscalatorCount() { return escalatorCount; }
        public int getTotalStations() { return totalStations; }
        public int getWalkTimeMinutes() { return walkTimeMinutes; }
        public double getAccessibilityRate() { return accessibilityRate; }

        public static class RouteAccessibilityScoreBuilder {
            private RouteAccessibilityScore score = new RouteAccessibilityScore();

            public RouteAccessibilityScoreBuilder totalScore(double totalScore) {
                score.totalScore = totalScore;
                return this;
            }

            public RouteAccessibilityScoreBuilder elevatorCount(int elevatorCount) {
                score.elevatorCount = elevatorCount;
                return this;
            }

            public RouteAccessibilityScoreBuilder escalatorCount(int escalatorCount) {
                score.escalatorCount = escalatorCount;
                return this;
            }

            public RouteAccessibilityScoreBuilder totalStations(int totalStations) {
                score.totalStations = totalStations;
                return this;
            }

            public RouteAccessibilityScoreBuilder walkTimeMinutes(int walkTimeMinutes) {
                score.walkTimeMinutes = walkTimeMinutes;
                return this;
            }

            public RouteAccessibilityScoreBuilder accessibilityRate(double accessibilityRate) {
                score.accessibilityRate = accessibilityRate;
                return this;
            }

            public RouteAccessibilityScore build() {
                return score;
            }
        }
    }
}