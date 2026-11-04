# QuantumDisasters

마인크래프트 **Paper 26.3** 플러그인. 게임이 시작되면 일정 간격으로 매번 **다른** 자연재해가 무작위로 찾아옵니다.

## 흐름

- `/qd start` 로 게임을 시작하면 **DAY 1** 부터 날짜를 셉니다. 액션바에 `DAY n` 이 표시됩니다.
- `disaster-change-days` 일마다 직전과 **다른** 재해로 바뀝니다.
- 그 기간 동안은 같은 재해가 휴식 시간을 사이에 두고 반복해서 찾아옵니다. (`interval-seconds: 0` 이면 쉬지 않고 계속)
- `survive-days` 일을 버티면 **성공**, 리스폰 모드가 `unlimited` 가 아닐 때 모두 죽거나 탈락하면 **게임 오버**입니다.

재해가 처음 시작되거나 다른 재해로 바뀔 때는 **사이렌**이 울립니다. (이름은 알려주지 않고 바뀌었다는 것만 알 수 있음, 같은 재해가 반복될 때는 울리지 않음)

보스바에 다음 재해까지 남은 시간 / 진행 중인 재해가 표시되고, 재해 직전에 경고 카운트다운이 뜹니다.

## 리스폰 모드 (`respawn.mode`)

| 모드 | 설명 |
|---|---|
| `unlimited` | 죽어도 계속 리스폰 |
| `lives` | `respawn.lives` 개수만큼만 리스폰, 다 쓰면 탈락(관전 모드) |
| `after-disaster` | 재해 중에 죽으면 관전하다가 그 재해가 끝나면 부활 |

## 재해 종류

| ID | 이름 | 내용 | 대처법 |
|---|---|---|---|
| `meteor` | 유성우 | 하늘에서 운석이 떨어져 폭발 | 피하기 |
| `earthquake` | 지진 | 화면이 흔들리고 몸이 휘청이며 지면 블록이 튀어오름 | 높은 곳 피하기 |
| `thunder_storm` | 뇌우 | 천둥번개, 주변에 벼락 | 실내로 |
| `acid_rain` | 산성비 | 지붕 밖에 있으면 피해 + 독 | 지붕 아래 / 투구(피해 절반) |
| `tornado` | 토네이도 | 가까운 플레이어를 쫓아오며 생물·블록을 빨아올림 | 도망치기 |
| `blizzard` | 눈보라 | 바깥에 있으면 얼어붙고 느려짐 | 실내 / 가죽 갑옷 |

서바이벌·모험 모드 플레이어만 영향을 받습니다. (크리에이티브·관전자 제외)

### 추가 예정

- [ ] 홍수 (Flash Flood)
- [ ] 화재 (Fire)
- [ ] 쓰나미 (Tsunami)
- [ ] 눈사태 (Avalanche)
- [ ] 모래 폭풍 (Sandstorm)

## 명령어 (권한 `quantumdisasters.admin`, 기본 OP)

`/quantumdisasters`, `/qd`, `/disasters` 모두 같은 명령어입니다.

| 명령어 | 설명 |
|---|---|
| `/qd start` | 게임 시작 |
| `/qd stop` | 게임 종료 (진행 중인 재해 정리, 날씨 복구) |
| `/qd skip` | 현재 단계 건너뛰기 |
| `/qd trigger <id>` | 특정 재해 즉시 발생 |
| `/qd list` | 재해 목록 |
| `/qd status` | 현재 상태 |
| `/qd reload` | config.yml 다시 불러오기 |

## 설정 (`plugins/QuantumDisasters/config.yml`)

| 항목 | 기본값 | 설명 |
|---|---|---|
| `auto-start` | `false` | 서버가 켜지면 자동 시작 |
| `day-length-seconds` | `1200` | 하루 길이(초). 마인크래프트 하루 = 1200초 |
| `sync-world-time` | `true` | 새 날마다 월드 시간을 아침으로 맞춤 |
| `disaster-change-days` | `1` | 몇 일마다 재해가 바뀌는지 |
| `survive-days` | `10` | 몇 일을 버티면 성공인지 (0 = 무한) |
| `respawn.mode` | `lives` | `unlimited` / `lives` / `after-disaster` |
| `respawn.lives` | `3` | 목숨 개수 (`lives` 모드) |
| `first-delay-seconds` | `15` | 첫 재해까지 대기 시간 |
| `interval-seconds` | `45` | 재해 사이 휴식 시간 (0 = 쉬지 않음) |
| `warning-seconds` | `5` | 경고 카운트다운 |
| `siren.sound` | `minecraft:block.note_block.flute` | 사이렌에 쓸 소리 (리소스팩 커스텀 소리 가능) |
| `siren.volume` / `cycles` / `cycle-ticks` | `1.0` / `3` / `40` | 사이렌 음량, 오르내리는 횟수, 한 번 오르내리는 시간(틱) |
| `block-damage` | `true` | 재해가 블록을 부술지 여부 |
| `worlds` | `[]` | 재해가 일어날 월드 (비우면 전체) |
| `disasters.<id>.enabled` / `duration-seconds` | | 재해별 켜기·끄기, 한 번의 지속 시간 |

## 크로스플랫폼 (GeyserMC)

[Geyser](https://geysermc.org) + Floodgate 와 함께 써서 Bedrock(모바일·콘솔·Windows) 플레이어도 참여하는 것을 목표로 합니다.

- 화면 표시는 Bedrock 에서도 보이는 것만 사용합니다: 액션바, 타이틀, 보스바, 채팅
- 재해 이름에 이모지/특수 기호를 쓰지 않습니다 (Bedrock 글꼴에서 네모로 깨질 수 있음)
- 플레이어는 UUID 로 관리하므로 Floodgate 플레이어(이름 앞 `.` 등)도 목숨·부활이 정상 동작합니다
- 새 재해를 추가할 때도 위 규칙을 지켜주세요

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
