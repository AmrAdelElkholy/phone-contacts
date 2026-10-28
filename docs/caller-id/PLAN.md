# Caller ID plan

Goal: show who is calling when the number is not a saved contact, starting with data that
needs no server and expanding step by step. Focus markets are Egypt (EG) and Saudi Arabia (SA).

## Principles

- Saved contacts always win. Caller ID only fills in numbers that match no contact.
- Phase 1 runs fully offline: no number leaves the phone, nothing new to declare in Play Data safety.
- Every data source is open or written by us, with its license and attribution recorded here.
- Data lives in files that are easy to review and extend, separate from the code.

## Where results appear

| Place | What is shown |
|---|---|
| Incoming / ongoing call screen | Name (hotline or place) and details under the number |
| Call notification | Same name and details |
| Call history | Name instead of the bare number |

## Phase 1: offline (on the phone)

### 1.1 Number details from libphonenumber
Already a dependency, plus the `carrier` data artifact (matching version 2.40).
- Number type: mobile, landline, toll-free, premium rate, shared cost, VoIP, universal access.
- Location for landlines (e.g. Cairo, Riyadh) from the offline geocoder.
- Original mobile network (e.g. Vodafone, STC). Egypt and Saudi Arabia have number portability,
  so this is the network the number was issued on, not necessarily the current one.
- Emergency and short numbers via `ShortNumberInfo`.

### 1.2 Curated hotlines
A hand-maintained file `app/src/main/assets/caller_id/known_numbers.tsv`:

```
region  number      category    name_en                     name_ar
EG      19623       bank        National Bank of Egypt      البنك الأهلي المصري
```

- Emergency services, banks, mobile operators, then government, hospitals, delivery, airlines.
- Numbers are matched after parsing with libphonenumber, so `19623`, `+2019623` or a Saudi
  `920003344` / `+966920003344` resolve to the same entry.
- Every entry must be checked against the organization's official website before release.

### 1.3 OpenStreetMap places
- Extract `phone` / `contact:phone` tags of hospitals, clinics, pharmacies, banks, ATMs,
  shops, restaurants, hotels and government offices in EG and SA (Overpass API or Geofabrik extract).
- Normalize to E.164 with libphonenumber, drop duplicates and invalid numbers.
- Ship as a compact SQLite database in assets (target under 3 MB), queried by E.164.
- License: ODbL. Credit "© OpenStreetMap contributors" in About and NOTICE.md; the derived
  database must stay available under ODbL (publish the build script and the file in the repo).

## Phase 2: hosted database (Cloudflare Workers + D1)
- Same schema as 1.3, hosted so data can update without an app release.
- The app downloads a compact delta periodically (privacy friendly), with lookup of single
  numbers as an optional fallback.
- Admin tooling to add or fix entries.

## Phase 3: community spam reports
- "Report spam" and "Wrong name" actions on unknown numbers in the call history and after calls.
- A number is flagged after reports from several distinct installs; reports expire over time.
- Requires phase 2, an anonymous install id, abuse limits, and an updated privacy policy and
  Data safety form.

## Out of scope for now
- Uploading users' address books (legal risk in EG and SA, and against our privacy stance).
- Paid caller ID databases.
