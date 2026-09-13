"""Sample long-running job for the MVP-008 E2E: crawls 500 pages (simulated)."""
import time

TOTAL = 500
for page in range(1, TOTAL + 1):
    time.sleep(0.02)
    if page % 50 == 0:
        print(f"crawled {page}/{TOTAL}", flush=True)
print("crawl complete")
