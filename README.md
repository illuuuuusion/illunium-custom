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

## Konfiguration

`plugins/IlluniumCustom/config.yml` (wird beim ersten Start angelegt, Änderungen gelten nach Neustart):

```yaml
modules:             # <modul>: true|false, fehlende Module sind aktiv
  recipes: true
storage:
  filename: data.db  # SQLite-Datei im Pluginordner
```

Ungültige Werte oder unbekannte Module verhindern den Start mit einer präzisen Fehlermeldung im Log.

## Commands

| Command | Effekt | Permission |
| --- | --- | --- |
| `/illuniumcustom status` (`/ic status`) | Version, Schema-Version, aktive Module | `illuniumcustom.admin` |

## Backup

`data.db` läuft im WAL-Modus. Nur bei gestopptem Server kopieren (inkl. evtl. `data.db-wal`/`data.db-shm`) oder online mit `sqlite3 data.db ".backup backup.db"`.

## Permissions

| Permission | Verwendung | Standard |
| --- | --- | --- |
| `illuniumcustom.use` | Öffentliche Plugin-Kommandos | `true` |
| `illuniumcustom.admin` | Notfall-/Diagnose-Commands | `op` |
