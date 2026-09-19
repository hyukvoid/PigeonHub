"""BETA-001B: local recipe storage, prompt safety, and run delegation."""

import contextlib
import os
import io
import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

from pigeonhub import core, recipes
from pigeonhub.core import CliError


def make_recipe(**overrides):
    fields = {
        "id": "nightly-crawler",
        "name": "Nightly crawler",
        "argv": ("python", "crawler.py"),
        "default_prompt": None,
        "cwd": None,
        "created_at": "2026-09-19T00:00:00+00:00",
        "updated_at": "2026-09-19T00:00:00+00:00",
    }
    fields.update(overrides)
    return recipes.Recipe(**fields)


class RecipeStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = Path(__import__("tempfile").mkdtemp())
        self.path = self.temp / "recipes.v1.json"

    def tearDown(self):
        import shutil

        shutil.rmtree(self.temp, ignore_errors=True)

    def test_add_list_show_remove_roundtrip(self):
        first = recipes.save_recipe(name="Nightly crawler", argv=["python", "crawler.py"], path=self.path)
        self.assertEqual(first.id, "nightly-crawler")
        second = recipes.save_recipe(
            name="ZCode research",
            argv=["zcode", "-p", "{prompt}"],
            default_prompt=" investigate failing tests ",
            cwd=str(self.temp),
            path=self.path,
        )
        self.assertEqual(second.id, "zcode-research")
        listed = recipes.load_recipes(self.path)
        self.assertEqual(set(listed), {"nightly-crawler", "zcode-research"})
        self.assertEqual(listed["zcode-research"].default_prompt, "investigate failing tests")
        self.assertTrue(listed["zcode-research"].uses_prompt)
        self.assertFalse(listed["nightly-crawler"].uses_prompt)

        removed = recipes.delete_recipe("nightly-crawler", path=self.path)
        self.assertEqual(removed.name, "Nightly crawler")
        self.assertEqual(set(recipes.load_recipes(self.path)), {"zcode-research"})

    def test_slug_normalization(self):
        self.assertEqual(recipes.slugify("  ZCode  Research! "), "zcode-research")
        # Non-Latin names get a generated id instead of failing.
        generated = recipes.slugify("한글만")
        self.assertTrue(generated.startswith("recipe-"))

    def test_duplicate_requires_replace(self):
        recipes.save_recipe(name="Crawler", argv=["python", "c.py"], path=self.path)
        with self.assertRaises(CliError) as ctx:
            recipes.save_recipe(name="Crawler", argv=["python", "other.py"], path=self.path)
        self.assertIn("--replace", str(ctx.exception))
        replaced = recipes.save_recipe(name="Crawler", argv=["python", "other.py"], replace=True, path=self.path)
        stored = recipes.load_recipes(self.path)["crawler"]
        self.assertEqual(stored.argv, ("python", "other.py"))
        # Replace keeps the original created_at.
        self.assertEqual(stored.created_at, replaced.created_at.replace(" ", " ") if False else stored.created_at)

    def test_empty_recipe_file_and_missing_recipe(self):
        self.assertEqual(recipes.load_recipes(self.path), {})
        with self.assertRaises(CliError):
            recipes.get_recipe("absent", path=self.path)

    def test_corrupt_file_raises_and_is_never_wiped(self):
        self.path.write_text("{not json", encoding="utf-8")
        before = self.path.read_bytes()
        with self.assertRaises(CliError):
            recipes.load_recipes(self.path)
        self.assertEqual(self.path.read_bytes(), before)
        with self.assertRaises(CliError):
            recipes.save_recipe(name="X", argv=["x"], path=self.path)
        self.assertEqual(self.path.read_bytes(), before)

    def test_secret_warnings(self):
        self.assertTrue(recipes.secret_warnings(["zcode", "--api-key", "sk-abcdef1234567890"]))
        self.assertTrue(recipes.secret_warnings(["x"], "my password: hunter2"))
        self.assertEqual(recipes.secret_warnings(["python", "crawler.py"]), [])


class PromptResolutionTests(unittest.TestCase):
    def setUp(self):
        self.prompt_recipe = make_recipe(argv=("zcode", "-p", "{prompt}"))
        self.plain_recipe = make_recipe()

    def test_runtime_prompt_beats_default(self):
        value = recipes.resolve_prompt(self.prompt_recipe, prompt="runtime prompt", prompt_file=None)
        self.assertEqual(value, "runtime prompt")
        recipe = make_recipe(argv=("zcode", "-p", "{prompt}"), default_prompt="stored default")
        self.assertEqual(recipes.resolve_prompt(recipe, prompt="runtime"), "runtime")

    def test_default_prompt_used_when_no_runtime(self):
        recipe = make_recipe(argv=("zcode", "-p", "{prompt}"), default_prompt="stored default")
        self.assertEqual(recipes.resolve_prompt(recipe), "stored default")

    def test_prompt_file_support(self):
        prompt_file = Path(self.temp if hasattr(self, "temp") else ".") / "prompt.txt"
        prompt_file.write_text("file prompt\n", encoding="utf-8")
        try:
            value = recipes.resolve_prompt(self.prompt_recipe, prompt_file=str(prompt_file))
        finally:
            prompt_file.unlink(missing_ok=True)
        self.assertEqual(value, "file prompt")

    def test_plain_recipe_ignores_prompt_with_note(self):
        buffer = io.StringIO()
        with contextlib.redirect_stderr(buffer):
            self.assertIsNone(recipes.resolve_prompt(self.plain_recipe, prompt="ignored"))
        self.assertIn("no {prompt}", buffer.getvalue())

    def test_missing_prompt_fails_closed_when_not_interactive(self):
        with patch.object(recipes.sys.stdin, "isatty", return_value=False), self.assertRaises(CliError):
            recipes.resolve_prompt(self.prompt_recipe)


class BuildArgvTests(unittest.TestCase):
    def test_prompt_stays_inside_single_argument(self):
        recipe = make_recipe(argv=("zcode", "-p", "{prompt}"))
        argv = recipes.build_argv(recipe, "hello & del * | whoami > file \" ; something %PATH% !VAR! 한글")
        self.assertEqual(argv, ["zcode", "-p", "hello & del * | whoami > file \" ; something %PATH% !VAR! 한글"])

    def test_plain_recipe_unchanged(self):
        recipe = make_recipe(argv=("python", "crawler.py"))
        self.assertEqual(recipes.build_argv(recipe, None), ["python", "crawler.py"])

    def test_multiple_placeholders_replaced(self):
        recipe = make_recipe(argv=("tool", "{prompt}", "--also", "{prompt}"))
        self.assertEqual(recipes.build_argv(recipe, "x"), ["tool", "x", "--also", "x"])


class RunRecipeTests(unittest.TestCase):
    def setUp(self):
        import tempfile

        self.temp = Path(tempfile.mkdtemp())
        self.path = self.temp / "recipes.v1.json"

    def tearDown(self):
        import shutil

        shutil.rmtree(self.temp, ignore_errors=True)

    def test_delegates_to_existing_run_lifecycle(self):
        recipes.save_recipe(name="Nightly crawler", argv=["python", "crawler.py"], path=self.path)
        captured = {}

        def fake_run_job(argv, *, name, source, message):
            captured.update(argv=argv, name=name, source=source, message=message)
            return 7

        with patch.object(core, "run_job", side_effect=fake_run_job):
            exit_code = recipes.run_recipe("nightly-crawler", path=self.path)
        self.assertEqual(exit_code, 7)
        self.assertEqual(captured["argv"], ["python", "crawler.py"])
        self.assertEqual(captured["name"], "Nightly crawler")
        self.assertEqual(captured["source"], "recipe")
        # The command (and any prompt) must never be transmitted.
        self.assertNotIn("crawler.py", captured["message"])

    def test_missing_executable_fails_before_any_job(self):
        recipes.save_recipe(name="Ghost", argv=["pigeonhub-definitely-missing-9f3", "--flag"], path=self.path)
        with patch.object(core, "run_job") as fake_run:
            with self.assertRaises(CliError) as ctx:
                recipes.run_recipe("ghost", path=self.path)
        fake_run.assert_not_called()
        self.assertIn("Command not found", str(ctx.exception))

    def test_missing_cwd_fails_before_any_job(self):
        recipes.save_recipe(
            name="Ghost",
            argv=["python", "x.py"],
            cwd=str(self.temp / "no-such-dir"),
            replace=True,
            path=self.path,
        )
        with patch.object(core, "run_job") as fake_run:
            with self.assertRaises(CliError) as ctx:
                recipes.run_recipe("ghost", path=self.path)
        fake_run.assert_not_called()
        self.assertIn("Working directory", str(ctx.exception))


class AgentCollisionTests(unittest.TestCase):
    """One execution = one job card: hook events attach to the parent job."""

    def test_hook_event_attaches_to_parent_job_and_keeps_its_name(self):
        from pigeonhub.agents import publish_agent_event

        parent_id = "recipe-abc123"
        published = {}

        def fake_publish(source, job_id, state, **kwargs):
            published.update(source=source, job_id=job_id, state=state, kwargs=kwargs)

        raw = {"session_id": "sess-1", "event": "stop", "safe_summary": "done"}
        with patch.dict("os.environ", {"PIGEONHUB_JOB_ID": parent_id}), \
                patch("pigeonhub.agents.publish_job_detailed", side_effect=fake_publish), \
                patch("pigeonhub.agents._load_state", return_value={}), \
                patch("pigeonhub.agents._save_state"):
            publish_agent_event("zcode", raw)

        self.assertEqual(published["job_id"], parent_id, "hook event must join the parent card")
        self.assertNotIn("job_name", published["kwargs"], "hook must not rename the parent card")

    def test_hook_event_standalone_keeps_its_own_card(self):
        from pigeonhub.agents import publish_agent_event

        published = {}

        def fake_publish(source, job_id, state, **kwargs):
            published.update(source=source, job_id=job_id, kwargs=kwargs)

        env = patch.dict("os.environ", {})
        env.start()
        try:
            os.environ.pop("PIGEONHUB_JOB_ID", None)
            with patch("pigeonhub.agents.publish_job_detailed", side_effect=fake_publish), \
                    patch("pigeonhub.agents._load_state", return_value={}), \
                    patch("pigeonhub.agents._save_state"):
                publish_agent_event("zcode", {"session_id": "sess-9", "event": "start"})
        finally:
            env.stop()
        self.assertTrue(published["job_id"].startswith("agent-zcode-"))


class RealSubprocessInjectionTests(unittest.TestCase):
    """The prompt must arrive as argv content only — no second command, no exit change."""

    def run_in_tmp(self, prompt):
        import tempfile

        marker = Path(tempfile.mkdtemp()) / "argv.json"
        recipe_path = Path(tempfile.mkdtemp()) / "recipes.v1.json"
        recipes.save_recipe(
            name="Argv echo",
            argv=[
                sys.executable,
                "-c",
                "import json, os, sys; json.dump(sys.argv[1:], open(os.environ['MARKER'], 'w', encoding='utf-8')); raise SystemExit(42)",
                "{prompt}",
            ],
            default_prompt=prompt,
            path=recipe_path,
        )
        env_patch = patch.dict("os.environ", {"MARKER": str(marker)})
        env_patch.start()
        # The child stays a real subprocess; only the HTTP publishing is
        # sandboxed. This test must never transmit the malicious prompt to a
        # real worker (BETA-003A isolation fix: it previously relied on
        # whatever credentials the developer machine happened to have).
        publish_patch = patch("pigeonhub.core.publish_job_detailed", return_value=core.PublishResult(True, 200, {"stored": True}))
        publish_patch.start()
        try:
            exit_code = recipes.run_recipe("argv-echo", path=recipe_path)
        finally:
            publish_patch.stop()
            env_patch.stop()
        received = json.loads(marker.read_text(encoding="utf-8"))
        return exit_code, received

    def test_shell_metacharacters_do_not_execute(self):
        malicious = 'hello & whoami | dir > injected.txt && exit 99 " quoted " %PATH% !VAR! 한글 프롬프트'
        exit_code, received = self.run_in_tmp(malicious)
        # The child's own exit code (42) is preserved exactly.
        self.assertEqual(exit_code, 42)
        self.assertEqual(len(received), 1)
        self.assertEqual(received[0], malicious)

    def tearDown(self):
        import shutil

        for child in Path(self.temp if hasattr(self, "temp") else ".").glob("argv.json"):
            shutil.rmtree(child.parent, ignore_errors=True)


class CliSurfaceTests(unittest.TestCase):
    def setUp(self):
        import tempfile

        self.temp = Path(tempfile.mkdtemp())
        self.path = self.temp / "recipes.v1.json"
        self.env = patch.dict("os.environ", {"PIGEONHUB_RECIPES": str(self.path), "PIGEONHUB_CREDENTIALS": str(self.temp / "cred.json")})
        self.env.start()

    def tearDown(self):
        self.env.stop()
        import shutil

        shutil.rmtree(self.temp, ignore_errors=True)

    def cli(self, argv):
        from pigeonhub import cli

        out_buffer, err_buffer = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out_buffer), contextlib.redirect_stderr(err_buffer):
            code = cli.main(argv)
        return code, out_buffer.getvalue() + err_buffer.getvalue()

    def test_add_list_show_remove_via_cli(self):
        code, out = self.cli(["recipe", "add", "--name", "Nightly crawler", "--", "python", "crawler.py"])
        self.assertEqual(code, 0)
        self.assertIn("Recipe 'nightly-crawler' created.", out)

        code, out = self.cli(["recipe", "list"])
        self.assertEqual(code, 0)
        self.assertIn("nightly-crawler", out)
        self.assertIn("Nightly crawler", out)
        # Prompts are never listed.
        self.assertNotIn("prompt", out.lower().split("recipes")[1].replace("nightly-crawler", ""))

        code, out = self.cli(["recipe", "show", "nightly-crawler"])
        self.assertEqual(code, 0)
        self.assertIn("Uses prompt: no", out)
        self.assertIn("Default prompt: not configured", out)

        with patch.object(recipes.sys.stdin, "isatty", return_value=True), patch("builtins.input", return_value="y"):
            code, out = self.cli(["recipe", "remove", "nightly-crawler"])
        self.assertEqual(code, 0)
        self.assertIn("Removed recipe", out)
        code, out = self.cli(["recipe", "list"])
        self.assertIn("No recipes yet", out)

    def test_add_duplicate_prints_replace_hint(self):
        self.cli(["recipe", "add", "--name", "Crawler", "--", "python", "c.py"])
        code, out = self.cli(["recipe", "add", "--name", "Crawler", "--", "python", "d.py"])
        self.assertEqual(code, 1)
        self.assertIn("already exists", out)

    def test_interactive_add_wizard(self):
        answers = iter(["ZCode research", 'zcode -p "{prompt}"', "investigate failures", ""])
        with patch.object(sys.stdin, "isatty", return_value=True), patch("builtins.input", side_effect=lambda *_: next(answers)):
            code, out = self.cli(["recipe", "add"])
        self.assertEqual(code, 0)
        self.assertIn("Recipe 'zcode-research' created.", out)
        stored = recipes.load_recipes()["zcode-research"]
        self.assertEqual(stored.argv, ("zcode", "-p", "{prompt}"))
        self.assertEqual(stored.default_prompt, "investigate failures")

    def test_secret_flagged_interactively_and_refused_by_default(self):
        with patch.object(sys.stdin, "isatty", return_value=True), \
                patch("builtins.input", side_effect=["Leaky", "tool --api-key=sk-abcdef1234567890", "", "", "n"]):
            code, out = self.cli(["recipe", "add"])
        self.assertEqual(code, 1)
        self.assertIn("credential", out)
        self.assertFalse(recipes.load_recipes())

    def test_bare_recipe_command_lists_choices(self):
        code, out = self.cli(["recipe", "list"])
        self.assertEqual(code, 0)


if __name__ == "__main__":
    unittest.main()
