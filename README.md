# 🚌 길벗 (RoadMate) - Backend

<div align="center">

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-007396?style=for-the-badge&logo=java&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=for-the-badge&logo=redis&logoColor=white)

**AI 기반 고령자 맞춤 대중교통 안내 서비스 백엔드**

[🏠 Organization](https://github.com/RoadMate-AJOU) • [📱 Frontend](https://github.com/RoadMate-AJOU/roadmate-frontend)

</div>

---

## 🏗 시스템 아키텍처

```
Client (React Native)
         ↓
API Gateway (Spring MVC)
         ↓
    ┌────┴────┐
    ↓         ↓
NLP Layer   Route Layer
(OpenAI)    (Tmap API)
    ↓         ↓
    └────┬────┘
         ↓
   Redis Cache
```

---

## 🔧 핵심 기술 구현

### 1. OpenAI Fine-tuned NLP 처리

**Intent Classification & Entity Extraction**
- 18개 Intent 분류 체계 (경로 탐색, 실시간 정보, 안내 질의 등)
- JSON 구조화 응답으로 일관성 보장
- Redis 기반 대화 컨텍스트 관리 (TTL 30분)

**처리 흐름**
```
사용자 입력 → OpenAI 분석 → Intent 분류 → Entity 추출 → 서비스 연동
```

### 2. 접근성 기반 경로 추천 알고리즘

**데이터 소스**
- 서울시 지하철역 엘리베이터/에스컬레이터 설치 현황 (CSV 파싱)

**접근성 점수 산출**
- 엘리베이터 설치율 (40%)
- 에스컬레이터 설치율 (30%)
- 도보 시간 역산 (30%)

**개인화 경로 추천**
- Tmap API로부터 최대 5개 경로 수신
- 사용자 피드백 기반 가중치 조정
- 도보시간, 환승, 소요시간, 접근성을 종합한 점수 계산
- 최적 경로 자동 선택

### 3. Redis 기반 세션 관리

**저장 구조**
- 대화 컨텍스트: sessionId별 대화 히스토리 및 경로 정보 캐싱
- 사용자 세션: 토큰 기반 인증 (TTL 1시간)
- 피드백 데이터: Hash 구조로 카테고리별 카운트 저장

**최적화 전략**
- 반복 질문 시 캐시된 경로 정보 재사용
- 컨텍스트 유지로 연속 대화 지원
- 직렬화/역직렬화 오류 처리

---

## 📁 프로젝트 구조

```
ajou.roadmate/
│
├── global/
│   ├── config/
│   │   ├── CorsConfig
│   │   ├── RedisConfig
│   │   ├── RestTemplateConfig
│   │   └── SwaggerConfig
│   ├── exception/
│   │   ├── CustomException
│   │   ├── ErrorCode (Interface)
│   │   ├── ErrorResponse
│   │   ├── GPTErrorCode
│   │   ├── POIErrorCode
│   │   ├── RouteErrorCode
│   │   └── UserErrorCode
│   └── utils/
│       └── UserContext
│
├── gpt/
│   ├── controller/
│   │   └── NlpController
│   ├── service/
│   │   ├── OpenAiNlpService
│   │   ├── NlpOrchestrationService
│   │   ├── ContextService
│   │   ├── RouteInfoService
│   │   └── FeedbackService
│   └── dto/
│       ├── ChatContext
│       ├── LocationInfo
│       ├── Message
│       ├── NlpAnalysisResult
│       ├── NlpRequestDto
│       └── NlpResponseDto
│
├── route/
│   ├── controller/
│   │   └── RouteController
│   ├── service/
│   │   ├── TmapRouteService
│   │   └── AccessibilityService
│   └── dto/
│       ├── RouteRequest
│       ├── RouteResponse
│       └── TmapRouteResponse
│
├── poi/
│   ├── controller/
│   │   └── POIController
│   ├── service/
│   │   └── TmapPOIService
│   └── dto/
│       ├── POISearchRequest
│       ├── POISearchResponse
│       ├── POIItem
│       └── TmapPOIResponse
│
└── user/
    ├── controller/
    │   └── UserController
    ├── service/
    │   ├── UserService
    │   └── AuthService
    ├── domain/
    │   └── User
    └── dto/
        ├── SignUpRequest
        ├── SignUpResponse
        ├── SignInRequest
        └── SignInResponse
```

---

## 📡 API 명세

### NLP API

**POST** `/nlp/chat`

대화형 자연어 처리 및 Intent 분석

| Request | Type | Description |
|---------|------|-------------|
| sessionId | String | 세션 식별자 (UUID) |
| text | String | 사용자 입력 텍스트 |

| Response | Type | Description |
|----------|------|-------------|
| intent | String | 분류된 Intent |
| status | Enum | COMPLETE / INCOMPLETE / API_REQUIRED / ERROR |
| responseMessage | String | 응답 메시지 |
| data | Object | Intent별 추출된 데이터 |

---

### Route API

**POST** `/api/route/search`

출발지-목적지 간 최적 경로 탐색

| Request | Type | Description |
|---------|------|-------------|
| sessionId | String | 세션 식별자 |
| startLat | Double | 출발지 위도 |
| startLon | Double | 출발지 경도 |
| endLat | Double | 목적지 위도 |
| endLon | Double | 목적지 경도 |

| Response | Type | Description |
|----------|------|-------------|
| totalDistance | Integer | 총 거리 (m) |
| totalTime | Integer | 총 소요시간 (초) |
| totalFare | Integer | 총 요금 (원) |
| guides | Array | 구간별 길안내 정보 |
| accessibilityInfo | Object | 전체 경로 접근성 정보 |

**GuideInfo 구조**
- guidance: 안내 문구
- distance: 구간 거리
- time: 구간 소요시간
- transportType: 교통수단 (WALK / BUS / SUBWAY)
- stationAccessibility: 역별 엘리베이터/에스컬레이터 정보

---

### POI API

**POST** `/api/poi/search`

현재 위치 기반 관심 지점 검색

| Request | Type | Description |
|---------|------|-------------|
| destination | String | 검색 키워드 |
| currentLat | Double | 현재 위도 |
| currentLon | Double | 현재 경도 |

| Response | Type | Description |
|----------|------|-------------|
| places | Array | 검색된 장소 목록 |
| totalCount | Integer | 전체 결과 수 |

---

## 🔄 데이터 처리 파이프라인

### NLP 처리 흐름

```
1. 음성 입력 (Frontend STT)
2. 텍스트 → OpenAI API 전송
3. Intent 분류 및 Entity 추출
4. Intent별 처리
   - extract_route: 경로 탐색 API 호출 필요
   - real_time_*: 실시간 정보 조회
   - total_* / section_*: 캐시된 경로 정보 반환
   - feedback: 피드백 저장
5. 응답 생성 및 컨텍스트 업데이트
6. Redis 저장 (대화 히스토리 + 경로 정보)
```

### 경로 추천 흐름

```
1. 경로 탐색 요청 수신
2. Tmap API 호출 (최대 5개 경로 요청)
3. 각 경로 분석
   - 역명 추출
   - 접근성 데이터 매핑 (엘리베이터/에스컬레이터)
   - 접근성 점수 계산
4. 사용자 피드백 조회 (Redis)
5. 가중치 기반 경로 점수 산출
6. 최적 경로 선택
7. LineString 좌표 및 안내 정보 가공
8. 컨텍스트 저장 (향후 질의 대응)
```

---

## 🎯 기술적 특징

### 1. Fine-tuned GPT 모델 활용
- 고령자 발화 패턴 학습
- 일관된 JSON 응답 구조
- 18개 Intent 정확 분류

### 2. 접근성 우선 경로 추천
- 실제 지하철역 엘리베이터/에스컬레이터 데이터 활용
- 다차원 가중치 기반 점수 계산
- 사용자 피드백 실시간 반영

### 3. 컨텍스트 기반 대화 관리
- Redis 기반 세션별 상태 저장
- 대화 히스토리 유지
- 경로 정보 캐싱으로 반복 질문 최적화

### 4. 외부 API 통합
- Tmap Transit/POI API 활용
- RESTful 통신 및 에러 핸들링
- 응답 데이터 정규화 및 가공

---

## 📦 주요 의존성

```
Spring Boot 3.2.x
Spring Web MVC
Spring Data Redis
Redis 7.0
RestTemplate
Jackson (JSON Processing)
Lombok
Swagger/OpenAPI 3.0
```

---

## 📄 License

This project is licensed under the MIT License.
