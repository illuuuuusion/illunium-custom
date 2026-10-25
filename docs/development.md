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

## Verifikation Patch v1.2 (21.10.2026, Paper 26.3 Build 207, Temurin 25.0.4.1)

- Ausgangslage: v1.1-`SpawnModule` vorhanden (Sofort-Teleport ohne Zustand) und erweitert, keine zweite Registrierung. Baseline-Build vor den Änderungen grün.
- `./gradlew clean check build --no-daemon`: 69 Tests grün.
- Unit: `SpawnServiceTest` (Teleport fällig bei 9,999 s nein / 10,000 s ja, genau einmal; Doppel-`/spawn` ohne Neustart/Verkürzung; Abbruch idempotent und ohne Cooldown, auch während der Chunk lädt; eigenes Teleport-Event bricht `COMPLETING` nicht ab; gescheiterter Teleport ohne Cooldown; Cooldown 59:59,999 gesperrt / 60:00 frei mit Anzeige `42 min 19 s`; Combat-Tag 29,999 s / 30 s und Erneuerung; Quit löscht Warmup und Tag, nicht den Cooldown; nur X/Y/Z-Änderung > 0,001 zählt als Bewegung); `SpawnModuleTest` (Cooldown-Zeilen über DB-Neuöffnung, Upsert, abgelaufene Zeilen gelöscht); `DatabaseTest` (Migration v2 idempotent).
- Testserver ohne Clients (Kopie von `run/` mit DB auf Schema v1): Migration auf v2 beim Start, alle 8 Module aktiv, `/spawn` aus der Konsole → „nur Spieler“; Cooldown-Zeilen (aktiv/abgelaufen) über Stop/Start: aktive bleibt mit gleicher Deadline, abgelaufene entfernt; Log ohne `SEVERE`/Exception.
- **Offen für Client-E2E** (Testmatrix S01–S30 im Plan, Checkliste in [`operations.md`](./operations.md)): echte Bewegungs-/Schadens-/PvP-Fälle mit zwei Accounts, Kalibrierung der Bewegungstoleranz, Event-Reihenfolge von `getFinalDamage` bei Schild/Rüstung, Sit/Vanish-Kombination, DB-Fault-Injection. Bis dahin keine Produktionsfreigabe.

## Verifikation Patch v1.3 (24.10.2026, Paper 26.3 Build 207, Temurin 25.0.4.1)

Implementierung: `51ff21d`. Baseline: `ff4181e` (v1.2 bereits implementiert), vor dem Patch 69 Tests grün. Der Remote-Abgleich ergab keine konkurrierenden Änderungen. API-Pin, Java-Version, bestehende Migrationen und Spawn-Regeln unverändert. V3 ergänzt Leaderboard-Metriken/Spawn-IDs, V4 Staging-Einträge/Launches/Audit; Schema jetzt v4.

- `./gradlew clean check build --no-daemon`: **88 Tests**, keine Fehler oder übersprungenen Tests. Zusätzliche Tests: exakte elf Kategorien/Einheiten ohne PvP, positive Werte, deterministische Gleichstände und Wettbewerbsränge, Seitenvalidierung, Offline-OP-Ausschluss, langer Distanz-Summenwert ohne int-Overflow, ungültige Materialien einmalig auslassen; Platzierung/MultiPlace ohne Doppelzählung, Cancel/canBuild/GameMode, echte Totem-Hand, monotones XP; Spawn-Deduplizierung über DB-Neuöffnung und Transaktionsrollback mit Retry; Bulk-Livezeit entspricht `/playtime` über einen Checkpoint; 100 Staging-Einträge über Neustart, UUID-/Rename-/Case-Idempotenz, native Bestandskonten, feste Claims, Teilfehler/Retry und Recovery; Rechte trotz anderweitiger Admin-Permission; Vanish-Policy-Matrix, sichtbarer Opt-out-Join, späterer Viewer, Nicht-OP und Rejoin; Upgrade einer v2-DB bewahrt Profile, Spielzeit und Spawn-Cooldowns.
- **Isolierter Paper-Smoke:** Kopie des vorhandenen Testservers unter `/tmp`, nur Loopback-Port, keine Forever-World. Start/Stop und Migration auf v4, zehn Module aktiv, Übersicht und Alias, klare Fehler für unbekannte Kategorie/negative Seite. Mit 26 kontrollierten bekannten Offline-Profilen: Top 10, Folgeseiten, alle elf Kategorie-Commands, eigener Rang #24 außerhalb der Top 10, Offline-OP in jeder Kategorie ausgeschlossen. Ein temporäres Prüfplugin rief dieselben Commands mit einem kontrollierten Player-Sender auf; das ersetzt keine echten Clients.
- **Statistikadapter auf echter Paper-API:** gesetzte Offline-Werte `stone=7`, `diamond_ore=2`, `deepslate_diamond_ore=3` → `mined=12`, `diamonds=5`; Gehen/Sprinten/Sneaken → 70.368 cm; Mobs/Tode/Fänge → 4/5/6. Mit vollständiger Materialliste 41–42 Main-Thread-Schritte, insgesamt ca. 47–65 ms CPU, maximal ca. **2,2 ms pro Schritt** auf dieser Umgebung. Der erste vollständige Snapshot der 26 Profile brauchte rund eine Minute; spätere Commands scannten keine Statistiken. Einzelne Dateizugriffe sind nicht hart präemptierbar, große Bestände müssen auf Zielhardware profiliert werden.
- **Native Whitelist:** echter nie gejointer Java-Account `jeb_` zunächst STAGED bei leerer `whitelist.json`; erst ausdrücklicher Launch führte den Vanilla-Command aus und bestätigte UUID `853c80ef-3c37-49fd-aa49-938b674adae6` plus Name. Wiederholter Launch idempotent. Profilabfragen für unbekannte/nicht erreichbare Profile wurden sichtbar abgelehnt und erzeugten keine Fake-Einträge. Die Wahl ist ausdrücklich **Vanilla-Command**, nicht `getOfflinePlayer(uuid).setWhitelisted(true)`: vor dem Command muss der verifizierte Name bereits mit derselben UUID im nativen Namenscache liegen, damit kein Netzwerkzugriff auf dem Tick-Thread erfolgt. Anschließend native UUID und nichtleeren Namen prüfen; fehlender Cache oder Verifikation bedeutet FAILED, kein blinder UUID-Write.
- **Finaler Lauf mit aktueller JAR:** APPLYING-Fixture nach simuliertem nativem Write → Recovery bestätigt APPLIED. Separater Launch nach Neustart → `whitelist.json` enthält genau die erwartete UUID und `name: "jeb_"`; `/whitelist remove jeb_` plus Retry und weiterer Neustart reaktivieren nichts. Whitelist/Enforcement bleiben aktiv. Der Testserver wurde sauber gestoppt.
- **Betrieb:** konsistentes SQLite-Online-Backup (`integrity_check=ok`) und Restore nach absichtlichem Löschen der 25 Metrikzeilen erfolgreich. `playtime`, `vanish` und `whiteliststaging` abgeschaltet → Leaderboard startet weiter, Spielzeit klar nicht verfügbar, native Freigaben unverändert. String `"false"` im Vanish-Override → klarer Startup-Fehler. Alte Schema-v1-JAR mit ihrer passenden Config und v4-DB → sichere Ablehnung wegen neuerem Schema; dieselbe alte JAR mit passendem v1-Backup → erfolgreicher Start. Danach isolierte Kopie auf aktuellen Build/v4 zurückgesetzt. Kein Produktionsserver verändert.
- **Fehlergrenzen:** Die Antwort auf einen mehrteiligen Launch wird erst nach den Einzel-Commits abgeschlossen. Ein DB-Fehler kann nach einem erfolgreichen nativen Write auftreten; deshalb behauptet die allgemeine Command-Fehlermeldung nicht mehr, es sei nichts geändert worden. Fehler in asynchronen Ausgabe-Callbacks werden ebenfalls sichtbar behandelt. Claims werden zusätzlich gegen ihre Launch-ID bestätigt.

Manuelle Release-Gates stehen in [`operations.md`](./operations.md#v13-abnahme-vor-produktionsfreigabe). **Nicht durchgeführt:** echte Client-Joins/-Kicks, Tablist-/Entity-/Ping-Sichtprüfung mit mehreren Accounts, reales Totem-/Spawn-/Gameplay-E2E und Lastprofil auf dem Produktionshost. Der Code ist implementiert und automatisiert geprüft; das ist keine Produktionsfreigabe.

## Verifikation Patch v1.4 (25.10.2026, Paper 26.3 Build 207, Temurin 25.0.4.1)

Basis: `e45c105`, isolierter Branch `patch/v1.4`. Baseline `./gradlew clean check build --no-daemon`: **88 Tests**, erfolgreich. Vorhandene lokale Staging-DB (Schema v1) per SQLite-Backup-API gesichert, `integrity_check=ok`, Welten archiviert. Ein separater v1.3-Paper-Start erzeugte Schema v4 und stoppte sauber; hiervon wurden V4-DB und Welten erneut gesichert. Entwicklung/Smoke verwenden ausschließlich isolierte Kopien unter `run/v14-*`.

- **104 Unit-/SQLite-Tests**: Config-Defaults und strikte Werte; versioniertes 27-Slot-Format mit Kürzungs-/Marker-/Versions-/Längenfehlern; geordnete atomare Snapshots, Revisionen, stale-write rejection, Speichern/Neustart, beschädigte Blobs unverändert; Disband in Normal-/Adminpfad und SQL-Trigger, leere Cascade und ID-Wiederverwendung; Post-Commit-Lifecycle einschließlich unveränderter Mitglieder; Allowlist sämtlicher Paper-InventoryActions und Swap-/Creative-/Collect-Wege; Gruppenchat mit Gruppe A/B, Offline-Mitgliedern, Kick/Join/Leave, verborgenem Sender/Empfänger, Konsole, Literaltext/Hexfarbe, Unicode-Limit und senderspezifischem Cooldown; farbige Komponenten, alle elf Kategorie-Links, Rang-/Werttext und Seitennavigation. V4 → V5 vergleicht alle elf bestehenden Tabellen vor/nach Migration und zweitem Start vollständig; V1–V4 bleiben unverändert.
- **Paper-26.3-Smoke mit Produkt-JAR**: Migration der separaten V4-DB auf v5, alle Module aktiv, `/ic status`; `/gec` und `/g` weisen Konsole zurück, `/leaderboard` zeigt elf Kategorien, `/lb diamonds` funktioniert. Plugin-Enable/Disable ohne Pluginfehler. Alte v1.3-Konfiguration ohne neue Schlüssel startet kompatibel.
- **Paper-Prüfplugin, PASS**: vier gleichzeitige synthetische Zuschauer teilen dieselbe Live-Instanz; Kick schließt den entfernten Zuschauer, verbleibende wechseln bei 4 → 3 in Entnahme-Modus und bei 3 → 4 wieder zu Einlagen. Click-/Drag-Events werden auf echten Bukkit-Inventaren geprüft. Benannte/verzauberte Items, PDC, Shulker-Inhalte, Bücher und direkte Data Components überstehen NBT und Save/Load. Last-Close bestätigt die DB, leere Cascade/ID-Wiederverwendung sperrt neue Kleingruppen, Stop ohne Checkpoint tick flusht. Erzwungener SQL-Schreibfehler sperrt den Zugriff und behält Cache/Disband-Sperre; beschädigter Blob bleibt unverändert gesperrt. Absichtliche Fehlerlogs sind Teil dieses Tests.
- **Neustart/Config/Rollback, PASS**: echte Stop/Start-Prüfung der Sonderitems (`PAPER_PROBE_RESTART_PASS`); zweiter Produktstart bleibt auf genau fünf Migrationen. `groups.gec.enabled: false` / `groups.chat.enabled: false` und `modules.groups: false` registrieren die jeweiligen Commands nicht. Ungültiges `min-members: 3` deaktiviert das Plugin mit präzisem Fehler. Alte v1.3-JAR gegen V5 lehnt den Start ab; Wiederherstellung des V4-Backups mit alter JAR startet wieder auf Schema v4. Nach jedem Fall `integrity_check=ok`.
- **Finaler Build**: `./gradlew clean check build paperProbeJar --no-daemon` erfolgreich, 104 Tests, keine Compilerwarnungen. Produkt-JAR SHA-256: `473a5c929b1491abdd44e48055db0e50780a3ce8d99112de90be19d04866b47d`; enthält fünf Migrationen und keine Probe-Testklassen.
- **Offen**: echte Client-E2E mit mindestens vier Accounts, vollständige Input-/Creative-/Bundle-Matrix im Client, parallele echte Transfers, Disconnect/Prozesscrash während Transfers, Gameplay-Regression und visuelle Abnahme. Ohne diese Tests gibt es keine Produktionsfreigabe.

### Opt-in Paper-Prüfplugin

`./gradlew paperProbeJar --no-daemon` erzeugt ausschließlich `build/paper-probe/IlluniumGroupChestProbe.jar`. Diese Datei ist **kein Release-Artefakt**. Nur auf einem frischen, wegwerfbaren Paper-26.3-Testserver als einziges Plugin starten, mit leerem Plugin-Datenordner. Der Prüfplugin nutzt synthetische Zuschauer, echte Bukkit-Inventare, NBT und SQLite. Er erzeugt Gruppen/Items, injiziert absichtlich SQL-Schreibfehler und beschädigte Blobs, meldet abschließend `PAPER_PROBE_PASS` oder `PAPER_PROBE_FAIL` und stoppt den Testserver selbst. Nach dem ersten PASS denselben Testserver erneut starten: `PAPER_PROBE_RESTART_PASS` prüft die gespeicherten Sonderitems nach einem echten Serverneustart. Für einen kompletten neuen Durchlauf einen neuen Wegwerf-Datenordner verwenden. Fehlerlogs aus Failure-Injection sind erwartet; nur PASS bestätigt den Durchlauf. Kein Ersatz für echte Client-E2E, niemals auf der Forever-World einsetzen.
