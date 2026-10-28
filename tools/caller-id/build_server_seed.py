"""Writes server/caller-id/seed.sql, the starting data of the caller ID server (phase 2).

The server starts with exactly what the APK ships: the OpenStreetMap places database and the
curated hotlines, all at BASELINE_VERSION. Phones then only download rows changed after that.

Usage:
  python tools/caller-id/build_osm_db.py
  python tools/caller-id/build_server_seed.py
  cd server/caller-id && npx wrangler d1 execute easy-phone-caller-id --remote --file seed.sql
Needs: pip install phonenumbers
"""

import pathlib
import sqlite3

import phonenumbers

from build_osm_db import BASELINE_VERSION, OUTPUT as PLACES_DB, ROOT

KNOWN_NUMBERS = ROOT / "app" / "src" / "main" / "assets" / "caller_id" / "known_numbers.tsv"
SEED = ROOT / "server" / "caller-id" / "seed.sql"


def quote(value):
    return "'" + value.replace("'", "''") + "'"


def main():
    rows = {}
    db = sqlite3.connect(PLACES_DB)
    for number, name_en, name_ar, category in db.execute("SELECT number, name_en, name_ar, category FROM places"):
        rows[number] = (name_en, name_ar, category, "osm")
    db.close()

    # curated hotlines win over OpenStreetMap, like in the app
    for line in KNOWN_NUMBERS.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        region, number, category, name_en, *rest = line.rstrip().split("\t")
        parsed = phonenumbers.parse(number, region)
        key = f"{parsed.country_code}:{phonenumbers.national_significant_number(parsed)}"
        rows[key] = (name_en, rest[0] if rest else "", category, "curated")

    statements = ["DELETE FROM numbers;"]
    items = sorted(rows.items())
    for start in range(0, len(items), 200):
        values = ",\n".join(
            f"({quote(number)}, {quote(en)}, {quote(ar)}, {quote(category)}, {quote(source)}, 0, {BASELINE_VERSION})"
            for number, (en, ar, category, source) in items[start:start + 200]
        )
        statements.append(f"INSERT INTO numbers (number, name_en, name_ar, category, source, deleted, version) VALUES\n{values};")

    SEED.write_text("\n".join(statements) + "\n", encoding="utf-8")
    print(f"wrote {len(rows)} numbers to {SEED.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
