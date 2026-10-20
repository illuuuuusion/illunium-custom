# IlluniumCustom

Eigenständiges Paper-Plugin für die Illunium Forever-World. Produktvertrag: [`MVP.md`](./MVP.md), Umsetzung: [`Implementation_Plan.md`](./Implementation_Plan.md).

## Voraussetzungen

- Java **25**
- Paper **26.3**, getestet mit Build **207** (`26.3.build.207-stable`)
- Keine weiteren Plugins

## Bauen

```bash
./gradlew clean check build --no-daemon
```

Ergebnis: `build/libs/IlluniumCustom-<version>.jar`.

## Download

Jeder Push auf `main` baut und ersetzt das Release [`latest`](https://github.com/illuuuuusion/illunium-custom/releases/tag/latest) (als Pre-Release markiert):

- JAR: https://github.com/illuuuuusion/illunium-custom/releases/download/latest/IlluniumCustom.jar
- SHA-256: https://github.com/illuuuuusion/illunium-custom/releases/download/latest/IlluniumCustom.jar.sha256

Das ist ein Entwicklungsbuild, keine Produktionsfreigabe; vor jedem Rollout auf dem Testserver prüfen. Details und lokaler Teststart: [`docs/development.md`](./docs/development.md).

## Module

### Recipes (`recipes`)

Shapeless-Rezept `1 × Quartz-Block → 4 × Nether Quartz` (Key `illuniumcustom:quartz_from_quartz_block`), im 2×2- und 3×3-Feld, für alle Spieler ohne Permission. Nur der normale `QUARTZ_BLOCK` zählt, nicht Säule, gemeißelter, glatter Block oder Ziegel. Vanilla-Rezepte bleiben unverändert. Weitere Rezepte: `RecipesModule.recipes()` ergänzen.

### Groups (`groups`)

Soziale Gruppen mit Name (1–16 Zeichen), Tag (1–5 Zeichen, jeweils `A-Z a-z 0-9 _ -`, case-insensitiv eindeutig) und Farbe (eine der 16 Minecraft-Farben, Standard `white`). Pro Spieler höchstens eine Gruppe, genau ein Leader, beliebig viele Mods.

| Command | Effekt | Rolle |
| --- | --- | --- |
| `/group create <name> <tag>` | Gruppe gründen | gruppenlos |
| `/group list` · `/group info [group]` | Gruppen / Gruppeninfo | jeder (auch Konsole) |
| `/group members` | Mitglieder mit Rollen | Mitglied |
| `/group invite <player>` · `/group uninvite <player>` | Einladung (5 Minuten gültig, nur an Online-Spieler) senden/zurückziehen | Leader, Mod |
| `/group accept <group>` · `/group deny <group>` | Einladung annehmen/ablehnen | Eingeladener |
| `/group kick <player>` | Member entfernen (Leader auch Mods) | Leader, Mod |
| `/group promote <player>` · `/group demote <player>` | Member ↔ Mod | Leader |
| `/group rename <name>` · `/group tag <tag>` · `/group color <color>` | Darstellung ändern | Leader |
| `/group transfer <player>` · `/group disband` | Leader übertragen (alter Leader wird Mod) / auflösen, jeweils mit Bestätigung | Leader |
| `/group confirm` · `/group cancel` | Bestätigung (30 Sekunden gültig) / Abbruch | Leader |
| `/group leave` | Gruppe verlassen; Leader müssen vorher übertragen oder auflösen | Mod, Member |

Kick/Promote/Demote/Transfer funktionieren auch für offline Mitglieder (letzter bekannter Name). Einladungen und offene Bestätigungen sind flüchtig und verfallen beim Neustart. Bei `accept` wird erneut geprüft: Frist, Gruppe existiert noch, Einladender ist noch Leader/Mod, Spieler ist noch gruppenlos.

Admin-Recovery (`illuniumcustom.admin`, jede Änderung wird mit Absender geloggt):

- `/ic group inspect <group>` — gespeicherten Zustand und Leader-Invariante prüfen
- `/ic group setleader <group> <player>` — Leader neu setzen (Ziel muss Mitglied sein; bisheriger Leader wird Mod)
- `/ic group disband <group>` — Gruppe sofort auflösen

## Konfiguration

`plugins/IlluniumCustom/config.yml` (wird beim ersten Start angelegt, Änderungen gelten nach Neustart):

```yaml
modules:             # <modul>: true|false, fehlende Module sind aktiv
  recipes: true
  groups: true
storage:
  filename: data.db  # SQLite-Datei im Pluginordner
```

Ungültige Werte oder unbekannte Module verhindern den Start mit einer präzisen Fehlermeldung im Log.

## Commands

| Command | Effekt | Permission |
| --- | --- | --- |
| `/illuniumcustom status` (`/ic status`) | Version, Schema-Version, aktive Module | `illuniumcustom.admin` |
| `/ic group ...` | Gruppen-Recovery, siehe oben | `illuniumcustom.admin` |
| `/group ...` | Gruppen, siehe oben | `illuniumcustom.use` |

## Backup

`data.db` läuft im WAL-Modus. Nur bei gestopptem Server kopieren (inkl. evtl. `data.db-wal`/`data.db-shm`) oder online mit `sqlite3 data.db ".backup backup.db"`.

## Permissions

| Permission | Verwendung | Standard |
| --- | --- | --- |
| `illuniumcustom.use` | Öffentliche Plugin-Kommandos | `true` |
| `illuniumcustom.admin` | Notfall-/Diagnose-Commands | `op` |
