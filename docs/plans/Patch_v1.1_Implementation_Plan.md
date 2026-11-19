# IlluniumCustom — Patch v1.1 Implementation Plan

> **Stand:** 21.10.2026  
> **Status:** Umsetzungsplan / noch keine Patch-Implementierung oder Produktionsfreigabe  
> **Repository:** https://github.com/illuuuuusion/illunium-custom  
> **Basis:** vorhandenes Plugin aus `MVP.md`, `Implementation_Plan.md` und aktuellem Repository-Code auf `main` (am 21.10.2026 eingesehen)  
> **Ziel:** Paper/Minecraft Java **26.3**, **Java 25**, weiterhin **eine eigenständige JAR ohne Pflichtabhängigkeit von anderen Plugins**  
> **Scope:** Forever-World; weder Lumen-Verbindung noch Eingriff in das separate Kingdoms-Plugin  
> **Verhältnis zu v1.0:** Dieses Dokument ist ein **additiver Patch-Plan**. Bestehende Regeln/Funktionen bleiben unverändert, soweit hier nicht ausdrücklich überschrieben.

## 0. Ziel und Änderungen im Überblick

Patch v1.1 erweitert die bereits implementierte Basis gezielt um sieben Änderungen:

| ID | Thema | Fachlicher Sollzustand | Art |
| --- | --- | --- | --- |
| P1 | Gruppenfarben | `/group color <farbname>` **oder** `/group color #RRGGBB` | Änderung |
| P2 | Sit | Space, W/A/S/D und andere Bewegungs-Eingaben beenden Sitzen und geben sofort die Steuerung frei | Korrektur |
| P3 | Marriage | Bei zwei sneakenden Ehepartnern innerhalb von **3 Blöcken** erscheinen Herzpartikel | Erweiterung |
| P4 | Vanish | Vanilla-OPs joinen automatisch unsichtbar und ohne öffentliche Join-Nachricht/Tab-Eintrag; `/unvanish` macht sichtbar, `/vanish` verbirgt wieder | Neues Modul |
| P5 | Spawn | `/spawn` teleportiert zum festgelegten Welt-Spawn | Neues Modul |
| P6 | Difficulty | Änderungen über Command überstehen **Stop/Start und Restart**; erst Vanilla-Verhalten testen, dann ggf. ergänzen | Bedingtes neues Modul |
| P7 | Erweiterbarkeit | Neue Features bleiben unabhängig registrierbar und testbar; keine Kern-Umstrukturierung ohne Bedarf | Architektur-Anforderung |

> **Schreibweise:** In Tabellen/Code gilt überall `sneakend` (geduckt). Die Grenze von drei Blöcken wird als quadratische Distanz `<= 9.0` zwischen den Spielerpositionen definiert.

### 0.1 Explizite Nicht-Ziele

- Keine Neuerstellung des Plugins, kein Reset von v1.0-Daten und kein Ersatz des bestehenden SQLite-Schemas.
- Keine ProtocolLib-, EssentialsX-, LuckPerms-, PlaceholderAPI-, Client-Mod- oder Resourcepack-Pflicht.
- Kein globales `/tpa`-, `/home`-, Warp- oder Teleportationssystem.
- Keine Änderung am nativen Minecraft-Whitelist-Management.
- Kein genereller Ersatz aller Vanilla-Commands; **Difficulty nur bei tatsächlich nachgewiesenem Bedarf**.
- Kein neuer Webservice, keine Discord-/Lumen-Integration und keine automatische Produktionsinstallation.
- „Silent Vanish“ bedeutet **keine normale öffentliche Ingame-Anwesenheitsanzeige**. Eine absolute Tarnung gegenüber sämtlichen Drittplugins, Clientmods, Protokollanalysen oder Konsolenadministratoren lässt sich ohne tiefere Protokolleingriffe nicht garantieren. Solche Abweichungen müssen in Tests offen dokumentiert werden.

## 1. Verifizierte Codebasis und Änderungsorte

Das Repo ist nicht mehr leer: Es enthält Java-25-/Gradle-Build, Paper 26.3, SQLite, die sechs ursprünglichen Module und Tests. In `Implementation_Plan.md` ist ein Release Candidate `0.1.0-rc.1` dokumentiert; die Client-End-to-End-/Produktionsabnahme bleibt dort als offen beschrieben. **Diese Dokumentationsangabe ist keine eigene Live-Verifikation.**

Am 21.10.2026 sind insbesondere diese Dateien im Repository vorhanden:

| Datei | Aktuelle Aufgabe | Patch-Auswirkung |
| --- | --- | --- |
| `src/main/java/de/illunium/custom/groups/Group.java` | `NamedTextColor color`, Erzeugung des `[TAG]`-Prefix | Farbe auf beliebiges `TextColor` erweitern |
| `src/main/java/de/illunium/custom/groups/GroupService.java` | Farbvalidierung, Gruppenmutationen und Cache | Named-/Hex-Eingaben normalisieren und persistent speichern |
| `src/main/java/de/illunium/custom/groups/GroupCommand.java` | `/group color`, Completion | Hex-Eingabe akzeptieren; Farbnamen weiter vorschlagen |
| `src/main/java/de/illunium/custom/groups/GroupRepository.java` | SQL-Mapping | Vorhandene Farbwerte kompatibel lesen |
| `src/main/java/de/illunium/custom/appearance/AppearanceService.java` | Einziger Eigentümer von Nametag/Tablist | Bestehenden Service weiterverwenden; mit Vanish abstimmen |
| `src/main/java/de/illunium/custom/sit/SitModule.java` | Sitz-ArmorStand, `/sit`, `/unsit`, Cleanup | `PlayerInputEvent`, ohne Bewegungs-Rubberbanding |
| `src/main/java/de/illunium/custom/marriage/MarriageModule.java` | Marriage-Commands, Heart-Tablist | Herzpartikel-Komponente/Ereignisprüfung ergänzen |
| `src/main/java/de/illunium/custom/marriage/MarriageService.java` | Partnerbeziehung, SQLite | Bestehende Partnerdaten abfragen, kein neues Marriage-Schema erforderlich |
| `src/main/java/de/illunium/custom/core/module/Modules.java` | Explizite Modul-Registry | `vanish`, `spawn` und ggf. `difficulty` aufnehmen |
| `src/main/java/de/illunium/custom/core/config/PluginConfig.java` | Validierung der Modulflags | Neue Flags/Config gegen bekannte Module prüfen |
| `src/main/java/de/illunium/custom/IlluniumCustomPlugin.java` | Bootstrap und Lifecycle | Bestehendes Muster beibehalten; keine zweite Hauptklasse |
| `src/main/resources/plugin.yml` | Metadaten und Permissions | Neue OP- und Spieler-Permissions definieren |
| `src/main/resources/config.yml` | Modulflags/Settings | Neue Einstellungen samt Defaults ergänzen |
| `docs/commands.md`, `docs/architecture.md`, `docs/development.md`, `README.md`, `CHANGELOG.md` | Projektdokumentation | Nach jeder Phase gezielt aktualisieren |

**Wichtige Istzustände:**

- Der vorhandene `AppearanceService` benutzt **ein Team pro dekoriertem Spieler** und setzt den Tablist-Namen ausdrücklich selbst. Vanish darf diese Ausgabe **nicht als Sichtbarkeitskontrolle missbrauchen**.
- Das aktuelle `SitModule` verwendet einen **unsichtbaren Marker-ArmorStand**, auf dem der Spieler als Passenger sitzt. Es reagiert bereits auf Dismount, Schaden, Quit, Teleport usw.; **Tastendruck** ist die neue Lücke.
- `Group` und die Gruppen-Speicherung arbeiten bisher mit `NamedTextColor`; vorhandene Daten enthalten Farb**namen** und müssen gültig bleiben.
- `MarriageModule` zeigt bereits online/offline-Herzen in der Tablist. Die neue Partikelfunktion ergänzt das, ersetzt es nicht.
- Die Module besitzen bereits das Interface `FeatureModule` und die Registry `Modules.create()`; dieses Modell soll weiter genutzt werden.

Vor tatsächlichem Coding zuerst `git status --short --branch`, `HEAD`, Repository-Regeln (`AGENTS.md`, `CLAUDE.md` falls vorhanden), Tests, aktuellen Dependency-Pin und gegebenenfalls neuere Änderungen prüfen. **Aktueller Code schlägt die Dateiliste in diesem Plan.**

## 2. P1 — Gruppenfarben mit HEX-Support

### 2.1 Commands und Verhalten

```text
/group color red
/group color gold
/group color light_purple
/group color #ff8800
/group color #A1B2C3
```

- Die bisher erlaubten Minecraft-/Adventure-Farbnamen bleiben verfügbar (case-insensitive).
- Zusätzlich wird ein vollständiger sechsstelliger HEX-Code **mit `#`** akzeptiert: `^#[0-9A-Fa-f]{6}$`.
- `#` ist Pflicht; drei-, vier- oder achtstellige Formen (`#FFF`, `#FFFFFFFF`) sind **nicht** im Patch vorgesehen.
- Eingaben werden zur Speicherung auf kanonische Form `#RRGGBB` (Großbuchstaben) normalisiert. Für bestehende Farbnamen muss ein kompatibler Lesepfad bestehen; ob Farbnamen ebenfalls kanonisiert werden, darf ihre Farbwirkung nicht verändern.
- Nur der Leader darf die Farbe ändern; sämtliche bisherigen Rollenregeln gelten unverändert.
- Nametag **und** Tablist erhalten sofort dieselbe Farbe des `[TAG]`-Prefix; der Spielername und Marriage-Suffix bleiben unverändert.
- Keine Interpretation der Nutzereingabe als MiniMessage/Format-String; ausschließlich validierten Wert in `TextColor` umwandeln.
- Tab-Completion schlägt die 16 Standardfarben vor. Freie HEX-Eingaben müssen auch ohne Vorschlag funktionieren.

### 2.2 Technischer Ansatz

1. In der Gruppen-Fachschicht `NamedTextColor` durch `net.kyori.adventure.text.format.TextColor` ersetzen, **wo** die Farbe nicht auf Named-only limitiert sein muss.
2. Eine kleine testbare `GroupColor`-/`ColorParser`-Utility einführen (`parse`, `normalize`, `serialize`), statt Validierung über Commands, SQL und Appearance zu duplizieren.
3. Die bestehende `groups.color`-Spalte als `TEXT` weiterverwenden, falls sie beliebige Strings aufnehmen kann. **Kein zerstörerischer Schemaumbau.**
4. Beim Lesen alte Named-Werte (z. B. `WHITE`, `RED`, `LIGHT_PURPLE`) kompatibel interpretieren. Noch vorhandene Benennungsvarianten vor der Migration aus dem Repo ermitteln.
5. `Group.prefix()` weiter als Adventure-`Component` ausgeben; `AppearanceService` bleibt die zentrale Render-Stelle.
6. Den Scoreboard-Team-**Prefix** hex-fähig gestalten; nicht versuchen, `Team#color` für beliebiges RGB zu erzwingen, falls die jeweilige API dort nur Named Colors zulässt.
7. Mutation weiterhin transaktional, Appearance-Refresh erst nach erfolgreichem Commit.

**Abnahme P1:** Named und HEX rendern oberhalb des Kopfes und in der Tablist identisch; Neustart erhält die Farbe; alte Gruppen bleiben lesbar; `#GG0000` und unzulässige Farbcodes werden zurückgewiesen; Spieler ohne Leader-Recht können nichts ändern.

## 3. P2 — Sit: Aufstehen mit Bewegungstasten

### 3.1 Sollverhalten

- Während `/sit` aktiv ist, beendet **Leertaste** das Sitzen.
- Ebenso beenden **W, A, S, D**, **Sneak/Dismount** oder **Sprint-Taste** das Sitzen.
- Danach kann der Spieler **sofort normal laufen, springen und sich drehen**, ohne nochmal `/sit` oder `/unsit` ausführen zu müssen.
- Kamera-/Mausdrehung allein beendet das Sitzen **nicht**.
- Die bisherigen Ausstiegsfälle (Schaden, Tod, Teleport, Weltwechsel, Disconnect, ArmorStand-Dismount, Plugin-Disable) bleiben erhalten.
- Ein einzelner Tastendruck darf nur **eine** Stand-up-Aktion auslösen; keine Doppelteleports und keine verbleibenden ArmorStands.

### 3.2 Technischer Ansatz

`PlayerInputEvent` und `Input` der Paper-/Bukkit-API prüfen; die Input-API bietet unter anderem `isForward()`, `isBackward()`, `isLeft()`, `isRight()`, `isJump()`, `isSneak()` und `isSprint()`. **Kein ProtocolLib erforderlich.** Die API bzw. konkrete Event-Reihenfolge muss auf dem gepinnten Paper-26.3-Build mit echtem Client getestet werden.

1. In `SitModule` einen `PlayerInputEvent`-Listener für Sitzende ergänzen.
2. Bei Sit-Beginn den letzten Inputzustand erfassen. Stand-up auf **neu aktivierte** relevante Eingaben auslösen (Rising Edge) und eine sehr kurze Mount-Initialisierungsphase berücksichtigen, damit bereits vor `/sit` gehaltene Tasten keinen sofortigen ungewollten Abbruch auslösen.
3. Sitzenden via aktuellem `Seat`-State identifizieren; Events für Nicht-Sitzende direkt ignorieren.
4. Bei einer relevanten Eingabe denselben zentralen idempotenten `standUp(...)`-Cleanup nutzen wie bei `/unsit`.
5. Für **tastengesteuertes** Aufstehen **keinen verzögerten Teleport an den alten Sitzpunkt** ausführen; andernfalls kann ein `runTask`-Restore den ersten Bewegungsschritt zurücksetzen (Rubberbanding).
6. Den Spieler nach dem sicheren Entmounten an seiner aktuellen zulässigen Position belassen; keine dauerhafte Flug-/Bewegungs-/NoClip-Änderung.
7. Dismount-Event und Input-Event können in derselben Tick-Phase eintreffen: genau **einer** entfernt die Session und Entity, der zweite ist ein No-op.
8. Bei einer unzuverlässigen `PlayerInputEvent`-Zustellung während des Reitens das konkrete Client-/Paper-Verhalten nachweisen und eine **Paper-native** Alternative untersuchen; den Patch nicht mit einer ungetesteten Vermutung als PASS markieren.

**Abnahme P2:** W/A/S/D/Space/Sneak/Sprint jeweils separat mit echtem Client; bei jedem Input sofort normales Gehen möglich; kein Teleport zurück, keine Stuck-Passenger, keine Seat-Leaks nach Restart/Quit/Death.

## 4. P3 — Marriage: Sneak-Herzpartikel

### 4.1 Trigger-Vertrag

Herzpartikel entstehen **nur**, wenn gleichzeitig:

1. zwei **miteinander verheiratete** Spieler online sind,
2. sich beide in **derselben Welt** befinden,
3. beide **aktuell sneaken**,
4. ihr Abstand **maximal 3,0 Blöcke** beträgt (`distanceSquared <= 9.0`),
5. keiner der beiden aktuell im **Vanish** verborgen ist.

Die Partikel erscheinen im Raum zwischen bzw. etwas oberhalb der Spieler, für normale umstehende Spieler sichtbar. Es gibt **keine** neue Marriage-Permission oder Gameplay-Belohnung.

**Verhalten:**

- Ein gemeinsames, leichtes Herzpartikel-Ereignis ungefähr **einmal pro Sekunde pro Paar**, solange die Bedingungen gelten; keine doppelte Emission für A→B und B→A.
- Stoppt spätestens beim nächsten Intervall nach Loslassen von Sneak, Überschreiten von 3 Blöcken, Weltwechsel, Divorce, Quit, Death oder Vanish.
- Kein Spawning für offline/hidden Partner und keine sichtbaren Partikel, die die Position eines vanished Admins verraten.
- Kein Paarungsverhalten, keine neuen Entitäten, keine Partikel bei nur einem sneakenden Spieler.

### 4.2 Technischer Ansatz

- In `marriage` einen `MarriageHeartsEffect` (oder eine ähnlich eng begrenzte Klasse) ergänzen.
- Vorhandene `MarriageService`-Beziehungen als Source of Truth lesen; **keine** zweite Partnerschafts-Map oder SQLite-Migration.
- Ein gemeinsam verwalteter, synchroner und begrenzter Scheduler-Task reicht: z. B. alle 20 Ticks **einmal über verheiratete Online-Paare** iterieren.
- Pair-Deduplizierung anhand geordneter UUIDs, Distanz als `distanceSquared`, Weltgleichheit vor Distanzprüfung.
- Für Partikel `Particle.HEART` und die Paper-/Bukkit-World- bzw. Player-Partikel-API nutzen; Partikelmenge gering halten, keine Chunkloads erzwingen.
- Vanish-Status nur über eine explizit lesbare **Core-/Visibility-Schnittstelle** prüfen, nicht durch fachfremden Zugriff auf Vanish-interne Collections.
- Falls `vanish` als Modul deaktiviert ist, liefert die Sichtbarkeits-Schnittstelle für alle Spieler **sichtbar**.
- Beim `disable()` Task garantiert abbrechen.

**Abnahme P3:** 2,9 / 3,0 / 3,1 Blöcke; beide vs. nur einer sneakt; Weltwechsel; Ehewechsel; Divorce; 2 gleichzeitige Paare; Vanish; Restart. Bei erfülltem Trigger Partikel, ansonsten keine.

## 5. P4 — OP Auto-Vanish und Commands

### 5.1 Spieler-Sollverhalten

**Admin-Definition:** ausschließlich `Player#isOp()` / **Vanilla Operator**. Eine Gruppenrolle `MOD` oder die Plugin-Permission `illuniumcustom.admin` allein macht niemanden zum Vanish-Admin.

| Ereignis | Sichtbares Verhalten für normale Spieler |
| --- | --- |
| OP joint | **Keine** gelbe Join-Nachricht, **kein** Tablist-Eintrag, keine sichtbare Spieler-Entity/Nametag |
| OP ist nach Join online | Bleibt verborgen bis zu `/unvanish` |
| OP führt `/unvanish` aus | Admin wird für alle sichtbar und erscheint in der Tablist; **keine** zusätzliche Join-Nachricht (bewusster Default) |
| Sichtbarer OP führt `/vanish` aus | Admin verschwindet aus Sicht/Tablist; **einmal** gelbe Vanilla-artige `Name left the game`-Nachricht |
| Bereits verborgener OP führt `/vanish` aus | Keine doppelte Nachricht, idempotenter Erfolg/Status |
| Sichtbarer OP führt erneut `/unvanish` aus | Keine doppelte Nachricht, idempotenter Erfolg/Status |
| Verborgener OP disconnectet | Keine öffentliche Quit-Nachricht |
| Sichtbarer OP disconnectet | Normale Vanilla-Quit-Nachricht, sofern nicht anderweitig geändert |
| Normaler Spieler joint | Alle aktuell vanished OPs bleiben auch für diesen Neuankömmling verborgen |
| OP joint, während andere Spieler online sind | Neue OP-Session wird für sämtliche vorhandenen Viewer verborgen |

**Admin-Commands:**

```text
/vanish
/unvanish
```

- Beide Commands sind ausschließlich **im Spiel** von Vanilla-OPs ausführbar; Konsole hat keinen eigenen Player-Zustand.
- Auf `isOp()` bei **jeder** Ausführung prüfen; ein bloßer Bukkit-Permission-Eintrag genügt nicht.
- Nach `/unvanish` sichtbar bleibende OPs sind in dieser Sitzung sichtbar, bis sie `/vanish` benutzen oder disconnecten. Beim **nächsten Join** startet jeder OP wieder automatisch vanished.
- Vanish ist **kein Spectator-/Godmode-/Invulnerability-/NoClip-System** und verleiht keine weiteren Spielrechte.
- Eine Konsole darf weiterhin vollständig sehen, wer verbindet oder Commands ausführt. **Es darf kein eigenes „unvanish required“ oder ähnlicher Sicherheits-Schritt den Konsolenzugang blockieren.**

### 5.2 Darstellung und Nachrichten

- Originale `PlayerJoinEvent`-Meldung für OPs mit `joinMessage(null)` unterdrücken; `PlayerQuitEvent` für hidden OPs ebenso.
- Bei `/vanish` eines zuvor **sichtbaren** OPs eine **gelbe Vanilla-artige** `Name left the game`-Nachricht an normale Ingame-Viewer senden. Für einen versteckten Neu-Join gibt es keine solche Nachricht.
- Keine künstliche Meldung „joined the game“ beim `/unvanish` (nicht angefordert, kann später konfigurierbar werden).
- Sichtbarkeitswechsel dürfen Gruppentags und Marriage-Tablist-Herzen **nicht löschen oder duplizieren**. Der `AppearanceService` behält den Namen/Prefix/Suffix; `VanishService` kontrolliert **viewer-bezogene Sichtbarkeit**.
- Der öffentliche Marriage-Online-Status muss einen vanished Partner als **offline/nicht sichtbar** behandeln, damit das Herz keine versteckte OP-Anwesenheit verrät. `/marry info` und Command-Completion dürfen verborgenes Online-Sein nicht preisgeben.
- Versteckte OPs dürfen keine Partikel aus P3 erzeugen und nicht in öffentlichen Spielerliste-/Completion-Ergebnissen auftauchen, soweit die eigene Plugin-Ausgabe kontrolliert wird.

### 5.3 Architektur

Neues Paket `de.illunium.custom.vanish`:

```text
vanish/
├── VanishModule.java       # Commands, Listener, Lifecycle
├── VanishService.java      # Sichtbarkeitszustand, Viewer-Synchronisierung
└── VanishPolicy.java       # ausschließlich isOp() / Statusregeln
```

**Zustand:** flüchtiges `Set<UUID> hiddenAdmins`, serverseitig auf dem Main Thread. **Nicht** nach SQLite persistieren: `isOp()` + Joinregel entscheiden jedes Mal neu.

**Schnittstelle für andere Module:** Im Core eine sehr schmale `VisibilityService`-Abstraktion anbieten, z. B. `isPubliclyVisible(UUID)` und `publiclyOnline(UUID)` / ggf. `canSee(viewer, target)`; Vanish-Modul registriert den konkreten Provider. Marriage/Tablist konsumieren **nur** diese Sicht, keine Vanish-Interna. Nicht eine zweite Render-Pipeline neben `AppearanceService` aufbauen.

**Paper-/Bukkit-API-Ansatz:**

1. Bei OP-Join frühzeitig in der Event-Verarbeitung den `hidden`-Zustand setzen und die Join-Nachricht unterdrücken.
2. Für **alle Online-Viewer** `viewer.hidePlayer(plugin, op)` anwenden, nicht nur Entity-Invisibility-Potion (die lässt Nametag/Tablist ggf. sichtbar).
3. Für jeden neu joinenden Viewer alle aktuell verborgenen OPs erneut ausblenden.
4. Bei `/unvanish` `viewer.showPlayer(plugin, admin)` auf sämtlichen Viewern anwenden; bei `/vanish` entsprechend `hidePlayer`.
5. Sichtbarkeit und Appearance-Zustand nach jedem Wechsel sauber synchronisieren; wiederholte Commands sind idempotent.
6. Bei serverseitigem OP-Entzug während einer laufenden Sitzung darf keine dauerhafte privilegierte Vanish-Session verbleiben. Revalidierung/Verlust des OP-Status behandeln (z. B. ereignis- oder intervallbasiert), auf **sichtbar** zurückführen oder Session explizit beenden; keinen unprivilegierten Hidden-Status fortführen.
7. Bei normalem Quit Hidden-State entfernen und eventuelle eigene Viewer-Caches bereinigen; bei Modul-Disable Sichtbarkeit der noch Online-Spieler wiederherstellen, soweit die Bukkit-API dies erlaubt.
8. Für Admins, die **vor** dem Plugin-Enable online sind (Reload-/Recovery-Sonderfall), eine dokumentierte sichere Behandlung implementieren; keine stillschweigende Annahme, dass alle nur via Join ins Modul gelangen.

### 5.4 Realistische Stealth-Grenzen und Tests

**Wichtig:** `hidePlayer` ist viewer-bezogen und nach aktuellem API-Vertrag für Tablist **und** Entity vorgesehen; der exakte Zeitpunkt des Join-/PlayerInfo-Paketversands kann sich trotzdem auf kurzzeitiges Tab-Flackern auswirken. `PlayerJoinEvent` alleine garantiert keine lückenlose Paket-Level-Tarnung. Das ist ein **eigenes Abnahme-Gate mit echten Clients**, keine unhinterfragte Zusicherung.

Zusätzlich explizit prüfen:

- Vanilla `/list`, Name-Suggestions/Tab-Completion, Serverlisten-Ping/Spielerprobe und eigene Plugin-Commands auf Leaks.
- Eine verborgene Person darf sich nicht durch Marriage-Heart-Status, P3-Partikel, Gruppen-Funktionen oder öffentliche Plugin-Anzeigen verraten.
- Normale Tode, Advancements, Chat-Nachrichten und direkte Interaktionen eines verborgenen OPs können Namen/Aktivität offenbaren. **Patch v1.1 ist kein vollwertiges „unsichtbar und trotzdem unbeobachtbar spielen“-System.** Im Patch werden Leaks geprüft und, soweit für einen Silent-Join ohne externe Bibliotheken nötig und verlässlich möglich, unterdrückt; unlösbare Fälle vor Freigabe dokumentieren.
- OPs, die bewusst Nachrichten schreiben, Blöcke abbauen oder andere sichtbare Aktionen ausführen, können ihre Anwesenheit verraten. Vanish garantiert ausdrücklich keinen Schutz davor.
- Falls der **verlangte** No-Tab-/No-Join-Gate mit Paper-API auf dem Zielbuild nicht stabil erreicht wird, **nicht** die Implementierung als erfolgreich deklarieren. Umsetzung entweder auf eine getestete Paper-native Lösung anpassen oder als Blocker zurückmelden (keine ungefragte Packet-Library einführen).

**Abnahme P4:** Zwei reguläre Clients + eine OP-Session, Join vor/nach Viewer, alle State-Transitions, keine OP-Join-Nachricht, keine sichtbare Tab-/Entity-Anzeige, korrekt gelbes Leave bei `/vanish`, sichtbare Rückkehr nur über `/unvanish`, persistenter Standard „hidden at every login“, OP-Entzug, Quit/Restart, Ehe-/Gruppen-Integration.

## 6. P5 — `/spawn`

### 6.1 Verhalten

```text
/spawn
```

- Alle normalen Spieler dürfen den Command verwenden (`illuniumcustom.use`, wie die übrigen Public Commands).
- Teleportziel ist die **Welt-Spawnposition der primären Overworld** aus dem Paper-/Bukkit-Weltzustand (`server.getWorlds()`/`level-name` bzw. geprüfte Server-Standardwelt) — **nicht** der persönliche Respawn-/Bed-/Anchor-Punkt.
- Kein zweiter, eigener Spawn in SQLite, kein `/setspawn` im MVP. Admins können den Standardspawn weiterhin mit dem nativen `/setworldspawn` verwalten.
- Sofortige Teleportation ohne Cooldown, Warmup oder Kosten; keine zusätzliche Permissions-/Economy-Integration.
- Command nur für Spieler; Konsole bekommt eine klare Rückmeldung.
- Bei ungültigem/unsicherem Ziel (z. B. blockiert oder ohne sicheren Standbereich) nicht blind in Blöcke/Lava teleportieren; vorher Ziel prüfen und nachvollziehbar abbrechen oder eng begrenzten sicheren Platz am Spawn verwenden. **Kein ungeprüftes Chunk-Pregenerieren.**
- Teleport-Event/Cancel respektieren. Einen sitzenden Spieler vor dem Teleport über den Sit-Lifecycle sauber entmounten lassen.

### 6.2 Technischer Ansatz

Neues isoliertes Paket `spawn/SpawnModule.java`; Zielwelt einmal beim Startup validieren, Zielposition bei Command-Ausführung erneut aus der Welt lesen (damit `/setworldspawn` ohne Pluginrestart wirkt). Native `Player#teleport` bzw. getestete Paper-API verwenden; Welt-/Location-Aufrufe nur auf dem Main Thread.

**Abnahme P5:** Spawn in normaler Welt und aus Nether/End, anderer persönlicher Bedspawn, geänderter `/setworldspawn`, Sit-Zustand, blockierter Zielort, Cancel/Failure, normaler Spieler vs. Konsole. Bestehende Welten unberührt.

## 7. P6 — Difficulty dauerhaft per Command

### 7.1 Vor der Implementierung: Vanilla-Gate

Die Vanilla-/Paper-Runtime hat bereits `/difficulty <peaceful|easy|normal|hard>`. Ob der Wert **in genau dieser vorhandenen Serverkonfiguration** nach sauberem Stop/Start oder Restart bestehen bleibt, muss **zuerst real geprüft werden**. Dokumentationsangaben zur Rolle von `level.dat` vs. `server.properties` sind je nach Version/Hosting-Konfiguration widersprüchlich. **Nicht automatisch einen Vanilla-Command überschreiben.**

**Testablauf (nur Testserver, mit Backup):**

1. Ausgangswerte von `/difficulty`, `server.properties` (`difficulty=`) und den beteiligten Welten read-only notieren.
2. Als OP bzw. Konsole z. B. `/difficulty hard` ausführen; Ingame-Wert kontrollieren.
3. Server sauber per `stop` herunterfahren, normal starten; Difficulty für **alle betroffenen Welten** erneut prüfen.
4. Ein zweites Mal nach Pterodactyl-/Hosting-typischem Restart testen (ohne blindes `kill`).
5. Zusätzlich prüfen, ob Panel/Startup-Konfiguration beim Start `server.properties` zurücksetzt.

**Wenn beide Tests PASS:** **kein Difficulty-Modul hinzufügen**. Den vorhandenen `/difficulty`-Befehl dokumentieren, Patchpunkt als „Vanilla ausreichend“ schließen.

**Wenn ein Test FAIL:** Nur dann folgende Fallback-Implementierung durchführen.

### 7.2 Fallback (nur bei Bedarf)

**Empfohlener Command:**

```text
/setdifficulty peaceful
/setdifficulty easy
/setdifficulty normal
/setdifficulty hard
```

- Nur **Vanilla-OPs** und die Konsole; kein Gruppen-/Plugin-Adminrollen-Ersatz.
- Der neue Befehl setzt die Difficulty **sofort** und speichert die gewünschte Difficulty **atomar** als Plugin-Sollzustand (kleine Konfigurationsdatei/eindeutiger Storage-Key). Sie wird bei jedem Start wieder angewandt.
- Komplette Liste betroffener Welten vorher festlegen: Default **alle geladenen Welten**, inklusive Nether/End, damit das Verhalten nachvollziehbar bleibt. Bei Welten, die erst später laden, Welt-Load ebenfalls berücksichtigen.
- Eingabe nur aus vier gültigen Werten. Bei Speicherfehler **kein** Erfolg melden; Live-Änderung kontrolliert zurücknehmen oder vor Änderung erfolgreich persistieren.
- Bei Startup nachvollziehbar loggen: welcher Wert gespeichert ist, auf welche Welten er angewandt wurde und ob ein Fehler auftrat.
- Existierende Welt-/Spielerdaten und die Vanilla-Whitelist nicht verändern.
- Der Vanilla-Command `/difficulty` bleibt zunächst verfügbar und unverändert. Die Dokumentation stellt klar, dass bei einem nachgewiesenen Vanilla-Persistenzproblem **`/setdifficulty` der persistente Weg** ist. Ein stilles Überschreiben von `/difficulty` findet nicht statt.
- Optionaler, **separat zu reviewender** Unterpunkt nur bei ausdrücklichem Bedarf: delegierender `/difficulty`-Wrapper, der beide Command-Quellen (Spieler/Konsole) korrekt übernimmt und die native Namensregistrierung nicht beschädigt. **Nicht** mit Preprocess-Event-Hacks ungetestet simulieren.

**Abnahme P6:** Der per unterstütztem Command gewählte Wert wirkt sofort und besteht nach mindestens zwei normalen Startzyklen, auch mit Pterodactyl; kein unabsichtlicher Eingriff in den Vanilla-Command, keine neue Funktion wenn Vanilla bereits zuverlässig persistiert.

## 8. P7 — Erweiterbare Modularchitektur und Integration

### 8.1 Feste Leitlinien

- **Ein JAR, vorhandenes `FeatureModule` beibehalten.** Neue Pakete implementieren `id()`, `enable(ModuleContext)` und `disable()`.
- Neue Module werden explizit in `Modules.create()` aufgenommen; `modules.vanish`, `modules.spawn` und ggf. `modules.difficulty` kommen mit dokumentierten Defaults in `config.yml`.
- Modulfehler beim Boot verhindern einen halb gestarteten Zustand; nach einem Fehler Cleanup/Disable prüfen.
- Keine zyklischen Modulabhängigkeiten. Gemeinsame, wiederverwendbare Logik nur bei echtem Bedarf im Core.
- **Appearance bleibt Single Writer** für nametag/tablist-Dekoration. **Vanish ist zuständig für Viewer-Sichtbarkeit**, nicht für Text-Rendering.
- `VisibilityService` als schmale Core-Schnittstelle: Vanilla-OP-Visibility für Hearts/Completion und ggf. künftige Features nutzbar machen; ohne Vanish-Modul definierter „alle sichtbar“-Default.
- Keine SQL-Abfragen über fachfremde Tabellen, keine direkte Änderung anderer Modulkörper.
- Bestehende Migrationen unverändert lassen; SQL-Migration nur, wenn das reale Schema es verlangt, additive Migration mit Upgrade-Test.
- Keine neuen Pflicht-Abhängigkeiten, NMS-Reflection oder schwer wartbare Packet-Hacks.
- Einstellungen beim Startup validieren; kein Ingame-Hot-Reload versprechen. Lebenszyklus/Cleanup vollständig testbar.

### 8.2 Konsistenzregeln zwischen Features

1. **Vanish + Marriage:** Ob ein Partner „online“ ist, muss für öffentliche Herzen auf `VisibilityService.publiclyOnline(partner)` beruhen, nicht allein auf `Server#getPlayer`.
2. **Vanish + Group:** Die DB-Mitgliedschaft bleibt erhalten. Ein unsichtbarer Admin wird weder durch Group-Prefix noch durch Tablist-Refresh versehentlich sichtbar.
3. **Vanish + Hearts:** Keine Partikel, die den Partner oder Aufenthaltsort eines hidden OPs verraten.
4. **Sit + Spawn:** Teleport beendet Sit und entfernt Seat-ArmorStand zuverlässig.
5. **Group color + Appearance:** HEX/Named nur im Prefix; Marriage-Suffix bleibt separat und korrekt.
6. **Vanish + Tablist:** Ein `setPrefix`-/`setSuffix`-Update darf `showPlayer` **nicht** indirekt auslösen.
7. **Disable/Restart:** Keine Sitz-Entities/Tasks/DB-Ressourcen zurücklassen; Vanish-Status wird bei jedem OP-Login wieder `hidden`.

## 9. Phasenplan mit Abnahme-Gates

Die Phasen sind bewusst **klein, reviewbar und nacheinander**. Nach jedem Block: Build/Unit-Tests → gezielte Paper-Smoke-Tests → Code-Review → Docs → nächster Block. Große ungetestete Sammel-Commits vermeiden.

| Phase | Gegenstand | Erforderliches Gate |
| --- | --- | --- |
| **0** | Read-only Repo-Inventur, Version/Dependencies/Tests, Backup und Testserver-Candidate | Clean/zuordenbarer Istzustand, `./gradlew clean check build --no-daemon` PASS |
| **1** | Groups: HEX/Namen, Datenkompatibilität, Tests | P1 PASS, existierende DB nach Upgrade nutzbar |
| **2** | Sit: Bewegungseingaben, Cleanup/Rubberbanding, Tests | P2 mit echtem Client PASS |
| **3** | Core `VisibilityService` + `vanish` (Join/Commands/Viewer-Sync) | P4 mit 2 Spielern + OP getestet |
| **4** | Marriage-Sichtbarkeit + Sneak-Partikel | P3 und Vanish-Leak-Tests PASS |
| **5** | Spawn-Modul | P5 PASS inkl. Sit und Worldspawn-Änderung |
| **6** | Difficulty-Vanilla-Restart-Gate; nur bei FAIL Fallback | P6 dokumentiert; kein unnötiger Override |
| **7** | Vollständige Integration, Regression, Doku, RC-Artefakt | Alle relevanten Tests + manuelle Freigabe zur Produktionskandidatur |

### Phase 0 — Inventur und Schutz vor Regression

- Aktuellen `main`-Stand, uncommitted Änderungen und Repo-Konventionen ermitteln; **keine lokalen Arbeiten überschreiben**.
- Bestehende Kommandos, eventuelle Permissions und Daten-Schema anhand aktuellen Codes verifizieren.
- SQLite-/Config-Snapshot des Testservers vorbereiten und Upgradetest mit echten v1.0-Beispieldaten anlegen; **kein** Live-DB-Backup im Chat veröffentlichen.
- Java-25-/Paper-26.3-Build und CI testen; Artefakt/JAR-Hash erfassen.
- Acceptance-Arbeitsblatt anlegen mit Ergebnis `PASS` / `FAIL` / `BLOCKED` und Verweis auf Test.

### Phase 1 — Gruppenfarben

- Parser/Typumstellung/Repository-Readback in kleinen Commits.
- Unit-Tests für 16 Standardnamen, `#000000`, `#FFFFFF`, `#a1B2c3`, Fehlformen, Rechte.
- SQLite-Kompatibilitätstest: v1.0-Gruppe laden → HEX setzen → Serverrestart → gleiche Farbe.
- Client-Test Nametag und Tablist gleichzeitig; kein Reset anderer Gruppenfelder.

### Phase 2 — Sit

- `PlayerInputEvent` zunächst isoliert instrumentieren **nur auf Testserver**, keine Spams in Produktion.
- Tastenfolge mit echtem Client messen; Event-Reihenfolge Dismount/Input festhalten.
- Stand-up-Callback, initiale Key-State-Erkennung und No-Rubberbanding umsetzen.
- Unit-/Integrationstests für duplicate Events, Cleanup, Tick-/Mount-Transitions; Sit-Altverhalten erhalten.

### Phase 3 — Vanish

- `VisibilityService`-Vertrag vor der Fachimplementierung fixieren.
- Neue Modulregistrierung, Default-Config und Operator-only Commands.
- Früheste zuverlässig mögliche Hidden-Join-Behandlung sowie Join/Quit-Message-Handling implementieren.
- Viewer-Sync für bestehende und später joinende Spieler.
- Zwei echte Clients plus OP testen: **kein Join-Text und kein Tab-Flackern**; falls nicht sicher, **Gate BLOCKED**.
- `/vanish`, `/unvanish`, Deop, Quit, Restart, Tab-Completion und `/list`-Leaks separat prüfen.

### Phase 4 — Marriage-Partikel

- Marriage-Online-Status auf öffentliche Visibility-Schnittstelle umstellen.
- Einen geteilten Hearts-Task mit Pair-Deduplizierung und begrenzter Particle-Frequenz ergänzen.
- 3-Block-Boundaries, Sneak/Un-Sneak, gleichzeitige Paare, Vanish-Filter testen.
- Ausbleiben von Tasks/Partikeln nach Modul-Disable/Logout prüfen.

### Phase 5 — Spawn

- Neue isolierte Command/Module-Klasse mit Basisworld-Spawn; Permission `illuniumcustom.use`.
- Sicherheits-/Event-Checks und Integration mit Sit prüfen.
- Mehrwelt-/Bedspawn-/Worldspawn-Änderung mit realem Server testen.

### Phase 6 — Difficulty

- **Zuerst** Vanilla über sauberen `stop` und Hosting-Restart testen und Ergebnisse dokumentieren.
- Falls PASS: Feature nicht schreiben, Doku aktualisieren.
- Falls FAIL: `/setdifficulty` in eigenem Modul; gewünschter Zustand wird vor/mit erfolgreicher Live-Änderung persistent gespeichert und beim Boot wieder angewandt.
- Zwei Restart-Runden und Multiworld-Checks absolvieren; bei unerwartetem Server-Properties-/Hosting-Override Ursache dokumentieren.

### Phase 7 — Abschluss und Release-Gate

- `./gradlew clean check build --no-daemon` erfolgreich; keine neuen Warnungen oder instabilen Tests.
- Unabhängigen Diff-Review durchführen (Permissions, Datenmigration, Entity-/Task-Cleanup, Visibility-Leaks).
- `README.md`, `MVP.md` (als v1.0-Basis **nicht rückwirkend umschreiben**; v1.1 additiv verweisen), `docs/commands.md`, `docs/configuration.md`, `docs/architecture.md`, `docs/development.md`, `CHANGELOG.md` auf Patchstand bringen.
- JAR mit Commit-SHA und SHA-256 erfassen; Staging-Deployment mit Rollback-Artefakt.
- Restart-/Backup-/Restore-Test; keine ungeprüften Server-/Paper-Updates bündeln.
- Produktion nur nach **separater manueller Freigabe** und vorherigem Forever-World-Backup.

## 10. Testmatrix (Minimum)

| Testfall | Erwartung |
| --- | --- |
| `/group color red` | Bestehende Named-Farbe funktioniert |
| `/group color #fF8800` | In Nametag/Tablist `#FF8800`, nach Restart gleich |
| `/group color FF8800` / `#FFF` / `#ZZ0000` | Valider Fehler, alter Wert unverändert |
| Gruppen-Member nutzt `/group color #112233` | Abgelehnt, kein SQL-Update |
| `/sit`, dann `W` | Sofort aufstehen, normal vorwärts laufen; kein Seat zurück |
| `/sit`, dann `Space` | Sofort aufstehen, normal springen |
| `/sit`, dann nur Maus drehen | Weiterhin sitzend |
| Sit + Dismount/Input im selben Tick | Genau einmal Cleanup, kein Teleport-Rubberband |
| Ehepaar mit 3,0 Blöcken, beide sneaken | Herzpartikel in begrenzter Frequenz |
| Ehepaar mit >3,0 Blöcken oder nur einer sneakt | Keine Partikel |
| Ehepartner vanished | Kein Heart-Online-Leak, keine Partikel |
| OP joint bei 2 sichtbaren Spielern | Kein sichtbarer Join-Text, kein Tab-/Entity-Eintrag |
| OP `/unvanish`, dann `/vanish` | Sichtbar → unsichtbar; beim Vanish einmal gelber Leave-Text |
| OP hidden disconnectet | Keine Quit-Nachricht |
| Normaler Spieler versucht `/vanish` | Abgewiesen, kein Hide |
| Neue Spieler joinen nach hidden OP | OP bleibt verborgen |
| `/spawn` von Nether/End bzw. nach `/setworldspawn` | Primärwelt-Spawn, kein persönlicher Bedspawn |
| `/spawn` während Sit | Entmount und Teleport ohne Seat-Leak |
| Vanilla `/difficulty hard` + zweimal Restart | Bei PASS keine eigene Implementierung |
| Fallback `/setdifficulty hard` + zweimal Restart | Bei Bedarf Persistenz nachgewiesen |
| V1.0-DB nach Patchstart | Gruppen, Marriage und Playtime intakt |
| Plugin Disable/Restart | Tasks/ArmorStands/Team-Zustände bereinigt |

### 10.1 Negative Sicherheits-/Privatsphäre-Tests

- Kein Nicht-OP kann `/vanish` oder `/unvanish` nutzen, auch nicht durch reines Erteilen von `illuniumcustom.admin`.
- Kein Group-Mod kann globale OP-/Visibility-/Difficulty-Rechte erlangen.
- Join-/Quit- und Vanish-Sichtbarkeit sind pro Viewer korrekt; `/list`, Serverlisten-Ping/Player-Sample und Tab-Completion werden auf Leaks kontrolliert.
- HEX-Parser akzeptiert keine MiniMessage-Tags, Steuerzeichen, siebenstellige oder unvalidierte Inputs.
- Task-Fehler/DB-Fehler führen nicht zu unkontrolliertem Sichtbarwerden anderer Spieler oder Datenverlust.
- Kein zusätzlicher Inbound-Port, HTTP-Endpunkt, Discord-Connector oder Host-/Containerrecht.

## 11. Definition of Done (Patch v1.1)

- [ ] Baseline/Build und vorhandene Daten geprüft; keine v1.0-Regression.
- [ ] Named **und** `#RRGGBB` für Gruppenfarbe funktionieren und persistieren.
- [ ] `/sit` lässt sich über **Space/W/A/S/D/Sneak/Sprint** zuverlässig verlassen, sofortiges Weiterlaufen möglich.
- [ ] Herzen erscheinen bei **beiden sneakend**, **gleiche Welt**, **maximal 3 Blöcke**, ohne Vanish-Leak.
- [ ] Vanilla-OP ist bei jedem Join standardmäßig unsichtbar, ohne öffentliche Join-Nachricht und ohne Tab-/Entity-Sichtbarkeit.
- [ ] `/unvanish` und `/vanish` funktionieren nur für Vanilla-OPs; `/vanish` erzeugt genau eine gelbe Leave-Nachricht, wenn zuvor sichtbar.
- [ ] `/spawn` teleportiert normale Spieler zum Weltspawn, ohne alte Sit-Entities zu hinterlassen.
- [ ] Difficulty-Persistenz auf **realem** Paper-/Pterodactyl-Testserver geprüft; bei Bedarf zuverlässiger Fallback implementiert.
- [ ] Java 25/Paper 26.3, **ohne neue fremde Plugin-Abhängigkeiten**; alle Module lifecycle-sauber.
- [ ] Bestehende SQLite-Daten kompatibel; Test-Backup und Restore nachgewiesen.
- [ ] Tests/CI, Dokumentation, Diff-Review, Staging- und Release-Artefakt vollständig.
- [ ] Produktiver Rollout bleibt ein **gesonderter Human Gate** und wird nicht durch den Plan freigegeben.

## 12. Verbindliche Arbeitsweise für Umsetzung

1. **READ-ONLY:** Repositoryzustand, bestehende Regeln und zu ändernde Codepfade lesen; Ausgangs-SHA dokumentieren.
2. **CANDIDATE:** Pro Phase kleinste sinnvolle Änderung erstellen, zunächst keine Produktiv-Mutation.
3. **DIFF/REVIEW:** Feature-Regeln, Threading, SQL-/Config-Kompatibilität und Security prüfen.
4. **VERIFY:** Build/Tests/echter Paper-Smoke. Bei Fehlern Stop → Ursache → gezielter Fix → denselben Test wiederholen.
5. **DOCS:** Commands, Konfiguration, Bedienung und Grenzen aktualisieren.
6. **HUMAN GATES:** Commit, Push, PR/Merge, produktives Deployment und Infrastrukturänderungen nur nach expliziter Freigabe. Commits nach vorhandener Repo-Konvention kurz auf Englisch, ohne ungefragte Co-Author-Zeilen.
7. **DEPLOY/ROLLBACK:** Erst Staging mit gepinntem Paper-Build; danach separate Freigabe und Restorefähigkeit für Forever-World.

**Operative Schritte müssen immer Ausführungsort, WireGuard-Bedarf, Modus (READ-ONLY/MUTATION), erwartetes Ergebnis und Abbruchbedingung angeben.** Das Dokument selbst enthält **keine** Ausführungsfreigabe für Produktivänderungen.

## 13. Technische Referenzen

- Paper / Adventure MiniMessage: https://docs.papermc.io/adventure/minimessage/format/ (HEX-Format `#RRGGBB`)
- Paper Command API (Named Colors): https://docs.papermc.io/paper/dev/command-api/arguments/adventure/
- Bukkit Input API: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Input.html (`isForward`, `isBackward`, `isLeft`, `isRight`, `isJump`, `isSneak`, `isSprint`)
- Bukkit `PlayerInputEvent`: https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/player/PlayerInputEvent.html
- Paper `PlayerJoinEvent`: https://jd.papermc.io/paper/26.3/org/bukkit/event/player/PlayerJoinEvent.html
- Paper `World#setDifficulty`: https://jd.papermc.io/paper/26.3/org/bukkit/World.html
- Server-Properties: https://docs.papermc.io/paper/reference/server-properties/

---

**Versionierungsregel:** Dieses Patch-Dokument ist die v1.1-Erweiterung zum bestehenden MVP und Implementation Plan. Es beschreibt das **zu implementierende Soll**, nicht einen bereits durchgeführten Code-Patch.
