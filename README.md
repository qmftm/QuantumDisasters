# QuantumDisasters

마인크래프트 **Paper 26.3** 플러그인. 게임이 시작되면 일정 간격으로 매번 **다른** 자연재해가 무작위로 찾아옵니다.

## 흐름

`/disaster start` → 휴식(카운트다운) → 무작위 재해 → 휴식 → 직전과 다른 재해 → … (`/disaster stop` 까지 반복)

화면 위 보스바에 다음 재해까지 남은 시간 / 진행 중인 재해의 남은 시간이 표시되고, 재해 직전에 경고 카운트다운이 뜹니다.

## 재해 종류

| ID | 이름 | 내용 | 대처법 |
|---|---|---|---|
| `meteor` | ☄ 유성우 | 하늘에서 운석이 떨어져 폭발 | 피하기 |
| `earthquake` | 🌋 지진 | 화면이 흔들리고 몸이 휘청이며 지면 블록이 튀어오름 | 높은 곳 피하기 |
| `lightning` | ⚡ 번개 폭풍 | 천둥번개, 주변에 벼락 | 실내로 |
| `acid_rain` | ☔ 산성비 | 지붕 밖에 있으면 피해 + 독 | 지붕 아래 / 투구(피해 절반) |
| `tornado` | 🌪 토네이도 | 가까운 플레이어를 쫓아오며 생물·블록을 빨아올림 | 도망치기 |
| `blizzard` | ❄ 눈보라 | 바깥에 있으면 얼어붙고 느려짐 | 실내 / 가죽 갑옷 |

서바이벌·모험 모드 플레이어만 영향을 받습니다. (크리에이티브·관전자 제외)

## 명령어 (권한 `quantumdisasters.admin`, 기본 OP)

| 명령어 | 설명 |
|---|---|
| `/disaster start` | 게임 시작 |
| `/disaster stop` | 게임 종료 (진행 중인 재해 정리, 날씨 복구) |
| `/disaster skip` | 현재 단계 건너뛰기 |
| `/disaster trigger <id>` | 특정 재해 즉시 발생 |
| `/disaster list` | 재해 목록 |
| `/disaster status` | 현재 상태 |
| `/disaster reload` | config.yml 다시 불러오기 |

별칭: `/qd`, `/재해`

## 설정 (`plugins/QuantumDisasters/config.yml`)

- `auto-start` — 서버가 켜지면 자동 시작
- `first-delay-seconds` / `interval-seconds` — 첫 재해까지 / 재해 사이 대기 시간
- `warning-seconds` — 경고 카운트다운
- `block-damage` — 재해가 블록을 부술지 여부
- `worlds` — 재해가 일어날 월드 (비우면 전체)
- `disasters.<id>.enabled` / `duration-seconds` — 재해별 켜기·끄기, 지속 시간

## 빌드

Java 25 와 Maven 이 필요합니다.

```bash
mvn package
```

`target/QuantumDisasters-1.0.0.jar` 를 서버의 `plugins/` 폴더에 넣으세요.

## 다른 플러그인에서 게임 시작하기

```java
QuantumDisasters qd = (QuantumDisasters) Bukkit.getPluginManager().getPlugin("QuantumDisasters");
qd.disasterManager().start(); // 게임 시작 시
qd.disasterManager().stop();  // 게임 종료 시
```
