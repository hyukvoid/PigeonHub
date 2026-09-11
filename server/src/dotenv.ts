import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

/**
 * Minimal .env loader (server/.env, gitignored). No dependency: real lines are
 * KEY=VALUE, '#' comments allowed, quotes stripped. Existing non-empty env
 * vars always win so the shell can override.
 */
export function loadDotEnv(): void {
  const here = dirname(fileURLToPath(import.meta.url));
  let text: string;
  try {
    text = readFileSync(join(here, "..", ".env"), "utf8");
  } catch {
    return; // no .env — fine, env vars / defaults apply
  }
  for (const line of text.split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (!match) continue;
    const key = match[1];
    let value = match[2];
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    const current = process.env[key];
    if (current === undefined || current === "") {
      process.env[key] = value;
    }
  }
}
