# 🏃‍♂️ LocalNow — 로컬 이벤트 지도 기반 탐색 플랫폼

> 흩어진 지역 이벤트 정보를 하나로 모아, 지도 위에서 한눈에 탐색하고 참여하는 Android 앱

[![Android](https://img.shields.io/badge/Android-3DDC84?style=flat-square&logo=android&logoColor=white)]()
[![Python](https://img.shields.io/badge/Python-3776AB?style=flat-square&logo=python&logoColor=white)]()
[![Flask](https://img.shields.io/badge/Flask-000000?style=flat-square&logo=flask&logoColor=white)]()
[![SQLite](https://img.shields.io/badge/SQLite-003B57?style=flat-square&logo=sqlite&logoColor=white)]()

---

## 📌 프로젝트 배경

지역 축제, 공연, 전시 정보가 구청 홈페이지·SNS·블로그 등에 **분산**되어 있어 탐색이 불편하고, 거리감 파악이 어려우며, 일정 관리가 번거롭습니다.

**LocalNow**는 이 문제를 해결하기 위해 공공데이터 API와 웹 크롤링으로 정보를 자동 수집하고, Kakao Maps 위에 실시간 마커로 시각화하여 **내 주변 이벤트를 직관적으로 탐색**할 수 있게 합니다.

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| **이벤트 자동 수집** | Selenium + BeautifulSoup4 동적 크롤링, 공공데이터 API 연동, APScheduler 자동 갱신 |
| **지도 시각화** | Kakao Maps SDK v2 마커 렌더링, GPS 실시간 위치 탐지, 드래그 제스처 마커 동기화 |
| **카테고리 필터링** | 축제·공연·교육·전시 자동 분류, 날짜·지역 복합 필터 |
| **북마크 & 캘린더** | 원터치 저장 → 캘린더 자동 등록, 알림·캘린더 통합 관리 |
| **D-Day 푸시 알림** | AlarmManager 로컬 알림, 관심 키워드 신규 이벤트 즉시 알림, WorkManager 백그라운드 처리 |
| **실시간 채팅** | Flask-SocketIO + Eventlet 비동기 엔진, 이벤트별/반경별 채팅방 분리 |

---

## 🏗 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        1. Data Layer                            │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐  │
│  │ 공공데이터포털 API │  │ Selenium WebDriver│  │ BeautifulSoup4│  │
│  └────────┬─────────┘  └────────┬─────────┘  └───────┬───────┘  │
│           └──────────────┬──────┘                     │          │
│                    정규화 (RegEx → YYYY-MM-DD)         │          │
│                    Geocoding (주소 → 좌표)              │          │
│                    SQLite + SQLAlchemy ORM             │          │
├─────────────────────────────────────────────────────────────────┤
│                        2. Server Layer                          │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐  │
│  │ Flask REST API   │  │ APScheduler      │  │ Flask-SocketIO│  │
│  │ JSON 응답        │  │ 자동 스케줄링      │  │ + Eventlet    │  │
│  └──────────────────┘  └──────────────────┘  └───────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                        3. Client Layer                          │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐  │
│  │ Kotlin/Java      │  │ Kakao Maps SDK   │  │ WorkManager   │  │
│  │ MVVM Pattern     │  │ Glide 이미지 캐싱  │  │ 백그라운드 알림 │  │
│  │ Retrofit2+OkHttp │  │ 마커 렌더링        │  │ AlarmManager  │  │
│  └──────────────────┘  └──────────────────┘  └───────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🛠 기술 스택

### Frontend (Android)

| 구분 | 기술 |
|------|------|
| 언어 | Kotlin, Java |
| 플랫폼 | Native Android (MVVM) |
| 빌드 | Gradle (AGP 8.2.0, Kotlin 1.9.10) |
| 네트워킹 | Retrofit2 + OkHttp, Gson |
| 지도 | Kakao Maps SDK v2 |
| 이미지 | Glide |
| 백그라운드 | WorkManager, AlarmManager |
| 인증 | Google Play Services Auth |

### Backend (Python)

| 구분 | 기술 |
|------|------|
| 프레임워크 | Flask |
| 데이터베이스 | SQLite + SQLAlchemy ORM |
| 크롤링 | Selenium + BeautifulSoup4 |
| 스케줄링 | APScheduler |
| 실시간 통신 | Flask-SocketIO + Eventlet |

---

## 📁 프로젝트 구조

```
LocalNowR/
├── app/                          # Android 앱 모듈
│   └── src/main/
│       ├── java/.../localnow/    # Kotlin/Java 소스
│       └── res/                  # 레이아웃, 리소스
├── backend/                      # Flask 백엔드 서버
│   ├── app.py                    # Flask 메인 서버
│   ├── crawler/                  # 크롤링 모듈
│   └── models/                   # SQLAlchemy 모델
├── db/                           # 데이터베이스 스키마/마이그레이션
├── gradle/wrapper/               # Gradle Wrapper
├── build.gradle.kts              # 루트 빌드 설정
├── settings.gradle.kts           # 프로젝트 설정 (Kakao Maven 포함)
├── install_and_launch.sh         # APK 빌드 & 설치 스크립트
├── run_tests.sh                  # 테스트 실행 스크립트
└── check_db.py                   # DB 상태 확인 유틸
```

---

## 🚀 시작하기

### 사전 요구사항

- Android Studio Hedgehog (2023.1.1) 이상
- Python 3.9+
- Kakao Developers 앱 키 ([발급하기](https://developers.kakao.com))

### 1. 클론

```bash
git clone https://github.com/LocalNow/LocalNowR.git
cd LocalNowR
```

### 2. 백엔드 서버 실행

```bash
cd backend
pip install -r requirements.txt
python app.py
```

> 서버가 기본적으로 `http://localhost:5000`에서 실행됩니다.

### 3. Android 앱 빌드

1. Android Studio에서 프로젝트 열기
2. `local.properties`에 Kakao 앱 키 설정
3. Run (또는 CLI: `./gradlew installDebug`)

```bash
# 또는 스크립트로 빌드 & 설치
chmod +x install_and_launch.sh
./install_and_launch.sh
```

---

## 📱 스크린샷

> 추후 앱 스크린샷 추가 예정
> 
> | 지도 탐색 | 이벤트 리스트 | 캘린더 | 실시간 채팅 |
> |:---------:|:----------:|:------:|:----------:|
> | 지도 위 마커로 주변 이벤트 탐색 | 카테고리별 필터링 | 북마크 일정 자동 연동 | 이벤트별 채팅방 |

---

## 🗺 개선 방향

| 항목 | 내용 |
|------|------|
| 크롤링 안정화 | 동적 렌더링 타임아웃 예외 처리 강화 |
| UX 개선 | 지도 중심 경험 강화, 클러스터링 마커 도입 |
| AI 기반 추천 | 북마크 패턴 분석으로 개인화 추천 엔진 |
| 채팅 서버 개선 | 분산 처리 및 메시지 영속성 (Redis 도입) |
| 원스톱 예매 | 이벤트 예매까지 연동한 통합 플랫폼 확장 |

---

## 📄 라이선스

이 프로젝트는 학술 목적으로 개발되었습니다.

---

<p align="center">
  <b>LocalNow</b> — 내 주변 이벤트, 지금 바로 발견하세요 🌟
</p>
