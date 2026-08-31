# ADR-006: Image Retention Policy on the Docker Volume

**Date:** 2026-08-31
**Status:** Accepted

**Context:** Uploaded receipt photos are stored on a Docker volume (path referenced by
`receipts.image_path`), mounted into the backend container. Some policy is needed for how long
these are kept — indefinitely, or purged after some retention window (a common pattern for
"working" upload storage once the derived data is durably captured elsewhere).

Two things make purge-after-processing a bad fit here, unlike a typical transient-upload
scenario:
- **The photo is the only copy of the source document.** Once classified, the durable data is
  the `receipt_line_items` rows — but if a mis-classification is spotted later, or a `reprocess`
  is triggered, the classifier needs the original image again. Deleting it after first
  processing would make `reprocess` (an explicit, designed-for feature — see
  `docs/openapi.yaml`'s `POST /receipts/{id}/reprocess`) unable to actually re-read the receipt;
  it could only ever re-submit whatever was already extracted.
- **Storage cost is a non-issue.** A personal user's shopping-receipt volume (maybe a few hundred
  photos a year, a few hundred KB each) is trivially small next to Raspberry Pi disk capacity —
  there's no storage-pressure reason to purge.

**Decision:** Keep receipt images **indefinitely**. No TTL, no scheduled purge job, no
"archive after N months" tier. The only way an image is removed is `DELETE /receipts/{id}`
(explicit user action — removing a duplicate/bad receipt), which deletes the file alongside the
DB row.

**Consequences:**
- The image volume grows monotonically with receipt count, forever. Acceptable at this app's
  scale (see storage-cost note above); revisit only if actual disk usage ever becomes a real
  constraint on the Pi, which isn't anticipated here.
- `reprocess` remains fully functional for the lifetime of a receipt — the source photo is always
  available to re-run classification against, no separate "photo has expired, please re-upload"
  failure mode to design for.
- Backup/disaster-recovery scope for this volume is "the only copies of the source receipts" —
  DevOps should treat it with the same durability expectations as the Postgres data volume, not
  as disposable cache. Not designing a backup mechanism here; flagging the expectation for
  DevOps to account for in `infra/compose.yml` volume configuration.
