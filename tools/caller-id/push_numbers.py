"""Adds, fixes or removes caller ID numbers on the server (phase 2), phones pick them up within a day.

Input is a tab separated file like known_numbers.tsv: region, number, category, English name,
Arabic name. Write "delete" as the category to remove a number.

Usage: python tools/caller-id/push_numbers.py changes.tsv
The admin token is read from server/caller-id/.dev.vars (ADMIN_TOKEN=...), never commit it.
Needs: pip install phonenumbers
"""

import json
import sys
import urllib.request

import phonenumbers

from build_osm_db import ROOT

SERVER = "https://easy-phone-caller-id.amr-adel-elkholy.workers.dev"
DEV_VARS = ROOT / "server" / "caller-id" / ".dev.vars"


def read_token():
    for line in DEV_VARS.read_text(encoding="utf-8").splitlines():
        if line.startswith("ADMIN_TOKEN="):
            return line.split("=", 1)[1].strip()
    sys.exit(f"ADMIN_TOKEN missing from {DEV_VARS}")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)

    rows = []
    for line in open(sys.argv[1], encoding="utf-8").read().splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        region, number, category, *names = line.rstrip().split("\t")
        parsed = phonenumbers.parse(number, region)
        key = f"{parsed.country_code}:{phonenumbers.national_significant_number(parsed)}"
        if category == "delete":
            rows.append({"number": key, "deleted": True})
        else:
            rows.append({
                "number": key,
                "category": category,
                "name_en": names[0] if names else "",
                "name_ar": names[1] if len(names) > 1 else "",
                "source": "curated",
            })

    request = urllib.request.Request(
        f"{SERVER}/v1/admin/numbers",
        data=json.dumps({"rows": rows}).encode(),
        headers={"Authorization": f"Bearer {read_token()}", "Content-Type": "application/json", "User-Agent": "EasyPhone-admin/1.0"},
    )
    with urllib.request.urlopen(request) as response:
        print(response.read().decode())


if __name__ == "__main__":
    main()
