# IlluniumCustom

Eigenständiges Paper-Plugin für die Illunium Forever-World: Quartz-Rezept, Gruppen, Marriage, Playtime, Whitelist-Schutz und Sitzen in **einer** JAR, ohne weitere Plugins. Produktvertrag: [`MVP.md`](./MVP.md), Umsetzung: [`Implementation_Plan.md`](./Implementation_Plan.md), Änderungen: [`CHANGELOG.md`](./CHANGELOG.md).

## Voraussetzungen

- Java **25**
- Paper **26.3**, getestet mit Build **207** (`26.3.build.207-stable`)
- Keine weiteren Plugins

## Bauen

```bash
./gradlew clean check build --no-daemon
```

Ergebnis: `build/libs/IlluniumCustom-<version>.jar` (bytegleich reproduzierbar).

## Download

Jeder Push auf `main` baut und ersetzt das Release [`latest`](https://github.com/illuuuuusion/illunium-custom/releases/tag/latest) (als Pre-Release markiert):

- JAR: https://github.com/illuuuuusion/illunium-custom/releases/download/latest/IlluniumCustom.jar
- SHA-256: https://github.com/illuuuuusion/illunium-custom/releases/download/latest/IlluniumCustom.jar.sha256
- Build-Info (Commit, Java, Paper-API, Schema): https://github.com/illuuuuusion/illunium-custom/releases/download/latest/build-info.txt

Das ist ein Entwicklungsbuild, keine Produktionsfreigabe; vor jedem Rollout auf dem Testserver prüfen.

## Installation

1. JAR nach `plugins/` eines Paper-26.3-Servers kopieren (Hash prüfen), Server starten.
2. `server.properties`: `online-mode=true`, `white-list=true`, `enforce-whitelist=true` (der Whitelist Guard schaltet die beiden Whitelist-Werte notfalls selbst ein).
3. `plugins/IlluniumCustom/config.yml` anpassen (vor allem `whitelistguard.staff`), Server neu starten.
4. Spieler mit Vanilla `/whitelist add <Name>` freischalten.

Deployment, Launch, Backup/Restore und Rollback: [`docs/operations.md`](./docs/operations.md).

## Commands

| Command | Effekt | Permission |
| --- | --- | --- |
| `/group create\|list\|info\|members\|invite\|uninvite\|accept\|deny\|kick\|promote\|demote\|rename\|tag\|color\|transfer\|disband\|confirm\|cancel\|leave` | Gruppen mit Tag, Farbe und Rollen Leader/Mod/Member | `illuniumcustom.use` |
| `/marry <player>\|accept\|deny\|info [player]` · `/divorce [confirm\|cancel]` | Partnerschaft, Herz in der Tablist | `illuniumcustom.use` |
| `/playtime [player]` | Spielzeit, auch offline | `illuniumcustom.use` |
| `/sit` · `/unsit` | Hinsetzen/Aufstehen | `illuniumcustom.use` |
| `/ic status` · `/ic group inspect\|setleader\|disband ...` | Diagnose, Gruppen-Recovery | `illuniumcustom.admin` |
| Vanilla `/whitelist add\|remove\|list\|reload` | Zugang (native Whitelist, nicht vom Plugin) | Vanilla/OP |

Rollenmatrix, Regeln und Verhalten aller Module: [`docs/commands.md`](./docs/commands.md). Das Quartz-Rezept (`1 × Quartz-Block → 4 × Nether Quartz`) braucht keinen Command.

## Permissions

| Permission | Verwendung | Standard |
| --- | --- | --- |
| `illuniumcustom.use` | Öffentliche Plugin-Kommandos | `true` |
| `illuniumcustom.admin` | Notfall-/Diagnose-Commands | `op` |

## Konfiguration

`plugins/IlluniumCustom/config.yml`, wird beim ersten Start angelegt; Änderungen gelten nach Neustart, ungültige Werte verhindern den Start mit präziser Meldung. Alle Schlüssel: [`docs/configuration.md`](./docs/configuration.md).

## Backup und Restore

Zu sichern: `plugins/IlluniumCustom/` (`data.db`, `config.yml`) und die native `whitelist.json` des Servers. `data.db` läuft im WAL-Modus: online nur mit `sqlite3 data.db ".backup backup.db"`, sonst bei gestopptem Server inklusive `data.db-wal`/`data.db-shm` kopieren. Restore: Server stoppen, `data.db` ersetzen, alte `-wal`/`-shm` löschen, starten. Details: [`docs/operations.md`](./docs/operations.md).

## Entwicklung

Lokaler Testserver, Modulvertrag und Testnachweise: [`docs/development.md`](./docs/development.md), Architektur: [`docs/architecture.md`](./docs/architecture.md).
