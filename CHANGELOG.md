# Changelog for ALS_Kt

## 0.1.0 - Initial release

First working version of ALS_Kt implementing the cell tower lookup.

Changes:

- Added: The apple-location-services.proto file defining the ALS entities.
- Added: The query code for ALS cell tower lookups.
- Added: A CLI (not released to Maven Central, git repo only.)
- Added: A heuristic for UMTS cells, which are required to be queried through the GSM entity.
