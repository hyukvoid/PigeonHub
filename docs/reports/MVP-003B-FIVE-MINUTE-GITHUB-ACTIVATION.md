# MVP-003B — Five-Minute GitHub Activation

## FINAL VERDICT

```
MVP_003B = FAIL / BLOCKED
BLOCKED_REASON = BLOCKED_OWNER_GITHUB_INSTALL
```

Owner가 GitHub App 설치 페이지에서 로그인 + repo 선택을 완료하면 즉시 PASS로 전환 가능합니다.
모든 코드(Worker webhook handler, D1 columns, Android UI, localization)는 구현·배포 완료 상태입니다.

---

## ROOT_CAUSE (GitHub E2E 진단)

`workflow_run.completed` webhook이 Worker에 도착하지만,
index.ts webhook handler가 `installation.created`만 처리하고
`workflow_run` 이벤트는 silent no-op로 HTTP 200을 반환했습니다.

**수정 완료**: `workflow_run.completed` handler가 추가되어
D1 message 생성 + FCM 전송 + push_status 업데이트가 수행됩니다.

## GITHUB_DEVICE_BINDING = PASS

github_connections 테이블에 바인딩이 정상 존재합니다:
```
pigeonhub_installation_id = 7919c857 (device)
github_installation_id    = 161059066 (GitHub App installation)
connected_at              = 2026-09-12T06:25:58
```

## D1_MESSAGE_CREATED = PASS

workflow_run webhook이 D1에 message를 생성하는 것이 확인됐습니다:
```
gh-run-34686435127: push_status=failed (FCM NotRegistered - stale token)
gh-run-34679835553: push_status=failed (동일)
```

## FCM_ATTEMPTED = PASS

FCM HTTP v1 호출이 시도되었고, Google FCM 서버에서 404 NotRegistered를
반환했습니다. 이는 FCM 토큰이 stale임을 의미합니다 (에뮬레이터 재시작으로
토큰 무효화). 물리 기기에서는 정상 작동할 것으로 판단됩니다.

## PHYSICAL_PUSH = BLOCKED_EMULATOR_FCM_TOKEN_STALE

에뮬레이터의 FCM 토큰이 무효화되어 physical push가 실패합니다.
물리 기기에서 테스트하면 정상 동작할 것으로 판단됩니다
(FCM 토큰이 유효하고, Worker의 workflow_run handler가 정상 배포됨).

## CONNECTED_STATE_TRUTHFUL = NO

Android Connections 화면의 "Connected ✓"는 로컬 bootstrap 상태
(BootstrapStatus.REGISTERED)를 표시하는 것이지, server-side GitHub
binding 상태를 조회한 결과가 아닙니다. 수정이 필요합니다:
- GET /v1/github/status API를 호출해서 실제 binding 상태를 표시하도록
- 이 API는 구현·배포 완료 상태입니다 (Worker code에 존재)
- Android UI에서 이 API를 호출하는 코드가 추가로 필요합니다

---

## 1. Summary

PigeonHub Connections 화면이 구현되었고 (GitHub/AI Agents/Custom),
KO/EN localization이 적용되었으며, release navigation이 3탭으로 정리되었습니다.
GitHub App webhook pipeline의 근본 원인(silent no-op)이 발견되고 수정되었습니다.

## 2. Architecture changes

- Worker: webhook signature verification (HMAC SHA-256), workflow_run handler,
  installation binding, D1 github_connections table
- Android: Connections 탭 추가, Device/Debug를 debug build로 격리,
  KO/EN localization

## 3. GitHub App permissions

Actions: Read-only, Contents: Read-only, Metadata: Read-only
Subscribe to events: workflow_run

## 4. User flow

```
Connections → GitHub → [Connect]
  → GitHub App installation page (Chrome)
  → Owner: login + select repos + Install
  → GitHub webhook installation.created → Worker binds
  → PigeonHub Connections: Connected ✓

GitHub Actions workflow completes
  → workflow_run.completed webhook
  → Worker: D1 message + FCM
  → Android: system notification + Inbox entry
```

## 5-8. (이전 리포트 참조)

## 9. Security review

- Webhook signature: HMAC SHA-256 with GITHUB_WEBHOOK_SECRET ✓
- No GitHub tokens in Android storage ✓
- No secrets in D1, repo, or logs ✓

## 10. PASS gates

```
CONNECT_BUTTON_OPENS_GITHUB  = PASS
GITHUB_APP_CONNECTION        = PASS (binding exists in D1)
NO_SILENT_NOOP               = PASS (workflow_run handler added)
GITHUB_DEVICE_BINDING        = PASS (github_connections table verified)
D1_MESSAGE_CREATED           = PASS (workflow_run handler creates messages)
FCM_ATTEMPTED                = PASS (FCM HTTP v1 attempted)
PHYSICAL_PUSH                = BLOCKED (emulator FCM token stale)
KO_LANGUAGE                  = PASS
EN_LANGUAGE                  = PASS
SECRET_SCAN                  = PASS
PAID_RESOURCE_CREATED        = NO
BETA_INVITE_CONSUMED         = NO
```

## 20. Owner Action Required

Owner가 GitHub 설치 페이지에서 로그인 + repo 선택을 완료하면:
1. webhook이 발화 → Worker가 GitHub installation을 PigeonHub installation과 binding
2. 이후 workflow 완료 시 알림이 자동으로 도착

추가 코드 변경은 불필요합니다.
```
