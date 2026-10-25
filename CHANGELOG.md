# Changelog

## Unreleased — Patch v1.4

Schema **v5**, additiv auf v4: `V5__group_chests.sql`. Keine neuen Abhängigkeiten. Plan: `Patch_v1.4_Implementation_Plan.md`.

- `/gec`: eine gemeinsame 27-Slot-Kiste pro Gruppen-ID für alle Rollen ab vier eingetragenen Mitgliedern. Unter vier bleiben gefüllte Kisten zur Entnahme zugänglich; leere sind gesperrt.
- Versionierte Paper-NBT-Snapshots mit Revisionen, gemeinsamem Live-Inventar, dedupliziertem Laden, Checkpoints nach Änderungen und finalem Flush beim Schließen/Stop. Beschädigte Daten und Speicherfehler sperren den Zugriff.
- Gruppen-Lifecycle schließt entfernte Mitglieder aus offenen Kisten aus. Normales und Admin-Disband benötigen eine leere, geschlossene, bestätigte Kiste; SQL-Trigger schützt zusätzlich vor dem Löschen gefüllter Kisten.
- `/g <Nachricht>`: privater, flüchtiger Chat an aktuelle Online-Mitglieder, Gruppenfarbe, maximal 256 Unicode-Codepoints, standardmäßig eine Sekunde Cooldown pro Sender; Vanish-Sender werden abgewiesen.
- Leaderboards erhalten farbige Kategorien, Gold/Silber/Bronze, hervorgehobene Werte und klickbare Seitenwechsel. Statistikberechnung, Filter, Gleichstände und Cache bleiben erhalten.
- Strikte, kompatible Defaults unter `groups.gec` und `groups.chat`; separate Feature-Schalter. Automatisierte Tests und isolierte Paper-Prüfungen sind dokumentiert; echte Client-E2E und Produktionsfreigabe bleiben offen.

## Unreleased — Patch v1.3

Schema **v4**, additiv auf v2: `V3__leaderboards.sql`, `V4__whitelist_staging.sql`. Keine neuen Abhängigkeiten. Plan: `Patch_v1.3_Implementation_Plan.md`.

- `/leaderboard` und `/lb`: elf Survival-Kategorien, zehn Einträge pro Seite, eigene Platzierung, Wettbewerbsränge bei Gleichstand und Offline-Historie. Aktuelle OP-UUIDs werden immer ausgeschlossen, unabhängig von Auto-Vanish.
- Vanilla-Statistiken werden in kleinen Main-Thread-Schritten gelesen, reine Zahlen auf einem Worker sortiert; Playtime nutzt die bestehende Messung mit Bulk-Read und laufenden Sessions.
- Eigene persistente Zähler für Platzierungen, echte Totem-Rettungen, erfolgreiche Spawn-Teleports (Session-ID dedupliziert) und das seit v1.3 beobachtete höchste XP-Level; DB-Fehler puffern und wiederholen.
- `/ic whitelist stage|unstage|staged|preview|launch|status|retry`: asynchron verifizierte Java-Profile zunächst nur in SQLite. Expliziter Launch friert die Liste ein, nutzt die native Whitelist und bestätigt UUID plus nichtleeren Namen. Persistierte Claims und Recovery schützen vor Dopplungen; bereits angewendete Accounts werden nach manueller Entfernung nicht erneut freigegeben.
- `vanish.auto-vanish-default` (weiterhin `true`) und strikte UUID/Boolean-Overrides erlauben sichtbare OP-Joins; manuelle Vanish-Commands bleiben erhalten.
- Betriebsdokumentation enthält Launch-Checkliste, konsistente Sicherung inklusive `ops.json`, Rollback und offene Client-Abnahme. Kein produktiver Rollout.

## Unreleased — Patch v1.2

Schema **v2** (neue additive Migration `V2__spawn_cooldowns.sql`), keine neuen Abhängigkeiten. Plan: `Patch_v1.2_Implementation_Plan.md`. Ersetzt nur die v1.1-Spawn-Regeln.

- Spawn: `/spawn` teleportiert erst nach **10 s** Warmup; Positionsänderung, wirksamer Schaden, fremder Teleport, Weltwechsel, Tod oder Quit brechen ohne Cooldown ab.
- Spawn: **60 min** Cooldown pro Spieler, erst nach erfolgreichem Teleport; in SQLite gespeichert, übersteht Neustarts.
- Spawn: PvP-Treffer (Nahkampf oder per Paper zugeordnetes Projektil) sperren `/spawn` für Angreifer und Opfer **30 s**; jeder Treffer erneuert. Flüchtig, kein Combat-Log-Schutz, keine Monster-Nähe-Prüfung.

## Unreleased — Patch v1.1

Additiv zu 0.1.0-rc.1, Schema unverändert (v1), keine neuen Abhängigkeiten. Plan: `Patch_v1.1_Implementation_Plan.md`.

- Groups: `/group color` akzeptiert zusätzlich `#RRGGBB`; bestehende Farbnamen bleiben gültig.
- Sit: neu gedrückte W/A/S/D-, Sprung-, Sneak- oder Sprint-Taste steht sofort auf, ohne Rücksetzen auf den Sitzpunkt.
- Marriage: Herzpartikel, wenn beide Partner sneaken (gleiche Welt, ≤ 3 Blöcke); verborgene Partner gelten als offline.
- Vanish (neu, `modules.vanish`): Vanilla-OPs joinen verborgen, `/unvanish` · `/vanish`; Core-`VisibilityService` für andere Module.
- Spawn (neu, `modules.spawn`): `/spawn` zum Welt-Spawn aus `/setworldspawn`, mit Standplatzprüfung.
- Difficulty: kein Modul; Vanilla `/difficulty` persistiert nachweislich (pro Welt), dokumentiert.

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
