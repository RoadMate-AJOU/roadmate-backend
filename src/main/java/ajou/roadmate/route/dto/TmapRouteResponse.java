package ajou.roadmate.route.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class TmapRouteResponse {

    @JsonProperty("metaData")
    private Map<String, Object> metaData;

    @Data
    public static class MetaData {
        @JsonProperty("requestParameters")
        private RequestParameters requestParameters;

        @JsonProperty("plan")
        private Plan plan;
    }

    @Data
    public static class RequestParameters {
        @JsonProperty("startX")
        private String startX;

        @JsonProperty("startY")
        private String startY;

        @JsonProperty("endX")
        private String endX;

        @JsonProperty("endY")
        private String endY;
    }

    @Data
    public static class Plan {
        @JsonProperty("itineraries")
        private List<Itinerary> itineraries;
    }

    @Data
    public static class Itinerary {
        @JsonProperty("fare")
        private Fare fare;

        @JsonProperty("totalTime")
        private Integer totalTime;

        @JsonProperty("totalDistance")
        private Integer totalDistance;

        @JsonProperty("totalWalkTime")
        private Integer totalWalkTime;

        @JsonProperty("totalWalkDistance")
        private Integer totalWalkDistance;

        @JsonProperty("transferCount")
        private Integer transferCount;

        @JsonProperty("legs")
        private List<Leg> legs;
    }

    @Data
    public static class Fare {
        @JsonProperty("regular")
        private Regular regular;
    }

    @Data
    public static class Regular {
        @JsonProperty("totalFare")
        private Integer totalFare;

        @JsonProperty("currency")
        private Currency currency;
    }

    @Data
    public static class Currency {
        @JsonProperty("symbol")
        private String symbol;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("currencyCode")
        private String currencyCode;
    }

    @Data
    public static class Leg {
        @JsonProperty("mode")
        private String mode; // WALK, BUS, SUBWAY 등

        @JsonProperty("sectionTime")
        private Integer sectionTime;

        @JsonProperty("distance")
        private Integer distance;

        @JsonProperty("start")
        private Location start;

        @JsonProperty("end")
        private Location end;

        @JsonProperty("route")
        private String route; // 버스 노선명

        @JsonProperty("routeColor")
        private String routeColor;

        @JsonProperty("steps")
        private List<Step> steps; // 도보 구간에만 있음

        @JsonProperty("passShape")
        private PassShape passShape; // 버스/지하철 구간에만 있음
    }

    @Data
    public static class Location {
        @JsonProperty("name")
        private String name;

        @JsonProperty("lon")
        private Double lon;

        @JsonProperty("lat")
        private Double lat;
    }

    @Data
    public static class Step {
        @JsonProperty("streetName")
        private String streetName;

        @JsonProperty("distance")
        private Integer distance;

        @JsonProperty("description")
        private String description;

        @JsonProperty("linestring")
        private String linestring;
    }

    @Data
    public static class PassShape {
        @JsonProperty("linestring")
        private String linestring;
    }
}