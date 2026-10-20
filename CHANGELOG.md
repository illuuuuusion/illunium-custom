# Changelog

## 0.1.0-rc.1 — 21.10.2026

Erster Release Candidate des MVP für Paper 26.3 (Build 207), Java 25. Schema v1.

- Core: modulare Registry, validierte Config, SQLite mit Migrationen auf eigenem Thread, UUID-basierte Identitäten, `/ic status`.
- Recipes: `1 × Quartz-Block → 4 × Nether Quartz` (shapeless).
- Groups: Gruppen mit Name, Tag, Farbe, Rollen Leader/Mod/Member, Einladungen (5 min), Bestätigungen (30 s), Admin-Recovery `/ic group`.
- Marriage: Antrag/Annahme/Ablehnung/Scheidung, höchstens eine Ehe pro UUID.
- Appearance: Gruppen-Tag über dem Kopf und in der Tablist, Marriage-Herz in der Tablist für alle Zuschauer.
- Playtime: `/playtime [player]`, Checkpoint alle 60 s, Quit- und Shutdown-Flush.
- Whitelist Guard: native Whitelist und Enforcement bleiben an, Mojang-verifizierte Staff-Accounts werden wieder eingetragen.
- Sit: `/sit`, `/unsit` mit nicht persistenten, markierten Sitz-Entities und Orphan-Cleanup.
- CI: Build, Tests, SHA-256 und `build-info.txt` als Artefakt; rollendes Pre-Release `latest`.

Offen vor der Produktionsfreigabe: Gameplay-Abnahme mit Vanilla-Clients (siehe `docs/operations.md`).
