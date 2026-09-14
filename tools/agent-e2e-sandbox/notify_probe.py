import json, sys, time
# probe: capture what codex passes to the notify program
with open(r"C:\PigeonHub\tools\agent-e2e-sandbox\notify_probe.log", "a", encoding="utf-8") as f:
    f.write(f"--- {time.time()} ---\n")
    f.write("argv: " + json.dumps(sys.argv[1:]) + "\n")
