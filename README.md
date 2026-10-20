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

Ergebnis: `build/libs/IlluniumCustom-<version>.jar`. Details und lokaler Teststart: [`docs/development.md`](./docs/development.md).

## Konfiguration

`plugins/IlluniumCustom/config.yml` (wird beim ersten Start angelegt, Änderungen gelten nach Neustart):

```yaml
modules: {}          # <modul>: true|false, fehlende Module sind aktiv
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
