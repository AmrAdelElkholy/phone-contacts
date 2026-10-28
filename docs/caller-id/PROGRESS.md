# Caller ID progress

See [PLAN.md](PLAN.md) for the full plan.

## Phase 1: offline

### 1.1 Number details from libphonenumber
- [x] Add the `carrier` artifact
- [x] `CallerIdHelper` builds details: type, landline location, original network, emergency
- [x] Shown on the call screen and notification for numbers that match no contact

### 1.2 Curated hotlines
- [x] `known_numbers.tsv` format and loader
- [x] Emergency numbers EG and SA
- [x] First set of banks and mobile operators EG and SA
- [x] Hotline name on the call screen, notification and call history
- [ ] Every entry checked against official websites (needs review by the owner)
- [ ] More categories: government, hospitals, delivery, airlines

### 1.3 OpenStreetMap places
Rebuild with `python tools/caller-id/build_osm_db.py --refresh` (needs `pip install phonenumbers`).
- [x] Extraction script (Overpass) for EG and SA
- [x] Normalization and SQLite build
- [x] Lookup in the app
- [x] OSM attribution in About and NOTICE.md
- [ ] Grow coverage: OSM has few phone numbers in EG and SA, adding missing places to OSM
      itself helps every app; other open sources to be evaluated

## Phase 2: hosted database
- [ ] Not started

## Phase 3: spam reports
- [ ] Not started

## Log
- 2026-10-28: plan written, phase 1 started.
- 2026-10-28: 1.1 and 1.2 built. Unknown numbers show their type, landline city or original
  network on the call screen and notification. 20 hotlines (emergency, banks, operators in EG
  and SA) show their name there and in the call history. APK grew about 150 KB.
  Not yet seen on a real incoming call; the hotline list still needs the owner's review.
- 2026-10-28: 1.3 built. OpenStreetMap gave 2,498 places in EG and 4,119 in SA with a phone,
  4,621 usable numbers after normalization (39 numbers shared by many places dropped).
  Database 320 KB, copied out of the APK once per update and queried read-only. Hotlines from
  `known_numbers.tsv` win over OSM. Attribution added to About and NOTICE.md.
