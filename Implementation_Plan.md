# IlluniumCustom — Implementation Plan v1.0

> **Stand:** 20.10.2026  
> **Status:** umsetzungsbereiter Plan; **keine Implementierung durchgeführt**  
> **Repository:** https://github.com/illuuuuusion/illunium-custom  
> **Produktvertrag:** [`MVP.md`](./MVP.md) — bei Abweichungen ist dieser MVP für das Spielverhalten maßgeblich.  
> **Zielruntime:** Minecraft/Paper **26.3**, Java **25**; Testbuild vor Implementierungsbeginn exakt festlegen.

## 0. Arbeitsmodus und Entscheidungsgrundlagen

Dieses Repository wurde am 20.10.2026 über GitHub geprüft und war **leer** (Branch `main`, keine bestehenden Dateien). Es gibt daher weder vorhandenen Code noch ein geerbtes Build-/Deployment-System. Dieser Plan erzeugt bewusst zuerst einen vollständigen, wartbaren Projektkern und danach **kleine, einzeln überprüfbare Feature-Inkremente**.

**Grundregeln:**

1. Vor jeder Änderung: `git status`, aktuelle Repo-Regeln (`AGENTS.md`, `CLAUDE.md`, `.github/` usw.) und aktuellen Code erneut lesen. Ein später veränderter Istzustand hat Vorrang vor diesem historischen Snapshot.
2. **Read-only → Design/Candidate → Diff/Review → Implementierung → Tests → Dokumentation**. Keine massenhaften unreviewten Änderungen.
3. Entwicklungs- und Testserver strikt von der Forever-World trennen. Nichts unbeabsichtigt auf Produktion deployen.
4. Git Commit, Push, PR-Merge und produktiver Rollout sind getrennte **menschliche Freigaben**; der Plan autorisiert sie nicht automatisch.
5. Keine Passwörter, Tokens, API-Keys, RCON-Daten, SSH-Private-Keys oder DB-Secrets in Repository, Logs oder Chat.
6. **Kein** Eingriff in Jenics/Proxmox/Edge/WireGuard/Firewall-Sicherheitsgrenzen für die Pluginentwicklung.
7. Kein neues Microservice, Web-API, Discord-Linking oder eigenes Proxy-System; **eine** Paper-JAR.
8. Fehler in Build, Tests oder Runtime stoppen den Ablauf bis zur Diagnose; kein „weiter trotz FAIL“.''
9. Git Commits ohne Co-Author, auf englisch und kurz

### 0.1 Entwicklungsumgebung

- Java **25** für Compiler, Tests und Paper.
- Gradle Kotlin DSL + **Gradle Wrapper**, mit festgepinnten Versionen.
- `paper-api` **26.3**, ausschließlich als `compileOnly` und auf einen tatsächlich veröffentlichten, getesteten API-Build gepinnt; zur Recherche darf ein dynamisches Artefakt verwendet werden, **nicht** als finale Release-Abhängigkeit.
- JUnit 5 (Unit-/Repositorytests); optional MockBukkit **nur**, wenn nachweislich 26.3-kompatibel, sonst echte Paper-Smoke-Tests.
- SQLite-JDBC als **gebündelte Java-Library** (kein externes Plugin und kein Datenbankserver); Shadow-/Relocation-Konfiguration in der JAR prüfen.
- Classic `plugin.yml` reicht für ein MVP; Paper-spezifische API-Funktionen dürfen trotzdem genutzt werden.
- Java-/Gradle-Versionen in CI und lokal konsistent halten.

**Wichtige externe Referenzen:**

- https://docs.papermc.io/paper/dev/project-setup/
- https://docs.papermc.io/paper/dev/api/
- https://github.com/PaperMC/Paper

**Gate 0:** Der genaue Paper-26.3-Server-/API-Build wird **live** geprüft (Build-Kanal und Versionsstatus), festgehalten und im Testserver reproduziert. Historische 26.3-Beta-Angaben oder ein API-`+` sind keine Release-Freigabe.

## 1. Architekturziel

### 1.1 Verzeichnisstruktur (Ziel, kein vorhandener Istzustand)

```text
illunium-custom/
├── README.md
├── MVP.md
├── Implementation_Plan.md
├── CHANGELOG.md
├── .gitignore
├── gradlew / gradlew.bat
├── gradle/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── .github/
│   └── workflows/
│       └── build.yml
├── docs/
│   ├── commands.md
│   ├── configuration.md
│   ├── development.md
│   ├── operations.md
│   └── architecture.md
└── src/
    ├── main/
    │   ├── java/de/illunium/custom/
    │   │   ├── IlluniumCustomPlugin.java
    │   │   ├── core/
    │   │   │   ├── module/           # FeatureModule, Context, Registry
    │   │   │   ├── config/           # Laden, Validierung
    │   │   │   ├── command/          # Registrierung, Completion
    │   │   │   ├── identity/         # UUID + lastKnownName
    │   │   │   ├── permission/       # Bukkit-Admin + Domain-Checks
    │   │   │   └── lifecycle/        # Tasks, Fehler, Shutdown
    │   │   ├── storage/             # SQLite, Migration, Transaktionen
    │   │   ├── appearance/          # Scoreboard/Nametag/Tablist
    │   │   ├── recipes/
    │   │   ├── groups/
    │   │   ├── playtime/
    │   │   ├── marriage/
    │   │   ├── whitelistguard/
    │   │   └── sit/
    │   └── resources/
    │       ├── plugin.yml
    │       ├── config.yml
    │       └── db/migrations/
    └── test/
        └── java/de/illunium/custom/
```

Packages und Klassen sind **Vorschläge**. Entscheidend sind Modulgrenzen, Testbarkeit und klare Eigentümerschaft.

### 1.2 Modulvertrag

Jedes Feature implementiert ein schlankes Interface, konzeptionell:

```java
interface FeatureModule {
    String id();
    void enable(ModuleContext context) throws Exception;
    void disable();
}
```

Ein `ModuleContext` bietet explizit benötigte gemeinsame Services (Logger, Config-View, Scheduler, DB, Spieleridentitäten, Appearance-Update-Schnittstelle). Keine globalen `static getInstance()`-Zugriffe als Standardmuster. Der Bootstrap nutzt eine **explizite Registry**, keine Classpath-/Reflections-Magie.

**Modul-Lifecycle:**

1. Konfiguration und Storage vorbereiten, Migration ausführen.
2. Gemeinsame Core-Services registrieren.
3. Appearance-Service (Nametags/Tablist) initialisieren.
4. Fachmodule aktivieren, Commands und Listener anmelden.
5. Bereits online befindliche Spieler initialisieren und Anzeige einmal synchronisieren.
6. Beim Disable: weitere Mutationen sperren → Sessions/Tasks schließen → Daten flushen → Entities/Teams bereinigen → DB schließen.

Neue Features sollen sich **durch neues Paket + neues Modul + Registry-Eintrag + eigene Tests** integrieren lassen, ohne bestehende Module fachlich umzubauen. Feature-Flags gelten beim **Start/Restart**, nicht als Hot-Reload-API.

### 1.3 Verantwortlichkeiten und Abhängigkeitsrichtung

```text
Paper events / commands
         |
         v
Feature Commands/Listeners
         |
         v
Feature Service (Regeln/Permissions)
         |
         +----> Repository/Storage (persistente Daten)
         |
         +----> Appearance API (nur Datenänderung melden)
                        |
                        v
                Paper UI/Scoreboard
```

- `groups` verwaltet **nur** Gruppen/Mitgliedschaft/Rollen/Einladungen.
- `marriage` verwaltet **nur** Partnerbeziehungen/Anträge.
- `appearance` kombiniert die Daten für Zuschauer und besitzt **allein** die vom Plugin kontrollierten Nametag-/Tablist-Mutationen.
- `playtime` verwaltet Zeitstände; `sit` hält ausschließlich flüchtige Sessionzustände.
- `whitelistguard` benutzt native Server-Whitelist und Staff-Config; **keine zweite Access-Datenbank**.
- Features dürfen nicht direkt gegenseitig SQL ausführen oder gegenseitig Commands aufrufen.

### 1.4 Threading und Performance

- **Alle Bukkit-/Paper-Spieler-, Entity-, Scoreboard-, Command- und Weltoperationen auf dem Primary Server Thread**.
- DB-/Datei-I/O asynchron über einen **dedizierten begrenzten Executor**; keine ungehemmten neuen Threads pro Command.
- Schreiboperationen werden geordnet; bei DB-Fehlern erhält der Spieler einen Fehler, statt dass nur der Chat „Erfolg“ meldet.
- Für kritische Mutationen gilt: **Transaktion erfolgreich → In-Memory-State aktualisieren → Antwort/Appearance-Refresh**. Kein Erfolg vor Commit.
- Keine synchrone externe Profilauflösung beim Join; Vanilla `/whitelist add` übernimmt die Accountauflösung.
- Chunk-/Entity-Scans nur ereignisgetrieben oder begrenzt. Kein per-Tick-Polling für Gruppen-/Marriage-UI.
- Async-Callbacks prüfen, ob Spieler/Plugin noch aktiv sind, bevor sie Paper-API benutzen.

### 1.5 Konfiguration

`config.yml` enthält ausschließlich stabile Runtime-Optionen, keine geheimen Tokens. Beispiel der Struktur:

```yaml
modules:
  recipes: true
  groups: true
  playtime: true
  marriage: true
  whitelistguard: true
  sit: true

storage:
  filename: data.db

playtime:
  checkpoint-seconds: 60

groups:
  invite-expiry-seconds: 300
  confirmation-expiry-seconds: 30

marriage:
  request-expiry-seconds: 300
  divorce-confirmation-seconds: 30

whitelistguard:
  check-interval-seconds: 30
  staff: [] # Einträge mit verifizierter Minecraft-UUID und bekanntem Profilnamen

sit:
  cleanup-on-start: true
```

**Validierung:** Ungültige Intervallwerte, doppelte Staff-UUIDs oder invalide Plugin-Einstellungen erzeugen eine präzise Fehlermeldung. Bei nicht validierbarer Staff-Identität keine Pseudo-UUID erzeugen oder fremde Accounts zulassen. Secrets gehören nicht in diese Datei; im MVP werden keine benötigt.

## 2. Persistenz-Design

### 2.1 SQLite-Daten

**Vorschlag Schema v1 (präzise SQL wird in Phase 1 reviewed):**

```text
schema_migrations(
  version PRIMARY KEY,
  applied_at
)

player_profiles(
  uuid PRIMARY KEY,
  last_known_name,
  last_seen_at
)

groups(
  id PRIMARY KEY,
  name, name_normalized UNIQUE,
  tag, tag_normalized UNIQUE,
  color,
  leader_uuid,
  created_at
)

group_memberships(
  group_id, player_uuid UNIQUE,
  role,             # MEMBER | MOD; LEADER wird über groups.leader_uuid bestimmt
  joined_at,
  PRIMARY KEY(group_id, player_uuid)
)

marriages(
  id PRIMARY KEY,
  partner_a_uuid,
  partner_b_uuid,
  married_at
)

playtime(
  player_uuid PRIMARY KEY,
  total_seconds,
  updated_at
)
```

### 2.2 Konsistenzregeln

- `PRAGMA foreign_keys=ON` für jede SQLite-Connection.
- `groups.leader_uuid` muss auf ein Mitglied **derselben** Gruppe zeigen (Service-Invariante plus geeignete DB-Constraints/Trigger oder transaktionale Prüfung).
- Im laufenden Commit darf eine Gruppe **nie ohne genau einen Leader** vorliegen; beim Disband Gruppe/Mitgliedschaften atomar löschen.
- Maximal eine `group_memberships`-Zeile pro UUID.
- Name/Tag immer normalized-case-unique; Regex zusätzlich im Service validieren.
- Beim Leader-Transfer: Ziel muss Mitglied sein; bisheriger Leader wechselt in einem Commit zu `MOD`, neuer Leader wird in `groups.leader_uuid` gesetzt.
- Marriage: Partner-UUIDs kanonisch sortieren, keine Selbstpartnerschaft; zusätzlich garantieren, dass **keine UUID in mehreren Marriage-Zeilen** vorkommt (Transaktion + entsprechende Schema-Strategie/Constraints). Parallel ankommende Anträge dürfen nicht zu Doppelheirat führen.
- Zeitstände ausschließlich **nichtnegativ**, monotone Session-Zeitmessung und atomare Checkpoints; keine negativen oder doppelt gezählten Sekunden.
- **Whitelist nicht in SQLite spiegeln.** Nativer `whitelist.json`-Stand ist allein maßgeblich.
- Migrationen sind inkrementell und dokumentiert; vor schemaänderndem Deployment Datenbank-Backup.

### 2.3 Backup und Recovery

- Plugin-Daten: Server-Pluginordner inklusive `data.db`, `config.yml`; bei SQLite-WAL einen **konsistenten Backup-/Checkpoint-Pfad** nutzen (SQLite Backup API bzw. kontrollierter Stop, **nicht** unkoordiniert nur die `.db` im Live-Betrieb kopieren).
- Native Whitelist: separate Datei `whitelist.json` gehört zum Minecraft-/Pterodactyl-Serverbackup, **nicht** zur Plugin-Datenbank.
- Pre-Deploy-Snapshot von Plugin-JAR, DB, relevanter Config und ggf. `whitelist.json`.
- Restoretest auf dem **Testserver**; Verknüpfung von getesteter JAR-Version und Schema-Version dokumentieren.
- Ein reiner JAR-Rollback reicht **nicht**, wenn eine neuere Datenbankmigration inkompatibel ist; Rollbackstrategie mit DB-Snapshot prüfen.

## 3. Umsetzungsphasen

Die Phasen sind in dieser Reihenfolge empfohlen. **Jede Phase hat einen klaren, reproduzierbaren Exit-Gate** und soll separat reviewed werden.

### Phase 0 — Repository & Build Foundation

**Arbeit:**

- Repository-Regeln prüfen und dieses `MVP.md` sowie `Implementation_Plan.md` an Repo-Root aufnehmen.
- Gradle Wrapper, Java-25-Toolchain, `build.gradle.kts`, `settings.gradle.kts`, `.gitignore` anlegen.
- Offiziellen Paper-26.3-Server-/API-Build prüfen, Version **exakt pinnen**; Java 25 validieren.
- Plugin-Hauptklasse, `plugin.yml` (`api-version: '26.3'`, Commands, Permissions), Logging, sauberer Enable-/Disable-Pfad.
- Unit-Test-Grundlage und CI mit `./gradlew clean check build --no-daemon`; JAR als CI-Artefakt bereitstellen.
- `README.md` mit Entwicklungs- und lokalem Teststart; `docs/development.md`.

**Exit-Gate:**

- [ ] Clean Checkout baut unter Java 25 deterministisch.
- [ ] Paper 26.3 erkennt Plugin ohne andere Plugins, Enable/Disable zeigt keine Exceptions.
- [ ] CI führt mindestens einen echten Test aus; generierte Build-Dateien/DB nicht im Git.
- [ ] Keine Produktions-Deploy-Action im CI.

**Vorgeschlagener Review-/Commit-Schnitt:** `chore: bootstrap standalone Paper plugin`.

### Phase 1 — Core, Storage und Erweiterungsvertrag

**Arbeit:**

- Module Registry/Context/Lifecycle, Config-Validierung, zentraler Fehlerhandler, Scheduler-Wrapping.
- `PlayerIdentityService` mit UUID und lastKnownName; Join aktualisiert Cache.
- SQLite-Integration inkl. Migration v1, Connection-Lifecycle, transaktionaler Repository-API, dediziertem DB-Executor.
- Repositories und Service-Interfaces als **kleine explizite Verträge**; keine Feature-Implementierungen vorgaukeln.
- Permission-Nodes `illuniumcustom.use` (`true`) und `illuniumcustom.admin` (`op`).
- Konfiguration für Modulaktivierung; Shutdown-Tasks geordnet schließen.
- Test: Migration erstmals und erneut ausführen; DB beschädigt/readonly; Transaction-Rollback; mehrere gleichzeitige Schreibanforderungen.

**Exit-Gate:**

- [ ] Modul per Registry hinzufügen lässt Bootstrap unverändert bis auf einen Registry-Eintrag.
- [ ] DB-Migration ist idempotent; falsches Schema erzeugt sichtbaren FAIL, keine stille Löschung.
- [ ] Keine Bukkit-API auf DB-Executor; keine Main-Thread-Warteoperation für Storage.
- [ ] Restart/Disable mit offenen Tasks erzeugt keinen Datenverlust durch Race Conditions.

**Review-/Commit-Schnitt:** `feat: add modular core and SQLite foundation`.

### Phase 2 — Recipes (kleines End-to-End-Modul)

**Arbeit:**

- Modul `recipes` mit eigenem NamespacedKey und Shapeless-Rezept `QUARTZ_BLOCK` → `4 × QUARTZ`.
- Registrieren auf Enable; Umgang mit vorhandenem Key und deaktiviertem Modul ohne Vanilla-Rezepte zu beschädigen.
- Erste vollständige Probe: neues Modul ohne Änderung in anderen Fachmodulen, außer Registry.

**Exit-Gate:**

- [ ] 2×2 und 3×3 funktionieren, Ausgabemenge korrekt.
- [ ] Säulen/Chiseled/Smooth werden nicht akzeptiert.
- [ ] Plugin-Neustart/erneute Registrierung führt zu keinen doppelten Rezepten.

**Review-/Commit-Schnitt:** `feat: add reversible quartz recipe`.

### Phase 3 — Groups: Domain, Commands, Rollen

**Arbeit:**

1. `Group`, `Membership`, `GroupRole` und Repositories auf Schema v1.
2. `GroupService`: create, list, info, invite, uninvite, accept, deny, leave, kick, promote, demote, rename, tag, color, transfer, disband.
3. **Policy-Matrix als Code** (Leader/Mod/Member + Zielrolle), nicht in jedem Command separater `if`-Wildwuchs.
4. Name/Tag-Regex und case-insensitive Unique-Prüfung (vor Schreibversuch + DB-Constraint).
5. Einladungsspeicher mit fünf Minuten TTL und expliziter Gruppenauswahl; Race/Revalidierung bei `accept`.
6. Bestätigungsflow für `transfer`/`disband`, TTL 30 Sekunden; atomare Transaktionen.
7. Begrenzte Admin-Recovery-Commands unter `/ic group ...`.
8. Tab-Completion, hilfreiche Fehler/Success-Messages, Permission-Gate, kein Spieler-Only-Command aus Konsole ohne sinnvollen Kontext.

**Exit-Gate:**

- [ ] **Jeder** Eintrag aus der Rollen-Matrix mindestens einmal positiv und negativ getestet.
- [ ] Einzigartigkeit: `NOVA` und `nova` nicht gleichzeitig als Name/Tag, max. 16/5 Zeichen, keine Leer-/Formatzeichen.
- [ ] Eine UUID nie in zwei Gruppen, exakt ein Leader, beliebig viele Mods.
- [ ] Offline-Kick/Promotion/Demotion und Anzeige nach späterem Rejoin korrekt.
- [ ] Gleichzeitige Beitrittsversuche/Transfer/Disband hinterlassen keine halben Änderungen.
- [ ] Einladung 299 s noch gültig, ab 300 s abgelaufen; Restart verwirft pending Invites.
- [ ] Recovery-Commands nur mit `illuniumcustom.admin`, dokumentiert/logged.

**Review-/Commit-Schnitt:** `feat: implement group domain and role management`.

### Phase 4 — Marriage und zentrales Appearance-Modul

**Arbeit:**

1. `MarriageService` inkl. Request/Accept/Deny/Info/Divorce/Confirm/Cancel.
2. Paar-Konsistenz in DB einschließlich konkurrierender Requests/Annahmen und einseitiger Scheidung.
3. **Ein zentraler `AppearanceService`:**
   - Gruppendaten als Prefix für Scoreboard-Team/Nametag.
   - Tablist aus **Group-Prefix + Vanilla-Name + Marriage-Symbol**.
   - Appearance-Refresh nach Join/Quit, Gruppenmutationen, Marriage-Mutationen, Pluginstart/-ende.
   - Kein permanentes per-Tick-Refresh.
4. Technischer Mini-Prototyp auf **dem tatsächlich gepinnten Paper 26.3**: prüft, ob Scoreboard-Team-Prefix plus expliziter `playerListName`-Component im Client den Tag nicht doppelt anzeigt.
5. Festlegen und dokumentieren, ob Main Scoreboard oder kontrollierte per-viewer Scoreboards notwendig sind. Keine Überschreibung fremder Objectives, die dieses Plugin nicht besitzt.
6. Font-Test mit Vanilla-Client für `❤`/`💔`; falls `💔` fehlt, Sichtbarkeit durch getestetes Zeichen wie `✕❤` sicherstellen. **Kein Resourcepack verpflichtend**.
7. Sämtliche vom Plugin angelegten Teams eindeutig prefixen, aktualisieren und beim Disable bereinigen.

**Exit-Gate:**

- [ ] Tag über dem Kopf, Tag plus Herz in Tablist; **keine doppelten Prefixes**.
- [ ] Alle Zuschauer sehen den Marriage-Status jedes verheirateten Spielers.
- [ ] Partner-Join/Quit zeigt zeitnah `❤`/sichtbare Offline-Alternative.
- [ ] Einfache Gruppen-/Marriage-Änderungen verursachen nicht unnötig breite Scoreboard-Rebuilds.
- [ ] Ein Spieler kann nie mehr als einen Ehepartner haben, auch bei gleichzeitigen Requests.
- [ ] Plugin-Disable/Restart hinterlässt keine veralteten Nametags oder Team-Artefakte.

**Review-/Commit-Schnitt:** `feat: add marriage and unified player appearance`.

### Phase 5 — Playtime

**Arbeit:**

- UUID-basiertes Session-Tracking: Startzeit Join, Zeitpunkt letzter Persistenz, kumulierte Sekunden.
- Monotone Zeitquelle für laufende Sessions verwenden; nicht aus Systemzeitänderungen negative oder doppelte Zeit errechnen.
- Checkpoint maximal 60 Sekunden, Quit-Flush, Shutdown-Flush; ausstehende async DB-Schreibvorgänge geordnet abschließen.
- `/playtime` und `/playtime <name>` mit Daten aus aktueller Session + gespeicherter Zeit; Offline-Playerlookup via Cache, ggf. eindeutige Namensauflösung.
- Erwarteten Verlust bei ungeplantem Crash (höchstens Zeitraum seit letztem Checkpoint) dokumentieren.

**Exit-Gate:**

- [ ] Livezeit steigt korrekt; kein mehrfaches Zählen bei parallelem Checkpoint/Quit.
- [ ] Offline-Spieler abrufbar, unbekannte Namen mit verständlicher Fehlermeldung.
- [ ] Restart und geordneter Shutdown erhalten Zeit.
- [ ] Kein synchroner DB-Query pro `/playtime` auf dem Main Thread.

**Review-/Commit-Schnitt:** `feat: add persistent playtime queries`.

### Phase 6 — Native Whitelist Guard

**Zentral:** **Keine** neue Managed List, `/access`-Commands oder `enableall`/`disableall` entwickeln.

**Arbeit:**

1. Native `server.properties`: `online-mode=true`, `white-list=true`, `enforce-whitelist=true` als dokumentierte Betriebsbasis.
2. Plugin überprüft beim Start die Whitelist-Flags über Paper/Bukkit; korrigiert ggf. `white-list` und Enforcement, soweit API unterstützt, und warnt.
3. `staff` aus Config als geprüfte UUID/Accountprofil-Paare; Profile müssen vor Freigabe korrekt validiert sein. Staff-Einträge **nur** in die native Whitelist aufnehmen bzw. wiederaufnehmen, niemals reguläre Einträge löschen.
4. Wiederholte Konsistenzprüfung (Default 30 s) ohne teure Profil-Netzwerk-Requests pro Poll. Unauflösbare/ungültige Staff-Profile: **keine** ungeprüfte Freigabe, klarer Fehler/Alarm im Log.
5. Native `/whitelist add`, `/whitelist remove`, `/whitelist list`, `/whitelist reload` unangetastet lassen. Dokumentieren, dass `/whitelist add` für Online-Profilnamen vor erstem Login funktioniert, sofern Mojang-/Profilauflösung erreichbar ist.
6. Mit getrenntem **Testserver** prüfen, ob `enforce-whitelist` eine gerade online entfernte UUID sofort kickt; bei Abweichung die reale Native-Semantik dokumentieren, **nicht** heimlich andere Vanilla-Commands ersetzen.
7. Launch-Runbook: Vorab-Eintragung nur bei **noch nicht öffentlich erreichbarem** Minecraft-Endpunkt; danach externer Public Path manuell öffnen.

**Exit-Gate:**

- [ ] `/whitelist add <nie zuvor gejoint>` → danach erfolgreicher Login; ohne Whitelist → Ablehnung.
- [ ] Kein neues SQLite-Access-Schema, kein `access`-Command; native `whitelist.json` bleibt maßgeblich.
- [ ] Staff-UUIDs sind trotz versehentlichem `/whitelist remove` nach Konsistenzzyklus wieder native whitelisted.
- [ ] `white-list` und Enforcement werden beim Start und zyklisch geprüft.
- [ ] Kein Gruppen-Mod kann sich damit Staff-Rechte geben.
- [ ] Kein Produktions-Port, Firewall-Rule oder Public-Ingress wird für diesen Test verändert.

**Review-/Commit-Schnitt:** `feat: guard native whitelist and protected staff`.

### Phase 7 — Sit

**Arbeit:**

- Sicherer `/sit`- und `/unsit`-Flow als kleine zustandsgebundene Session-Maschine (`NOT_SITTING` / `SITTING`).
- Ziel-Standort validieren (fester Boden, geeignete Kollisionsgeometrie, keine Flüssigkeit/andere Fahrzeuge/Flight/Fall).
- Sitz per **Paper/Bukkit-API** und markierter, plugin-eigener Hilfsentity (z. B. unsichtbarer ArmorStand, falls API-Test sauber funktioniert) implementieren; keine NMS-/Packet-Hacks.
- Sit-Entities eindeutig per PersistentDataContainer markieren; beim Disable, PlayerQuit, Death, Teleport, Damage, WorldChange, Dismount, Chunk-Unload korrekt entfernen.
- Bei Chunk-Load und Pluginstart verwaiste **eigene** Sitzentities bereinigen, ohne fremde ArmorStands zu löschen oder die gesamte Welt auf dem Main Thread zu durchsuchen.
- Anti-Spam/Double-Sit, kein Entity-Leak bei wiederholter Ausführung oder Crash.

**Exit-Gate:**

- [ ] Spieler sitzt sichtbar, `/sit` toggelt, `/unsit` steht auf.
- [ ] Zwei Spieler können gleichzeitig sitzen, ohne sich gegenseitig zu beeinflussen.
- [ ] Damage/Quit/Restart/WorldChange/Chunk-Reload bereinigt Sitzstatus und Helper-Entities.
- [ ] Keine verwaisten plugin-eigenen Entities nach simuliertem Crash + Neustart.

**Review-/Commit-Schnitt:** `feat: add sit with reliable cleanup`.

### Phase 8 — Integration, Security, Release Candidate

**Arbeit:**

- Echte End-to-End-Acceptance auf **isoliertem Paper-26.3-Testserver** mit Vanilla-Client und mehreren Testaccounts.
- Module einzeln und in Kombination testen: Gruppenmutationen + Marriage-Status + Joins/Quit + Playtime + `/sit`.
- Neustart-, ungeplanter Crash-, DB-Restore- und Version-Rollback-Tests.
- Vanilla-Whitelist-Fall „vor erstem Join“, Staff-Schutz, non-whitelisted Join, Spieler ohne OP und Console-Admin-Commands.
- `README.md`, `docs/commands.md`, `docs/configuration.md`, `docs/architecture.md`, `docs/operations.md` finalisieren.
- Security-Review: Eingabevalidierung, Permission-Negativtests, Event-Cleanup, async I/O, SQL-Parameterbindung, keine fremden Scoreboard-Teams/Entities löschen.
- CI: `clean check build`, statische Checks soweit vorhanden, SHA-256 und Build-Metadaten erzeugen; Testresultate mit konkreten Versionen dokumentieren.
- Release-Version vergeben (z. B. `0.1.0`); manuelles Produktions-Gate.

**Exit-Gate:**

- [ ] **Alle** DoD-Kriterien aus [`MVP.md`](./MVP.md) sind mit Testnachweis erfüllt.
- [ ] Paper-Build, Java-Version, JAR-Hash, SQLite-Schema und Config-Version festgehalten.
- [ ] Kein `ERROR`/`SEVERE`/unerwarteter Exception im frischen Start-/Smoke-Log.
- [ ] Backup und praktischer Restore bestanden; Rückrollpfad dokumentiert.
- [ ] Negative Permission-/Whitelist-Tests bestanden.
- [ ] Installation auf einem frischen Paper 26.3 **ohne andere Plugins** erfolgreich.
- [ ] Verantwortliche Person hat **separat** produktiven Rollout freigegeben.

**Review-/Commit-Schnitt:** `test/docs: finalize IlluniumCustom MVP release candidate`.

## 4. Testmatrix (Mindestabdeckung)

| Bereich | Positivtest | Negativ-/Fehlertest |
| --- | --- | --- |
| Plugin Bootstrap | Start/Stop/restart Java 25 + Paper 26.3 | Ungültige Config, DB nicht beschreibbar |
| Recipes | Normaler Quartz Block → 4 Quartz | Andere Quartz-Varianten kein Treffer |
| Groups | Leader/Mod/Member-Workflows | Rolle darf unzulässigen Kick/Promotion/Transfer nicht ausführen |
| Groups Validierung | `NOVA_1`, max. 16/5 Zeichen | Leerzeichen, Case-Duplikat, Überlänge, Steuerzeichen |
| Gruppen-Einladung | Annahme vor fünf Minuten | Expired, zurückgezogen, bereits in Gruppe, Gruppe gelöscht |
| Datenintegrität | Rename, Transfer, Disband transaktional | Simultan-Joins, konkurrierender Transfer, DB-Fehler |
| Appearance | Nametag-Prefix und Tablist zusammengesetzt | Keine Duplikate/Geistertags nach Quit/Disband |
| Marriage | 2 Accounts heiraten, Scheidung | Selbstheirat, Doppelehe, abgelaufener Antrag |
| Heart | Partner online/offline wird bei allen angezeigt | Fehlendes `💔` wird sichtbar ersetzt, ohne Pflicht-Resourcepack |
| Playtime | Live, offline, Restart | Quit/Checkpoint-Race, unbekannter Name, Crash |
| Native Whitelist | `/whitelist add` für neuen Namen | Nicht gelisteter Account abgelehnt |
| Staff Guard | Konfigurierter Staff bleibt zugelassen | Fehlerhafte UUID nicht still freischalten; Gruppenmod ≠ Staff |
| Sit | Sit/Unsit, mehrere Spieler | Flight, Swim, Damage, Teleport, Quit, Crash, Chunk-Unload |
| Module | Feature deaktiviert, Daten erhalten | Modul-Fehler sichtbar; kein heimlicher Teilstart |
| Backup/Restore | SQLite + Config + Server-Whitelist wiederherstellen | JAR-Rollback mit inkompatiblem Schema wird erkannt |

### 4.1 Testebenen

- **Unit:** Validatoren, Rollenpolicy, Ablaufzeiten, Domainzustände, Formatierung und Zeitrechnung.
- **Repository/Integration:** SQLite-Transaktionen, Foreign Keys, parallele Änderungsversuche, Migration, Recovery.
- **Paper Smoke:** Commands registriert, Listener aktiv, Rezept vorhanden, Staff-Guard startet, `onDisable` sauber.
- **Gameplay E2E:** mehrere echte Java-Clients; Nametags/Tablist, Marriage-Herzen, Sit/Entity-Cleanup, Vanilla-Whitelist vor erstem Join.
- **Negative Security:** alle nicht erlaubten Gruppenaktionen, unauthorized Admin-Commands, Whitelist-Negativtest, ungültige IDs.

## 5. Test- und Deploymentpfad

**Zielablauf:**

```text
Developer Workspace
   → Gradle clean check build (Java 25)
   → JAR + SHA-256
   → separater Paper 26.3 Testserver
   → Startup Smoke + Gameplay Acceptance
   → Human Review / Release Candidate
   → Backup von JAR + Plugin-Daten + whitelist.json
   → manueller Forever-World-Rollout
   → Fresh-Log-Readiness + Spieltests
   → dokumentierte Freigabe oder Rollback
```

**Wichtig:** Der bestehende Illunium-Stack benutzt Pterodactyl und eine separate Forever-World; Test-/Deployment-Arbeiten an `illunium-custom` dürfen weder die Kingdoms-Testumgebung noch die produktive Forever-Welt ohne explizite Freigabe verändern. Das Kingdoms-Deploy-Skript ist eine **Referenz für Qualität und Safety**, nicht automatisch ein direkt wiederverwendbarer IlluniumCustom-Deploy-Endpunkt.

### 5.1 Review vor Liveänderungen

Für reale Server-/Pterodactyl-Aktionen jeweils ausdrücklich dokumentieren:

```text
Ausführungsort: lokal / Testserver / illunium-services
WireGuard: AN / AUS / egal
Modus: READ-ONLY / MUTATION
Scope: exakte betroffene Dateien/Services
Erwartetes Ergebnis: überprüfbarer PASS/FAIL
Rollback: exakter zuvor getesteter Weg
```

Kein Push auf `main`, kein Prod-Restart und keine Änderung an Public-Ports „als Nebenwirkung“ dieses Plans. Secretwerte nie lesen oder ausgeben, wenn Metadaten ausreichen.

### 5.2 Release-Checkliste

- [ ] Sicherer Code-/Config-Review, Testserver-Stand konsistent.
- [ ] Eingesetzte Java-/Paper-Version ist tatsächlich getestet und gepinnt.
- [ ] Versioniertes JAR mit Hash; Release Notes vorhanden.
- [ ] JAR, Plugin-DB/Config und native `whitelist.json` vor dem Rollout gesichert.
- [ ] Migration-/Downgrade-Kompatibilität und Restore geprüft.
- [ ] Manueller Deployment-Freigabeschritt.
- [ ] Frische Readiness-/Error-Logs und reale Command-/Gameplay-Tests.
- [ ] Falls FAIL: Stop, Ursache dokumentieren, JAR + ggf. Daten korrekt wiederherstellen, erneut prüfen.

## 6. Definition of Done — Entwicklung

Ein einzelnes Feature ist erst „Done“, wenn **Code + Tests + dokumentierte Bedienung + Restart-/Cleanup-Verhalten + Negativtest** vorliegen. Ein „Command funktioniert einmal“ zählt nicht.

Der **MVP** ist erst „Done“, wenn die Checkliste in [`MVP.md`](./MVP.md), die Testmatrix oben und die Testserver-Acceptance komplett bestanden sind. Produktionsdeployment ist ein **separater**, manuell freigegebener Vorgang.

## 7. Nach dem MVP: weitere Features ergänzen

Für **jedes neue Feature** denselben Ablauf verwenden:

1. Neues Mini-Scope-Dokument: User Story, Commands, Daten, Permissions, Nicht-Ziele, Akzeptanz.
2. Neue `feature/`-Package-Struktur mit eigener `FeatureModule`-Implementierung; nur explizit benötigte Core-Interfaces verwenden.
3. Falls Daten nötig: **neue additive Migration**; bestehende Tabellen nicht ohne Migrations-/Backup-Plan mutieren.
4. Commands und Listener im eigenen Modul registrieren; wenn der Spielername/Tablist betroffen ist, das **Appearance-Interface** erweitern, nicht direkt Scoreboards aus einem Feature manipulieren.
5. Tests und Docs gleichzeitig ergänzen; negativ prüfen, dass bestehende Gruppen-, Marriage- und Whitelist-Regeln unverändert bleiben.
6. Modul in Registry/Config eintragen; CI und Testserver; manuelle Produktionsfreigabe.

**Kein vorsorglicher Bau** einer generischen Web-API, Event-Bus-Infrastruktur, externen Message Queue, REST-Services oder „Plugin-Marketplace“. Zunächst reichen klare Java-Interfaces, kurze Services, gute Tests und saubere Datenmigrationen.

## 8. Empfohlener erster Arbeitsschritt

Zuerst ausschließlich **Phase 0** durchführen: Repository-Baseline prüfen, Paper/Java-Builds verifizieren, Gradle-/Plugin-Bootstrap erzeugen, `./gradlew clean check build --no-daemon` ausführen und **eine** reale Installation auf dem getrennten Paper-26.3-Testserver nachweisen. **Erst bei PASS** mit Phase 1 beginnen.
