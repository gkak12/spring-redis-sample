# Spring Redis Sample

Spring Boot + Redis를 활용해 실무에서 자주 사용하는 Redis 핵심 기능을 구현한 샘플 프로젝트입니다.  
단순 사용법을 넘어 **왜 이 패턴을 선택했는지** 설계 근거와 함께 구현했습니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Kotlin 1.9, Java 21 |
| Framework | Spring Boot 3.2 |
| DB | MySQL 8, Spring Data JPA, QueryDSL, Flyway |
| Cache | Spring Data Redis, Lettuce |
| 인증 | JWT (jjwt 0.12.3), Spring Security, OAuth2 Client |
| 모니터링 | Micrometer, Prometheus |
| 기타 | ULID Creator, Commons Pool2 |

---

## 구현 기능

### 1. Redis 자료구조 6종

| 자료구조 | 적용 기능 | 선택 이유 |
|---------|----------|----------|
| **String** | JWT Refresh Token 저장 | TTL 기반 자동 만료 |
| **Hash** | OAuth2 사용자 프로필 캐싱 | 필드 단위 부분 업데이트 가능 |
| **List** | 최근 검색어 (최대 10개) | LPUSH + LTRIM으로 최신순 유지 |
| **Set** | 매장 좋아요 | SADD 1회로 중복 방지 + 추가 동시 처리 |
| **ZSet** | 실시간 인기 검색어 | Score 기반 자동 정렬 |
| **Geo** | 반경 내 매장 검색 | GEOSEARCH로 DB 없이 거리 계산 |

### 2. 캐싱 전략

**@Cacheable + RedisCacheManager**
- 캐시별 개별 TTL 설정 (`nearby-stores`: 10분, `trending-keywords`: 1분)
- 커스텀 키 생성기: `{ClassName}:{methodName}:{params}` 형태로 통일

**Cache Stampede 방지**
- 문제: 캐시 TTL 만료 순간 동시 요청이 모두 DB로 직행
- 해결: 분산 락 기반 Mutex Lock + Double-Checked Locking
```
캐시 Miss → 분산 락 획득 시도
  ├─ 락 획득 성공 → 캐시 재확인(DCL) → DB 조회 → 캐시 저장
  └─ 락 획득 실패 → 100ms 간격 폴링 → 캐시 반환
```

### 3. Write-Behind 패턴 (좋아요 DB 동기화)

Redis Set에 즉시 반영 후 스케줄러가 1분마다 DB 동기화합니다.

```
like/unlike 요청 → Redis Set 즉시 반영 + dirty Set에 storeId 마킹
                                  ↓ (1분마다)
                   StoreLikeSyncScheduler → dirty 매장 Redis ↔ DB diff → INSERT/DELETE
```

- **선택 이유**: 좋아요는 최종 일관성으로 충분한 데이터 → DB 부하 분산
- **안전장치**: 동기화 실패 시 dirty Set 유지 → 다음 주기 자동 재시도
- **서버 재시작 시**: `StoreLikeRestoreRunner`가 DB → Redis 복원 (캐시 워밍업)

> **Write-Through vs Write-Behind 판단 기준**  
> 유실 시 비즈니스 손실이 있는 데이터(재고, 결제) → Write-Through  
> 최종 일관성으로 충분한 데이터(좋아요, 조회수) → Write-Behind

### 4. 분산 락 (Distributed Lock)

SETNX + Lua 스크립트로 직접 구현했습니다.

```kotlin
// 락 획득: SETNX로 ULID 값 저장 (TTL 포함)
fun acquire(lockKey: String): String?

// 락 해제: Lua 스크립트로 소유자 확인 후 원자적 삭제
// → 내 락만 해제 가능, 타 스레드 락을 실수로 삭제하는 문제 방지
fun release(lockKey: String, lockValue: String)
```

Lua 스크립트를 사용하는 이유: GET + DEL을 별도로 실행하면 사이에 다른 스레드가 락을 재획득할 수 있어 원자성이 깨짐

### 5. Rate Limiting (Sliding Window)

Redis ZSet을 활용한 Sliding Window 방식으로 구현했습니다.

```
Fixed Window 문제: 윈도우 경계에서 2배 요청 허용 (59초 60회 + 61초 60회 = 2초에 120회)
Sliding Window:   현재 시점 기준 과거 N초를 항상 계산 → 경계 버그 없음
```

Lua 스크립트로 ZREMRANGE + ZCARD + ZADD를 원자적으로 처리합니다.

| 엔드포인트 | 제한 | 목적 |
|------------|------|------|
| POST /api/auth/login | 1분 5회 | Brute Force 방지 |
| POST /api/auth/signup | 1분 3회 | 스팸 가입 방지 |
| POST /api/stores/{id}/likes | 1분 20회 | 좋아요 도배 방지 |
| GET/POST /api/search/** | 1분 30회 | API 남용 방지 |

### 6. Pipeline

`recordSearch` 에서 `incrementScore` + `expire` 2개 명령을 Pipeline으로 묶어 Redis 왕복 횟수를 2회 → 1회로 줄였습니다.

### 7. 인증 (JWT + OAuth2)

- Access Token (15분) + Refresh Token (7일, Redis 저장)
- OAuth2 소셜 로그인: Google, Kakao, Naver
- 로그인 성공 시 Redis Hash에 사용자 프로필 캐싱 (30분 TTL)

---

## 프로젝트 구조

```
src/main/kotlin/com/spring/redis/sample
├── config/               # RedisConfig, SecurityConfig, WebMvcConfig
├── controller/           # AuthController, StoreController, SearchController, StoreLikeController
├── entity/               # User, Store, StoreLike
├── initializer/          # StoreGeoInitializer, StoreLikeRestoreRunner (캐시 워밍업)
├── ratelimit/            # @RateLimit, RateLimiter, RateLimitInterceptor
├── redis/                # RedisLockManager, CacheStampedeProtector
├── repository/           # JPA Repository + QueryDSL
├── scheduler/            # StoreLikeSyncScheduler (Write-Behind)
├── security/             # JWT Filter, OAuth2 UserService/Handler
└── service/              # AuthService, StoreService, SearchService, StoreLikeService
```

---

## API 목록

### 인증
| Method | URI | 설명 |
|--------|-----|------|
| POST | /api/auth/signup | 회원가입 |
| POST | /api/auth/login | 로그인 (JWT 발급) |
| POST | /api/auth/refresh | Access Token 재발급 |
| POST | /api/auth/logout | 로그아웃 |

### 매장
| Method | URI | 설명 |
|--------|-----|------|
| GET | /api/stores/nearby | DB 기반 반경 내 매장 검색 |
| GET | /api/stores/nearby/geo | Redis Geo 기반 반경 내 매장 검색 |

### 좋아요
| Method | URI | 설명 |
|--------|-----|------|
| POST | /api/stores/{storeId}/likes | 좋아요 |
| DELETE | /api/stores/{storeId}/likes | 좋아요 취소 |
| GET | /api/stores/{storeId}/likes/count | 좋아요 수 조회 |
| GET | /api/stores/{storeId}/likes/check | 좋아요 여부 확인 |

### 검색
| Method | URI | 설명 |
|--------|-----|------|
| GET | /api/search/trending | 실시간 인기 검색어 Top 10 |
| POST | /api/search | 검색어 기록 |
| POST | /api/search/recent/{username} | 최근 검색어 저장 |
| GET | /api/search/recent/{username} | 최근 검색어 조회 |
| DELETE | /api/search/recent/{username} | 최근 검색어 삭제 |

---

## 실행 방법

### 사전 요구사항
- Java 21
- Docker (MySQL, Redis 실행용)

### 1. MySQL + Redis 실행

```bash
# MySQL
docker run -d \
  --name mysql \
  -e MYSQL_ROOT_PASSWORD=your_password \
  -e MYSQL_DATABASE=spring_redis_sample \
  -p 3306:3306 \
  mysql:8.0

# Redis
docker run -d \
  --name redis \
  -p 6379:6379 \
  redis:7 redis-server --requirepass 1234
```

### 2. OAuth2 환경변수 설정 (선택)

```bash
export GOOGLE_CLIENT_ID=your-google-client-id
export GOOGLE_CLIENT_SECRET=your-google-client-secret
export KAKAO_CLIENT_ID=your-kakao-client-id
export KAKAO_CLIENT_SECRET=your-kakao-client-secret
export NAVER_CLIENT_ID=your-naver-client-id
export NAVER_CLIENT_SECRET=your-naver-client-secret
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

---

## Redis Key 구조

| Key 패턴 | 자료구조 | 설명 |
|----------|---------|------|
| `refresh:token:{username}` | String | JWT Refresh Token |
| `user:profile:{username}` | Hash | OAuth2 사용자 프로필 |
| `recent:search:{username}` | List | 최근 검색어 (최대 10개) |
| `store:likes:{storeId}` | Set | 매장 좋아요한 username 집합 |
| `store:likes:dirty` | Set | DB 동기화 대기 중인 storeId 집합 |
| `search:bucket:{yyyyMMddHH}` | ZSet | 시간대별 검색어 점수 |
| `trending:result:{ulid}` | ZSet | 인기 검색어 집계 결과 (임시) |
| `stores:geo` | Geo | 전체 매장 위경도 |
| `nearby-stores::{key}` | String | 반경 검색 캐시 |
| `trending-keywords::{key}` | String | 인기 검색어 캐시 |
| `stampede:lock:{cache}:{key}` | String | Cache Stampede 방지 락 |
| `rate:limit:{ip}:{endpoint}` | ZSet | Rate Limit 요청 이력 |

---

## 모니터링

Prometheus 메트릭 엔드포인트: `GET /actuator/prometheus`
