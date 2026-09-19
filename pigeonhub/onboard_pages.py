"""BETA-003: Setup Center page (single-page, KO/EN, no external assets).

The template is a plain string with __PLACEHOLDER__ substitution — deliberate,
because the page's JavaScript uses braces heavily and an f-string would make
every brace a landmine. The page is rendered fresh for the session token; all
state changes go through POST /api/action with the same token (the browser
attaches the Origin header, which the server verifies). Status polling is a
GET of /api/status.
"""

from __future__ import annotations

import json

STRINGS = {
    "ko": {
        "tagline": "오래 걸리는 작업이 끝나거나 확인이 필요할 때 알려드려요.",
        "get_started": "시작하기",
        "lang": "English",
        "s_phone": "휴대폰 연결",
        "s_tools": "도구 연결",
        "s_test": "PigeonHub 테스트",
        "s_done": "완료",
        "phone_title": "휴대폰을 연결하세요",
        "phone_hint": "휴대폰 앱에서 아래 QR을 스캔해 주세요.",
        "phone_path": "PigeonHub → 연결 → PC 연결 → QR 스캔",
        "consent_title": "연결된 개발 도구도 자동으로 설정",
        "consent_body": "PigeonHub가 설치된 Codex, ZCode 등의 작업 완료 알림을 연결할 수 있습니다.",
        "consent_check": "지원되는 도구 자동 연결",
        "start_pairing": "휴대폰 연결 시작",
        "waiting": "휴대폰 스캔을 기다리는 중…",
        "approved": "휴대폰 연결 완료 ✓",
        "expired": "QR 코드가 만료됐어요.",
        "new_qr": "새 QR 코드 만들기",
        "qr_error": "QR을 만들지 못했어요. 잠시 후 다시 시도해 주세요.",
        "next_tools": "다음: 도구 연결",
        "tools_title": "어떤 도구를 사용하세요?",
        "tools_hint": "휴대폰 연결 후 지원되는 Codex 완료 알림은 자동 연결됩니다. 다른 도구는 여기서 선택해 연결할 수 있어요.",
        "detected": "감지됨",
        "not_detected": "미감지",
        "not_detected_hint": "먼저 이 도구를 설치해 주세요. PigeonHub가 대신 설치하지 않아요.",
        "connected": "연결됨 ✓",
        "attention": "확인 필요",
        "connect": "연결",
        "remove": "제거",
        "check": "연결 확인",
        "codex_trust_title": "마지막 한 단계",
        "codex_trust_body": "기본 연결은 Codex의 지원되는 notify 완료 알림을 사용하므로 훅 Trust가 필요하지 않아요. 훅 기반 전체 수명주기를 직접 선택한 경우에만 Codex의 검토·신뢰 절차를 따르며, PigeonHub는 신뢰를 우회하지 않아요.",
        "open_codex": "확인",
        "confirm_title": "연결: {name}",
        "confirm_receives": "PigeonHub가 받는 정보:",
        "r_started": "작업 시작",
        "r_progress": "진행 상황",
        "r_done": "작업 완료",
        "r_attention": "확인 필요 여부",
        "confirm_not": "PigeonHub가 받지 않는 정보:",
        "r_prompt": "전체 프롬프트",
        "r_source": "소스 코드",
        "r_transcript": "전체 대화 기록",
        "cancel": "취소",
        "confirm": "연결",
        "test_title": "PigeonHub를 테스트해요",
        "test_body": "휴대폰으로 테스트 알림을 보낼게요.",
        "send_test": "테스트 알림 보내기",
        "test_sent": "알림을 보냈어요 ✓",
        "test_arrived": "휴대폰에 도착했나요?",
        "yes": "네, 도착했어요",
        "try_again": "다시 보내기",
        "test_fail": "알림을 보내지 못했어요. 휴대폰 연결을 다시 확인해 주세요.",
        "done_title": "준비 완료!",
        "done_body": "이제 오래 걸리는 명령을 이렇게 실행하세요:",
        "done_recipe": "자주 쓰는 명령은 Recipe로 저장해 두면 한 번에 실행할 수 있어요.",
        "finish": "끝내기",
        "busy": "처리 중…",
        "set_up": "설정",
        "remove_confirm": "연결을 제거할까요? PigeonHub가 추가한 부분만 제거돼요.",
    },
    "en": {
        "tagline": "Know when your long-running work needs you.",
        "get_started": "Get started",
        "lang": "한국어",
        "s_phone": "Connect phone",
        "s_tools": "Connect tools",
        "s_test": "Test PigeonHub",
        "s_done": "Done",
        "phone_title": "Connect your phone",
        "phone_hint": "Scan this QR with the PigeonHub app on your phone.",
        "phone_path": "PigeonHub → Connections → Connect PC → scan",
        "consent_title": "Set up connected developer tools automatically",
        "consent_body": "PigeonHub can connect completion notifications from installed tools such as Codex and ZCode.",
        "consent_check": "Automatically connect supported tools",
        "start_pairing": "Start phone connection",
        "waiting": "Waiting for your phone…",
        "approved": "Phone connected ✓",
        "expired": "This QR has expired.",
        "new_qr": "Generate a new QR",
        "qr_error": "We couldn't create the QR. Please try again in a moment.",
        "next_tools": "Next: connect tools",
        "tools_title": "What do you use?",
        "tools_hint": "After your phone connects, supported Codex completion notifications connect automatically. Choose other tools here if you need them.",
        "detected": "Detected",
        "not_detected": "Not detected",
        "not_detected_hint": "Install this tool first — PigeonHub won't install it for you.",
        "connected": "Connected ✓",
        "attention": "Needs attention",
        "connect": "Connect",
        "remove": "Remove",
        "check": "Check connection",
        "codex_trust_title": "One last step",
        "codex_trust_body": "The basic connection uses Codex's supported notify completion event, so hook Trust is not required. If you explicitly choose full hook lifecycle events, follow Codex's own review and trust flow; PigeonHub never bypasses trust.",
        "open_codex": "Got it",
        "confirm_title": "Connect {name}",
        "confirm_receives": "PigeonHub receives:",
        "r_started": "task started",
        "r_progress": "progress",
        "r_done": "task completed",
        "r_attention": "attention needed",
        "confirm_not": "PigeonHub does NOT receive:",
        "r_prompt": "your full prompt",
        "r_source": "source code",
        "r_transcript": "full transcript",
        "cancel": "Cancel",
        "confirm": "Connect",
        "test_title": "Test PigeonHub",
        "test_body": "We'll send a test notification to your phone.",
        "send_test": "Send test notification",
        "test_sent": "Notification sent ✓",
        "test_arrived": "Did it arrive?",
        "yes": "Yes, it arrived",
        "try_again": "Try again",
        "test_fail": "We couldn't send it. Check your phone connection and retry.",
        "done_title": "You're ready.",
        "done_body": "For any long-running command:",
        "done_recipe": "Run commands often? Save them as Recipes for one-line reruns.",
        "finish": "Finish",
        "busy": "Working…",
        "set_up": "Set up",
        "remove_confirm": "Remove the integration? Only what PigeonHub added is removed.",
    },
}

AGENT_NAMES = {"codex": "OpenAI Codex", "claude": "Claude Code", "zcode": "ZCode · GLM", "grok": "Grok Build"}

_TEMPLATE = """<!doctype html>
<html lang="__LANG__"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>PigeonHub Setup</title>
<style>
  :root { --brand:#5b5bd6; --ink:#1c1c2e; --muted:#6b6b80; --card:#ffffff; --bg:#f4f4fb; --ok:#1a9c6b; --warn:#c77700; }
  * { box-sizing:border-box; }
  body { margin:0; font-family:'Segoe UI',system-ui,sans-serif; background:var(--bg); color:var(--ink); }
  header { background:var(--brand); color:#fff; padding:18px 24px; display:flex; align-items:center; gap:12px; }
  header h1 { font-size:20px; margin:0; flex:1; }
  header button { background:rgba(255,255,255,.16); color:#fff; border:0; border-radius:20px; padding:7px 16px; font-size:14px; cursor:pointer; }
  main { max-width:760px; margin:0 auto; padding:24px 16px 60px; }
  .card { background:var(--card); border-radius:16px; padding:28px; box-shadow:0 2px 10px rgba(40,40,90,.08); margin-top:18px; }
  .steps { display:flex; gap:6px; margin-top:18px; }
  .stepchip { flex:1; text-align:center; font-size:13px; color:var(--muted); background:#e9e9f6; border-radius:8px; padding:8px 4px; }
  .stepchip.on { background:var(--brand); color:#fff; font-weight:600; }
  h2 { margin:4px 0 10px; font-size:24px; }
  p.sub { color:var(--muted); margin:0 0 18px; }
  button.cta { background:var(--brand); color:#fff; border:0; border-radius:12px; padding:14px 26px; font-size:17px; font-weight:600; cursor:pointer; width:100%; }
  button.cta:disabled { opacity:.55; }
  button.ghost { background:#ececf7; color:var(--ink); border:0; border-radius:12px; padding:12px 22px; font-size:15px; cursor:pointer; width:100%; margin-top:10px; }
  .qr { display:block; width:min(340px, 82vw); margin:16px auto; border-radius:12px; border:1px solid #e3e3f2; }
  .center { text-align:center; }
  .timer { color:var(--muted); font-size:14px; }
  .agentcard { display:flex; gap:14px; align-items:center; border:1px solid #e3e3f2; border-radius:14px; padding:16px; margin-top:12px; }
  .agentcard .meta { flex:1; }
  .agentcard b { font-size:16px; }
  .state { display:inline-block; font-size:12.5px; border-radius:10px; padding:3px 10px; margin-top:4px; background:#ececf7; color:var(--muted); }
  .state.connected { background:#dcf5ea; color:var(--ok); }
  .state.not_detected { background:#f0f0f4; color:#9a9aa8; }
  .state.attention { background:#fdeeda; color:var(--warn); }
  .agentcard button { background:var(--brand); color:#fff; border:0; border-radius:10px; padding:10px 18px; font-size:14.5px; cursor:pointer; }
  .agentcard button.remove { background:#ececf7; color:var(--ink); }
  .agentcard button:disabled { opacity:.5; }
  .privacy { text-align:left; font-size:14.5px; background:#f7f7fc; border-radius:10px; padding:14px; margin:12px 0; }
  ul { margin:6px 0; padding-left:20px; }
  .mono { font-family:Consolas,monospace; background:#ececf7; border-radius:8px; padding:10px 14px; display:inline-block; margin:8px 0; }
  .hidden { display:none; }
  .msg { color:var(--muted); font-size:14px; min-height:20px; margin-top:10px; }
  .big { font-size:42px; }
</style></head>
<body>
<header>
  <h1>PigeonHub</h1>
  <button id="langToggle">__LANG_TOGGLE__</button>
</header>
<main>
  <div class="steps">
    <div class="stepchip" id="chip-phone">__S_PHONE__</div>
    <div class="stepchip" id="chip-tools">__S_TOOLS__</div>
    <div class="stepchip" id="chip-test">__S_TEST__</div>
    <div class="stepchip" id="chip-done">__S_DONE__</div>
  </div>

  <section id="view-welcome" class="card center">
    <div class="big">🕊️</div>
    <h2>PigeonHub</h2>
    <p class="sub" style="font-size:17px">__TAGLINE__</p>
    <button class="cta" onclick="go('phone')">__GET_STARTED__</button>
  </section>

  <section id="view-phone" class="card center hidden">
    <h2>__PHONE_TITLE__</h2>
    <p class="sub">__PHONE_HINT__<br><b>__PHONE_PATH__</b></p>
    <div class="privacy" style="text-align:left">
      <b>__CONSENT_TITLE__</b><br>
      <span>__CONSENT_BODY__</span>
      <label style="display:block;margin-top:12px"><input id="autoConnectConsent" type="checkbox"> __CONSENT_CHECK__</label>
    </div>
    <button id="startPairingBtn" class="cta" onclick="startPairing()" disabled>__START_PAIRING__</button>
    <img id="qr" class="qr" alt="QR">
    <div id="qrMsg" class="msg"></div>
    <div id="pairStatus" class="timer"></div>
    <button id="newQrBtn" class="ghost hidden" onclick="startPairing()">__NEW_QR__</button>
    <button id="toTools" class="cta hidden" onclick="go('tools')">__NEXT_TOOLS__</button>
  </section>

  <section id="view-tools" class="card hidden">
    <h2>__TOOLS_TITLE__</h2>
    <p class="sub">__TOOLS_HINT__</p>
    <div id="agents"></div>
    <button class="cta" style="margin-top:18px" onclick="go('test')">__S_TEST_BTN__</button>
  </section>

  <section id="view-test" class="card center hidden">
    <h2>__TEST_TITLE__</h2>
    <p class="sub">__TEST_BODY__</p>
    <button id="sendTest" class="cta" onclick="sendTest()">__SEND_TEST__</button>
    <div id="testMsg" class="msg"></div>
    <div id="testOk" class="hidden">
      <p style="color:var(--ok);font-weight:600">__TEST_SENT__</p>
      <p>__TEST_ARRIVED__</p>
      <button class="cta" onclick="go('done')">__YES__</button>
      <button class="ghost" onclick="sendTest()">__TRY_AGAIN__</button>
    </div>
  </section>

  <section id="view-done" class="card center hidden">
    <div class="big">✅</div>
    <h2>__DONE_TITLE__</h2>
    <p class="sub">__DONE_BODY__</p>
    <div class="mono">pigeonhub run -- &lt;command&gt;</div>
    <p class="sub">__DONE_RECIPE__</p>
    <button class="cta" onclick="finish()">__FINISH__</button>
  </section>

  <div id="modal" class="hidden" style="position:fixed;inset:0;background:rgba(20,20,40,.45);display:flex;align-items:center;justify-content:center;">
    <div class="card" style="max-width:460px;width:92vw;margin:0">
      <h2 id="mTitle"></h2>
      <div id="mBody"></div>
      <div style="display:flex;gap:10px;margin-top:16px">
        <button class="ghost" style="margin:0" onclick="closeModal()">__CANCEL__</button>
        <button class="cta" style="margin:0" id="mOk">__CONFIRM__</button>
      </div>
    </div>
  </div>
</main>
<script>
const T = __STRINGS__;
const AGENT_NAMES = __AGENT_NAMES__;
const SESSION = new URLSearchParams(location.search).get('session');
let lang = '__LANG__', pollTimer = null;

function t(key, vars) {
  let s = T[key] || key;
  if (vars) for (const k in vars) s = s.split('{' + k + '}').join(vars[k]);
  return s;
}
async function post(action, extra) {
  const r = await fetch('/api/action?session=' + SESSION, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(Object.assign({action: action}, extra || {})),
  });
  return r.json();
}
async function status() {
  const r = await fetch('/api/status?session=' + SESSION);
  return r.json();
}
function go(view) {
  for (const v of ['welcome','phone','tools','test','done']) {
    document.getElementById('view-' + v).classList.toggle('hidden', v !== view);
  }
  const order = ['phone','tools','test','done'];
  for (const c of order) {
    document.getElementById('chip-' + c).classList.toggle('on', order.indexOf(view) >= order.indexOf(c));
  }
  if (view === 'phone') {
    const consent = document.getElementById('autoConnectConsent');
    if (consent) consent.onchange = () => { document.getElementById('startPairingBtn').disabled = !consent.checked; };
  }
  if (view === 'tools') renderAgents();
  if (view === 'done') stopPoll();
}
async function startPairing() {
  const consent = document.getElementById('autoConnectConsent');
  if (!consent || !consent.checked) return;
  document.getElementById('startPairingBtn').disabled = true;
  document.getElementById('qrMsg').textContent = t('busy');
  document.getElementById('newQrBtn').classList.add('hidden');
  await post('START_PAIRING', {consent: true});
  pollPairing();
}
function pollPairing() {
  stopPoll();
  pollTimer = setInterval(async () => {
    const s = await status();
    const p = s.pairing || {};
    const img = document.getElementById('qr');
    const msg = document.getElementById('qrMsg');
    const st = document.getElementById('pairStatus');
    if (p.status === 'waiting') {
      img.src = '/api/qr?session=' + SESSION + '&ts=' + Date.now();
      img.classList.remove('hidden');
      const secs = p.expires_in || 0;
      st.textContent = t('waiting') + ' ' + Math.floor(secs/60) + ':' + String(secs%60).padStart(2,'0');
      msg.textContent = '';
      document.getElementById('newQrBtn').classList.add('hidden');
    } else if (p.status === 'expired') {
      img.classList.add('hidden');
      st.textContent = t('expired');
      msg.textContent = '';
      document.getElementById('newQrBtn').classList.remove('hidden');
    } else if (p.status === 'approved') {
      st.textContent = t('approved');
      img.classList.add('hidden');
      document.getElementById('toTools').classList.remove('hidden');
      document.getElementById('newQrBtn').classList.add('hidden');
      stopPoll();
    } else if (p.status === 'error') {
      img.classList.add('hidden');
      st.textContent = t('qr_error');
      document.getElementById('newQrBtn').classList.remove('hidden');
    }
  }, 1500);
}
function stopPoll() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } }
async function renderAgents() {
  const s = await status();
  const list = document.getElementById('agents');
  list.innerHTML = '';
  for (const a of (s.agents || [])) {
    const name = AGENT_NAMES[a.id] || a.id;
    const div = document.createElement('div');
    div.className = 'agentcard';
    const connected = a.state === 'connected';
    const notDetected = a.state === 'not_detected';
    const stateLabel = connected ? t('connected') : notDetected ? t('not_detected') :
      a.state === 'attention' ? t('attention') : t('detected');
    let buttons = '';
    if (connected) {
      buttons = '<button class="remove" onclick="removeAgent(\\'' + a.id + '\\')">' + t('remove') + '</button>';
      if (a.id === 'codex') {
        buttons = '<button onclick="showCodexTrust()">' + t('check') + '</button>' + buttons;
      }
    } else if (notDetected) {
      buttons = '<button disabled>' + t('set_up') + '</button>';
    } else {
      buttons = '<button onclick="confirmConnect(\\'' + a.id + '\\')">' + t('connect') + '</button>';
    }
    div.innerHTML = '<div class="meta"><b>' + name + '</b><br>' +
      '<span class="state ' + (connected ? 'connected' : notDetected ? 'not_detected' : a.state === 'attention' ? 'attention' : '') + '">' + stateLabel + '</span> ' +
      (!notDetected && a.detail ? '<span class="state">' + a.detail + '</span>' : '') +
      (notDetected ? '<div style="font-size:13px;color:var(--muted);margin-top:4px">' + t('not_detected_hint') + '</div>' : '') +
      '</div>' + buttons;
    list.appendChild(div);
  }
}
function confirmConnect(agent) {
  document.getElementById('mTitle').textContent = t('confirm_title', {name: AGENT_NAMES[agent]});
  document.getElementById('mBody').innerHTML =
    '<p style="margin:4px 0"><b>' + t('confirm_receives') + '</b></p>' +
    '<ul><li>' + t('r_started') + '</li><li>' + t('r_progress') + '</li><li>' + t('r_done') + '</li><li>' + t('r_attention') + '</li></ul>' +
    '<p style="margin:10px 0 4px"><b>' + t('confirm_not') + '</b></p>' +
    '<ul style="color:var(--muted)"><li>' + t('r_prompt') + '</li><li>' + t('r_source') + '</li><li>' + t('r_transcript') + '</li></ul>';
  document.getElementById('mOk').textContent = t('confirm');
  document.getElementById('mOk').onclick = async () => {
    closeModal();
    await post('SETUP_' + agent.toUpperCase(), {confirm: true});
    if (agent === 'codex') showCodexTrust();
    renderAgents();
  };
  document.getElementById('modal').classList.remove('hidden');
}
function showCodexTrust() {
  // Codex requires its own in-app trust; surface the guidance, never bypass it.
  document.getElementById('mTitle').textContent = t('codex_trust_title');
  document.getElementById('mBody').innerHTML = '<p>' + t('codex_trust_body') + '</p>';
  document.getElementById('mOk').textContent = t('open_codex');
  document.getElementById('mOk').onclick = () => { closeModal(); };
  document.getElementById('modal').classList.remove('hidden');
}
async function removeAgent(agent) {
  if (!confirm(t('remove_confirm'))) return;
  await post('REMOVE_' + agent.toUpperCase(), {confirm: true});
  renderAgents();
}
async function sendTest() {
  const btn = document.getElementById('sendTest');
  btn.disabled = true;
  document.getElementById('testMsg').textContent = t('busy');
  document.getElementById('testOk').classList.add('hidden');
  const r = await post('TEST_NOTIFICATION');
  btn.disabled = false;
  document.getElementById('testMsg').textContent = '';
  if (r.ok) document.getElementById('testOk').classList.remove('hidden');
  else document.getElementById('testMsg').textContent = t('test_fail');
}
async function finish() {
  await post('FINISH');
  document.body.innerHTML = '<main><div class="card center" style="margin-top:60px"><div class="big">🕊️</div><h2>PigeonHub</h2><p class="sub">' + T.done_title + '</p></div></main>';
}
function closeModal() { document.getElementById('modal').classList.add('hidden'); }
document.getElementById('langToggle').onclick = async () => {
  const r = await post('SET_LANG', {lang: lang === 'ko' ? 'en' : 'ko'});
  if (r.ok) location.reload();
};
</script>
</body></html>"""


def render_page(token: str, lang: str) -> str:
    strings = STRINGS.get(lang, STRINGS["ko"])
    page = _TEMPLATE
    replacements = {
        "__LANG__": lang,
        "__STRINGS__": json.dumps(strings, ensure_ascii=False),
        "__AGENT_NAMES__": json.dumps(AGENT_NAMES, ensure_ascii=False),
        "__LANG_TOGGLE__": strings["lang"],
        "__S_PHONE__": strings["s_phone"],
        "__S_TOOLS__": strings["s_tools"],
        "__S_TEST__": strings["s_test"],
        "__S_DONE__": strings["s_done"],
        "__TAGLINE__": strings["tagline"],
        "__GET_STARTED__": strings["get_started"],
        "__PHONE_TITLE__": strings["phone_title"],
        "__PHONE_HINT__": strings["phone_hint"],
        "__PHONE_PATH__": strings["phone_path"],
        "__CONSENT_TITLE__": strings["consent_title"],
        "__CONSENT_BODY__": strings["consent_body"],
        "__CONSENT_CHECK__": strings["consent_check"],
        "__START_PAIRING__": strings["start_pairing"],
        "__NEW_QR__": strings["new_qr"],
        "__NEXT_TOOLS__": strings["next_tools"],
        "__TOOLS_TITLE__": strings["tools_title"],
        "__TOOLS_HINT__": strings["tools_hint"],
        "__S_TEST_BTN__": strings["s_test"],
        "__TEST_TITLE__": strings["test_title"],
        "__TEST_BODY__": strings["test_body"],
        "__SEND_TEST__": strings["send_test"],
        "__TEST_SENT__": strings["test_sent"],
        "__TEST_ARRIVED__": strings["test_arrived"],
        "__YES__": strings["yes"],
        "__TRY_AGAIN__": strings["try_again"],
        "__DONE_TITLE__": strings["done_title"],
        "__DONE_BODY__": strings["done_body"],
        "__DONE_RECIPE__": strings["done_recipe"],
        "__FINISH__": strings["finish"],
        "__CANCEL__": strings["cancel"],
        "__CONFIRM__": strings["confirm"],
    }
    for key, value in replacements.items():
        page = page.replace(key, value)
    return page
