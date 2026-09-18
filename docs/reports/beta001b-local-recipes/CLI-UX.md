# CLI-UX — Recipes (BETA-001B)

## Create — interactive (bare `recipe add` on a terminal)

```
> pigeonhub recipe add
Create a PigeonHub Recipe
Name: ZCode research
Command (mark the prompt with {prompt}, if any): zcode -p "{prompt}"
Default prompt (optional, Enter to skip): 현재 repo를 검사하고 테스트 실패를 수정해줘.
Working directory (optional, Enter for the current directory):

Recipe 'zcode-research' created.

Run it with:
  pigeonhub recipe run zcode-research
```

- Every field accepts Enter-to-accept when prefilled via flags; EOF
  (Ctrl+Z/Ctrl+D) cancels without writing.
- Prompt recipes are auto-detected from `{prompt}` in the command — there is
  no separate `--prompt-template` flag to memorize.
- If a flag-supplied default prompt exists but the command has no `{prompt}`,
  the CLI says so and (non-interactive) drops it instead of storing dead data.

## Create — non-interactive

```
pigeonhub recipe add --name "Nightly crawler" -- python crawler.py
pigeonhub recipe add --name "Blender render" -- blender -b project.blend -a
pigeonhub recipe add --name "ZCode research" --prompt "초기 프롬프트" -- zcode -p "{prompt}"
pigeonhub recipe add --name "Priced render" --cwd "D:\path with spaces" -- ffmpeg -i in.mp4 out.mp4
```

- Duplicates: `Recipe 'x' already exists. Use --replace to update it.` (exit 1)
- Credential-shaped content: interactive → warning + `Store anyway? [y/N]`;
  non-interactive → warning on stderr, add proceeds (documented, not a boundary).

## List / show

```
> pigeonhub recipe list
Recipes

  nightly-crawler   Nightly crawler
  zcode-research    ZCode research

> pigeonhub recipe show zcode-research
zcode-research
  Name: ZCode research
  Command: zcode -p '{prompt}'
  Working directory: (the directory you run it from)
  Uses prompt: yes
  Default prompt: configured (stored locally, not shown)
```

`list` never prints prompts. `show` confirms a default prompt exists without
printing it.

## Run

```
pigeonhub recipe run zcode-research                     # stored default prompt
pigeonhub recipe run zcode-research --prompt "..."      # runtime override
pigeonhub recipe run zcode-research --prompt-file p.txt # avoids shell history
```

Prompt precedence: `--prompt` → `--prompt-file` → stored default → interactive
`Prompt:` (TTY only; non-TTY without any prompt = clear error, no job started).
A `--prompt` against a prompt-less recipe is ignored with a stderr note.
Exit code = the child's exit code (42 → 42). Ctrl+C propagates as in
`pigeonhub run` (no DONE published, no hang).

## Remove

```
> pigeonhub recipe remove zcode-research
Remove 'ZCode research' (zcode-research)? [y/N] y
Removed recipe 'zcode-research'. Only the recipe was deleted; agent configs,
credentials, and job history are untouched.
```

`--yes` skips the prompt for scripts.

## Discoverability

Connected-state first screen adds one block, kept short:

```
Run something often? Save it once:
  pigeonhub recipe add
```

## E2E demo (source build, then packaged exe)

```
> pigeonhub --version
PigeonHub CLI 0.19.0-beta.1
```
