# Entwicklung

## Build

```bash
./gradlew clean check build --no-daemon
```

- Gradle Wrapper und Java-25-Toolchain sind festgelegt; `paper-api` ist exakt auf `26.3.build.207-stable` gepinnt (`compileOnly`).
- CI (`.github/workflows/build.yml`) führt denselben Befehl aus und stellt die JAR als Artefakt bereit. Bei Push auf `main` ersetzt der Job `release` das Pre-Release `latest` (Tag wird auf den neuen Commit verschoben) mit `IlluniumCustom.jar`, `.sha256` und `build-info.txt` (Commit, Java, Paper-API, Schema); das Build-Artefakt enthält zusätzlich `SHA256SUMS`. Das JAR-Manifest trägt Version, Paper-API und Java-Version. Es gibt **keinen** Deploy auf einen Server.

## Lokaler Teststart

Nur auf einem lokalen bzw. getrennten Testserver, nie gegen die Forever-World.

```bash
mkdir -p run/plugins && cd run
curl -o paper.jar https://fill-data.papermc.io/v1/objects/2df3b846f70716907d380915cc026667a9cadedf99b1ec680b0c5ff0e6132526/paper-26.3-207.jar
echo "2df3b846f70716907d380915cc026667a9cadedf99b1ec680b0c5ff0e6132526  paper.jar" | sha256sum -c
echo eula=true > eula.txt
cp ../build/libs/IlluniumCustom-*.jar plugins/
java -jar paper.jar --nogui
```

`run/` ist in `.gitignore`. Erwartung im Log: `Enabling IlluniumCustom`, beim `stop` `Disabling IlluniumCustom`, keine `ERROR`/`SEVERE`-Zeilen des Plugins.

Architektur: [`architecture.md`](./architecture.md).

## Neues Modul hinzufügen

1. Paket `de.illunium.custom.<feature>` mit einer `FeatureModule`-Implementierung (`id()`, `enable(ModuleContext)`, `disable()`).
2. Eintrag in `core/module/Modules.create()` und `modules.<id>: true` in `src/main/resources/config.yml` (`ModulesTest` prüft beides).
3. Eigene Einstellungen unter `<id>:` in `config.yml`; sie kommen als `context.settings()` an und werden in `enable` validiert.
4. Persistente Daten nur über eine neue Migration; kein SQL auf Tabellen anderer Module.
5. Commands über `context.plugin().registerCommand(...)` (Paper `BasicCommand`), Admin-Subcommands über `context.admin().register("<name>", ...)` (→ `/ic <name>`, automatisch `illuniumcustom.admin`), Listener über den PluginManager. `disable()` muss auch nach einem fehlgeschlagenen `enable` aufräumen.

## Verifikation Phase 0–2 (20.10.2026, Paper 26.3 Build 207, Temurin 25.0.4.1)

- `./gradlew clean check build --no-daemon`: 18 Tests grün, JAR bei zwei Builds bytegleich.
- Lokaler Testserver ohne andere Plugins: Enable/Disable ohne Plugin-Fehler, zweiter Start migriert nicht erneut, ungültige Config bricht den Start sichtbar ab, `recipes: false` registriert nichts.
- Rezept per `Server#getCraftingRecipe`/`craftItemResult` mit Wegwerf-Prüfplugin: verschiedene Gitterpositionen und Stack-Eingabe → genau 4 Quartz; Säule, gemeißelt, glatt, Ziegel und 2 Blöcke kein Treffer; Vanilla `4 Quartz → Quartz-Block` intakt; ein vorab unter unserem Key registriertes Fremdrezept wird ersetzt; genau ein Rezept im Namespace `illuniumcustom` nach jedem Start. Die API akzeptiert nur 3×3-Matrizen; das echte 2×2-Inventarfeld wird im Client-E2E (Phase 8) abgenommen.

## Verifikation Phase 3 (20.10.2026, Paper 26.3 Build 207)

- Unit/Repository: `GroupPolicyTest` prüft alle 60 Zellen der Rollenmatrix (erlaubt/verboten) gegen eine unabhängig ausgeschriebene Tabelle; `GroupServiceTest` gegen echtes SQLite: Name/Tag-Regex, Längen 16/5, `NOVA`/`nova`-Kollision, Steuer-/Format-/Unicode-Zeichen, eine Gruppe pro UUID, Rollenregeln auf committetem Zustand, Einladung 299 s gültig/300 s abgelaufen, Revalidierung bei `accept`, Bestätigung 30 s, Transfer (alter Leader → Mod), Disband, gleichzeitige Accepts in zwei Gruppen (genau einer gewinnt), Transfer gegen Admin-Disband (kein Halbzustand), Neustart verwirft Einladungen, Offline-Kick bleibt nach Neustart bestehen.
- Testserver ohne andere Plugins: `/group` und `/ic group` registriert; Konsole erhält bei Spieler-Subcommands eine Fehlermeldung; vorab gespeicherte Gruppe wird geladen, `inspect`/`setleader`/`disband` arbeiten und loggen `[admin] CONSOLE ...`; Enable/Disable ohne Plugin-Fehler.
- Offen für Client-E2E (Phase 8): Spieler-Flows mit mehreren echten Accounts.

## Verifikation Phase 4 (20.10.2026, Paper 26.3 Build 207)

- `MarriageServiceTest` gegen echtes SQLite: Antrag/Annahme/Ablehnung, Selbstheirat, bereits verheiratet, ein Antrag pro Empfänger (kein stilles Überschreiben), Ablauf nach 300 s, gleichzeitige Annahmen zweier Anträge (genau eine Ehe), einseitige Scheidung mit 30-s-Bestätigung und Abbruch, Ehe übersteht Neustart. `GroupServiceTest` prüft, dass Rename/Promote keine Anzeige-Updates auslösen und Tag/Farbe nur die Mitglieder dieser Gruppe neu melden.
- Testserver: simulierter Absturz (`save-all flush`, `kill -9`) mit gespeichertem Team `illuniumcustom_stale` und fremdem Team `foreign` → nach Neustart nur noch `foreign`. Gespeicherte Ehe wird geladen (`/marry info Steve` → verheiratet, Partner offline), unbekannte Namen und Konsole erhalten klare Fehler.
- **Offen für Client-E2E (Phase 8):** Sichtprüfung im Vanilla-Client, dass Tag über dem Kopf und in der Tablist genau einmal erscheint, Herz für alle Zuschauer und bei Partner-Join/Quit wechselt, und ob `💔` im Vanilla-Font gerendert wird (sonst `marriage.offline-symbol: "✕❤"`).

## Verifikation Phase 5 (20.10.2026, Paper 26.3 Build 207)

- `PlaytimeTest`: Livezeit aus monotoner Uhr (`System.nanoTime`), Sub-Sekunden-Rest bleibt über Checkpoints erhalten, doppeltes Join-Event startet die Session nicht neu, Checkpoint + Quit + erneuter Checkpoint zählen jede Sekunde genau einmal, gespeicherte Zeit übersteht Neustart, Formatierung `d/h/m`.
- Gespeichert werden Deltas (`total_seconds = total_seconds + Δ`); der Abschnitt wird auf dem Main Thread abgegrenzt, bevor der Schreibauftrag in die geordnete DB-Queue geht. `/playtime` liest asynchron und addiert die zum Aufrufzeitpunkt noch ungespeicherten Sekunden; da alle vorherigen Checkpoints vor dem Lesen in der Queue stehen, fehlt oder doppelt sich nichts.
- Testserver: gespeicherte Zeit offline abrufbar (`/playtime steve` → `1d 2h 3m`, case-insensitiv), unbekannter Name und Konsole ohne Argument mit klarer Meldung; Enable/Disable ohne Plugin-Fehler.
- **Offen für Client-E2E (Phase 8):** laufende Session mit echtem Spieler über Checkpoint, Quit und Neustart.

## Verifikation Phase 6 (21.10.2026, Paper 26.3 Build 207)

- `WhitelistGuardTest`: Staff-Parser lehnt fehlende/undashed UUIDs, Offline-UUIDs (v3), ungültige/zu lange Namen und doppelte UUIDs ab; Guard schaltet Whitelist und Enforcement wieder ein (ohne Log-Spam bei korrektem Zustand), fügt nur verifizierte Staff wieder ein, entfernt nie fremde Einträge; nicht auflösbare UUID, falscher Name und abweichende UUID werden nie eingetragen.
- Paper 26.3 füllt bei `createProfile(uuid).update()` den Namen **nicht** aus (nur Properties). Geprüft wird daher gegen `profileId`/`profileName` im von Mojang gelieferten `textures`-Property.
- Testserver (`online-mode=true`, Start mit `white-list=false`, `enforce-whitelist=false`): beide Flags beim Start eingeschaltet und in `server.properties` persistiert; Staff `Notch` verifiziert und eingetragen, `jeb_`-UUID mit Name `NotJeb` abgelehnt (`ERROR`, nicht eingetragen); `/whitelist remove Notch` + `/whitelist off` → nach ≤ 30 s Whitelist wieder an und Notch wieder drin; `/whitelist add jeb_` (nie gejoint) erzeugt den nativen Eintrag mit Name. Vom Guard eingetragene Staff stehen mit leerem Namen in `whitelist.json` (Bukkit-API kennt den Namen nie gejointer Accounts nicht); `/whitelist remove <Name>` funktioniert trotzdem.
- Vanilla-Semantik: Mit `enforce-whitelist=true` kickt `/whitelist remove` bzw. `/whitelist reload` online befindliche, nicht mehr gelistete Spieler sofort.
- **Offen für Client-E2E (Phase 8):** Login eines frisch per `/whitelist add` eingetragenen Accounts, Ablehnung eines nicht gelisteten Accounts, Kick beim Entfernen.

## Verifikation Phase 7 (21.10.2026, Paper 26.3 Build 207)

- `SitModuleTest`: Standortprüfung lehnt Zuschauer, Reiten, Fliegen/Gleiten/Riptide, Schwimmen/Wasser/Lava, Bett und fehlenden festen Block unter den Füßen ab; fester Boden (auch Kreativ) ist erlaubt. Geprüft wird der Block 0,05 unter den Füßen auf dem Server, nicht das client-gemeldete On-Ground-Flag.
- Zustand: `SITTING` genau solange ein Eintrag in `seats` existiert; jeder Ausstieg läuft über `standUp`, das zuerst den Eintrag und dann den Stand entfernt. Dadurch ist das beim Entfernen ausgelöste Dismount-Event ein No-op, wiederholtes `/sit` toggelt ohne zusätzliche Entities.
- Testserver: getaggter Sitz (`BukkitValues:{"illuniumcustom:seat":1b}`) und fremder ArmorStand in einem forceloaded Chunk, `save-all flush`, `kill -9`, Neustart → Sitz entfernt (Entity-Load), fremder Stand erhalten. Getaggter Stand in einem Chunk, der entladen und neu geladen wird → entfernt. Enable/Disable ohne Plugin-Fehler.
- **Offen für Client-E2E (Phase 8):** sichtbares Sitzen, Sitzhöhe auf Block/Stufe/Treppe, Aufstehen per Sneaken mit Rücksetzen auf die Sitzstelle, zwei gleichzeitig Sitzende, Schaden/Teleport/Weltwechsel/Quit/Neustart während des Sitzens.

## Verifikation Phase 8 — Release Candidate 0.1.0-rc.1 (21.10.2026)

Umgebung: Paper 26.3 Build 207 (`26.3-207-main@bc68acd`), Temurin 25.0.4.1, WSL2. JAR `IlluniumCustom-0.1.0-rc.1.jar`, SHA-256 `5431edbdc6c27be1bd52b2fc631c0eca6a52abe133d5ee5174fa338988c6fa75` (zwei Clean-Builds bytegleich), Schema v1.

- `./gradlew clean check build --no-daemon`: 52 Tests in 13 Klassen, 0 Fehler.
- **Frische Installation** (leeres Serververzeichnis, Default-Welt, `online-mode=true`, keine anderen Plugins, keine Config/DB): Config und `data.db` angelegt, alle sechs Module aktiv, einzige Plugin-Warnung `Whitelist enforcement was off; turning it on`; Start und Stop ohne `ERROR`/`SEVERE`/Exception.
- **Backup/Restore:** gespeicherte Spielzeit (`Steve`, 1h 2m) online per `sqlite3 ".backup"` gesichert (`integrity_check` ok), Server gestoppt, `data.db*` gelöscht → `Unknown player steve.`; Backup zurückgespielt → wieder `1h 2m`.
- **Rollback-Erkennung:** `schema_migrations` um Version 2 ergänzt → Start bricht mit `Database schema v2 is newer than supported v1; restore a matching plugin version or database backup` ab, Plugin deaktiviert, keine Commands registriert, Daten unverändert.
- **Security-Review** (Code): SQL nur als konstante Strings mit gebundenen Parametern (einzige Verkettung: Migrationsnummer aus dem Code); Eingaben per Regex/Längen/Enum validiert, Staff nur mit v4-UUID und geprüftem Profil; jeder Spieler-Command mit `illuniumcustom.use`, `/ic` mit `illuniumcustom.admin`; Bukkit-API nur auf dem Main Thread, async Ergebnisse über `context.mainThread()`; gelöscht werden nur eigene Teams/Rezepte/Sitz-Entities, nie Whitelist-Einträge. Testserver-Nachweise dazu in Phase 4, 6 und 7.
- **Nicht durchgeführt (braucht echte Vanilla-Clients und mehrere Accounts):** die Gameplay-Abnahme aus [`operations.md`](./operations.md#gameplay-abnahme-testserver-vanilla-client), darunter Whitelist-Login/-Ablehnung, Permission-Negativtests als Nicht-OP-Spieler, Nametag/Tablist/Herz-Sichtprüfung, 2×2-Rezept, Sit-Sichtprüfung und Playtime-Session über Neustart. Bis dahin ist `0.1.0-rc.1` **nicht** produktionsfreigegeben.

## Verifikation Patch v1.1 (21.10.2026, Paper 26.3 Build 207, Temurin 25.0.4.1)

- `./gradlew clean check build --no-daemon` grün, keine Compiler-Warnungen.
- Unit: `GroupColorsTest` (16 Namen case-insensitiv, v1.0-Großschreibung lesbar, `#000000`/`#FFFFFF`/`#a1B2c3`, Ablehnung von `FF8800`, `#FFF`, `#FFFFFFFF`, `#GG0000`, Leerzeichen, Zeilenumbruch, MiniMessage, `&c`/`§c`, Steuerzeichen); `GroupServiceTest` (Hex speichern, nach Neustart gleich, abgelehnte Farbe ändert nichts, Mod darf nicht färben); `SitModuleTest` (jede der 7 Tasten steht neu gedrückt auf, gehaltene/losgelassene Tasten und Mausbewegung nicht); `MarriageModuleTest` (2,9/3,0/3,1 Blöcke, vertikal, diagonal, andere Welt); `VisibilityServiceTest` (Default sichtbar, idempotent, Quit ohne Benachrichtigung); `SpawnModuleTest` (fester Boden, freie Füße/Kopf, kein Wasser/Lava/Feuer).
- Testserver ohne Clients: alle 8 Module aktiv, `/spawn`, `/sit`, `/group color` aus der Konsole → „nur Spieler“, `/vanish`/`/unvanish` aus der Konsole → „Only operators in game“, Disable ohne Fehler.
- **P6 Difficulty-Gate** (frischer Testserver, `difficulty=easy`): `difficulty hard` per Konsole → Overworld Hard, Nether weiterhin Easy. Danach zweimal sauber `stop`/Start: Overworld blieb Hard, Nether/End Easy, `server.properties` unverändert `easy`. Ergebnis **PASS** für Persistenz → kein Difficulty-Modul; Paper wirkt pro Welt (siehe `commands.md`). Offen: ein Neustart über Pterodactyl auf dem Zielhost (Panel könnte `server.properties` setzen, wirkt aber nur bei neuen Welten).
- **Offen für Client-E2E:** Sit-Aufstehen pro Taste mit echtem Client (Zustellung von `PlayerInputEvent` beim Reiten, kein Zurückspringen); Hex-Farbe über Kopf und in der Tablist; Herzpartikel mit zwei Accounts; Vanish mit zwei normalen Clients + OP (kein Join-Text, kein Tab-Flackern, `/list`, Namensvorschläge, Serverlisten-Probe, Leave-Text genau einmal, De-OP); `/spawn` aus Nether/End, nach `/setworldspawn`, während Sitzen, blockierter Spawn.
