# QueueSystem

Spring Boot, Redis **대기열 시스템**  

## 2026-07-30 업데이트

YuGiOhDeck에서 운영 중인 대기열 구현을 독립 프로젝트에 동기화했다.

- `site`, `predict` 그룹별 Redis 키 분리
- `vip`, `main` 대기열과 전체 Running 정원 통합 관리
- Redis Lua 스크립트로 입장 및 승급을 원자적으로 처리
- WebSocket 세션을 `group + userId` 기준으로 관리하고 중복 연결 교체
- WebSocket 종료는 일시 장애로 간주하며, 명시적 `/queue/leave` 또는 heartbeat TTL 만료 때만 Redis에서 제거
- WebSocket 연결 파라미터 검증 및 동시 전송 보호
- 5초 heartbeat와 그룹별 세션 TTL 만료 처리
- Running 이탈·만료 시 Waiting 사용자를 즉시 승급
- Redis 정렬 결과의 인덱스로 순번을 계산해 사용자별 `STATUS` 메시지 전송
- `STATUS.running`은 전체 Running이며 `runningVip`, `runningMain`을 함께 제공
- REST API의 `group`, `qid`, `userId` 입력값 검증
- 관리자 API에서 그룹별 `throughput`, `sessionTtlMillis`, `maxRunning` 설정 지원
- React 데모가 현재 호스트의 WebSocket을 사용하고 heartbeat를 전송하도록 변경

기본 TTL은 `30,000ms`, 기본 최대 Running 수는 그룹별 `30명`이다. 운영 환경에서는 Redis의 `config:{site}`, `config:{predict}` 해시로 값을 조정할 수 있다.

검증:

```bash
cd queueSystem
./gradlew test

cd ../front/my-app
npm ci
npm run build
```

---

## 📦 주요 기능

- **동시 접속자 N명 제한**: 초과 사용자는 순서 기반 대기열로 이동  
- **Redis SET + ZSET** 활용 => **멀티 큐 확장**: `vip`, `main` 등 `qid` 파라미터로 여러 등급 큐 운영  
  - `active_users` (SET): 서비스 이용 중  => `running:<qid>` (ZSET): 서비스 이용 중 (`score = 입장 시각`)  
  - `waiting_queue` (ZSET): 대기열 (`score = timestamp`) => `waiting:<qid>` (ZSET): 대기열 (`score = 대기 시작 시각`)  
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
  WSH -- heartbeat 이벤트 --> QS
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
| 입장 직후 즉시 퇴장        | React Strict-Mode 더블 언마운트, WebSocket 재연결 | WS close에서는 제거하지 않고 heartbeat TTL로 처리 |
| 대기번호 역주행           | `waiting + 1` 잘못 계산                           | waiting 감소분만 반영                     |
| 대기번호 미갱신           | `/queue/position` API 오류/폴링 실패                | HTTP 폴링 제거, waiting diff 로컬 계산      |
| Bean 순환 참조         | Service ↔ WebSocketHandler 상호 의존              | `QueueNotifier` 인터페이스로 단방향 참조       |
| VIP 진입 시 Main 화면 순번 오류   | 전체 브로드캐스트(`notifier.broadcast`) 사용 | 큐별 대기자만 `sendToUser`로 브로드캐스트 변경  |
| diff 계산 오차               | diff+REST 혼용 → race condition 발생   | WebSocket으로 **절대 pos** 푸시 방식 전환  |
| Redis `KEYS` 명령 과부하      | 대규모 키 스캔 시 블로킹                     | 운영 시 `SCAN` 명령으로 변경              |
|VIP 큐 변화 시, main 큐 갱신X|broadcast에서 qid에 해당 대기열만 STATUS 메시지 전송|VIP 큐 변화 시 main 큐 상태도 함께 전송</br>→ 각 사용자에게 절대 순번이 포함된 STATUS 메시지 전송|

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
### 3) 멀티 큐 지원 추가(완료)

#### Redis 키 네이밍

| 큐 ID(qid) | 실행중 (ZSET)     | 대기열 (ZSET)     |
| --------- | -------------- | -------------- |
| `vip`     | `running:vip`  | `waiting:vip`  |
| `main`    | `running:main` | `waiting:main` |

---

#### REST 컨트롤러

```java
    @PostMapping("/enter")
    public QueueService.QueueResponse enter(@RequestParam String qid, @RequestParam("userId") String userId) {
        return svc.enter(qid, userId);
    }

    @PostMapping("/leave")
    public void leave(@RequestParam String qid, @RequestParam("userId") String userId) {
        svc.leave(qid, userId);
    }

    @GetMapping("/status")
    public QueueService.QueueStatus status(@RequestParam String qid) { return svc.status(qid); }

    @GetMapping("/position")
    public Map<String, Long> QueuePosition(@RequestParam String qid, @RequestParam("userId") String userId) {
        return svc.QueuePosition(qid, userId);
    }
}
```

#### Admin 컨트롤러(throughput, ttl 갱신)

```java
@RestController
@RequestMapping("/admin/queue")
@RequiredArgsConstructor
public class QueueAdminController {

    private final RedisTemplate<String, String> redis;

    /** 현재 설정 조회 */
    @GetMapping("/{qid}")
    public QueueConfig getConfig(@PathVariable String qid) {
        Map<Object,Object> m = redis.opsForHash().entries("config:" + qid);
        return QueueConfig.from(m);
    }

    /** throughput, sessionTtlMillis 동시 갱신 */
    @PostMapping("/{qid}")
    public void updateConfig(@PathVariable String qid,
                             @RequestParam(required = false) Integer throughput,
                             @RequestParam(required = false) Long sessionTtlMillis) {
        if (throughput != null) {
            redis.opsForHash().put("config:" + qid, "throughput", throughput.toString());
        }
        if (sessionTtlMillis != null) {
            redis.opsForHash().put("config:" + qid, "sessionTtlMillis", sessionTtlMillis.toString());
        }
    }
```

#### `broadcastStatus(qid)` (절대 pos 보정)

```java
 private void broadcastStatus(String qid) {
        try {
             // 1) 현재 상태 집계
            long runningCnt = size(RUNNING_PREFIX + qid);
            long vipCnt = size(WAITING_PREFIX + "vip");
            long mainCnt = size(WAITING_PREFIX + "main");
            long totalWaiting = vipCnt + mainCnt;

            // 2) 이 큐의 대기자 리스트
            String waitKey = WAITING_PREFIX + qid;
            Set<String> waiters = redis.opsForZSet().range(waitKey, 0, -1);
            if (waiters == null) return;

            // 3) 각 사용자에게 개별 STATUS+pos 전송
            for (String uid : waiters) {
                // ZSET 내 0-based rank → 1-based 순번
                Long rank = redis.opsForZSet().rank(waitKey, uid);
                long localRank = (rank == null ? 0L : rank) + 1;

                // main 큐라면 VIP 수 보정
                long pos = qid.equals("main")
                         ? vipCnt + localRank
                         : localRank;

                // JSON 생성
                ObjectNode node = om.createObjectNode()
                    .put("type",        "STATUS")
                    .put("qid",         qid)
                    .put("running",     runningCnt)
                    .put("waitingVip",  vipCnt)
                    .put("waitingMain", mainCnt)
                    .put("waiting",     totalWaiting)
                    .put("pos",         pos);

                // 개인 세션으로 전송
                notifier.sendToUser(uid, node.toString());
            }
        } catch (Exception e) {
            log.error("broadcast fail", e);
        }
    }
```

#### React 클라이언트 처리

```jsx
ws.onmessage = e => {
  const msg = JSON.parse(e.data);
  if (msg.type==='STATUS' && msg.qid===qid) {
    setRunning(msg.running);
    setWaitingVip(msg.waitingVip);
    setWaitingMain(msg.waitingMain);
    setWaiting(msg.waitingVip + msg.waitingMain);
    setPos(msg.pos);
  }
};
```


## 📖 참고 자료

* [G마켓 Dev Blog: Redcarpet 대기열 구조](https://dev.gmarket.com/46)
* [Redis Sorted Set (ZSET) 문서](https://redis.io/docs/data-types/sorted-sets/)
* [Spring Boot WebSocket 가이드](https://spring.io/guides/gs/messaging-stomp-websocket/)

---
