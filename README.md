# 🚌 길벗 (RoadMate)

<div align="center">

<img width="918" height="1832" alt="image" src="https://github.com/user-attachments/assets/834efa76-fde5-43f7-a6df-acbd37b1e921" />


<br/><br/>

![Competition](https://img.shields.io/badge/2025-SW중심대학_디지털_경진대회-orange?style=for-the-badge)
![University](https://img.shields.io/badge/대학-아주대학교-blue?style=for-the-badge)
![Award](https://img.shields.io/badge/수상-우수상-gold?style=for-the-badge)

**AI 기반 고령자 맞춤 대중교통 안내 서비스**

---

## 👥 팀 구성

**아주대학교 RoadMate팀**

<table>
<tr>
<td align="center" width="33%">
<a href="https://github.com/gae-ddong">
<img src="https://github.com/gae-ddong.png" width="100px;" alt="강수빈"/><br/>
<sub><b>강수빈</b></sub>
</a><br/>
<sub>프로젝트 기획 및 설계<br/>Frontend, Design</sub>
</td>
<td align="center" width="33%">
<a href="https://github.com/sebeeeen">
<img src="https://github.com/sebeeeen.png" width="100px;" alt="권세빈"/><br/>
<sub><b>권세빈</b></sub>
</a><br/>
<sub>프로젝트 기획 및 설계<br/>Backend, Docs</sub>
</td>
<td align="center" width="33%">
<a href="https://github.com/nowijnah">
<img src="https://github.com/nowijnah.png" width="100px;" alt="한지원"/><br/>
<sub><b>한지원</b></sub>
</a><br/>
<sub>프로젝트 기획 및 설계<br/>Backend, AI</sub>
</td>
</tr>
</table>
</div>

---

## 🏆 수상 내역

| 대회 | 수상 | 연도 |
|------|------|------|
| 🥈 **SW중심대학협의회 디지털 경진대회** | SW부문 우수상 | 2025 |
| 🥇 **아주대 생성형 AI 활용 아이디어 대회** | 최우수상 | 2025 |
| 🥈 **아주대 SW중심대학 하계 모각소** | 우수상 | 2025 |

---

## 📋 개요

음성 기반 실시간 대중교통 길안내 서비스. OpenAI Fine-tuning을 활용한 자연어 처리와 접근성 기반 경로 추천 알고리즘 구현.

### 핵심 기술

- **OpenAI GPT-3.5 Fine-tuning**: 15개 Intent 분류 및 Entity 추출
- **접근성 점수 알고리즘**: 엘리베이터/에스컬레이터 가중치 기반 경로 추천
- **실시간 음성 대화**: STT/TTS 기반 자연어 인터페이스

---

## 📚 문서

| 문서 | 링크 |
|------|------|
| 📝 **Notion 페이지** | [전체 프로젝트 문서](https://hollow-cow-caa.notion.site/25-234edd4769b481b29eb5ce2d07cad76d?pvs=74) |
| 🎤 **본선 발표자료** | [본선 발표자료](https://drive.google.com/file/d/17y87JAjqbnlfzC7cevjRYevWl3POcAZA/view?usp=drive_link) |
| 💻 **포스터** | [포스터](https://drive.google.com/file/d/1M0v7Fs1jJuljuHw_4VdEV79QabPFgQPt/view?usp=drive_link) |
| 📑 **아이디어 기획서** | [아이디어 기획서](https://drive.google.com/file/d/1a3xr8d1J90kPa4ySuNOwRx61MJEIVI6F/view?usp=drive_link)|

---

## 🛠️ 기술 스택

### Architecture
```
React Native (Mobile) 
    ↓ REST API
Spring Boot (Server)
    ↓
├─ OpenAI API (NLP)
├─ Tmap API (Route)
├─ Google Maps (Display)
└─ Redis (Cache)
```

### Tech Stack

| Layer | Stack |
|-------|-------|
| **Frontend** | React Native, Expo, STT/TTS |
| **Backend** | Spring Boot 3.2, JPA, Redis |
| **AI/ML** | OpenAI GPT-3.5 Fine-tuning |
| **API** | Tmap, Google Maps, DATA.GO.KR |
| **Infra** | Naver Cloud, Docker |

---

## 📁 구조
```
RoadMate/
├── frontend/           # React Native
│   ├── src/
│   │   ├── components/
│   │   ├── screens/
│   │   ├── services/
│   │   └── utils/
│   └── package.json
│
├── backend/            # Spring Boot
│   ├── src/main/java/com/roadmate/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   └── config/
│   ├── Dockerfile
│   └── pom.xml
│
└── docs/               # 문서
```

---

## 📡 API

### Base URL
```
http://localhost:8080/api/v1
```

### Endpoints

#### 1. 음성 의도 분석
```http
POST /voice/analyze
Content-Type: application/json

{
  "text": "서울역에서 강남역 가는 길 알려줘"
}

Response:
{
  "intent": "extract_route",
  "entities": {
    "origin": "서울역",
    "destination": "강남역"
  }
}
```

#### 2. 경로 탐색 (접근성 최적화)
```http
POST /route/search

{
  "origin": {"lat": 37.5547, "lon": 126.9707},
  "destination": {"lat": 37.4979, "lon": 127.0276},
  "accessibility": true
}

Response:
{
  "routes": [{
    "score": 87.5,
    "elevatorRatio": 100,
    "escalatorRatio": 80,
    "totalTime": 35,
    "sections": [...]
  }]
}
```

#### 3. 실시간 질문
```http
POST /realtime/question

{
  "question": "몇 번 버스 타야 해?",
  "routeId": "route-1",
  "currentSection": 2
}

Response:
{
  "answer": "472번 버스를 타시면 됩니다.",
  "busNumber": "472",
  "arrivalTime": 5
}
```

**Swagger UI**: `http://localhost:8080/swagger-ui/index.html`


---

## 📄 라이센스

This project is licensed under the MIT License.

---

<div align="center">

**"말 한마디로 길을 찾는 디지털 동반자"**

</div>
