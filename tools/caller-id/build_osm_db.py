"""Builds the offline caller ID database from OpenStreetMap, see docs/caller-id/PLAN.md (1.3).

Downloads every place with a phone number in the regions below through the Overpass API,
normalizes the numbers with phonenumbers and writes app/src/main/assets/caller_id/osm_places.db.

The data is (c) OpenStreetMap contributors, available under the ODbL. The generated database
is a derivative database and is published under the ODbL as well.

Usage: python tools/caller-id/build_osm_db.py [--refresh]
Needs: pip install phonenumbers
"""

import collections
import json
import pathlib
import re
import sqlite3
import sys
import urllib.error
import urllib.parse
import urllib.request

import phonenumbers
from phonenumbers import shortnumberinfo

REGIONS = ["EG", "SA"]
# public Overpass servers, tried in order when one is busy
OVERPASS_URLS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]
USER_AGENT = "EasyPhone-caller-id-build/1.0 (https://github.com/AmrAdelElkholy/phone-contacts)"

ROOT = pathlib.Path(__file__).resolve().parents[2]
CACHE_DIR = pathlib.Path(__file__).resolve().parent / "cache"
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "caller_id" / "osm_places.db"

# a number shared by more distinct names than this is a switchboard or a placeholder, not useful
MAX_DISTINCT_NAMES = 3

# version of the data shipped in the APK, see tools/caller-id/build_server_seed.py
BASELINE_VERSION = 1

ARABIC = re.compile(r"[؀-ۿ]")

QUERY = """
[out:json][timeout:900];
area["ISO3166-1"="{region}"][admin_level=2]->.country;
(
  nwr(area.country)["phone"]["name"];
  nwr(area.country)["contact:phone"]["name"];
);
out tags;
"""


def fetch(region, refresh):
    cache = CACHE_DIR / f"{region}.json"
    if cache.exists() and not refresh:
        return json.loads(cache.read_text(encoding="utf-8"))

    print(f"downloading {region} from Overpass, this takes a few minutes")
    body = urllib.parse.urlencode({"data": QUERY.format(region=region)}).encode()
    data = None
    for url in OVERPASS_URLS:
        try:
            request = urllib.request.Request(url, data=body, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=1200) as response:
                data = json.loads(response.read().decode("utf-8"))
            break
        except (urllib.error.URLError, TimeoutError) as error:
            print(f"  {url} failed: {error}")
    if data is None:
        sys.exit(f"every Overpass server failed for {region}, try again later")

    CACHE_DIR.mkdir(exist_ok=True)
    cache.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    return data


def get_category(tags):
    amenity = tags.get("amenity", "")
    healthcare = tags.get("healthcare", "")
    if amenity in ("hospital", "clinic", "doctors", "dentist") or healthcare in ("hospital", "clinic", "doctor", "dentist", "laboratory", "centre"):
        return "hospital"
    if amenity == "pharmacy" or healthcare == "pharmacy" or tags.get("shop") == "chemist":
        return "pharmacy"
    if amenity in ("bank", "atm", "bureau_de_change"):
        return "bank"
    if amenity in ("restaurant", "cafe", "fast_food", "food_court", "ice_cream"):
        return "restaurant"
    if tags.get("tourism") in ("hotel", "motel", "guest_house", "hostel", "apartment", "resort"):
        return "hotel"
    if amenity in ("school", "university", "college", "kindergarten", "language_school", "driving_school"):
        return "education"
    if amenity in ("police", "fire_station", "townhall", "courthouse", "post_office", "embassy") or tags.get("office") in ("government", "diplomatic"):
        return "government"
    if tags.get("office") == "telecommunication" or tags.get("shop") == "mobile_phone":
        return "telecom"
    if tags.get("aeroway") or tags.get("office") == "airline":
        return "airline"
    if "shop" in tags:
        return "shop"
    if "office" in tags or "craft" in tags:
        return "company"
    return "place"


def get_names(tags):
    name = tags.get("name", "").strip()
    name_en = tags.get("name:en", "").strip()
    name_ar = tags.get("name:ar", "").strip()
    if not name_ar and ARABIC.search(name):
        name_ar = name
    if not name_en:
        name_en = name if not ARABIC.search(name) else ""
    return name_en, name_ar


def parse_numbers(raw, region):
    numbers = []
    for part in re.split(r"[;,/]|\s{2,}", raw):
        part = part.strip()
        if not part:
            continue
        try:
            parsed = phonenumbers.parse(part, region)
        except phonenumbers.NumberParseException:
            continue

        national = phonenumbers.national_significant_number(parsed)
        valid = phonenumbers.is_valid_number(parsed)
        # hotlines like 19623 are short numbers, they only parse against their own country
        short = parsed.country_code == phonenumbers.country_code_for_region(region) and shortnumberinfo.is_valid_short_number_for_region(parsed, region)
        if valid or short:
            numbers.append(f"{parsed.country_code}:{national}")
    return numbers


def main():
    refresh = "--refresh" in sys.argv
    names_by_key = collections.defaultdict(collections.Counter)
    category_by_name = {}

    for region in REGIONS:
        elements = fetch(region, refresh)["elements"]
        print(f"{region}: {len(elements)} places with a phone number")
        for element in elements:
            tags = element.get("tags", {})
            name_en, name_ar = get_names(tags)
            if not name_en and not name_ar:
                continue

            raw = ";".join(tags[key] for key in ("phone", "contact:phone") if key in tags)
            category = get_category(tags)
            for key in set(parse_numbers(raw, region)):
                names_by_key[key][(name_en, name_ar)] += 1
                category_by_name[(key, name_en, name_ar)] = category

    rows = []
    skipped = 0
    for key, names in names_by_key.items():
        if len(names) > MAX_DISTINCT_NAMES:
            skipped += 1
            continue
        (name_en, name_ar), _ = names.most_common(1)[0]
        rows.append((key, name_en, name_ar, category_by_name[(key, name_en, name_ar)]))

    rows.sort()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    if OUTPUT.exists():
        OUTPUT.unlink()

    db = sqlite3.connect(OUTPUT)
    db.execute("CREATE TABLE places (number TEXT PRIMARY KEY, name_en TEXT NOT NULL, name_ar TEXT NOT NULL, category TEXT NOT NULL) WITHOUT ROWID")
    db.executemany("INSERT INTO places VALUES (?, ?, ?, ?)", rows)
    db.execute("CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID")
    db.execute("INSERT INTO metadata VALUES ('source', '© OpenStreetMap contributors, ODbL 1.0')")
    # the server holds this same data as version BASELINE_VERSION, phones download newer rows only
    db.execute("INSERT INTO metadata VALUES ('version', ?)", (str(BASELINE_VERSION),))
    db.commit()
    db.execute("VACUUM")
    db.close()

    categories = collections.Counter(row[3] for row in rows)
    print(f"wrote {len(rows)} numbers, skipped {skipped} shared numbers, {OUTPUT.stat().st_size // 1024} KB")
    print(", ".join(f"{name} {count}" for name, count in categories.most_common()))


if __name__ == "__main__":
    main()
