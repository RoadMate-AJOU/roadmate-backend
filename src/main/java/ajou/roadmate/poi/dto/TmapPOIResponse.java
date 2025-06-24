package ajou.roadmate.poi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class TmapPOIResponse {
    @JsonProperty("searchPoiInfo")
    private SearchPoiInfo searchPoiInfo;

    @Data
    public static class SearchPoiInfo {
        @JsonProperty("totalCount")
        private String totalCount;

        @JsonProperty("count")
        private String count;

        @JsonProperty("page")
        private String page;

        @JsonProperty("pois")
        private Pois pois;
    }

    @Data
    public static class Pois {
        @JsonProperty("poi")
        private List<Poi> poi;
    }

    @Data
    public static class Poi {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("telNo")
        private String telNo;

        @JsonProperty("frontLat")
        private String frontLat;

        @JsonProperty("frontLon")
        private String frontLon;

        @JsonProperty("noorLat")
        private String noorLat;

        @JsonProperty("noorLon")
        private String noorLon;

        @JsonProperty("upperAddrName")
        private String upperAddrName;

        @JsonProperty("middleAddrName")
        private String middleAddrName;

        @JsonProperty("lowerAddrName")
        private String lowerAddrName;

        @JsonProperty("detailAddrName")
        private String detailAddrName;

        @JsonProperty("mlClass")
        private String mlClass;

        @JsonProperty("firstNo")
        private String firstNo;

        @JsonProperty("secondNo")
        private String secondNo;

        @JsonProperty("roadName")
        private String roadName;

        @JsonProperty("firstBuildNo")
        private String firstBuildNo;

        @JsonProperty("secondBuildNo")
        private String secondBuildNo;

        @JsonProperty("radius")
        private String radius;

        @JsonProperty("bizName")
        private String bizName;

        @JsonProperty("upperBizName")
        private String upperBizName;

        @JsonProperty("middleBizName")
        private String middleBizName;

        @JsonProperty("lowerBizName")
        private String lowerBizName;

        @JsonProperty("detailBizName")
        private String detailBizName;

        @JsonProperty("rpFlag")
        private String rpFlag;

        @JsonProperty("parkFlag")
        private String parkFlag;

        @JsonProperty("detailInfoFlag")
        private String detailInfoFlag;

        @JsonProperty("desc")
        private String desc;
    }
}
