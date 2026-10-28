# Caller ID progress

See [PLAN.md](PLAN.md) for the full plan.

## Phase 1: offline

### 1.1 Number details from libphonenumber
- [ ] Add the `carrier` artifact
- [ ] `CallerIdHelper` builds details: type, landline location, original network, emergency
- [ ] Shown on the call screen and notification for numbers that match no contact

### 1.2 Curated hotlines
- [ ] `known_numbers.tsv` format and loader
- [ ] Emergency numbers EG and SA
- [ ] First set of banks and mobile operators EG and SA
- [ ] Hotline name on the call screen, notification and call history
- [ ] Every entry checked against official websites (needs review by the owner)
- [ ] More categories: government, hospitals, delivery, airlines

### 1.3 OpenStreetMap places
- [ ] Extraction script (Overpass) for EG and SA
- [ ] Normalization and SQLite build
- [ ] Lookup in the app
- [ ] OSM attribution in About and NOTICE.md

## Phase 2: hosted database
- [ ] Not started

## Phase 3: spam reports
- [ ] Not started

## Log
- 2026-10-28: plan written, phase 1 started.
