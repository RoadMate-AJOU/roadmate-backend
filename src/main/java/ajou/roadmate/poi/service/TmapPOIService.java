package ajou.roadmate.poi.service;

import ajou.roadmate.global.exception.CustomException;
import ajou.roadmate.global.exception.POIErrorCode;
import ajou.roadmate.poi.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TmapPOIService {

    @Value("${tmap.api.key}")
    private String tmapApiKey;

    @Value("${tmap.api.url}")
    private String tmapApiUrl;

    @Qualifier("tmapRestTemplate")
    private final RestTemplate restTemplate;

    public POISearchResponse searchPOI(POISearchRequest request) {
        try {
            validateRequest(request);

            log.info("=== T맵 POI 검색 시작 ===");
            log.info("요청 데이터: {}", request);

            TmapPOIResponse tmapResponse = callTmapAPI(request);
            POISearchResponse response = processTmapResponse(tmapResponse, request);

            log.info("검색 완료 - 결과 수: {}", response.getTotalCount());
            return response;

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("POI 검색 중 예상치 못한 오류 발생: ", e);
            throw new CustomException(POIErrorCode.TMAP_API_ERROR);
        }
    }

    private void validateRequest(POISearchRequest request) {
        if (request.getDestination() == null || request.getDestination().trim().isEmpty()) {
            throw new CustomException(POIErrorCode.INVALID_DESTINATION);
        }

        if (request.getCurrentLat() == null || request.getCurrentLon() == null) {
            throw new CustomException(POIErrorCode.INVALID_LOCATION);
        }
    }

    private TmapPOIResponse callTmapAPI(POISearchRequest request) {
        String url = UriComponentsBuilder.fromHttpUrl(tmapApiUrl)
                .queryParam("version", "1")
                .queryParam("searchKeyword", request.getDestination())
                .queryParam("searchType", "all")
                .queryParam("page", "1")
                .queryParam("count", "20")
                .queryParam("resCoordType", "WGS84GEO")
                .queryParam("reqCoordType", "WGS84GEO")
                .queryParam("centerLat", request.getCurrentLat())
                .queryParam("centerLon", request.getCurrentLon())
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        headers.set("appKey", tmapApiKey);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<TmapPOIResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, TmapPOIResponse.class);

            if (response.getBody() == null) {
                throw new CustomException(POIErrorCode.NO_RESULTS_FOUND);
            }

            return response.getBody();

        } catch (Exception e) {
            log.error("T맵 API 호출 실패: ", e);
            throw new CustomException(POIErrorCode.TMAP_API_ERROR);
        }
    }

    private POISearchResponse processTmapResponse(TmapPOIResponse tmapResponse, POISearchRequest request) {
        if (tmapResponse == null ||
                tmapResponse.getSearchPoiInfo() == null ||
                tmapResponse.getSearchPoiInfo().getPois() == null ||
                tmapResponse.getSearchPoiInfo().getPois().getPoi() == null) {
            return POISearchResponse.builder()
                    .places(new ArrayList<>())
                    .totalCount(0)
                    .build();
        }

        List<TmapPOIResponse.Poi> poiList = tmapResponse.getSearchPoiInfo().getPois().getPoi();

        List<POIItem> places = poiList.stream()
                .map(poi -> convertToPoiItem(poi, request))
                .collect(Collectors.toList());

        int totalCount = parseInteger(tmapResponse.getSearchPoiInfo().getTotalCount());

        return POISearchResponse.builder()
                .places(places)
                .totalCount(totalCount)
                .build();
    }

    private POIItem convertToPoiItem(TmapPOIResponse.Poi poi, POISearchRequest request) {
        try {
            Double lat = parseDouble(poi.getFrontLat());
            Double lon = parseDouble(poi.getFrontLon());
            String address = buildAddress(poi);
            Double distance = calculateDistance(
                    request.getCurrentLat(), request.getCurrentLon(), lat, lon);
            String category = buildCategory(poi);

            return POIItem.builder()
                    .name(poi.getName())
                    .address(address)
                    .latitude(lat)
                    .longitude(lon)
                    .distance(distance)
                    .category(category)
                    .tel(poi.getTelNo())
                    .build();

        } catch (Exception e) {
            log.warn("POI 변환 중 오류 발생: {}", poi.getName(), e);
            throw new CustomException(POIErrorCode.COORDINATE_PARSE_ERROR);
        }
    }

    // 기존 헬퍼 메서드들은 동일...
    private String buildAddress(TmapPOIResponse.Poi poi) {
        StringBuilder address = new StringBuilder();
        if (poi.getUpperAddrName() != null && !poi.getUpperAddrName().isEmpty()) {
            address.append(poi.getUpperAddrName()).append(" ");
        }
        if (poi.getMiddleAddrName() != null && !poi.getMiddleAddrName().isEmpty()) {
            address.append(poi.getMiddleAddrName()).append(" ");
        }
        if (poi.getLowerAddrName() != null && !poi.getLowerAddrName().isEmpty()) {
            address.append(poi.getLowerAddrName()).append(" ");
        }
        if (poi.getDetailAddrName() != null && !poi.getDetailAddrName().isEmpty()) {
            address.append(poi.getDetailAddrName());
        }
        return address.toString().trim();
    }

    private String buildCategory(TmapPOIResponse.Poi poi) {
        StringBuilder category = new StringBuilder();
        if (poi.getUpperBizName() != null && !poi.getUpperBizName().isEmpty()) {
            category.append(poi.getUpperBizName());
        }
        if (poi.getMiddleBizName() != null && !poi.getMiddleBizName().isEmpty()) {
            if (category.length() > 0) category.append(" > ");
            category.append(poi.getMiddleBizName());
        }
        if (poi.getLowerBizName() != null && !poi.getLowerBizName().isEmpty()) {
            if (category.length() > 0) category.append(" > ");
            category.append(poi.getLowerBizName());
        }
        return category.toString();
    }

    private Double parseDouble(String value) {
        try {
            return value != null ? Double.parseDouble(value) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseInteger(String value) {
        try {
            return value != null ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return 0.0;
        }

        final int R = 6371000; // 지구 반지름(미터)
        Double latDistance = Math.toRadians(lat2 - lat1);
        Double lonDistance = Math.toRadians(lon2 - lon1);

        Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}