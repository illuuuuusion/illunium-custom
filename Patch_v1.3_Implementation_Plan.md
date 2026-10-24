# IlluniumCustom — MVP Patch v1.3: Leaderboards, Whitelist-Staging und Auto-Vanish — Implementation Plan

> **Stand:** 24.10.2026  
> **Status:** Im Repository implementiert; automatisierte Prüfungen und isolierte Paper-Tests siehe `docs/development.md`. Manuelle Client-Abnahme und Produktionsfreigabe bleiben offen.
> **Umsetzungsbaseline:** `ff4181e` (v1.2 bereits enthalten), 24.10.2026; Schema v2 → v4. Der folgende Entwurfs-Iststand ist historisch und beschreibt nicht den jetzigen Quellcode.  
> **Repository:** https://github.com/illuuuuusion/illunium-custom  
> **Ziel:** Illunium Forever-World, Minecraft Java / Paper **26.3**, Java **25**  
> **Bezug:** [`MVP.md`](./MVP.md), [`Implementation_Plan.md`](./Implementation_Plan.md), [`Patch_v1.1_Implementation_Plan.md`](./Patch_v1.1_Implementation_Plan.md), [`Patch_v1.2_Implementation_Plan.md`](./Patch_v1.2_Implementation_Plan.md)  
> **Umfang:** Gemeinsamer MVP-Patch v1.3 mit drei Arbeitspaketen: **L** (Leaderboards, bisheriger v1.3-Entwurf), **W** (Whitelist-Staging für Projektstart) und **V** (pro OP konfigurierbares Auto-Vanish). Alles in der bestehenden IlluniumCustom-JAR; keine separate Plugin-Installation.
> **Aktueller Quellstand:** GitHub `main` am 24.10.2026, Git-Tree `44c0480965033c88c222249e52b3769b57ac0546` (read-only geprüft). Diese Planfassung enthält **keine** bereits implementierten W-/V-Änderungen.

## 0. Ziel und verbindliche Entscheidungen

Dieser Patch vereint drei ausdrücklich gewünschte Ergänzungen. **L:** Die Community wünscht langfristige, unkomplizierte Survival-Ranglisten; der Server ist **kein PvP-Server**. **W:** Vor dem Launch werden Minecraft-Namen manuell aus Discord gesammelt, geprüft und **nur vorgemerkt**; ein späterer Befehl übernimmt die vorgemerkten Accounts in die **native** Whitelist. **V:** Vanilla-OPs sollen individuell entscheiden können, ob sie automatisch unsichtbar joinen oder als normaler Survival-Spieler sichtbar einloggen.

Der bisherige Leaderboard-v1.3-Inhalt bleibt Bestandteil dieses Dokuments; §9–§13 definieren die neuen Anforderungen und die daran gekoppelten Release-Gates.

**L-MVP:** `/leaderboard` zeigt eine Übersicht aller Kategorien und ihre Befehle; `/leaderboard <kategorie>` zeigt die Top 10 sowie die eigene Platzierung. Gesamtrankings sind serverweit, unabhängig davon, ob die Spieler online sind. Es gibt keine Preise, täglichen Aufgaben, Saisonresets oder PvP-Wertung.

**W-MVP:** `/ic whitelist stage <Name>` bereitet einen Account vor, ohne Zugriff zu gewähren. `/ic whitelist launch` schaltet **alle gültig gestageten Accounts** über die Vanilla-Whitelist frei; bereits freigeschaltete Accounts bleiben unangetastet.

**V-MVP:** Auto-Vanish bleibt für OPs standardmäßig eingeschaltet (kompatibel mit v1.1), kann aber je UUID deaktiviert werden. Manuelle `/vanish`- und `/unvanish`-Commands bleiben immer für OPs verfügbar.

### 0.1 Die 10 Survival-Leaderboards + höchstes XP-Level

Die letzte Community-Zusammenfassung enthielt **neun PvP-freie Kernkategorien**. Um die gewünschten **zehn** ohne öffentliche Player-Kill-Rangliste zu erhalten, ergänzt v1.3 **Fischfang** als zehnte Kategorie (aus den zuvor diskutierten Zusatzideen). **Höchstes XP-Level** ist die elfte Kategorie.

| Nr. | Schlüssel | Im Spiel | Messgröße | Datenquelle | Historie |
| ---: | --- | --- | --- | --- | --- |
| 1 | `diamonds` | Diamanterze abgebaut | Anzahl Diamanterz-Blöcke | Vanilla `MINE_BLOCK` für `DIAMOND_ORE` + `DEEPSLATE_DIAMOND_ORE` | Vorhandene Vanilla-Historie |
| 2 | `placed` | Blöcke platziert | Tatsächlich platzierte Blöcke | Eigener BlockPlace-/MultiPlace-Zähler | Ab v1.3 |
| 3 | `mined` | Blöcke abgebaut | Summe aller vom Spieler abgebauten Blockarten | Vanilla `MINE_BLOCK` je gültigem Blockmaterial | Vorhandene Vanilla-Historie |
| 4 | `playtime` | Spielzeit | Aufgelaufene Zeit in Sekunden | **Bestehendes `playtime`-Modul** | Seit Einführung des vorhandenen Moduls |
| 5 | `distance` | Zu Fuß zurückgelegte Distanz | Meter/Kilometer, intern Zentimeter | Vanilla `WALK_ONE_CM` + `SPRINT_ONE_CM` + `CROUCH_ONE_CM` | Vorhandene Vanilla-Historie |
| 6 | `mobs` | Mobs getötet | Anzahl Mob-Kills | Vanilla `MOB_KILLS` | Vorhandene Vanilla-Historie |
| 7 | `deaths` | Tode | Anzahl gestorbener Spieler | Vanilla `DEATHS` | Vorhandene Vanilla-Historie |
| 8 | `totems` | Totems ausgelöst | Anzahl erfolgreicher Totem-Rettungen | Eigenes Tracking über `EntityResurrectEvent` | Ab v1.3 |
| 9 | `spawn` | `/spawn` verwendet | **Erfolgreiche** Spawn-Teleports | Erfolgsereignis aus dem Spawn-Modul | Ab v1.3 |
| 10 | `fishing` | Fische gefangen | Anzahl Fänge | Vanilla `FISH_CAUGHT` | Vorhandene Vanilla-Historie |
| 11 | `peaklevel` | Höchstes XP-Level | Höchstes **seit Einführung von v1.3 beobachtetes** Erfahrungslevel | Eigener persistenter Maximalwert | Ab v1.3, initiales aktuelles Level wird übernommen |

**Kein öffentliches `playerkills`-, K/D- oder Spieler-Schadens-Ranking.** Das vorhandene Vanilla-Statistiksystem darf davon unabhängig bestehen; IlluniumCustom baut dafür keine Wettbewerbsschnittstelle.

### 0.2 Nicht-Ziele

- Kein PvP-Anreiz und keine PvP-Rewards; keine automatische Siegerehrung oder Belohnungen.
- Keine Hologramme, Chat-Spam, permanentes Sidebar-/Scoreboard-Overlay, Bossbar oder Webansicht im v1.3-MVP.
- **Kein** `/leaderboard toggle` im v1.3-MVP: Es gibt nichts dauerhaft Sichtbares, das zu toggeln wäre. Ein späteres optionales Sidebar-Feature benötigt ein separates, scoreboard-kompatibles Design.
- Keine neue externe Datenbank, kein Redis, keine Discord-/Lumen-Anbindung und kein PlaceholderAPI-/ProtocolLib-/LuckPerms-Zwang.
- Kein Reset vorhandener Vanilla-Statistiken und **keine** Änderung an der Funktionsweise anderer Module.
- Kein rückwirkendes Schätzen fehlender Totem-, Platzierungs-, Spawn- oder XP-Rekorde.
- Keine komplexe Exploit-Erkennung gegen wiederholtes Platzieren/Abbauen oder Mobfarmen; es sind Spaß-Ranglisten. Keine stillschweigende Anti-Farm-Regel erfinden.

## 1. Aktueller Repository-Iststand und Abhängigkeiten

Das GitHub-Repository wurde am **24.10.2026** auf `main` gelesen. Folgende Bausteine sind tatsächlich vorhanden:

| Codepfad | Bedeutung für v1.3 |
| --- | --- |
| `src/main/java/de/illunium/custom/core/module/FeatureModule.java` | Beibehalten: Feature-Lifecycle |
| `src/main/java/de/illunium/custom/core/module/Modules.java` | Neue `LeaderboardModule` **einmal** registrieren |
| `src/main/java/de/illunium/custom/core/module/ModuleContext.java` | Gemeinsame Dienste statt globale Singleton-Abhängigkeiten |
| `src/main/java/de/illunium/custom/storage/Database.java` | SQLite-Transaktionsqueue und nummerierte Migrationen |
| `src/main/java/de/illunium/custom/core/identity/PlayerIdentityService.java` | UUID und zuletzt bekannter Name |
| `src/main/java/de/illunium/custom/core/VisibilityService.java` | Existierender Vanish-/Online-Sichtbarkeitsdienst |
| `src/main/java/de/illunium/custom/playtime/PlaytimeModule.java` | Eigene Spielzeit-Quelle und laufende Sessions |
| `src/main/java/de/illunium/custom/spawn/SpawnModule.java` | Zur Prüfung: aktuell vorhandener direkter Teleport; v1.2-Warmup/Cooldown noch nicht im eingesehenen Code |
| `src/main/java/de/illunium/custom/appearance/AppearanceService.java` | Bestehende Teams/Tablist **nicht** verändern |
| `src/main/resources/plugin.yml`, `config.yml` | Rechte und Feature-Flag ergänzen |
| `src/test/java/de/illunium/custom/` | Bestehende Tests beibehalten, zusätzliche Leaderboard-Tests |

Die aktuelle Registry enthält `recipes`, `groups`, `marriage`, `playtime`, `whitelistguard`, `sit`, `vanish` und `spawn`; **kein** `leaderboard` und **kein** `whiteliststaging`. In der aktuell geprüften Datenbank-Migrationsliste steht `V1__initial_schema.sql`. **Bei späterer Implementierung trotzdem erneut prüfen:** v1.2 kann zwischenzeitlich ein weiteres Schema oder ein neues Spawn-Service-Interface hinzugefügt haben.

**Zusätzlich auf `main` konkret geprüft (24.10.2026):**

| Codepfad | Verifizierter Istzustand / Arbeit für v1.3 |
| --- | --- |
| `whitelistguard/WhitelistGuard.java` | Hält `white-list`/`enforce-whitelist` aktiv; repariert geprüfte Staff-Einträge; **keine Staging-Liste** |
| `whitelistguard/WhitelistGuardModule.java` | Prüft Staff-UUID/Name beim Start asynchron und führt periodische Whitelist-Checks aus |
| `vanish/VanishModule.java` | Versteckt derzeit **jeden OP bei jedem Join**; kein Account-spezifischer Opt-out; `/vanish` & `/unvanish` existieren |
| `core/VisibilityService.java` | Gemeinsame Sichtbarkeitsinformation für andere Module; muss wiederverwendet werden |
| `src/main/resources/config.yml` | `modules.vanish: true`, `modules.whitelistguard: true`; noch keine Vanish-Ausnahmeliste |
| `storage/Database.java` | SQLite-Migrationsliste enthält derzeit **nur V1**; jede neue Migration darf nur angehängt werden |
| `core/command/AdminCommand.java` | Vorhandener `/ic <name>`-Mechanismus für administrative Subcommands |

Vor der Implementierung:

```bash
git status --short --branch
git rev-parse HEAD
rg -n 'Leaderboard|leaderboard|SpawnModule|cooldown|Spawn.*Success|WhitelistGuard|VanishModule|MIGRATIONS' src docs || true
./gradlew clean check build --no-daemon
```

**Priorität der Quellen:** Der dann tatsächlich vorliegende Code ist der Istzustand. Die ursprünglichen Produktentscheidungen aus v1.0–v1.2 bleiben bestehen, sofern dieses Dokument nicht ausdrücklich eine Ergänzung definiert. **Insbesondere bleiben die native Whitelist, geschützte Staff-Accounts, OP-only Vanish und das bestehende `/spawn`-Regelwerk erhalten.**

## 2. Command- und Anzeigevertrag

### 2.1 Public Commands

```text
/leaderboard
/leaderboard list
/leaderboard <kategorie>
/leaderboard <kategorie> [seite]

/lb                  # kurzer Alias
/lb diamonds
/lb playtime 2
```

Die Schlüssel sind **genau** die elf Schlüssel aus §0.1. Keine dynamisch erzeugten, unvorhersehbaren Befehle pro Kategorie; `/leaderboard diamonds` genügt.

- `/leaderboard` und `/leaderboard list`: Titel, die elf verfügbaren Kategorien und pro Kategorie den kopier-/anklickbaren Befehl.
- `/leaderboard <kategorie>`: Standardseite 1, **10 Einträge pro Seite**; mindestens Name, Rang und formatierter Wert.
- `/leaderboard <kategorie> [seite]`: Seitenzahl ab 1; ungültige oder außerhalb liegende Seiten führen zu verständlicher Antwort, nicht zu Exception.
- Am Ende jeder Rangliste: **eigene Platzierung samt Wert**, selbst wenn sie nicht in den Top 10 steht. Konsole erhält stattdessen nur Seiteninformationen.
- Ist der Spieler noch ohne messbaren Wert, steht **„Du hast noch keinen Eintrag“** statt einer fiktiven Rangnummer.
- Einträge sind nach Wert **absteigend** sortiert; bei Gleichstand teilen sich Spieler denselben Rang (Wettbewerbsrangfolge z. B. `1, 1, 3`). Die Zeilenfolge im Gleichstand ist deterministisch (letzter bekannter Name, danach UUID).
- Standardmäßig nur Werte `> 0` öffentlich listen. Wenn es keine Einträge gibt, lesbare Leermeldung.
- **Alle Spieler dürfen alle freigeschalteten Kategorien sehen** (`illuniumcustom.use`). Kein OP-Recht erforderlich.
- Tab-Completion für Kategorien und sinnvolle Seiten; unbekannter Schlüssel zeigt verfügbare Kategorien.
- Ausgabe über vorhandene Adventure-/Chat-Helfer, keine tausendzeiligen Rohlisten, maximal 10 Reihen plus eigene Position.

### 2.2 Beispielausgabe (fiktive Werte)

```text
Illunium » Leaderboard: Diamanterze abgebaut
#1  renko        482 Erze
#2  diaaar       391 Erze
#3  Jaevy        286 Erze
...
Seite 1/3  |  /leaderboard diamonds 2
Dein Platz: #18 — 34 Erze
```

### 2.3 Datenschutz-/Vanish-Verhalten

- **Keine Online-/Offline-Kennzeichnung** in Leaderboards; die Rangliste bewertet historische Werte, nicht Präsenz.
- **Vanished OPs dürfen nicht über Leaderboards oder Completion als gerade online verraten werden.**
- Für v1.3 gilt als einfacher, konsistenter Standard: **Vanilla-OP-Konten werden aus öffentlichen Ranglisten und öffentlichen „Mein Rang“-Vergleichen ausgeschlossen**. Dazu zählen auch aktuell offline OPs; dies vermeidet Sonderfälle beim Vanish und verfälschte Werte durch Creative/Admin-Arbeit. Ausgenommen ist der eigene private Wert in `/leaderboard <kategorie>` nur dann, wenn er ohne öffentliches Ranking dargestellt wird; ein OP soll nie heimlich als Top-Eintrag erscheinen.
- Die technische Filterung der OP-UUIDs erfolgt über die **aktuelle** Server-OP-Liste, nicht allein über `getOnlinePlayers()` und nicht über Gruppenrollen (`MOD` ist **kein** Server-OP).
- Die neue **sichtbare Auto-Vanish-Ausnahme** erlaubt einem OP, normal Survival zu spielen. Sie bedeutet **nicht automatisch Leaderboard-Teilnahme**: Der ältere v1.3-Leaderboardvertrag schließt OP-UUIDs vorerst generell aus. Soll dieser spielende Admin öffentlich mitranken, braucht das eine **separate, bewusst konfigurierbare Leaderboard-Ausnahme** (nicht Teil von W/V); er darf nicht allein durch `vanish`-Einstellungen aufgenommen werden.
- Wenn später OP-Teilnahme gewünscht ist, braucht es eine eigene, bewusst freigegebene Regel; kein automatisches Opt-in.

## 3. Definition jeder Statistik und technische Datenquellen

### 3.1 Vanilla-Daten verwenden statt doppelt mitzuschreiben

Für `diamonds`, `mined`, `distance`, `mobs`, `deaths` und `fishing` werden die bereits vom Server gepflegten **Vanilla-Statistiken** gelesen (`OfflinePlayer#getStatistic(...)` bzw. bei online Spielern `Player#getStatistic(...)`). Bereits vorhandene Daten **dürfen** für diese Kategorien angezeigt werden. Es gibt keinen zweiten eigenen Counter dafür.

| Kategorie | Konkrete Logik | Besonderheiten |
| --- | --- | --- |
| `diamonds` | `getStatistic(MINE_BLOCK, DIAMOND_ORE) + getStatistic(MINE_BLOCK, DEEPSLATE_DIAMOND_ORE)` | **Erze**, nicht Itemdrops; Fortune zählt einen Block nur einmal |
| `mined` | Summe `MINE_BLOCK` über alle von der Version unterstützten, statistisch gültigen Blockmaterialien | Nur echte von Spielern abgebaute Blöcke; keine Explosionen, kein Doppelzählen der zwei Diamanterze |
| `distance` | `WALK_ONE_CM + SPRINT_ONE_CM + CROUCH_ONE_CM` | Nur zu Fuß; kein Schwimmen, Boot, Reittier, Elytra; intern cm, Ausgabe in km/m |
| `mobs` | `MOB_KILLS` | Keine Player-Kills hinzufügen |
| `deaths` | `DEATHS` | Todesursache egal, auch PvP-Tode sind normale Tode |
| `fishing` | `FISH_CAUGHT` | Vanilla-Definition „Fang“, nicht zwingend essbarer Fisch; Label bei Bedarf auf „Angel-Fänge“ präzisieren |

**Materialvalidierung:** `MINE_BLOCK` ist eine materialbezogene Statistik. Nicht jedes `Material` ist dafür gültig. Eine einmalig gebildete, zur konkreten Paper-Version passende Menge gültiger Blockmaterialien verwenden; unzulässige Materials sicher auslassen, statt `getStatistic` ungeprüft auf jedes Item anzuwenden. Die Liste bei Version-Upgrade testen.

**Performance:** Ein vollständiger Scan aller Offline-Spieler × aller Blockmaterialien darf **nicht bei jedem `/leaderboard mined`** synchron laufen. Snapshots zeitlich begrenzt und/oder inkrementell in kleinen Main-Thread-Batches aktualisieren; nur finalisierte reine Zahlen auf einem Worker sortieren. Keine Bukkit-API und keine nicht thread-safe Statistik-/Profilobjekte auf den SQLite-Thread verschieben. Bei Cache-Ablauf den letzten gültigen Snapshot ausgeben und die Neuberechnung planen.

**Kandidaten:** Alle bekannten Spieler-UUIDs berücksichtigen — auch offline. Vorhandene `player_profiles` und die Serverhistorie (`OfflinePlayer`/`Bukkit.getOfflinePlayers()`) sinnvoll zusammenführen. Keine neuen Accounts über Namensauflösung/API-Abfragen erzeugen und keinen reinen Whitelist-Eintrag als „Teilnahme“ zählen. Unbekannte Namen als kurze UUID darstellen, nicht Spieler heimlich droppen.

### 3.2 Spielzeit: exakt eine Quelle

`playtime` ist **kein** Alias für `Statistic.PLAY_ONE_MINUTE`. Das bestehende IlluniumCustom-Modul zählt Spielzeit bereits ab seiner Erstinstallation und berücksichtigt aktive Sessions.

- Eine **kleine, read-only Service-Schnittstelle** aus `PlaytimeModule` zugänglich machen (gespeicherter Wert + laufende Session). Für die Anzeige **dieselbe Berechnung** wie `/playtime` verwenden.
- **Nicht** die `playtime`-SQL-Tabelle vom Leaderboard-Modul direkt verändern, keine zweite unabhängige Spielzeitmessung.
- Für regelmäßige Snapshots der vielen Spieler eine effiziente Bulk-Read-API statt N Einzeldatenbankabfragen; aktive Sessions nur auf dem Main Thread hinzufügen.
- Wenn `playtime` in `config.yml` deaktiviert ist, wird diese Kategorie als **nicht verfügbar** angezeigt; das Leaderboard-Modul darf dabei starten und andere Kategorien weiter anbieten. Keine heimliche Umstellung auf Vanilla-Zeit.
- Auf `PLAY_ONE_MINUTE` **nicht** ausweichen: diese Vanilla-Konstante zählt Ticks und hat anderen historischen Geltungsbereich.

### 3.3 Blocks placed: eigener Zähler

- Events: `BlockPlaceEvent` inkl. `BlockMultiPlaceEvent`; auf endgültig gültige, **nicht gecancelte** Platzierungen reagieren (MONITOR/`ignoreCancelled`, `canBuild` beachten).
- Zählweise: **tatsächlich neu platzierte Blockpositionen**, einschließlich entsprechender MultiPlace-Aktionen; `BlockMultiPlaceEvent` als Unterklasse **nicht zusätzlich als separaten einfachen Place-Event doppelt zählen**.
- Kein Zähler für Dispenser, Pistons, WorldEdit, Command-Blöcke oder von Plugins ohne Spieleraktion erzeugte Blöcke.
- Creative/Spectator/Admin-Aktionen sollen das öffentliche Ranking nicht beeinflussen: aktive Spieler in `SURVIVAL` (ggf. `ADVENTURE`, sofern erlaubte Platzierung) erfassen, Vanilla-OPs ohnehin öffentlich ausblenden.
- Kein Rückrechnen beim Abbau, keine Anti-Farm-Heuristik. Das ist eine **Aktivitäts-** und keine Netto-Bauleistungsmetrik.
- Ab Einführung: `0` für frühere Place-Aktivität; keine Rückdatierung möglich.

### 3.4 Totems: nur tatsächlich ausgelöste Rettung

- `EntityResurrectEvent` beobachten, nur wenn die Entity ein `Player` ist.
- **Nicht gecancelter** echter Totem-Verbrauch; geeignete API-Information (z. B. verwendete Hand/Item und finaler Eventzustand) für **Paper 26.3** prüfen und durch einen echten Test bestätigen. Keine normale Plugin-Resurrection ohne Totem mitzählen.
- Eine Rettung = ein Zählerzuwachs, unabhängig von Inventarmenge oder Anzahl durch die Hand gegangener Items.
- Keine rückwirkende Berechnung aus Inventaren, Toden oder bisherigen Vanilla-Statistiken.

### 3.5 Spawn-Nutzung: nach **erfolgreichem** Teleport

- Kein Zählen beim Ausführen von `/spawn` oder Starten eines Warmups.
- Erst nach dem **erfolgreich abgeschlossenen Teleport** ein `SpawnTeleportSucceeded`-Signal mit Spieler-UUID und eindeutiger Teleport-Session-ID veröffentlichen.
- Das v1.3-Leaderboard-Modul verarbeitet dieses Signal über eine schlanke Schnittstelle/Listener-Registry; **keine direkte Spawn-Abhängigkeit auf Leaderboard-Tabellen**.
- Derselbe Erfolg darf auch bei Retry oder Callback-Dopplung nur **einmal** gezählt werden (idempotente Session-ID mit Unique Constraint oder gleichwertiger Schutz).
- Wird v1.2 zuerst implementiert, den dortigen Success-Pfad verwenden. Falls v1.2 noch offen ist, **nicht** eigenständig den heutigen Sofort-Teleport als endgültiges v1.3-Verhalten festschreiben. Die Vorgaben **10 s Warmup, 60 min Cooldown, Bewegung/Schaden-Abbruch, 30 s Combat-Tag** bleiben gültig.
- Gescheiterte Zielprüfung, abgebrochener Countdown, Logout, Combat-Tag-Sperre und fehlgeschlagener Teleport zählen **nicht**.

### 3.6 Peak Level: historischer Höchststand ab Einführung

- **Keine** Rangliste nach dem aktuellen Level; das wäre nach einem Tod sofort zurückgesetzt.
- `PlayerLevelChangeEvent`: für jede Änderung `max(gespeichertesMaximum, oldLevel, newLevel)` bilden; niemals den bisherigen Rekord reduzieren.
- Bei Join/Pluginstart `Player#getLevel()` als Mindestwert übernehmen; vor einem Tod mit `PlayerDeathEvent` den bis dahin beobachteten Wert sichern, falls Event-Reihenfolgen oder Sonderfälle kritisch sind. Auch bei Quit/Disable zuletzt beobachteten Wert sichern.
- Der persistente Wert ist das **höchste seit Aktivierung von v1.3 beobachtete** Level; ein nie gespeicherter Rekord von vor v1.3 ist nicht rekonstruierbar. Das muss auch in der UI/Documentation klar sein.
- Level `0` ist ein zulässiger gespeicherter Zustand, wird aber bis zum ersten positiven Wert nicht im Ranking gelistet.
- Bei Datenbankproblemen keine falschen niedrigeren Rekorde schreiben; Updates sind monoton über `MAX(existing, incoming)`.

## 4. Modularchitektur und Datenhaltung

### 4.1 Neues Feature-Modul

Vorgeschlagene, nicht verpflichtend exakte Struktur:

```text
src/main/java/de/illunium/custom/leaderboard/
    LeaderboardModule.java        # FeatureModule, Command, Listener-Registration
    LeaderboardCommand.java       # Parser, Liste, Seiten, Completion
    LeaderboardDefinition.java    # Stabile Kategorie-Metadaten/Formatierung
    LeaderboardService.java       # Snapshot- und Ranking-Logik
    LeaderboardRepository.java    # Nur eigene SQL-Tabellen
    VanillaStatisticReader.java  # Bukkit/OfflinePlayer-Zugriffe auf Main Thread
    CustomMetricTracker.java     # placed, totems, peaklevel, spawn
    RankingSnapshot.java         # Immutable Rankings/Einzelränge
    LeaderboardFormatter.java    # Textdarstellung, Einheiten

src/test/java/de/illunium/custom/leaderboard/
    LeaderboardDefinitionsTest.java
    RankingServiceTest.java
    LeaderboardRepositoryTest.java
    LeaderboardCommandTest.java
    CustomMetricTrackerTest.java
```

Neuer Eintrag in `Modules.create()`, `modules.leaderboard: true` und ggf. ein eigener Abschnitt `leaderboard:` in `config.yml`. Das vorhandene `FeatureModule`-Interface und `ModuleContext`-Design weiterverwenden. **Keine pauschale Neuarchitektur.**

### 4.2 Quelle, Cache und Snapshot-Flow

```text
Vanilla Statistiken ─► VanillaStatisticReader ─┐
Playtime-Modul ──────► Read-only Playtime API ──┼─► LeaderboardService
Eigene Events ───────► CustomMetricTracker ──► SQLite
                                                    │
                                                    ▼
                           Immutable RankingSnapshot (sortiert, paginiert)
                                                    │
                                      /leaderboard + /lb
```

- Jeder Kategorieeintrag enthält mindestens `key`, Anzeigename, Einheit, Datenquelle, Historienhinweis und Formatierungsfunktion.
- Eine zentrale **Definition-Registry** sorgt für exakt elf bekannte Kategorien; neue Kategorien sollen zukünftig vor allem über eine zusätzliche Definition und passende Datenquelle hinzukommen.
- Keine separate Datenbank pro Modul, aber **klare Eigentümerschaft für Tabellen**.
- Vanilla-Statistiken und Profile auf einem Bukkit-/Paper-zulässigen Thread ablesen; Ergebnisse als immutable DTO zur Sortierung weiterreichen.
- Standard-Cachestaleness beispielsweise **30–60 Sekunden**, kein periodischer Vollscan pro Tick. Bei manueller Nachfrage mit altem Cache wird der Zeitpunkt („Stand: ...“) optional angezeigt; **keine garantierte Live-Sekundengenauigkeit**.
- Bei kleinem Server reicht ein pragmatischer TTL-Cache; komplexer Incremental-/Event-Bus-Aufbau nur wenn reale Messungen es verlangen.
- Snapshot-Aktualisierung darf keine spürbaren Tick-Lags verursachen; Offline-Stat-Ladevorgänge/Material-Scans in begrenzten Batches und mit Profiling testen.
- Auch bei leerem Cache oder abgeschaltetem Untermodul muss der Command korrekt antworten.

### 4.3 Persistente eigene Werte

**Nur selbst erhobene Kategorien** in SQLite speichern: `placed`, `totems`, `spawn`, `peaklevel`.

Beispiel für eine **neue additive Migration** (Versionsnummer **erst nach Prüfung der zu diesem Zeitpunkt vorhandenen Migrationen** festlegen):

```sql
CREATE TABLE leaderboard_metrics (
    player_uuid TEXT PRIMARY KEY,
    placed_blocks INTEGER NOT NULL DEFAULT 0 CHECK (placed_blocks >= 0),
    totems_used INTEGER NOT NULL DEFAULT 0 CHECK (totems_used >= 0),
    spawn_successes INTEGER NOT NULL DEFAULT 0 CHECK (spawn_successes >= 0),
    peak_xp_level INTEGER NOT NULL DEFAULT 0 CHECK (peak_xp_level >= 0),
    updated_at INTEGER NOT NULL
);

CREATE TABLE leaderboard_spawn_events (
    event_id TEXT PRIMARY KEY,
    player_uuid TEXT NOT NULL,
    recorded_at INTEGER NOT NULL
);
```

- Die Event-Tabelle ist **nur für deduplizierte Spawn-Erfolge**; Datensätze nicht unkontrolliert für jeden Blockplace erzeugen. Sie kann bei großem Wachstum nach dokumentierter Sicherheitsfrist bereinigt werden, sobald Doppel-Verarbeitung der alten Events unmöglich ist; im kleinen MVP genügt zunächst Beibehalten.
- Atomare Zählerinkremente `counter = counter + delta`, Peak-Level via `MAX(peak_xp_level, new_level)`; keine read-modify-write-Rennen.
- **Keine** Änderung an bestehenden Tabellen `playtime`, `marriages`, `groups` oder den v1.2-Cooldown-Daten. Frühere SQL-Migrationen niemals editieren.
- `placed` kann in kurzen Batches persistiert werden, mit Flush bei Quit/Disable; `totems`, `spawn`, `peaklevel` möglichst zeitnah, dennoch asynchron über die vorhandene DB-Queue. Beim Crash maximal seit dem letzten erfolgreichen Commit aufgelaufene neue Event-Deltas verlieren; offen dokumentieren, keine Fake-Garantie.
- Ein DB-Fehler darf **keine** Weltaktion, keine Rettung und keinen Spawn-Teleport rückgängig machen. Sichtbar loggen, im Speicher gepufferte noch nicht persistierte Werte nicht vergessen und kontrolliert erneut versuchen; Wiederholung bei Spawn durch Event-ID absichern.
- Ein Modul-Disable muss die eigenen Scheduler-Aufgaben abbrechen, Listener unwirksam machen, Snapshot-Cache freigeben und ausstehende DB-Arbeit ordnungsgemäß flushen.

### 4.4 Konfiguration und Berechtigungen

Beispiel (bestehende Config nur **ergänzen**):

```yaml
modules:
  leaderboard: true

leaderboard:
  page-size: 10
  cache-seconds: 60
  exclude-operators: true
```

- Im MVP ist `page-size` fest auf **10** spezifiziert; falls als Config angeboten, nur in geprüften Grenzen, beispielsweise 5–20, andernfalls die Einstellung zunächst weglassen.
- `exclude-operators` standardmäßig `true`; öffentliche OP-Teilnahme ist keine implizite Freigabe.
- Normale Aufrufe erfordern nur `illuniumcustom.use` (bisher für Spieler allgemein freigeschaltet).
- Kein neues Staff-/Admin-Command zur Veränderung der Werte, kein Reset-Command.
- Modul deaktiviert: `/leaderboard` und Alias nicht aktiv; andere IlluniumCustom-Funktionen funktionieren unverändert.

## 5. Umsetzung in überprüfbaren Phasen

Jede Phase endet mit grünen Tests, reviewbarem Diff und einer klaren Abnahme. Nicht alles in einem einzigen ungeprüften Commit umsetzen.

### Phase 0 — Baseline und Entscheidungssicherung

1. Aktuellen Repository-HEAD und die v1.1-/v1.2-Umsetzung erneut verifizieren.
2. Vorhandenen Paper-API-Pin, Java 25, Build und Tests aufnehmen; keine unmotivierten Versionsupdates.
3. Exakt **elf** Leaderboard-Keys (§0.1), kein Player-Kill-Ranking, Top 10 und eigene Platzierung als Test-Fixpunkt bestätigen.
4. Prüfen, ob Spawn v1.2 schon einen Success-Callback besitzt. Falls nicht, Integration als gezielte API-Erweiterung markieren.

**Gate P0:** Iststand dokumentiert, Tests vor dem Patch grün, keine ungeklärten doppelten Datenquellen.

### Phase 1 — Modul, Definitionsregister und Commands

1. `LeaderboardModule` + Registrierung in `Modules.create()` und Feature-Flag.
2. Stabiles Definitionsregister mit elf Keys und History-/Unit-Metadaten.
3. `/leaderboard`, `/lb`, `list`, Kategorie und optionale Seite registrieren.
4. Übersicht und klare Fehlermeldungen mit Platzhalter-/Leerdaten implementieren.
5. Tab-Completion und Permissions testen.

**Gate P1:** Alle elf Kategorien in Übersicht und Completion vorhanden; kein Sidebar- oder Scoreboard-Eingriff.

### Phase 2 — Vanilla-Statistikadapter und Offline-Spieler

1. Offline-/Online-Kandidaten und OP-Filter ohne Online-Präsenzleaks aufbauen.
2. `diamonds`, `mined`, `distance`, `mobs`, `deaths`, `fishing` anbinden.
3. Materialliste für `MINE_BLOCK` validieren; kein doppeltes Zählen und keine unzulässigen Materials.
4. Snapshot mit Cache und begrenzter Berechnung, keine synchronen Vollscans pro Request.
5. Vorhandene Vanilla-Historie auf einem Wegwerf-Testserver kontrollieren.

**Gate P2:** Sechs Vanilla-Kategorien stimmen für Online- und Offline-Accounts mit kontrollierten Teststatistiken überein.

### Phase 3 — Playtime sauber integrieren

1. Minimales, read-only Service-Interface aus dem bestehenden `PlaytimeModule` bereitstellen.
2. Gespeicherte Zeit plus aktive Session in das Ranking überführen.
3. Bei deaktiviertem Playtime-Modul Kategorie verständlich als nicht verfügbar markieren.
4. `/playtime` und `/leaderboard playtime` auf denselben Stand testen.

**Gate P3:** Keine zweite Playtime-Tabelle, keine abweichenden Startdaten, keine SQL-Zugriffe auf fremde Fach-Tabellen.

### Phase 4 — Eigene Counter und Migration

1. Nächste freie additive Migration für `leaderboard_metrics` und deduplizierte Spawn-Events.
2. `placed` erfolgreich und ohne MultiPlace-Doppelzählung messen.
3. `totems` nur bei echtem, erfolgreichen Totem-Effekt zählen.
4. Peak-Level auf Join, Levelwechsel, Tod und Quit monoton speichern.
5. Crash-/Restart-/DB-Fehlerfälle testen und Logging ergänzen.

**Gate P4:** Werte überleben Neustart; keine nachträgliche Reduktion des Peak-Levels und keine „historischen“ Fantasiewerte.

### Phase 5 — Spawn-Erfolg aus v1.2 anbinden

1. Den **tatsächlichen** Spawn-Erfolgspfad nach Warmup/Cooldown prüfen.
2. Dort einmalig Success-Signal mit UUID + Session-ID veröffentlichen.
3. Leaderboard-Listener registrieren; Erfolg idempotent persistieren.
4. Abgebrochene, fehlgeschlagene und verweigerte Versuche explizit ausschließen.
5. Spawn-Modul ohne Leaderboard weiter betreibbar halten.

**Gate P5:** 10 erfolgreiche Teleports = genau 10 Nutzungen; 10 fehlgeschlagene = 0; Doppelevent = 1.

### Phase 6 — Ranking, Formatierung und Performance

1. Immutable Snapshot pro Kategorie, Wert absteigend, definierte Gleichstände.
2. Top 10 + eigene Position, optionale Folgeseiten und Seitenanzeige.
3. Einheiten: Diamanterze/Blöcke/Totems/Fänge/Nutzungen als Integer; Spielzeit Tage/Stunden/Minuten; Distanz Meter/Kilometer; XP als Level.
4. Große Offline-Kohorten sowie viele `MINE_BLOCK`-Statistiken unter realistischem Server-Tickbudget testen.
5. Vanish-/OP-Filter und Berechtigungen in allen Ausgabepfaden prüfen.

**Gate P6:** Keine merklichen Server-Tick-Spitzen durch wiederholte Ranking-Aufrufe, deterministische Ergebnisse.

### Phase 7 — Leaderboard-Integration und gemeinsamer Release-Kandidat

1. `./gradlew clean check build --no-daemon`, JAR ohne zusätzliche Minecraft-Plugin-Pflicht.
2. Testserver mit echter Paper-26.3-Version und Java 25: zwei aktive Spieler, ein Offline-Spieler, ein OP, mehrere Einträge/Ties.
3. Restart-Test mit bestehender v1.0–v1.2-Datenbank auf **Kopie** der Testwelt.
4. `README.md`, `docs/commands.md`, `docs/configuration.md`, `docs/architecture.md`, `docs/development.md`, `CHANGELOG.md` anpassen.
5. Neue `Patch_v1.3_Implementation_Plan.md` mit Commit/PR dokumentieren; keine alten Patch-Pläne still überschreiben.
6. Release-Kandidat bauen, Checksums und Rollback dokumentieren. **Kein** automatischer Live-Rollout.

**Gate L7:** Leaderboards erfolgreich abgenommen. Der **gesamte Patch v1.3** wird erst freigegeben, wenn außerdem die Gates W0–W4 und V0–V2 bestanden sind (§12).

## 6. Testmatrix / Definition of Done

### 6.1 Commands und Anzeige

- [ ] `/leaderboard` listet **genau elf** Kategorien mit richtigem Befehl.
- [ ] `/lb` entspricht `/leaderboard`, keine Command-Kollisionen.
- [ ] Alle elf Kategorie-Commands funktionieren; `/leaderboard playerkills` ist **nicht verfügbar**.
- [ ] Seite 1 zeigt höchstens zehn Spieler, Seite 2 die nächsten Einträge.
- [ ] Eigene Platzierung wird korrekt angezeigt, auch außerhalb Top 10.
- [ ] Gleichstand führt zu identischem Rang und stabiler Reihenfolge.
- [ ] Neue Spieler ohne Werte erhalten eine verständliche Anzeige, keine fiktive Platzierung.
- [ ] Unbekannte Kategorie, negative/ungültige Seite und Konsole liefern klare Antworten.
- [ ] OPs/Vanished-Spieler tauchen standardmäßig nicht in öffentlichen Listen auf; keine Präsenzleaks.

### 6.2 Vanilla und Playtime

- [ ] Normales und Deepslate-Diamanterz zählen je +1, Fortune zählt nicht mehrfach.
- [ ] `mined` enthält unterschiedliche gültige Blockmaterialien, ohne Doppelzählung/Exceptions.
- [ ] `distance` zählt Gehen/Sprinten/Sneaken zu Fuß; Reiten/Boot/Schwimmen nicht.
- [ ] Mob-Kills, Tode und Angelfänge stimmen mit Vanilla-Testwerten überein.
- [ ] Offline-Spieler behalten Platzierung; alte Vanilla-Daten werden angezeigt.
- [ ] Playtime-Ranking und `/playtime` verwenden denselben Wert einschließlich aktiver Session.
- [ ] Ohne Playtime-Modul startet das Leaderboard weiter und zeigt einen klaren Hinweis.

### 6.3 Eigene Zähler

- [ ] Normale Platzierung +1; gecancelte Aktion +0; MultiPlace exakt nach platzierten Positionen.
- [ ] Kein zusätzlicher Zähler für Blockbewegungen ohne Spieleraktion.
- [ ] Totem wird bei erfolgreicher Auslösung +1 gezählt; gecancelte/falsche Resurrection +0.
- [ ] Erfolgreicher Spawn-Teleport +1; Warmup-Abbruch/Combat-Sperre/fehlgeschlagener TP +0.
- [ ] Doppelte Spawn-Success-ID zählt nur einmal, auch nach Neustart.
- [ ] XP-Level kann steigen/fallen; gespeicherter Höchststand bleibt maximal.
- [ ] Bereits beim ersten Join vorhandenes Level wird übernommen; vor v1.3 verlorene Rekorde werden **nicht** erfunden.
- [ ] Eigene Zähler und Rekorde bleiben nach sauberem Stop/Start erhalten.
- [ ] DB-Fehler sind sichtbar und blockieren keine normale Survival-Interaktion.

### 6.4 Integration, Performance, Betrieb

- [ ] Keine neuen Laufzeit-Pflichtplugins, keine Lumen-/Discord-Verbindung.
- [ ] Groups-Tag, Marriage-Herzen/Partikel und Vanish funktionieren unverändert.
- [ ] V1.2-Spawn-Regeln funktionieren weiterhin vollständig.
- [ ] Native Whitelist, `/sit`, Recipes, `/playtime` bleiben unverändert.
- [ ] Keine Main-Thread-Vollscans je Command; Offline-Stats/Material-Scans gemessen und begrenzt.
- [ ] Datenbank-Migration ist additiv und zweite Ausführung idempotent.
- [ ] Modul per Config deaktivierbar, ohne andere Features zu beschädigen.
- [ ] Vollständiger Build-/Test- und echter Client-Smoke-Test auf Paper 26.3 erfolgreich.
- [ ] Release-Dokumentation, Datenbackup und Rollback-Plan geprüft.

## 7. Rollout, Backup und Rückfallstrategie

1. **Nur Staging:** Kopie der Plugin-Konfiguration, Plugin-SQLite-DB (inklusive nötiger WAL/SHM-Dateien bzw. konsistentes SQLite-Backup) und der Minecraft-Weltstatistiken sichern.
2. Neue JAR auf isoliertem Paper-26.3-/Java-25-Testserver installieren. Migration und Neustart mehrfach prüfen.
3. Kein produktiver Deploy ohne Review und explizite Freigabe. Einen vorhandenen Produktionsserver nicht aus dem Plan heraus automatisch verändern.
4. **Rollback:** Vorherige JAR **und passendes konsistentes DB-Backup** gemeinsam wiederherstellen; ältere Binaries können neuere Schema-Versionen bewusst ablehnen. Vanilla-Spielerstatistiken dabei nicht löschen oder überschreiben.
5. Im Notfall das `leaderboard`-Modul per `modules.leaderboard: false` deaktivieren und normal neu starten, sofern die Gesamtplugin-Version weiterhin zum Schema passt.
6. Bei verlorenem Custom-Zählerstand nicht mit erfundenen Daten auffüllen; den realen Beginn der Erfassung dokumentieren.

## 8. Externe API-Referenzen (technische Prüfung)

Bei Implementierung gegen die **konkret gepinnte** Paper-Version testen. Die folgenden Referenzen erläutern die Datenquellen; sie sind **kein** Nachweis, dass das Feature bereits umgesetzt wurde:

- [Paper 26.3 API](https://jd.papermc.io/paper/26.3/) — `OfflinePlayer#getStatistic`, `Player#getLevel`, `PlayerLevelChangeEvent`, `BlockPlaceEvent`, `BlockMultiPlaceEvent`, `EntityResurrectEvent`.
- [Spigot/Bukkit 26.3 `Statistic` Enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Statistic.html) — `MINE_BLOCK`, `WALK_ONE_CM`, `SPRINT_ONE_CM`, `CROUCH_ONE_CM`, `MOB_KILLS`, `DEATHS`, `FISH_CAUGHT`.
- [Minecraft Wiki: Statistics](https://minecraft.wiki/w/Statistic) — historische/vanilla Statistiktypen, Zentimeter-/Tick-Einheiten, Minecraft-Statistikdateien.

## 9. W — Whitelist-Staging: fachlicher MVP-Vertrag

### 9.1 Ausgangslage und Grundprinzip

Der Server befindet sich **noch in der Testphase**. Die Administratoren sammeln Anmeldungen **manuell** in einem Discord-Channel. IlluniumCustom hat **keine** Discord-Bot-/Lumen-Integration und liest **keine** Discord-Daten automatisch aus. Ein Admin übernimmt den gemeldeten Minecraft-Java-Namen per Ingame- oder Konsolenbefehl.

**Eine gestagete Person darf dadurch nicht joinen.** Bis zum Launch ist der einzige dauerhafte Zugang weiterhin die **native** Datei `whitelist.json` (zusammen mit Vanilla-OP-Bypass und den bereits erlaubten Testern/Staff-Accounts). Staging ist ausschließlich eine vorbereitende **Warteschlange** im Plugin-Speicher. Die native Whitelist bleibt aktiviert und wird nie temporär ausgeschaltet.

```text
Discord-Anmeldung (manuell)
        │
        ▼
/ic whitelist stage <Name>
        │  Name asynchron anhand des offiziellen Minecraft-Profils verifizieren
        ▼
SQLite: STAGED {uuid, verified_name}  ─── NICHT in whitelist.json
        │
        │  Projektstart (ein Admin-Befehl)
        ▼
/ic whitelist launch
        │  eingefrorene Liste, überprüfbare Einzel-Freischaltungen
        ▼
Native /whitelist add <Name> bzw. gleichwertige geprüfte Paper-API
        │
        ▼
Whitelist-Eintrag anhand UUID+Name bestätigen → APPLIED
```

Wichtig: Die Vanilla-Befehle `/whitelist add`, `/whitelist remove`, `/whitelist list` bleiben **unverändert**. Wenn ein Admin einen Namen schon vor dem Launch mit Vanilla `/whitelist add` freischaltet, ist er **sofort** zugangsberechtigt — unabhängig von der Staging-Liste. Das muss in der Bedienungsdokumentation deutlich stehen.

**Keine zweite Autoritätsquelle:** Solange ein Account nur `STAGED` ist, liegt **keine** Zugangsberechtigung vor. Nach dem Launch entscheidet ausschließlich die native Whitelist (sowie normales Vanilla-OP-Verhalten). Ein Deaktivieren des Staging-Moduls darf keine schon übertragene Whitelist löschen und ein bereits manuell entfernter Account darf durch einen alten `APPLIED`-Datensatz **nicht erneut freigeschaltet** werden.

### 9.2 Admin-Commands: minimale, nachvollziehbare Oberfläche

| Command | Verhalten | Zugriff |
| --- | --- | --- |
| `/ic whitelist stage <MinecraftName>` | Account validieren, UUID/aktuellen Namen **asynchron** auflösen; anschließend **nur** vormerken | Vanilla-OP oder Serverkonsole |
| `/ic whitelist staged [seite]` | Vorbereitete Accounts samt Status, Gesamtzahl und erkannter bereits nativer Freischaltungen anzeigen | Vanilla-OP oder Serverkonsole |
| `/ic whitelist unstage <MinecraftName>` | Noch nicht erfolgreich angewendeten Eintrag aus der Staging-Liste nehmen; **keine** native Entwhitelistung | Vanilla-OP oder Serverkonsole |
| `/ic whitelist preview` | Vor Start: Anzahl `STAGED`, schon whitelisted, fehlerhaft und bereits angewendet; **keine Mutation** | Vanilla-OP oder Serverkonsole |
| `/ic whitelist launch` | **Ein Befehl** startet die einmalige, wiederholbare Bulk-Freischaltung aller bereiten Einträge | Vanilla-OP oder Serverkonsole |
| `/ic whitelist status` | Status laufender/letzter Freigabe, Fortschritt, Erfolge, Übersprungene, Fehler | Vanilla-OP oder Serverkonsole |
| `/ic whitelist retry` | Nur fehlgeschlagene/offene Einträge gezielt erneut verarbeiten; nie bereits erfolgreich angewendete | Vanilla-OP oder Serverkonsole |

Der Prefix `/ic` entspricht dem bereits vorhandenen `AdminCommand`. **Kein** eigener `/whitelist`-Alias und kein Überschreiben des Vanilla-Befehls. Der `whitelist`-Admin-Handler prüft zusätzlich **explizit** `Player#isOp()` für Spieler oder `ConsoleCommandSender` für die Serverkonsole; das allgemeine `illuniumcustom.admin`-Permission-Flag allein genügt für diesen sicherheitsrelevanten Bereich nicht.

- **Nameingabe:** Minecraft-Java-Profilnamen validieren; keine Leerzeichen, Steuerzeichen, Argumentketten, Selector-Zeichen, Newlines oder Command-Injection. Aufgelöste Mojang-Namen normalisiert speichern, Vergleiche case-insensitiv; Identität ist die UUID.
- **Stage:** Idempotent bei demselben UUID-Eintrag; ein bereits in der nativen Whitelist stehender Spieler wird ausdrücklich als „bereits freigeschaltet“ gemeldet und nicht versehentlich erneut vorgemerkt. Keine Eingabe ungültiger oder nicht verifizierbarer Accounts in `STAGED` übernehmen.
- **Unstage:** Vor erfolgreicher Übernahme erlaubt; während eines laufenden Launchs gesperrt oder nur durch ein explizit spezifiziertes, rennbedingungsfreies Verfahren. Nach `APPLIED` keine implizite Entwhitelistung: dafür ist das normale `/whitelist remove` zu nutzen.
- **Preview:** Zeigt z. B. `38 vorgemerkt · 3 schon erlaubt · 1 Fehler`; niemals vorgemerkte Namen als freigeschaltet darstellen.
- **Launch:** Startet nach Admin-Aufruf ohne zweiten Pflichtbefehl; führt den Batch mit nachvollziehbarem Fortschritt durch, gibt nicht voreilig „alle erfolgreich“ aus. Ein zweiter gleichzeitiger Launch wird sicher abgewiesen bzw. derselben laufenden Ausführung zugeordnet.
- **Retry:** Führt kein Rollback aus; wirkt nur auf noch nicht nachweisbar native-freigeschaltete Einträge.
- Alle Mutationen erzeugen Audit-Logeinträge (Akteur, UUID, verifizierter Name, Aktion, Ergebnis, Zeitpunkt; keine Geheimnisse).
- Für Spieler verständliche Meldungen, Tab-Completion ohne Leaks von ursprünglich gescheiterten, nicht validierten Profilen; Seite für lange Listen.

### 9.3 Beispiel: Projektstart

```text
# Die folgenden Command-Beispiele sind eine Illustration, keine laufenden Commands:
/ic whitelist stage renko
  ✓ renko (UUID …) für den Projektstart vorgemerkt. Noch NICHT freigeschaltet.

/ic whitelist stage diaaar
  ✓ diaaar (UUID …) vorgemerkt. Noch NICHT freigeschaltet.

/ic whitelist staged
  2 STAGED, 0 APPLIED, 0 FAILED

# Am Projektstart:
/ic whitelist preview
  2 bereit; native Whitelist weiterhin aktiv.

/ic whitelist launch
  Launch gestartet: 2 Einträge; Freischaltung läuft …

/ic whitelist status
  Fertig: 2 freigeschaltet, 0 Fehler. Beide stehen in der nativen Whitelist.
```

**Staff/Testbetrieb:** Bestehende Tester, Vanilla-OPs und über `whitelistguard.staff` geschützte Accounts bleiben unberührt. Staging allein soll **keine** bestehenden Whitelist-Einträge entfernen, umsortieren oder überschreiben. Vanilla-OPs können nach Vanilla-Regeln eine Whitelist ggf. umgehen; `stage` soll diese Mechanik nicht ändern.

### 9.4 Fehlerverhalten und Zustandsmodell

- `STAGED`: Name/UUID erfolgreich aufgelöst, **noch nicht** nativ whitelisted.
- `APPLYING`: Eintrag gehört zu einem gestarteten, dauerhaft protokollierten Batch; vor jeder Aktion auf native Existenz prüfen.
- `APPLIED`: Native Freischaltung wurde **erfolgreich nachgewiesen**. Kein automatischer erneuter Eintrag bei späteren Serverstarts.
- `ALREADY_PRESENT`: Account war bei der Batch-Prüfung bereits in der nativen Whitelist; kein doppelter Schreibvorgang. Ein ausschließlich vorab vorhandener Eintrag ist nicht „durch das Plugin hinzugefügt“.
- `FAILED`: Profil-/API-/Bukkit-/Whitelist-Schreibvorgang nicht bestätigt; Account bleibt gesperrt, sofern nicht anderweitig nativ erlaubt; gezielt retryfähig.

Während eines Launchs wird die Menge der abzuarbeitenden UUIDs **eingefroren**. Nachträglich gestagete Spieler werden **nicht** still in den bereits laufenden Launch eingerechnet; sie können mit einem weiteren Launch freigeschaltet werden. Mehrfaches Stage oder erneuter Launch führt **nicht** zu Duplikaten.

Eine Bulk-Whitelist ist auf Vanilla-/Paper-Ebene **keine atomare Datenbanktransaktion**. Bei Störung nach 7 von 40 Freigaben bleiben diese 7 tatsächlich freigeschaltet; das Plugin muss **7 Erfolge + 33 offen/fehlgeschlagen** zeigen und nach Neustart sicher fortsetzen können. **Keine automatische Rückabwicklung/Entwhitelistung der erfolgreich aufgenommenen Spieler.**

### 9.5 API-/Paper-26.3-Kante, die implementiert und getestet werden muss

**Identitätsauflösung:** `Server#createProfile(name)` / `PlayerProfile#update()` oder eine äquivalente öffentliche Paper-Profil-API asynchron verwenden; nur `getId() != null` plus bestätigten plausiblen **aktuellen** Minecraft-Java-Namen akzeptieren. `Bukkit.getOfflinePlayer(String)` auf dem Serverthread ist hierfür ungeeignet, da ein blockierender Web-Request möglich ist. Keine ungeprüften Offline-UUIDs oder Offline-Mode-Ersatzidentitäten freischalten.

**Freischaltung:** Im **konkret gepinnten** Paper-26.3-Build zuerst den Vanilla-kompatiblen Weg nachweisbar testen. Paper-Issue [#14412](https://github.com/PaperMC/Paper/issues/14412) beschreibt, dass `getOfflinePlayer(UUID).setWhitelisted(true)` einen `"name": ""`-Eintrag in `whitelist.json` erzeugen kann. Deshalb **nicht blind** auf `getOfflinePlayer(uuid).setWhitelisted(true)` setzen. Bevorzugt den offiziellen Befehl `whitelist add <verifizierterName>` über die Serverkonsole bzw. eine nachweislich korrekte Paper-API verwenden; Profil möglichst vorab asynchron auflösen/cache-wärmen. Die Vanilla-Kommandos dürfen nicht per ungeprüfter Benutzereingabe zusammengesetzt werden.

**Erfolg gilt erst nach Prüfung:** Nach Anwendung `getWhitelistedPlayers`/`isWhitelisted` gegen die **UUID** prüfen und das tatsächliche `whitelist.json`-Verhalten mit einem bislang nie angemeldeten Account im Test untersuchen: UUID stimmt, Name ist nicht leer, `/whitelist list` zeigt ihn und `/whitelist remove <Name>` findet ihn. Ist der Name falsch/leer oder die Prüfung negativ, bleibt der Batch als `FAILED`/ungeklärt markiert; keine falsche Erfolgsmeldung. Bei Schreibfehlern keine globale Whitelist-Deaktivierung als „Fallback“.

**Belastung:** Profile im Vorfeld stagen und dadurch die normalen Lookup-Arbeiten vor den Projektstart verlagern. Launch-Schreibvorgänge in **begrenzten Main-Thread-Batches** bzw. mit kontrollierten Async-Fortsetzungen ausführen (Bukkit-/Server-Whitelist-APIs nur auf zulässigem Thread verwenden). Rate Limits, Timeouts und teilweise fehlgeschlagene Spieler behandeln. Für einen großen Launch kein ungezügeltes `CompletableFuture`-Fan-out ohne Begrenzung.

Externe technische Referenzen: [Paper Profile API](https://github.com/PaperMC/Paper/blob/main/paper-api/src/main/java/com/destroystokyo/paper/profile/PlayerProfile.java), [Paper Command Profiles](https://docs.papermc.io/paper/dev/command-api/arguments/entity-player/), [Paper #14412](https://github.com/PaperMC/Paper/issues/14412), [Server/Whitelist API](https://jd.papermc.io/paper/26.3/org/bukkit/Server.html).

## 10. W — Technische Architektur, Schema und Integration

### 10.1 Verantwortungsgrenzen

```text
whitelistguard/WhitelistGuardModule         whiteliststaging/WhitelistStagingModule
  └── WhitelistGuard (NATIVE enforcement)      ├── WhitelistStageCommand ("/ic whitelist …")
                                              ├── WhitelistStageService (Prüfung, Batch, Audit)
                                              ├── ProfileResolver (asynchron, UUID + Name)
                                              ├── NativeWhitelistGateway (Paper/Vanilla, Main Thread)
                                              └── WhitelistStageRepository (SQLite)
```

- `whitelistguard` bleibt allein für **whitelist dauerhaft an** und **Staff-Schutz** verantwortlich. Keine doppelte, eigene Laufzeit-Whitelist für normale Spieler.
- `whiteliststaging` ist eine **abschaltbare vorbereitende Funktion** und greift nur beim expliziten Launch auf die native Whitelist zu. Keine zusätzlichen Join-Event-Freigaben, keine selbst gebaute Ersatz-Autorisierung.
- Ein gemeinsamer, schmaler Whitelist-Read-Service kann Status anzeigen; die Module sollen sich nicht gegenseitig an SQL-Tabellen oder Command-Strings koppeln.
- `modules.whiteliststaging: true` im Default-Config und ein Registry-Eintrag in `Modules.create()`; Abschalten nach dem Launch ist möglich und ändert die native Whitelist **nicht**. Bereits gestagete, noch nicht angewendete Datensätze bleiben gespeichert, ohne Zugang zu gewähren.
- Das vorhandene `AdminCommand`-Rechtemodell erweitern, ohne `/ic group`, `/ic status` oder die Vanilla-Whitelist-Befehle zu beschädigen.
- **Keine** Discord-/Lumen-API, keine Bot-Abhängigkeit, kein Webserver und keine zusätzliche Minecraft-Plugin-JAR.

### 10.2 Dauerhafter Zustand: additive Migration

Nur neue Tabellen, z. B. in der **nächsten freien** Migration `V<n>__whitelist_staging.sql`. **Nicht** `V1__initial_schema.sql` ändern. Wenn Leaderboards vorher V2 verwenden, folgt Whitelist-Staging als V3; wenn andere Änderungen dazwischenliegen, entsprechend später.

```sql
CREATE TABLE whitelist_stage_entries (
  player_uuid      TEXT PRIMARY KEY,
  verified_name    TEXT NOT NULL COLLATE NOCASE,
  status           TEXT NOT NULL CHECK (status IN (
                     'STAGED', 'APPLYING', 'APPLIED', 'ALREADY_PRESENT', 'FAILED')),
  staged_by        TEXT NOT NULL,
  staged_at        INTEGER NOT NULL,
  applied_at       INTEGER,
  launch_id        TEXT,
  attempt_count    INTEGER NOT NULL DEFAULT 0,
  last_error       TEXT,
  UNIQUE(verified_name)
);

CREATE TABLE whitelist_stage_launches (
  launch_id        TEXT PRIMARY KEY,
  started_by       TEXT NOT NULL,
  started_at       INTEGER NOT NULL,
  finished_at      INTEGER,
  state            TEXT NOT NULL CHECK (state IN ('RUNNING', 'DONE', 'PARTIAL', 'FAILED'))
);
```

Schema ist ein **Entwurf**; bei Umsetzung ergänzen: Indexe, Audit-Ereignisse, stabile Claim-/Commit-Phasen, idempotente Batch-IDs und eindeutige Wiederaufnahmelogik. Persistierte UUID und aktueller Name sind **kein** Zugangstoken. `APPLIED` darf nicht zur erneuten Whitelistung bei jedem Serverstart verwendet werden.

**Crashsicherheit:** Vor einem nativen Whitelist-Aufruf die Absicht in SQLite festhalten (`APPLYING` + Batch-ID), nach dem Aufruf **native Existenz** prüfen und erst dann `APPLIED` committen. Nach Crash die `APPLYING`-Einträge **zuerst** mit der Vanilla-Whitelist abgleichen; nur tatsächlich fehlende Einträge erneut versuchen. Fehlgeschlagene Namensauflösungen bleiben außerhalb von `STAGED` oder erhalten einen eindeutig abgewiesenen Status; niemals Blindfreischaltung.

### 10.3 Vor-Launch-Sicherung und Recovery

- Vor Installation / Launch konsistente Sicherung von **`whitelist.json`, `ops.json`, Plugin-DB (SQLite, WAL einschließen bzw. Online-Backup) und `config.yml`**.
- Ein `launch` setzt `white-list=true`/`enforce-whitelist=true` niemals aus und darf diese Werte nicht verstellen.
- Nach erfolgreicher Freischaltung darf ein Vanilla-Admin Spieler weiterhin manuell entfernen; eine spätere Staging-Statusprüfung reaktiviert diese Spieler **nicht automatisch**.
- Bei Fehler: `status`, `staged`, `retry`; keinen manuellen Eingriff in JSON-Dateien während laufendem Server als Standardweg vorschlagen.
- **Kein automatischer Live-Rollout**, keine unreviewten Repository-Writes.

## 11. V — Pro-Admin konfigurierbares Auto-Vanish

### 11.1 Sollverhalten

**Aktuell:** `VanishModule#onJoin` versteckt jedes `player.isOp()` automatisch und unterdrückt die öffentliche Join-Nachricht; `/vanish` und `/unvanish` sind für Vanilla-OPs vorhanden.

**Neu:** Die automatische Entscheidung beim **Join** ist pro **Minecraft-Account-UUID** konfigurierbar. Es gibt **keinen** Bezug zu Gruppenrollen `LEADER`/`MOD`; **Admins = Vanilla Operatoren**.

- OP mit `auto-vanish = true` (Default, kompatibel mit v1.1): Bei jedem Join bereits verborgen, **keine Join-Nachricht**, kein Tablist-Eintrag, keine normale Spieler-Entity für andere Clients. Konsole protokolliert den Join weiterhin.
- OP mit `auto-vanish = false`: **normaler, sichtbarer Join** samt üblicher Join-Nachricht und Tablist-Eintrag; normal spielen, Survival-Gameplay nicht einschränken. Ein früheres manuelles `/vanish` wird nicht über Logout persistiert.
- Beide OP-Typen können danach jederzeit `/vanish` und `/unvanish` verwenden. `/vanish` bei zuvor sichtbarem OP zeigt wie bisher eine **gelbe Vanilla-„left the game“**-Meldung; `/unvanish` macht ihn sichtbar und lässt die bisherige Nachrichten-Semantik unverändert.
- Ein **Nicht-OP** darf unabhängig von Konfiguration weder manuell vanischen noch von einer Auto-Vanish-Einstellung versteckt werden. OP-Degradierung hebt aktives Vanish wie bisher zeitnah auf.
- Neue Zuschauer sehen alle aktuell versteckten OPs ebenfalls nicht. Marriage-Herzen, Tab-Completion, Server-List-Ping und andere Anwesenheitssignale dürfen versteckte Accounts nicht verraten. **Sichtbare** OPs werden nicht versehentlich ausgeblendet.
- **Nur Join-Default:** Eine Änderung der Datei `config.yml` gilt wie bei anderen Modulen erst nach **vollständigem Serverneustart**; kein neuer `/vanish reload`-Befehl im v1.3-MVP.

### 11.2 Konfiguration (vorläufiger verbindlicher Schlüsselvertrag)

```yaml
modules:
  vanish: true
  whitelistguard: true
  whiteliststaging: true

vanish:
  # Kompatibilitätsdefault: Bisher kamen alle Vanilla-OPs versteckt online.
  auto-vanish-default: true
  # UUID des Minecraft-Java-Accounts, nicht Username oder Gruppen-Tag.
  auto-vanish-overrides:
    "11111111-1111-4111-8111-111111111111": false # Survival-Admin: normal sichtbar
    "22222222-2222-4222-8222-222222222222": true  # Moderations-Admin: unsichtbar
```

Die UUIDs im Beispiel sind **Platzhalter**, keine echten Accounts. Für `true`-Accounts kann der Eintrag weggelassen werden, solange der globale Default `true` ist.

- Vor `VanishModule.enable` alle Keys und Werte prüfen: UUID parsebar; Einträge als `boolean`; bei fehlerhaftem Format **klarer Startup-Fehler**, nicht stillschweigend einen Admin sichtbar machen.
- `isOp()` bei jedem Join/Command **zusätzlich** prüfen; die Liste ist keine OP-Erteilung.
- **UUID statt Name**, damit Umbenennungen und Groß-/Kleinschreibung keine Überraschung erzeugen.
- Kein Zwang, jeden OP manuell zu erfassen. Unbekannte/neue OPs folgen `auto-vanish-default: true`, bis sie bewusst Opt-out bekommen.
- `vanish.auto-vanish-default: false` ist optional unterstützt, falls zukünftig generell sichtbare OP-Logins gewünscht werden.
- Durch den Standard `true` verhalten sich Installationen **ohne neue Settings exakt wie zuvor**.

### 11.3 Minimaländerungen am vorhandenen Code

1. `VanishModule`: `onJoin` statt pauschalem `if (player.isOp())` zu `if (player.isOp() && autoVanish(player.getUniqueId()))` ändern. Nur in diesem Zweig Join-Message unterdrücken und `hide` aufrufen.
2. `enable`: aktuell werden schon online befindliche OPs bedingungslos versteckt; denselben Default-/Override-Check dort verwenden. Vorhandenes `VisibilityService` für alle Sichtbarkeitswechsel beibehalten, **keine** zweite Vanish-Liste.
3. Vorhandene `hide`, `show`, `revalidate`, `onQuit`, `onPing` und User-Commands beibehalten; prüfen, dass joinende sichtbare OPs schon versteckte Spieler nicht sehen (der Viewer-Loop bleibt **für jeden** Join erhalten).
4. Config-Parsing lokal im `vanish`-Modul über `context.settings()`, mit unveränderlichem Set/Map zur Laufzeit. Kein globales `static`-OP-Register.
5. `docs/configuration.md`, `docs/commands.md`, `README.md` und das ursprüngliche v1.1-Plan-Dokument nicht nachträglich fälschen, sondern den **neuen Opt-out** dokumentieren.
6. Schon unsichtbar gestartete OPs verlieren ihre Visibility nicht durch Group-/Marriage-/Leaderboard-Updates; öffentliche Online-/Offline-Ausgaben verwenden weiter `VisibilityService`.

### 11.4 Abgrenzung zur v1.3-Rangliste

Im vorherigen Leaderboard-v1.3-Entwurf (§2.3) sind **alle OP-Konten** aus öffentlichen Rankings ausgeschlossen, damit Creative-/Admin-Aktionen sie nicht verzerren. Ein **sichtbarer Survival-Admin** wird deshalb zwar ganz normal im Spiel angezeigt, taucht aber **vorerst nicht** in öffentlichen Top-10-Rankings auf. Ob dieser spezielle Account eine ausdrücklich konfigurierte öffentliche Leaderboard-Freigabe bekommt, ist eine **separate spätere Produktentscheidung**. Das neue `auto-vanish=false` umgeht also keinen Statistikfilter.

## 12. Verbindliche Implementierungsphasen für W und V

Vor der Umsetzung jeweils den **neuesten** `main`-Stand prüfen. Vorhandene Backend-/CI-Konventionen aus `Implementation_Plan.md` anwenden. L-Phasen 0–7 in §5 bleiben bestehen und können unabhängig geplant werden; ein **gemeinsamer v1.3-Release** benötigt beide W- und V-Phasen.

### Phase W0 — Baseline, Verträge und Testdaten

1. Bestehende `WhitelistGuard`-/`VanishModule`-/`AdminCommand`-Tests/Quelldateien erneut lesen und Branch-Baseline dokumentieren.
2. Mit **Kopie** von `whitelist.json` Testdaten vorbereiten: Tester bereits nativ erlaubt, Staff geschützt, drei noch nie gejointe echte Testaccounts, ein ungültiger Name, ein OP.
3. Paper-26.3-Build fixieren; den gemeldeten `setWhitelisted(UUID)`-Namensfehler prüfen, ohne riskante Produktivexperimente.
4. Staging-Commands und Log-Semantik in Testfälle übersetzen.

**Gate W0:** Istzustand festgehalten; keine neue Zugangsberechtigung, bevor eine explizite Launch-Aktion erfolgt.

### Phase W1 — Storage, Profilauflösung und Status

1. **Nächste freie** additive DB-Migration, Indexe, Repo-Layer und Service mit UUID-basierter Idempotenz implementieren.
2. Paper-Profil-Auflösung per Future mit Limit/Timeout und kontrolliertem Main-Thread-Callback, **ohne** Netzwerk auf dem Datenbank-/Tick-Thread.
3. `/ic whitelist stage`, `unstage`, `staged`, `preview` implementieren; Rechtprüfung (nur Konsole/Vanilla-OP) auf jedem Pfad.
4. Kein native Whitelist-Write in dieser Phase. Tests für Offline-Account, Rename und Case-insensitive Dubletten.

**Gate W1:** 100 vorbereitete Accounts überstehen Neustart, ohne dass irgendein vormals gesperrter Account durch das Staging Zutritt erhält.

### Phase W2 — Native Launch, Verifikation und Wiederaufnahme

1. Native-Whitelist-Gateway auf aktueller Paper-Version testen; Entscheidung **Vanilla-Command oder geprüfte API** in `docs/development.md` festhalten.
2. `/ic whitelist launch`, `status`, `retry` umsetzen. Serverseitige Batch-Größe und Wiederholungslogik begrenzen; neue Stages während Batch vom Snapshot trennen.
3. Vor/nach jeder Mutation UUID/Name gegen die native Whitelist verifizieren und Status atomar in SQLite sichern.
4. Neustart zwischen nativem Whitelist-Write und DB-Commit absichtlich simulieren; nach Wiederstart korrekte Recovery bestätigen.
5. Fehler bei Mojang/Profil-API sowie Teilfehler handhaben; keine stille Erfolgsmeldung bei `name: ""`.

**Gate W2:** Ein Launch schaltet alle gültig vorgemerkten Spieler frei und nur sie; wiederholter Launch erzeugt keine zusätzlichen Whitelist-Einträge.

### Phase W3 — Guard-/Staff-Integration und Doku

1. Native `white-list` und `enforce-whitelist` bleiben dauerhaft aktiv; geschützte `whitelistguard.staff`-Accounts prüfen.
2. Testserver mit realen Spielern: Noch STAGED → Whitelist-Kick; nach APPLIED → Eintritt erlaubt; `unstage` ändert nur Staging; `/whitelist remove` nach APPLIED bleibt wirksam.
3. `README.md`, `docs/commands.md`, `docs/configuration.md`, `docs/operations.md`, `docs/architecture.md`, `CHANGELOG.md` entsprechend ergänzen.
4. Separate Checkliste fürs Launch-Personal und Backup-/Rollback-Prozedur dokumentieren.

**Gate W3:** Der Release-Workflow ist von einem Admin anhand der Dokumentation ohne Java-Kenntnis ausführbar.

### Phase V0 — UUID-Konfiguration und Policy-Tests

1. `vanish.auto-vanish-default` und `vanish.auto-vanish-overrides` validiert laden, Default `true`.
2. Reine Policy-Tests für Kombinationen aus OP ja/nein, Default true/false und UUID-Override true/false.
3. Bei unklaren Configwerten deterministisch scheitern (keine gefährlich verdeckte Typ-Konvertierung).

**Gate V0:** Es ist für jede UUID eindeutig, ob **ein Operator** beim Join automatisch versteckt wird.

### Phase V1 — Join-/Show-/Hide-Verhalten

1. Bestehende `onJoin`- und `enable`-Logik minimal anpassen; keine Verschlechterung manueller `/vanish`-/`/unvanish`-Flüsse.
2. Join- und Quit-Chat, Tablist, Entities, Ping-Liste sowie Sichtbarkeitsänderungen gegen mehrere echte Clients prüfen.
3. Laufende OP-Demotions, Plugin-Shutdown/Restart und neue Viewer testen.

**Gate V1:** Survival-Admin (Opt-out) kommt sichtbar rein, Moderations-Admin (Default) silent und hidden; beide können manuell vanischen.

### Phase W4/V2 — Gesamtabnahme, CI, Release-Gate

1. Unit-/Integrationstests einschließlich bestehender Module; `./gradlew clean check build --no-daemon`.
2. Test mit Plugin-DB-Schema aus Bestand, sauberem Stop/Start und Rollback auf passende JAR+DB.
3. Kein neues Pflichtplugin, keine Discord-/Lumen-Verbindung, keine automatische Welt-/Infrastrukturänderung.
4. Launch- und Vanish-Tests auf einem **separaten** Paper-26.3-Testserver abzeichnen lassen.
5. Vor Produktivinstallation `whitelist.json`, `ops.json`, Plugin-DB und Plugin-Konfiguration konsistent sichern.

**Gate W4/V2:** Alle Tests §13 bestanden, keine regressiven Whitelist-, Group-, Marriage-, Spawn- oder Vanish-Fehler; explizite manuelle Produktionsfreigabe.

## 13. Ergänzende Testmatrix / Definition of Done (W und V)

### 13.1 Whitelist-Staging, Rechte und Join-Verhalten

- [ ] `/ic whitelist stage <Name>` löst den Java-Account asynchron zu echter UUID + Name auf und speichert ihn **ausschließlich** in SQLite.
- [ ] Ein ausschließlich `STAGED`-Account steht **nicht** in `whitelist.json` und kann nicht joinen (sofern nicht anderweitig erlaubt/OP).
- [ ] `stage` zweimal für dieselbe UUID erzeugt keinen zweiten Eintrag; Rename und Groß-/Kleinschreibung werden korrekt behandelt.
- [ ] Unbekannte/ungültige Minecraft-Namen sowie Mojang-API-Fehler werden sichtbar abgelehnt, ohne Fake-UUID-Staging.
- [ ] `/ic whitelist staged` und `preview` liefern klare Zahlen für bereit/vorhanden/fehlgeschlagen.
- [ ] `/ic whitelist unstage` entfernt nur Wartelisteneinträge, nicht native Whitelist-Einträge.
- [ ] `/ic whitelist launch` übernimmt alle geeigneten gestageten UUIDs in die **native** Whitelist und bestätigt jeden Erfolg erst nach Prüfung.
- [ ] `/whitelist list` und `whitelist.json` enthalten anschließend **korrekte UUIDs und nichtleere Namen**; `/whitelist remove <Name>` funktioniert für neue Accounts.
- [ ] Schon native whitelisted Tester/Staff bleiben erhalten; bereits hinzugefügte Einträge werden nicht erneut angelegt.
- [ ] Mehrfaches `launch` ist idempotent; ein während Launch gestageter Spieler wird nicht heimlich mit aufgenommen.
- [ ] Teilfehler werden einzeln angezeigt; `retry` ergänzt nur offene Einträge.
- [ ] Restart mitten im Launch erholt sich aus dem aufgezeichneten Stand; keine Duplikate, kein unbemerkter Zugangsentzug.
- [ ] Ein **nach** APPLIED nativ manuell entwhitelisteter Account wird nicht automatisch von Staging wiederhergestellt.
- [ ] Vanilla `/whitelist` funktioniert unverändert, `white-list`/`enforce-whitelist` bleiben aktiv.
- [ ] Nicht-OPs können die administrativen Staging-/Launch-Kommandos selbst mit sonstigen Gruppenrechten nicht benutzen.
- [ ] `modules.whiteliststaging: false` nimmt keine bestehenden nativen Freigaben zurück und gibt keine gestageten frei.
- [ ] Keine Minecraft-Main-Thread-Blockade bei großen Staging-Listen oder Mojang-Ausfall.

### 13.2 Vanish-Konfiguration und Spielmodus

- [ ] Bestehende Installationen **ohne** `vanish`-Settings lassen weiterhin jeden OP automatisch unsichtbar joinen.
- [ ] Per UUID `auto-vanish=false` gesetzter Vanilla-OP joint **mit** üblicher Join-Nachricht und **mit** Tablist-Eintrag.
- [ ] Für anderen OP mit `auto-vanish=true` bleibt Join öffentlich silent; kein Tablist-/Entity-/Ping-Leak.
- [ ] Sichtbarer OP darf `/vanish` nutzen und erzeugt die bestehende gelbe Leave-Meldung; `/unvanish` zeigt ihn wieder.
- [ ] Manuell versteckter Survival-Admin kommt bei seinem **nächsten Join** wieder sichtbar rein, wenn `auto-vanish=false`.
- [ ] Ein nicht-operierter Spieler wird unabhängig von Listenwerten nicht versteckt und darf keine Vanish-Commands verwenden.
- [ ] Ein neu joinender Spieler kann bereits versteckte OPs nicht sehen.
- [ ] OP-Verlust beendet aktiven Vanish wie bisher; UUID-Umbenennung beeinflusst die Join-Policy nicht.
- [ ] `marriage`, `appearance`, `groups`, `leaderboard`, Completion und Ping verraten unsichtbare Anwesenheit nicht.
- [ ] Ungültige UUID oder Nicht-Boolean-Konfiguration scheitert klar und wird nicht still ignoriert.
- [ ] `vanish`-Modul kann separat deaktiviert sein, ohne dass der Whitelist-Launch gestört wird.

### 13.3 Versions- und Schnittstellenregressionen

- [ ] Alle ursprünglichen 11 Leaderboard-Kategorien und die Tests §6 sind noch enthalten.
- [ ] Der v1.2-Spawn-Warmup/Cooldown/Combat-Tag bleibt unabhängig davon verbindlich; kein Rückfall zu Instant-TP.
- [ ] `WhitelistGuard` schützt Staff und schaltet die Whitelist niemals ab.
- [ ] Das v1.1-Gruppenfarben-/Sit-/Marriage-Verhalten bleibt unverändert.
- [ ] Neue Persistenz verwendet additive Migrationen; keine V1-Umschreibung oder Datendestruktion.
- [ ] Komplettbuild, Staging-Rollout, konsistenter Backup-/Rollback-Test, manueller Release-Gate dokumentiert.

---

### Gemeinsames Abschlusskriterium für Patch v1.3

**v1.3 ist erst vollständig freigabefähig, wenn** (L) die **10 öffentlichen Survival-Leaderboards + das Peak-XP-Level-Leaderboard**, (W) die **persistente Vorbereitung ohne Zugang** und der **einmalige, idempotent wiederholbare native Bulk-Whitelist-Launch** sowie (V) die **individuelle Auto-Vanish-Einstellung für Vanilla-OPs** auf Paper 26.3 / Java 25 funktionieren. Der Patch darf **weder PvP-Rankings noch stille Zugangsfreigaben noch neue Sichtbarkeitslecks** verursachen; die Bestandsfunktionen müssen weiter funktionieren.

**Implementierungsauftrag, keine Code-Änderung:** Dieses Dokument beschreibt den Sollzustand, implementiert oder deployt nichts, führt keinen Discord-Workflow automatisiert aus und setzt keine Spieler auf die Live-Whitelist.
