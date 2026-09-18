# RECIPE-SCHEMA — recipes.v1.json (BETA-001B)

Location: `%USERPROFILE%\.pigeonhub\recipes.v1.json`
Override for tests/CI: `PIGEONHUB_RECIPES` env var (absolute path).
Writes are atomic (temp file → fsync → `os.replace`), shared with the
credentials writer.

```json
{
  "schema_version": 1,
  "recipes": {
    "zcode-research": {
      "id": "zcode-research",
      "name": "ZCode research",
      "argv": ["zcode", "-p", "{prompt}"],
      "default_prompt": "현재 repo를 검사하고 테스트 실패를 수정해줘.",
      "cwd": "C:\\Projects\\PigeonHub",
      "created_at": "2026-09-19T00:00:00+00:00",
      "updated_at": "2026-09-19T00:00:00+00:00"
    }
  }
}
```

| Field | Rules |
|---|---|
| `id` | slug of the display name: lowercase `[a-z0-9-]`, other chars → `-`; if the name has no Latin letters/digits (e.g. Korean), an id `recipe-<8 hex>` is generated so adds never fail on language. Uniqueness key; `--replace` required to overwrite. |
| `name` | display name, shown on list/show and as the job card title. |
| `argv` | non-empty vector of non-empty strings; `{prompt}` may appear in any token, zero or more times. This is the command — there is no shell string form. |
| `default_prompt` | optional; stored as typed (trimmed). Omitted/null = not configured. |
| `cwd` | optional; must exist at run time (validated before any job starts). |
| `created_at` / `updated_at` | ISO-8601 UTC; `--replace` preserves `created_at`. |

Parsing: entries whose `argv` is missing/empty/non-string are skipped on load
instead of breaking the whole file. A file that cannot be parsed as JSON
raises a clear `CliError` and is never rewritten or deleted by the CLI —
the user fixes (or removes) it; the next successful `save` performs a normal
atomic replacement.

Known-bad loader behavior is deliberate: a broken entry disappears from the
menu, the file on disk stays byte-identical, and `recipe add --replace` of the
same id is the documented repair path.

## Versioning

`schema_version` exists so a future format change can migrate or reject
explicitly. Loader currently accepts `1` (unknown versions with a
`recipes` object still load defensively; unknown per-entry fields are ignored,
not stored).
