"""Sample FAILING job for the MVP-008 E2E: dies with HTTP 429 halfway."""
import sys

TOTAL = 100
for page in range(1, TOTAL + 1):
    if page == 61:
        print("HTTP 429 Too Many Requests", file=sys.stderr, flush=True)
        sys.exit(1)
print("crawled", page)
