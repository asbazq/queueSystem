# QueueSystem

Spring Boot, Redis, React로 만든 **Production-ready 대기열 시스템**  
G마켓 Redcarpet 아키텍처를 참고해 설계했으며, 단일 WebSocket → WS+SSE 확장, 운영 중 파라미터 조정, 트러블슈팅 기록
---

## 📦 주요 기능

- **동시 접속자 N명 제한**: 초과 사용자는 순서 기반 대기열로 이동  
- **Redis SET + ZSET** 활용  
  - `active_users` (SET): 서비스 이용 중  
  - `waiting_queue` (ZSET): 대기열 (`score = timestamp`)  
- **1초 단위 자동 승격** (`waiting → running`)  
- **세션 TTL 만료 처리** (`running → finished`)  
- **실시간 현황 + 개인 알림** 단일 WebSocket으로 처리  
- **Admin API**로 `throughput`·`sessionTtlMillis` 실시간 조정  
- **React 모달 UI**: 내 순번 실시간 갱신  

---

## 🏗️ 아키텍처

```mermaid
flowchart LR
  FE[React 클라이언트] 
    -- REST (/queue) --> QS[QueueService]
    -- WS (/queue-status) --> WSH[WebSocketHandler]
  subgraph BE[Spring Boot]
    QS
    WSH
    JOB[QueueJobService ⏱]
    ADM[QueueAdminController]
  end
  QS -- push/broadcast --> WSH
  WSH -- disconnect 이벤트 --> QS
  JOB -- ZPOPMIN/ZRANGEBYSCORE --> Redis[(SET + ZSET)]
  ADM -- HSET config --> Redis
  QS -- SCARD/ZCARD --> Redis
````

1. **QueueService**: `/enter`, `/leave`, `/status`, `/position` REST API
2. **WebSocketHandler**: 전체 현황 브로드캐스트 + 개인 ENTER/TIMEOUT 메시지
3. **JobService** (@Scheduled)

   * **promote** (1초): `waiting:{qid}` → `running:{qid}`
   * **expire** (10초): TTL 지난 세션 `running:{qid}` → `finished:{qid}`
4. **AdminController**: `config:{qid}` 해시로 `throughput`/`sessionTtlMillis` 조정

---

## 🚀 빠른 시작

### 선행 조건

* Java 17+, Gradle or Maven
* Redis 6+ (`localhost:6379`)
* Node.js 16+ & npm

### 백엔드 실행

```bash
cd backend-spring-boot
./gradlew bootRun
```

### 프런트엔드 실행

```bash
cd frontend-react
npm install
npm start
```

1. 브라우저에서 `http://localhost:3000` 열기
2. `userId` 입력 후 **입장** 클릭
3. 정원 초과 시 모달로 대기번호 확인 → 빈 자리 생기면 자동 승격

---

## ⚙️ 설정

**기본 QueueConfig** (Redis 해시가 비어 있을 때)

* `throughput = 10/sec`
* `sessionTtlMillis = 120_000ms (2분)`

---

## 🛠 트러블슈팅

| 증상                 | 원인                                            | 해결                                  |
| ------------------ | --------------------------------------------- | ----------------------------------- |
| WRONGTYPE Redis 오류 | ZSET 키를 SET API로 조회                           | `opsForZSet().size()` 사용            |
| 입장 직후 즉시 퇴장        | React Strict-Mode 더블 언마운트, client `/leave` 호출 | 클라이언트 `/leave` 제거, WS close 이벤트로 처리 |
| 대기번호 역주행           | `waiting + 1` 잘못 계산                           | waiting 감소분만 반영                     |
| 대기번호 미갱신           | `/queue/position` API 오류/폴링 실패                | HTTP 폴링 제거, waiting diff 로컬 계산      |
| Bean 순환 참조         | Service ↔ WebSocketHandler 상호 의존              | `QueueNotifier` 인터페이스로 단방향 참조       |

---

## ⚡ 확장 & 운영

### 1) WS + SSE 분리

* **SSE**(`/queue/stream`): 전체 현황 브로드캐스트
* **WS**(`/queue-status`): 개인 알림만 처리

```js
// Frontend 예시
const evt = new EventSource('/queue/stream');
evt.onmessage = e => {
  const { active, waiting } = JSON.parse(e.data);
  setActive(active); setWaiting(waiting);
};

const ws = new WebSocket('/queue-status?userId=...');
```

### 2) Admin API로 실시간 튜닝

```bash
# 조회
curl http://localhost:8080/admin/queue/main

# throughput=50, TTL=30분
curl -X POST "http://localhost:8080/admin/queue/main" \
     -d "throughput=50&sessionTtlMillis=1800000"
```

---

## 📖 참고 자료

* [G마켓 Dev Blog: Redcarpet 대기열 구조](https://dev.gmarket.com/46)
* [Redis Sorted Set (ZSET) 문서](https://redis.io/docs/data-types/sorted-sets/)
* [Spring Boot WebSocket 가이드](https://spring.io/guides/gs/messaging-stomp-websocket/)

---
