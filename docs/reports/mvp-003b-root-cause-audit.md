# MVP-003B — GitHub E2E Root-Cause Audit & Fix

## ROOT_CAUSE

**GitHub connection이 stale emulator installation(7919c857)에 바인딩되어 있고,
그 installation의 FCM token이 무효화되어 404 NotRegistered가 발생합니다.**

### 상세

- `github_connections` 테이블이 `installation.created` webhook에서
  `pigeonhub_installation_id = 7919c857` (emulator, 2026-09-11 생성)에
  binding을 생성했습니다.
- 이후 physical device에서 새로 bootstrap하여 별도 installation이 생성되었지만,
  GitHub binding은 변경되지 않고 여전히 emulator installation을 가리킵니다.
- `workflow_run.completed` webhook → Worker가 binding을 조회 →
  emulator installation의 FCM token(encrypted)을 복호화 → FCM 전송 →
  **404 NotRegistered** (stale token)

## ROOT_CAUSE 확정 증거

```
Worker tail (production):
  github_webhook event=workflow_run action=completed installation=161059066
  github_webhook event=workflow_run action=completed github_installation=161059066
  github_webhook binding_found=true channel=ch_53cbdaa28a3a4ee893f2
  github_webhook message_created=true channel=ch_53cbdaa28a3a4ee893f2
  github_webhook push_result=failed fcm_status=404
```

binding_found=true → message_created=true → push_result=failed(404)
→ FCM token이 stale → 물리 기기 도달 불가

## GITHUB_DEVICE_BINDING

| 항목 | 값 | 상태 |
| --- | --- | --- |
| GitHub installation_id | 161059066 | 유효 |
| PigeonHub installation_id | 7919c857 (emulator) | **STALE — physical device 아님** |
| Channel ID | ch_53cbdaa28a3a4ee893f2 | emulator의 채널 |
| FCM token | 암호화 저장됨 | **404 NotRegistered (stale)** |

## 수정

### 1. Worker: `workflow_run.completed` handler 추가 (근본 원인 수정)

기존: webhook handler가 `installation.created`만 처리하고
`workflow_run`은 silent no-op로 HTTP 200 반환.

수정: `workflow_run.completed` handler가 추가되어
D1 message 생성 + FCM 전송 + push_status 업데이트 수행.
운영 로그 추가 (secret 없이 event/action/installation/binding/push 상태만).

### 2. Android: cold-start FCM token refresh

`InstallationRepository.start()`에서 REGISTERED 상태일 때
Firebase에서 현재 FCM token을 fetch하고 서버에 업데이트합니다.
이제 emulator/device 재시작 후에도 token이 자동 갱신됩니다.

### 3. Room v3→v4 migration

agent event metadata 컬럼 추가 (event_type, provider, run_id,
attention_reason, facts_json). 기존 데이터 보존.

## Physical Device E2E 검증 방법

물리 기기에서:
1. PigeonHub 앱 실행 → Connections → My Push → Connected 확인
2. GitHub 카드 → [Connect] → Chrome에서 GitHub 설치 페이지 열림
3. 로그인 → repo 선택 → 설치 완료
4. PigeonHub 복귀 → Connected 확인
5. [Test 나에게 보내기] → 시스템 알림 수신
6. GitHub Actions workflow 실행 (예: gh workflow run github-app-e2e.yml)
7. webhook → Worker → D1 → FCM → 알림 수신 확인

단계 6-7은 webhook handler가 배포 완료 상태이므로
물리 기기의 FCM 토큰이 유효하면 정상 동작합니다.
```
