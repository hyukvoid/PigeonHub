"""BETA-003: Setup Center security contract + onboarding flow.

Covers the campaign's security QA matrix: loopback-only bind, session-token
gating (invalid/expired), GET cannot mutate, cross-origin POST rejected,
unknown actions rejected, confirm-required mutations, and delegation to the
existing setup/pairing engines (no logic duplication, no command execution).
"""

import base64
import http.client
import json
import threading
import time
import unittest
from pathlib import Path
from unittest.mock import patch

from pigeonhub import onboard as ob
from pigeonhub.onboard import SetupCenter, serve


class OnboardServerTests(unittest.TestCase):
    def setUp(self):
        self._old_interval = ob.POLL_INTERVAL_SECONDS
        ob.POLL_INTERVAL_SECONDS = 0.2
        self.center, self.url = serve()
        split = urllib_split(self.url)
        self.host, self.port = "127.0.0.1", int(split["port"])
        self.session = split["session"]
        self.path = split["path"]

    def tearDown(self):
        self.center.state.stop.set()
        self.center.server.shutdown()
        self.center.server.server_close()
        ob.POLL_INTERVAL_SECONDS = self._old_interval

    def request(self, method, path, body=None, headers=None):
        conn = http.client.HTTPConnection(self.host, self.port, timeout=10)
        payload = json.dumps(body).encode() if body is not None else None
        conn.request(method, path, payload, headers or ({"Content-Type": "application/json"} if payload else {}))
        response = conn.getresponse()
        data = response.read()
        conn.close()
        return response.status, data

    # ---- binding / session gate ------------------------------------------
    def test_binds_loopback_only_on_random_port(self):
        addr = self.center.server.server_address
        self.assertEqual(addr[0], "127.0.0.1")
        self.assertNotEqual(addr[1], 0)
        self.assertNotEqual(addr[1], 80)
        self.assertNotEqual(addr[1], 443)

    def test_bad_session_token_is_404_everywhere(self):
        code, _ = self.request("GET", f"{self.path}?session=wrong")
        self.assertEqual(code, 404)
        code, _ = self.request("GET", "/setup")  # missing token entirely
        self.assertEqual(code, 404)
        code, _ = self.request("POST", f"/api/action?session=wrong", {"action": "FINISH"})
        self.assertEqual(code, 404)

    def test_expired_session_is_rejected(self):
        self.center.state.created_at = time.time() - ob.SESSION_TTL_SECONDS - 1
        code, _ = self.request("GET", f"{self.path}?session={self.session}")
        self.assertEqual(code, 404)
        code, _ = self.request("POST", f"/api/action?session={self.session}", {"action": "FINISH"})
        self.assertEqual(code, 404)

    # ---- GET cannot mutate -------------------------------------------------
    def test_get_offers_only_read_routes(self):
        code, _ = self.request("GET", f"/api/action?session={self.session}", )
        self.assertEqual(code, 404)
        code, body = self.request("GET", f"/api/status?session={self.session}")
        self.assertEqual(code, 200)
        payload = json.loads(body)
        self.assertIn("pairing", payload)
        self.assertIn("agents", payload)
        code, body = self.request("GET", f"/api/qr?session={self.session}")
        self.assertEqual(code, 404)  # no pairing started: no QR to leak

    def test_status_never_contains_secrets(self):
        self.center.state.pairing.poll_secret = "phs_secret_value"
        code, body = self.request("GET", f"/api/status?session={self.session}")
        self.assertNotIn("phs_secret_value", body.decode())

    # ---- POST action hardening ---------------------------------------------
    def test_unknown_action_rejected(self):
        for action in ("RUN_COMMAND", "EXEC", "SHELL", "EVAL", "run?command=dir"):
            code, body = self.request(
                "POST", f"/api/action?session={self.session}", {"action": action})
            self.assertEqual(code, 400, action)
            self.assertEqual(json.loads(body)["error"], "unknown_action")

    def test_mutations_require_confirm_flag(self):
        code, body = self.request(
            "POST", f"/api/action?session={self.session}", {"action": "SETUP_CODEX"})
        self.assertEqual(code, 400)
        self.assertEqual(json.loads(body)["error"], "confirm_required")

    def test_cross_origin_post_rejected(self):
        code, _ = self.request(
            "POST", f"/api/action?session={self.session}",
            {"action": "FINISH"},
            headers={"Content-Type": "application/json", "Origin": "http://evil.example:80"},
        )
        self.assertEqual(code, 403)
        code, _ = self.request(
            "POST", f"/api/action?session={self.session}",
            {"action": "FINISH"},
            headers={"Content-Type": "application/json", "Referer": "http://evil.example/x"},
        )
        self.assertEqual(code, 403)

    def test_finish_stops_the_server(self):
        code, body = self.request(
            "POST", f"/api/action?session={self.session}", {"action": "FINISH"})
        self.assertEqual(code, 200)
        self.assertTrue(json.loads(body)["ok"])
        self.assertTrue(self.center.state.stop.is_set())

    # ---- engine reuse -------------------------------------------------------
    def test_setup_agent_uses_existing_engine(self):
        from pigeonhub.agents import SetupPlan, AgentDetection
        from pathlib import Path

        plan = SetupPlan(
            agent="codex",
            detection=AgentDetection("codex", "codex", None, Path("x"), True),
            target=Path("x"), exists=False, changed=True,
            desired=None, change_lines=(),
        )
        with patch.object(ob.agent_engine, "build_setup_plan", return_value=plan) as b, \
                patch.object(ob.agent_engine, "apply_setup", return_value=None) as a:
            code, body = self.request(
                "POST", f"/api/action?session={self.session}",
                {"action": "SETUP_CODEX", "confirm": True})
        self.assertEqual(code, 200)
        self.assertTrue(json.loads(body)["ok"])
        b.assert_called_once_with("codex")
        a.assert_called_once_with(plan)

    def test_setup_agent_noop_does_not_touch_config(self):
        from pigeonhub.agents import SetupPlan, AgentDetection
        from pathlib import Path

        plan = SetupPlan(
            agent="codex",
            detection=AgentDetection("codex", "codex", None, Path("x"), True),
            target=Path("x"), exists=True, changed=False,
            desired=None, change_lines=(),
        )
        with patch.object(ob.agent_engine, "build_setup_plan", return_value=plan), \
                patch.object(ob.agent_engine, "apply_setup") as a:
            code, body = self.request(
                "POST", f"/api/action?session={self.session}",
                {"action": "SETUP_CODEX", "confirm": True})
        self.assertEqual(code, 200)
        self.assertTrue(json.loads(body)["already"])
        a.assert_not_called()

    def test_remove_agent_uses_engine_remove(self):
        from pigeonhub.agents import SetupPlan, AgentDetection
        from pathlib import Path

        plan = SetupPlan(
            agent="zcode",
            detection=AgentDetection("zcode", "zcode", None, Path("x"), True),
            target=Path("x"), exists=True, changed=True,
            desired=None, change_lines=(),
        )
        with patch.object(ob.agent_engine, "build_setup_plan", return_value=plan) as b, \
                patch.object(ob.agent_engine, "remove_setup", return_value=None) as r:
            code, _ = self.request(
                "POST", f"/api/action?session={self.session}",
                {"action": "REMOVE_ZCODE", "confirm": True})
        self.assertEqual(code, 200)
        b.assert_called_once_with("zcode", remove=True)
        r.assert_called_once_with(plan)

    # ---- pairing flow (mocked worker) ---------------------------------------
    def _fake_worker(self, created, poll_results):
        calls = []

        def fake_http_json(url, payload=None, **kwargs):
            calls.append(url)
            if url.endswith("/v1/pairing/requests"):
                return created()
            if url.endswith("/v1/pairing/requests/poll"):
                return poll_results.pop(0)
            return 404, {"error": "nf"}

        return fake_http_json, calls

    def test_pairing_flow_renders_qr_and_converges(self):
        created = lambda: (200, {
            "ok": True, "request_id": "plr_x", "challenge": "phc_x",
            "poll_secret": "phs_secret", "expires_at": "2099-01-01T00:00:00+00:00",
        })
        approved = (200, {"ok": True, "status": "approved", "endpoint": "http://x/e", "write_token": "pct_t", "channel_id": "ch_t"})
        pending = (200, {"ok": True, "status": "pending"})
        fake, calls = self._fake_worker(created, [pending, approved])
        saved = {}
        with patch.object(ob, "http_json", side_effect=fake), \
                patch.object(ob, "_save_redeemed_login", side_effect=lambda b: saved.update(b) or Path("x")):
            code, body = self.request(
                "POST", f"/api/action?session={self.session}", {"action": "START_PAIRING"})
            self.assertEqual(code, 200)
            self.assertTrue(json.loads(body)["ok"])
            # QR is rendered and served as PNG; the poll secret never leaves memory.
            code, png = self.request("GET", f"/api/qr?session={self.session}")
            self.assertEqual(code, 200)
            self.assertEqual(png[:4], b"\x89PNG")
            # The poller converges on approval within the test window.
            for _ in range(100):
                if self.center.state.pairing.status == "approved":
                    break
                time.sleep(0.1)
            self.assertEqual(self.center.state.pairing.status, "approved")
            code, body = self.request("GET", f"/api/status?session={self.session}")
            self.assertEqual(json.loads(body)["pairing"]["status"], "approved")
            self.assertNotIn("phs_secret", body.decode())
            self.assertIn("write_token", saved)

    def test_pairing_approval_survives_codex_auto_connect_attention(self):
        created = lambda: (200, {
            "ok": True, "request_id": "plr_auto", "challenge": "phc_auto",
            "poll_secret": "phs_auto", "expires_at": "2099-01-01T00:00:00+00:00",
        })
        approved = (200, {"ok": True, "status": "approved", "endpoint": "http://x/e", "write_token": "pct_t", "channel_id": "ch_t"})
        fake, _ = self._fake_worker(created, [approved])
        saved = {}
        attention = ob.codex_integration.CodexConnectResult(
            ob.codex_integration.NEEDS_ATTENTION,
            "existing notify preserved",
        )
        with patch.object(ob, "http_json", side_effect=fake), \
                patch.object(ob, "_save_redeemed_login", side_effect=lambda b: saved.update(b) or Path("x")), \
                patch.object(ob.codex_integration, "connect_codex", return_value=attention):
            code, body = self.request(
                "POST", f"/api/action?session={self.session}",
                {"action": "START_PAIRING", "consent": True})
            self.assertEqual(code, 200)
            self.assertTrue(json.loads(body)["ok"])
            for _ in range(100):
                if self.center.state.pairing.status == "approved" and self.center.state.agent_events.get("codex", {}).get("state") == "attention":
                    break
                time.sleep(0.05)
        self.assertEqual(self.center.state.pairing.status, "approved")
        self.assertTrue(self.center.state.auto_connect_consent)
        self.assertEqual(self.center.state.agent_events["codex"]["state"], "attention")
        self.assertIn("write_token", saved)

    def test_pairing_expiry_is_presented_as_expiry(self):
        created = lambda: (200, {
            "ok": True, "request_id": "plr_y", "challenge": "phc_y",
            "poll_secret": "phs_y", "expires_at": "2000-01-01T00:00:00+00:00",
        })
        fake, _ = self._fake_worker(created, [])
        with patch.object(ob, "http_json", side_effect=fake):
            self.request("POST", f"/api/action?session={self.session}", {"action": "START_PAIRING"})
            for _ in range(50):
                if self.center.state.pairing.status == "expired":
                    break
                time.sleep(0.1)
        self.assertEqual(self.center.state.pairing.status, "expired")

    # ---- page ---------------------------------------------------------------
    def test_page_renders_in_both_languages_without_embedding_token(self):
        code, body = self.request("GET", f"{self.path}?session={self.session}")
        page = body.decode()
        self.assertEqual(code, 200)
        # The token is read from the URL at runtime, never embedded in the page.
        self.assertNotIn(self.session, page)
        self.assertIn("location.search", page)
        self.assertIn("PigeonHub", page)
        from pigeonhub.onboard_pages import render_page
        ko_page = render_page(self.session, "ko")
        en_page = render_page(self.session, "en")
        self.assertIn("시작하기", ko_page)
        self.assertIn("Get started", en_page)
        # Navigation/feature parity: identical structure, strings only differ.
        self.assertEqual(ko_page.count("section id="), en_page.count("section id="))
        self.assertNotEqual(ko_page, en_page)

    def test_set_lang_updates_session_preference(self):
        code, body = self.request(
            "POST", f"/api/action?session={self.session}", {"action": "SET_LANG", "lang": "en"})
        self.assertEqual(code, 200)
        self.assertEqual(json.loads(body)["lang"], "en")
        code, body = self.request("GET", f"/api/status?session={self.session}")
        self.assertEqual(json.loads(body)["lang"], "en")


def urllib_split(url):
    from urllib.parse import urlsplit, parse_qs

    parts = urlsplit(url)
    return {
        "port": parts.port,
        "session": parse_qs(parts.query)["session"][0],
        "path": parts.path,
    }


if __name__ == "__main__":
    unittest.main()
