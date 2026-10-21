# IlluniumCustom — Patch v1.2 Implementation Plan

> **Stand:** 21.10.2026  
> **Status:** Umsetzungsplan; **keine Implementierung oder Produktionsfreigabe**  
> **Repository:** https://github.com/illuuuuusion/illunium-custom  
> **Zielumgebung:** Minecraft Java / **Paper 26.3**, **Java 25**  
> **Einsatz:** Illunium Forever-World (Survival-/Community-Server, **kein PvP-Server**)  
> **Vorbedingungen:** [`MVP.md`](./MVP.md), [`Implementation_Plan.md`](./Implementation_Plan.md), [`Patch_v1.1_Implementation_Plan.md`](./Patch_v1.1_Implementation_Plan.md)  
> **Verhältnis zu v1.1:** Dieses Dokument **ersetzt ausschließlich** die v1.1-Spezifikation von `/spawn` (P5, dort §6 und die zugehörigen Spawn-Abnahmekriterien). Andere v1.0-/v1.1-Funktionen bleiben unverändert.

## 0. Ziel und feste Produktentscheidungen

Die Community möchte `/spawn` behalten, aber verhindern, dass er als **sofortiger Fluchtknopf** benutzt wird. Es handelt sich um einen einfachen Komfort-Teleport zu einem zentralen Treffpunkt, **nicht** um ein allgemeines PvP- oder Anti-Combat-Log-System.

| Verhalten | Patch v1.2 (verbindlich) |
| --- | --- |
| Command | `/spawn` |
| Ziel | Weiterhin der **native Weltspawn der primären Overworld** aus v1.1 |
| Teleport-Wartezeit | **10 Sekunden** ab erfolgreichem Start des Countdowns |
| Cooldown | **60 Minuten pro Spieler**, beginnend **erst nach erfolgreich abgeschlossenem Teleport** |
| Bewegung während Warmup | **Bricht Teleport ab** |
| Schaden während Warmup | **Bricht Teleport ab** |
| Combat-Tag | **30 Sekunden** nach aktivem PvP-Schaden (ausgeteilt **oder** erhalten); in dieser Zeit darf `/spawn` **nicht gestartet werden** |
| Treffer während Warmup | Warmup abbrechen; bei PvP zusätzlich Combat-Tag setzen/erneuern |
| Monster in der Nähe | **Völlig irrelevant**; **kein** Radius-/Mob-Check |
| Combat Logging | **Keine** Logout-Strafe, kein NPC/Combat-Body, keine Inventarstrafe, kein Join-Lock |
| Berechtigung | Für normale Spieler verfügbar; bestehende öffentliche `illuniumcustom.use`-Berechtigung beibehalten |
| Andere Module | Funktionieren weiterhin unabhängig; keine neuen Pflicht-Plugins oder Discord-/Lumen-Verbindungen |

**Wichtig:** Der Combat-Tag ist **nur eine kurze Sperre für `/spawn`**. Er hindert Spieler nicht am Ausloggen oder Wiederverbinden. Ein Spieler kann weiterhin combat-loggen; ein umfassender Schutz dagegen ist ausdrücklich **nicht** gewünscht.

### 0.1 Was explizit nicht gebaut wird

- Keine „Monster in 10 Blöcken“-Prüfung; keine Mob-Suche, auch nicht optional als MVP-Default.
- Kein PvP-Arena-, Anticheat-, Combat-Logging-, Kill-/Death- oder Anti-Teleport-System für andere Commands.
- Kein globales `/home`, `/tpa`, `/warp`, keine Economy-/Kosten- oder RTP-Funktion.
- Kein globales Verbot von PvP während der Wartezeit; es wird nur der Spawn-Teleport abgebrochen.
- Kein eigenständiger Permissions-/Profile-/Account-Service; keine Verknüpfung mit Lumen.
- Keine automatische Freigabe eines Produktions-Deployments.

## 1. Repository-Iststand und v1.1-Abhängigkeit

Am 21.10.2026 wurde das GitHub-Repository `illuuuuusion/illunium-custom` auf `main` gelesen. Es enthält den Java-25-/Paper-26.3-Build, das `FeatureModule`-Muster, `Modules.create()`, `ModuleContext`, SQLite mit Migrationen sowie den vorhandenen Modul- und Testaufbau.

**Wichtig für die Implementierung:** In dem eingesehenen `main`-Dateibaum sind derzeit **`src/main/java/de/illunium/custom/spawn/` und ein `SpawnModule` noch nicht vorhanden**. `/spawn` ist im v1.1-Plan als neues Modul vorgesehen, aber aus dem Repository allein **noch nicht als implementiert bestätigt**. Es ist also **kein** bestehender `/spawn`-Code zu unterstellen.

Vor der Arbeit erneut prüfen:

```text
git status --short --branch
git rev-parse HEAD
rg -n 'SpawnModule|registerCommand\("spawn"|spawn-cooldown|combat-tag' src docs || true
./gradlew clean check build --no-daemon
```

1. **Falls v1.1 `/spawn` bereits implementiert ist:** vorhandenes Modul gezielt um Warmup, Cooldown und Combat-Tag ergänzen; keine zweite Registrierung oder Parallel-Implementierung anlegen.
2. **Falls v1.1 `/spawn` noch fehlt:** zuerst den Basisvertrag aus v1.1 P5 herstellen (primäre Overworld, Weltspawn, sichere Zielprüfung, `/sit`-Integration), aber **ohne** dessen inzwischen überholte Sofort-Teleport-Semantik. Sofort mit den v1.2-Regeln implementieren.
3. Wenn sich der aktuelle Code von diesem Snapshot unterscheidet, **aktuellen Code als Istzustand** verwenden. Geänderte Annahmen dokumentieren.

### 1.1 Bestehende Architektur beibehalten

| Bestandteil | Verantwortung im Patch |
| --- | --- |
| `core/module/FeatureModule.java` | Gewöhnlicher Modul-Lifecycle; nicht verändern, wenn unnötig |
| `core/module/Modules.java` | `spawn` genau einmal registrieren, wenn noch nicht vorhanden |
| `core/module/ModuleContext.java` | Gemeinsame Core-Dienste verwenden; kein globales statisches Singleton |
| `storage/Database.java` | SQLite-Migration für den **persistenten** Cooldown |
| `sit/SitModule.java` | Aufstehen/Cleanup bei erfolgreichem Teleport bleibt wie in v1.1 vorgesehen |
| `appearance/AppearanceService.java` | Für diesen Patch **nicht** verändern |
| `vanish` aus v1.1 | Keine neue Vanish-Logik und keine neue Join-/Quit-Semantik |
| `plugin.yml`, `config.yml` | Permissions und Spawn-Konfiguration ergänzen/erweitern, nicht duplizieren |

Die bestehenden Modulgrenzen bleiben erhalten. Die Combat-Erfassung darf als **interne Spawn-Funktion** umgesetzt werden; es wird **kein** generisches Combat-Subsystem benötigt.

## 2. Fachvertrag für `/spawn`

### 2.1 Command-Flow

```text
/spawn
  │
  ├─ Ungültiger Command-Kontext / fehlende Berechtigung? → Ablehnung
  ├─ Bereits laufender Spawn-Countdown?                → Restzeit anzeigen; nicht neu starten
  ├─ Cooldown noch aktiv?                              → Restzeit anzeigen; nicht neu starten
  ├─ PvP-Combat-Tag noch aktiv?                         → Restzeit anzeigen; nicht neu starten
  ├─ Kein gültiger/sicherer Weltspawn?                  → Ablehnung ohne Cooldown
  └─ 10-Sekunden-Warmup starten
       │
       ├─ Spieler bewegt sich?                         → Abbruch; kein Cooldown
       ├─ Spieler nimmt Schaden?                       → Abbruch; kein Cooldown
       ├─ PvP-Schaden durch/an Spieler?                → Abbruch + 30-Sekunden-Combat-Tag
       ├─ Spieler quit/death/teleport/worldchange?     → Abbruch; kein Cooldown
       ├─ Plugin/Server stoppt?                        → Abbruch; kein Cooldown
       └─ 10 Sekunden abgelaufen:
            ├─ Vorbedingungen/Ziel erneut prüfen
            ├─ Teleport fehlgeschlagen/abgebrochen?    → Kein Cooldown
            └─ Teleport erfolgreich                   → 60-Minuten-Cooldown setzen
```

**Countdown:** genau **10 Sekunden**. Der Teleport darf **nicht vor Ablauf** der zehn Sekunden passieren. Eine niedrige Tickrate darf den Countdown verlängern, niemals verkürzen. Der Spieler erhält eine Startmeldung und eine zurückhaltende Restzeit-Anzeige (z. B. Actionbar pro Sekunde). Keine Chat-Nachricht pro Tick.

**Position:** Der Teleport verwendet weiterhin die **aktuelle** native Worldspawn-Position der primären Overworld, nicht das Bett bzw. den persönlichen Respawnpunkt. Eine Änderung durch `/setworldspawn` wird ohne Plugin-Neustart beim nächsten Teleport berücksichtigt. Vor Abschluss nochmal die sichere Position prüfen.

**Abbruch:** Nach jedem Abbruch ist der Spieler frei beweglich, es verbleibt kein Scheduler-Task und **kein Cooldown wird verbraucht**. Ein anschließend erneuter `/spawn` beginnt wieder bei zehn Sekunden, sofern kein Combat-Tag/Cooldown dagegensteht.

### 2.2 Bewegung während des Warmups

- Maßgeblich ist eine **tatsächliche Positionsänderung** (X/Y/Z), nicht reine Kamera-/Blickrichtungsänderung (Yaw/Pitch).
- **Laufen, Springen, Wegschieben, Fallen, Knockback und sonstiger Ortswechsel** führen zum Abbruch. Keine künstliche Bewegungsunterdrückung oder Rubberbanding.
- Serverseitiges Event: `PlayerMoveEvent`; Anfangsposition bzw. Event-`from`/`to` mit **kleiner numerischer Toleranz nur gegen Rundungsrauschen** vergleichen (keine großzügige Strecke zum „kleinen Herumlaufen“). Der Grenzwert wird anhand echter Client-Tests festgelegt und dokumentiert.
- `PlayerTeleportEvent` ist als spezielle Bewegungsart **separat** zu behandeln. Ein fremder oder Vanilla-Teleport während des Countdowns bricht ihn ab. Das **eigene** abschließende `/spawn`-Teleport-Event darf sich nicht selbst abbrechen; klarer Session-State/Guard statt pauschaler Event-Ausnahme.
- Sneaken oder den Kopf drehen **ohne Positionsänderung** bricht nicht ab. Wenn die Sneak-Taste tatsächlich eine Bewegung verursacht, gilt die normale Bewegungsregel.
- Reine Tasteneingaben ohne Positionsänderung sind **kein** zusätzlicher Cancel-Grund. Damit bleibt der Wunsch „Bewegung bricht ab“ konsistent, ohne eine unnötige Eingabeüberwachung.

### 2.3 Schaden während des Warmups

- **Jeder tatsächlich wirksame Schaden am Spieler** bricht den Countdown ab, unabhängig von der Ursache: Monster, Feuer, Fall, Lava, Projektil, PvP usw.
- Abgebrochene Schadenereignisse und Treffer, die **keinen wirksamen Schaden** verursachen, sollen nicht irrtümlich abbrechen (Event-Priorität und endgültigen Schaden auf Paper-26.3-Testbuild prüfen).
- **Schaden durch ein Monster** bricht zwar einen **laufenden** Teleport ab, sperrt aber **nicht** automatisch den nächsten `/spawn` über einen Combat-Tag.
- **Ein Angriff des Spielers auf einen anderen Spieler** zählt als PvP für den Combat-Tag und bricht einen eigenen laufenden Countdown ebenfalls ab (auch wenn man selbst keinen Schaden erhalten hat).
- **Keine** Suche nach Mobs: Ein Zombie fünf Blöcke entfernt verhindert weder das Starten noch das Abschließen des Teleports, solange er keinen Schaden verursacht und der Spieler sich nicht bewegt.

## 3. Combat-Tag: 30 Sekunden, nur für Spawn

### 3.1 Genaue Trigger

Der Spieler erhält bzw. erneuert einen **30-Sekunden-Combat-Tag**, wenn er

- einen anderen Spieler wirksam verletzt (**Angreifer**), oder
- von einem anderen Spieler wirksam verletzt wird (**Opfer**).

Beide beteiligten Spieler werden bei demselben gültigen PvP-Treffer getaggt, soweit sie online identifiziert werden können. **Ein weiterer gültiger PvP-Treffer setzt die jeweils betroffene 30-Sekunden-Frist erneut ab jetzt.**

Erfasst werden mindestens direkte Spielerangriffe sowie Spieler-Projektile mit eindeutig zuordenbarem Schützen (z. B. Pfeile). Weitere Paper-Schadensquellen nur dann zurechnen, wenn der verantwortliche Spieler über die API **zuverlässig** bestimmbar ist. Keine spekulative Attribution über Namen oder Nearby-Player.

**Nicht als PvP taggen:** Schaden durch Monster, natürliche Umwelt, Selbstschaden, nicht zuordenbare Explosionen, reine Item-Nutzung, bloßes Annähern an Feinde, gecancelte Treffer und Angriffe auf Mobs.

**Wirkung:**

- Während des Combat-Tags verweigert `/spawn` den Start und nennt die verbleibenden Sekunden.
- Wird jemand im Warmup in PvP verwickelt, Warmup abbrechen und Combat-Tag setzen bzw. verlängern.
- **Keine** Auswirkungen auf Chat, normalen Logout, Rejoin, Sterben, Schaden, Inventar, andere Commands oder PvP selbst.
- Der Tag ist **flüchtiger Online-Zustand** und wird beim Disconnect entfernt. Ein schneller Relog kann die Sperre somit umgehen; das ist eine **bewusst akzeptierte Grenze**, weil kein Anti-Combat-Log-System gewünscht ist. Ein persistenter Combat-Tag ist **kein Bestandteil von v1.2**.
- Für OPs gilt bei Verwendung von `/spawn` grundsätzlich dasselbe Verhalten wie für andere Spieler; bestehende native Admin-Commands bleiben unangetastet.

### 3.2 Zeitmodell

- Combat-Tag: Frist mit monotoner Serverlaufzeit (`System.nanoTime`/`Duration` oder vergleichbarer Clock-Abstraktion) im Arbeitsspeicher; kein selbstzähligender Tick-Counter und keine globale Logik in `PlayerMoveEvent` für ungetaggte Spieler.
- Bei Ablauf ist `/spawn` wieder erlaubt. Bei Quit: Eintrag und laufende Session entfernen, keine Strafe.
- Für Unit-Tests eine injizierbare Zeitquelle vorsehen, um `29.999 s`, `30.000 s` und Erneuerung exakt zu testen.

## 4. Persistenter Cooldown: 60 Minuten

Der Cooldown muss **einen gewöhnlichen Stop/Start und Neustart überleben**. Ein Plugin-/Serverrestart darf nicht zu beliebig vielen kostenlosen Spawn-Teleports führen.

### 4.1 Datenmodell

**Empfehlung:** vorhandene SQLite-Datenbank, neue additive Migration (Versionsnummer aus dem **zum Implementierungszeitpunkt tatsächlich vorhandenen** Migrationsstand bestimmen). Beispiel-Schema, **nicht** blind als `V2` einchecken:

```sql
CREATE TABLE spawn_cooldowns (
    player_uuid TEXT PRIMARY KEY,
    available_at_epoch_ms INTEGER NOT NULL
);
```

- UUID als Spieleridentität, nicht `last_known_name`.
- `available_at_epoch_ms` ist der Zeitpunkt, ab dem `/spawn` wieder zulässig ist.
- Unbenutzte/abgelaufene Zeilen können bei Start oder periodisch risikoarm bereinigt werden.
- Kein neuer Datenbankserver, keine neue SQLite-Connection außerhalb der vorhandenen `Database`-Abstraktion und **keine** blockierende SQLite-Arbeit in Main-Thread-Events.
- Bei Neustart die gespeicherte Deadline prüfen; verbleibende Zeit korrekt anzeigen.
- Serverseitige Zeitumstellungen sind bei epoch-basierter Persistenz zu berücksichtigen und in Tests zu dokumentieren; zur Laufzeit einen konservativen, konsistenten Vergleich verwenden.

### 4.2 Wann die Frist beginnt

**Nur nach tatsächlich erfolgreichem Teleport**. Weder eine Command-Eingabe noch ein abgebrochener Countdown noch ein am Ziel fehlgeschlagener Teleport dürfen den 60-Minuten-Cooldown verbrauchen.

Die Teleport-Operation (Bukkit-Main-Thread) und SQLite (DB-Worker) sind nicht gemeinsam atomar. Deshalb ist **explizit** eine sichere Fehlerstrategie zu implementieren und zu testen:

1. Pro Spieler höchstens eine aktive Spawn-Operation zulassen (`IDLE → WARMING_UP → COMPLETING → COOLDOWN`), damit doppelte Commands bzw. verzögerte DB-Futures keinen zweiten Teleport auslösen.
2. Vor dem Abschluss erneut die Spieler- und Zieldaten prüfen und sicherstellen, dass der Storage betriebsbereit ist.
3. Nach **bestätigtem** Teleporterfolg die Deadline (`jetzt + 60 Minuten`) sofort in der In-Memory-Sicht aktivieren und über die vorhandene DB-Queue persistieren.
4. Solange die Persistierung aussteht, weitere `/spawn`-Aufrufe **fail-closed** ablehnen; erst nach erfolgreichem DB-Commit einen bestätigten Cooldown-Status melden.
5. Bei DB-Fehler **keinen** ungeschützten Wiederholungs-Teleport freigeben; Fehler sichtbar loggen, In-Memory-Sperre behalten und einen kontrollierten Retry/Recovery-Pfad anbieten. Vor Produktionsfreigabe Speicherfehler/Fault-Injection sowie Restarts unter realistischem Fehlerszenario testen und dokumentieren.
6. Bei fehlgeschlagenem oder gecanceltem Teleport **keine** Cooldown-Deadline schreiben. Keine stillschweigenden Strafzeiten.

Ein kurzes Crash-Fenster zwischen Teleport und dauerhaftem Commit kann ohne transaktionale Kopplung beider Systeme nicht vollständig ausgeschlossen werden. Für den kleinen Community-Server genügt eine nachvollziehbar getestete **Best-Effort-Persistenz mit Fail-Closed-Verhalten**; kein zusätzliches verteiltes System bauen.

### 4.3 Cooldown-Anzeige

Beispiele für Spielerantworten (Sprache und Prefix an die bestehenden Plugin-Messages anpassen):

```text
/spawn
Illunium » Teleport zum Spawn startet in 10 Sekunden. Nicht bewegen!

[Bewegung]
Illunium » Spawn-Teleport abgebrochen: Du hast dich bewegt.

[Schaden]
Illunium » Spawn-Teleport abgebrochen: Du hast Schaden erhalten.

[Combat-Tag]
Illunium » Du bist noch 24 Sekunden im Kampf. /spawn ist gesperrt.

[Cooldown]
Illunium » /spawn ist in 42 Minuten und 18 Sekunden wieder verfügbar.

[Erfolg]
Illunium » Zum Spawn teleportiert. Nächster /spawn in 60 Minuten.
```

Keine künstliche globale Chat-Nachricht über den Teleport; nur der ausführende Spieler erhält Feedback.

## 5. Umsetzung im Code

### 5.1 Empfohlener Modulzuschnitt

```text
src/main/java/de/illunium/custom/spawn/
├── SpawnModule.java            # FeatureModule, Events, Command, Cleanup
├── SpawnService.java           # Start/Cancel/Complete und eindeutiger State
├── SpawnSession.java           # UUID, Startposition, Zeitmarke, Task
├── SpawnCombatTag.java         # kleine interne 30-Sekunden-PvP-Frist
└── SpawnCooldownRepository.java # SQLite-Lese-/Schreibpfad, Migration
```

Die Namen sind Vorschläge; vorhandenen Code erweitern statt Klassen zu duplizieren. Eine eigene `spawn/`-Teststruktur ergänzt die bereits bestehenden JUnit-Tests.

### 5.2 Events, Scheduling, Threading

| Mechanismus | Vorgeschlagene API / Vorgehen |
| --- | --- |
| `/spawn` | Wie in v1.1 Paper `BasicCommand`; vorhandene öffentliche Permission |
| Bewegung | `PlayerMoveEvent`: **Position** statt Kopfrotation prüfen |
| Andere Teleports | `PlayerTeleportEvent`: Warmup abbrechen, eigenes Completion-Teleport sauber ausnehmen |
| Schaden am Spieler | `EntityDamageEvent`: nur tatsächlich relevante, nicht gecancelte Schadensereignisse |
| PvP-Schaden | `EntityDamageByEntityEvent`, inkl. eindeutig zuordenbarer Projektil-Schützen; Doppelverarbeitung vermeiden |
| Lebenszyklus | `PlayerQuitEvent`, `PlayerDeathEvent`, `PlayerChangedWorldEvent`, Plugin-Disable: Session/Tasks beenden |
| Countdown | Ein begrenzter Bukkit-Scheduler-Task pro aktivem Warmup oder ein gemeinsamer Task für aktive Sessions; keine 20-Tick-DB-Abfragen |
| Countdown-Feedback | Adventure Actionbar oder sparsame Chat-Meldungen |
| Persistenz | SQLite über bestehende `Database`-Queue; kein SQL auf dem Minecraft-Main-Thread |

**Event-Prioritäten/Sequenz:** Die tatsächliche Paper-26.3-Event-Reihenfolge und `EntityDamageEvent#getFinalDamage()` mit Client/Integrationstests validieren. `PlayerTeleportEvent` erbt das Bewegungs-Event; verhindere doppeltes Cancel. Schadensevents nicht selbst verändern oder unterdrücken. Angriffe mit blockiertem/abgebrochenem Schaden nicht als valide Combat-Tags zählen.

### 5.3 Konfiguration

Vorschlag im vorhandenen `config.yml`-Stil:

```yaml
modules:
  spawn: true

spawn:
  warmup-seconds: 10
  cooldown-minutes: 60
  combat-tag-seconds: 30
```

Für **v1.2 sind diese drei Werte verbindliche Produkt-Defaults**. Eine spätere Konfigurierbarkeit ist möglich, aber kein Grund, jetzt ein allgemeines Regeln-/Profile-System einzuführen. Falls konfigurierbar, Wertebereiche validieren und dokumentieren; ungültige Werte dürfen nicht stillschweigend zu `0` werden.

- Keine `mob-radius`-, `nearby-monsters`- oder `allow-combat-logging`-Option anlegen.
- `plugin.yml` und Commands nicht doppelt registrieren.
- Die bisherige Spawn-Permission beibehalten; keine stillschweigende OP-Ausnahme und kein neues Admin-Bypass-Feature.
- Cooldown wird nicht in `config.yml` gespeichert: per UUID in SQLite.

### 5.4 Integrationspunkte

- **Sit:** Ein Spieler kann `/spawn` beim Sitzen ausführen. Beim tatsächlich erfolgreichen Teleport werden Sitz-ArmorStand und Passenger-Verbindung ordentlich aufgelöst. Wird der Sitz bereits durch Eingabe beendet und dabei der Spieler bewegt, ist der Spawn-Warmup abzubrechen.
- **Vanish:** Verborgene OPs bleiben beim `/spawn` verborgen. Spawn darf keine Tablist-/Join-/Leave-Änderung auslösen und nicht automatisch `/unvanish` ausführen.
- **Marriage/Groups/Playtime:** Keine Änderung der Daten, Rechte, Tablist oder Herzpartikel nötig. Regelmäßige Updates dieser Module dürfen Spawn-Tasks nicht beeinflussen.
- **Weltspawn:** Nur das sichere Ziel aus v1.1 verwenden; `/setworldspawn` und native Welten bleiben die Source of Truth. Nicht mit persönlichem Respawn verwechseln.
- **Whitelist:** Keine Verbindung; dieser Patch darf die native Whitelist nicht anfassen.

## 6. Implementierungsphasen und Gates

| Phase | Inhalt | Gate / erwartetes Ergebnis |
| --- | --- | --- |
| **0 — Inventur** | Read-only `main`-/Arbeitsbaum-Prüfung, v1.1-Status prüfen, Testserver/DB-Backup, Baseline-Build | Kein bestehender Code überschrieben; aktuelles `SpawnModule` identifiziert oder Fehlen dokumentiert |
| **1 — Fachlogik** | Testbare Sessions, Zustandsmaschine, Zeitquellen, Cancel-Gründe, keine Monster-Suche | Unit-Tests für Start, Doppelcommand, 10 s, Cancel und Ablauffristen PASS |
| **2 — Combat-Tag** | Spieler-Schaden in beide Richtungen, Projektil-Zuordnung, 30 s und Erneuerung | PvP-Tests PASS; Mob-/Umweltschaden erzeugt **keinen** Tag |
| **3 — Cooldown** | SQLite-Migration, 60-Minuten-Deadline, Cache/DB-Futures, Error-Fallback | Stop/Start, Crash-/Fehler-Fälle und 60-Minuten-Grenzen PASS |
| **4 — Spawn & Events** | Basis-Spawn aus v1.1 herstellen/erweitern, Paper-Events, Warmup und Feedback, Sit-/Vanish-Integration | Echter Paper-Client: 10-s-Teleport nur bei ungestörtem Ablauf |
| **5 — Regression** | Groups, Marriage, Sit, Whitelist, Module-Enable/Disable, Doku | Gesamtbuild PASS; keine Regressionen oder Entity-/Task-Leaks |
| **6 — Release Candidate** | Staging-Deployment, vollständige Acceptance, Rollback-Test, SHA-256, Changelog, manuelle Freigabe | Erst nach erfolgreicher Abnahme bereit zur gesonderten Produktionsfreigabe |

### Phase 0 — Inventur

1. Aktuellen Branch, HEAD, Working Tree und Repo-spezifische Agent-Regeln lesen (`AGENTS.md`, `CLAUDE.md`, `.github/`, sofern vorhanden).
2. Verfügbare Paper-26.3-/Java-25-Versionen, Gradle-Wrapper, Plugin-Commands und vorherige Tests prüfen. Aktueller Repository-Pin hat Vorrang vor historischen Angaben.
3. Prüfen, ob v1.1 `SpawnModule` bereits erstellt wurde. Ist es vorhanden: Diff zum v1.1-P5-Vertrag festhalten; ist es nicht vorhanden: Grundlage im selben isolierten Feature schaffen.
4. Testumgebung und sicheren, rückspielbaren SQLite-/Config-Snapshot vorbereiten; **keine** Produktion verändern.
5. `./gradlew clean check build --no-daemon` vor Änderungen ausführen und Ergebnis dokumentieren.

### Phase 1 — State Machine / Warmup

- Zustände `IDLE`, `WARMING_UP`, `COMPLETING` und `COOLDOWN` definieren; ein Spieler hat höchstens eine Spawn-Session.
- Timer über injizierbare Clock prüfbar machen; kein Teleport vor 10 s und keine doppelte Completion nach Event-Races.
- `cancel(reason)` als **idempotente** Operation gestalten; Task/Referenzen einheitlich aufräumen.
- Bewegen, Schaden, Tod, Quit, Weltwechsel, fremden Teleport sowie Module-Disable in gezielten Tests abdecken.
- Beim Abbruch **keinen** Cooldown schreiben; erneutes `/spawn` erlaubt, wenn sonstige Sperren abgelaufen sind.

### Phase 2 — PvP-Combat-Tag

- Angreifer **und** Opfer beim wirksamen PvP-Treffer erfassen; Projektilschützen mit zuverlässiger API-Zuordnung.
- Vorhandenen Warmup bei PvP eines der beteiligten Spieler abbrechen.
- Durch erneute Treffer die laufende Frist auf 30 s ab diesem Treffer setzen.
- Alle anderen Schadensursachen dürfen **keinen** Combat-Tag erzeugen (können aber den Warmup abbrechen, wenn der Spieler Schaden nimmt).
- Quit entfernt den flüchtigen Tag; **kein** neues Anti-Combat-Log-Verhalten einführen.

### Phase 3 — Persistenter Cooldown

- Neue additive SQLite-Migration und separaten Repository-Zugriff erstellen, vorhandene Migrationen **nie nachträglich bearbeiten**.
- Cooldown nur bei erfolgreichem Teleport setzen und 60 Minuten ab **Erfolg** berechnen.
- Zeitgrenzen und Restart mit echter SQLite-Datei testen; parallele Befehle vor/nach DB-Commit dürfen keinen doppelten Teleport ermöglichen.
- Fehlgeschlagene DB-Persistenz kontrolliert behandeln; keine falschen Erfolgsmeldungen und keine zusätzliche Freischaltung bei ungeklärtem Status.

### Phase 4 — Paper-Integration

- `/spawn` mit bestehender Registration/Permission implementieren oder anpassen.
- Client-Countdown 10 s, Bewegungs-/Damage-Cancel, Teleport-Event-Guard und Zielprüfung verbinden.
- Reale Clientfälle: Wand, Sprung, Sneak ohne Bewegung, Kopfrotation, Fall, Lava, Zombie-Treffer, PvP-Schwert, PvP-Bogen, Disconnect.
- Sit / Vanish / geänderter Weltspawn / geblockter Zielort testen.

### Phase 5 — Qualität und Dokumentation

- Dokumentieren: neue Werte, Befehlsantworten, SQLite-Migration, Fehlerstrategie und **bewusst akzeptiertes Relog-Bypass-Risiko**.
- `README.md`, `docs/commands.md`, `docs/configuration.md`, `docs/architecture.md`, `docs/development.md`, `docs/operations.md`, `CHANGELOG.md` auf v1.2 anpassen.
- `MVP.md` bleibt historische v1.0-Basis; v1.1-/v1.2-Patchdokumente ersetzen nur die konkret genannten Stellen. Keine rückwirkende Umschreibung bisheriger Entscheidungen als ursprüngliche Anforderungen.
- Modul-Lifecycle, Sitz-Cleanup, Nametag/Tablist, Group-HEX, Marriage-Hearts, Vanish und Whitelist erneut smoke-testen.

### Phase 6 — Release-Kandidat und Gate

- `./gradlew clean check build --no-daemon`: PASS.
- Identifizierbares JAR (Commit-SHA, Java, Paper-API, SHA-256), keine Secrets im Artefakt.
- Testserver-Deploy, Start-/Shutdown-Smoke, echte Zwei-Spieler-PvP-Tests und Neustart-Abnahme.
- Rollback auf vorherige getestete JAR **und** Wiederherstellung der persistenten Daten/Schemaversion planen bzw. an separater DB-Kopie prüfen. Die DB wird nicht blind auf älteres Schema zurückgesetzt.
- Produktions-Deployment auf Forever-World **ausschließlich nach separater manueller Freigabe und Backup**.

## 7. Abnahme-Testmatrix

| ID | Testfall | Erwartung |
| --- | --- | --- |
| S01 | `/spawn` normal, 10 s unbewegt und ohne Schaden | Teleport **frühestens** nach 10 s; 60-min-Cooldown beginnt |
| S02 | `/spawn` nach 9 s bewegen (X/Z) | Sofortiger Abbruch; **kein** Cooldown |
| S03 | Springen/Fallen/Y-Änderung während Warmup | Abbruch; kein Cooldown |
| S04 | Nur Kamera drehen (Yaw/Pitch) | **Kein** Abbruch |
| S05 | Sneaken ohne Positionsänderung | **Kein** Abbruch |
| S06 | Zombie/Monster in der Nähe, **ohne** Schaden | `/spawn` funktioniert normal |
| S07 | Monster verursacht während Warmup Schaden | Warmup abgebrochen, **kein 30-s-PvP-Tag** |
| S08 | Feuer/Fall/Lava während Warmup verursacht Schaden | Warmup abgebrochen, kein PvP-Tag |
| S09 | Spieler A trifft B im PvP | **Beide** erhalten 30-s-Tag; laufender Warmup beider wird beendet |
| S10 | Bogen-/Projektiltreffer durch Spieler | Shooter und Opfer erhalten Tag; direkte Angriffe ebenfalls funktionieren |
| S11 | Neuer PvP-Treffer nach 20 s | Frist wieder 30 s ab neuem Treffer |
| S12 | `/spawn` unmittelbar nach PvP | Abgelehnt; korrekte Restzeit-Anzeige |
| S13 | `/spawn` nach 30 s ohne weiteren PvP-Treffer | Neuer Warmup möglich |
| S14 | `/spawn` während Warmup erneut | Kein zweiter Task und keine Verkürzung; Restzeit anzeigen |
| S15 | Schaden oder Bewegung kurz vor Abschluss | Abbruch hat Vorrang; **kein** doppelter/unerlaubter Teleport |
| S16 | Fremd-Teleport/Worldchange während Warmup | Warmup abgebrochen; kein Cooldown |
| S17 | Quit während Warmup, danach Rejoin | Keine Teleport-Session mehr; kein Cooldown aus dem abgebrochenen Warmup |
| S18 | Combat-Log (Quit im Combat-Tag) und Rejoin | **Keine** Strafe/Combat-Body; flüchtiger Tag ist weg (bewusster Tradeoff) |
| S19 | Erfolgreicher `/spawn`, direkt nochmal `/spawn` | Abgelehnt für **60 Minuten** |
| S20 | Server Stop/Start oder Restart nach erfolgreichem Teleport | Cooldown bleibt erhalten; Restzeit korrekt |
| S21 | Serverrestart bei laufendem, noch nicht abgeschlossenen Warmup | Kein Teleport und **kein** neuer Cooldown |
| S22 | Erfolgloser/gecancelter Teleport oder unsicherer Weltspawn | Kein Cooldown; klarer Fehler |
| S23 | `/setworldspawn` geändert | Neuer Spawnpunkt beim nächsten erfolgreichen `/spawn` verwendet |
| S24 | Spieler sitzt via `/sit` während `/spawn` | Keine Seat-Entity-Leaks; Aufstehen/Teleport korrekt |
| S25 | OP befindet sich im Vanish und nutzt `/spawn` | Keine versehentliche Sichtbarkeit/Tablist-Änderung |
| S26 | Mob in unmittelbarer Nähe beim Start/Abschluss | **Kein Monster-Radius-Check** |
| S27 | Ungültige Config oder Ausfall der SQLite-Persistenz | Sichtbarer Fehler; kein stiller Cooldown-Bypass oder doppelter TP |
| S28 | Doppel-Commands / gleichzeitige Listener-Ereignisse | Genau ein zulässiger Session-/Teleport-Ausgang; keine Tasks übrig |
| S29 | Plugin allein auf frischem Paper 26.3 | Startet ohne externe Minecraft-Plugin-Abhängigkeit |
| S30 | 60 Minuten seit erfolgreichem Teleport abgelaufen | `/spawn` wieder möglich, jeweils mit 10-s-Warmup |

### 7.1 Negativ-/Regressionsprüfungen

- **Kein** PvP-Combat-Tag bei Monster-/Umweltschaden, aber ein laufender Warmup bricht bei wirksamem Schaden ab.
- **Keine** Blockade wegen nahe stehender Monster; keine Mob-Erkennung im Spawn-Code.
- **Keine** Logout-Strafe/Combat-Body; combat-loggen bleibt möglich.
- **Kein** Cooldown nach Bewegung, Schaden oder anderem fehlgeschlagenen Warmup.
- **Kein** Cooldown vor einem tatsächlich erfolgreichen Spawn-Teleport.
- **Keine** Regression bei Whitelist, Sit, Groups, Marriage, Vanish oder Difficulty.
- **Keine** Beeinträchtigung von Vanilla-/Admin-Teleport-Commands außerhalb von `/spawn`.

## 8. Definition of Done — Patch v1.2

Der Patch gilt erst als fertig, wenn:

1. `/spawn` den nativen sicheren Overworld-Weltspawn weiterhin erreicht und auf der Forever-World als Komfortfunktion verfügbar bleibt.
2. Ein Warmup **10 Sekunden** dauert und bei Positionsänderung oder tatsächlich wirksamem Schaden zuverlässig abbricht.
3. Ein **30-Sekunden-PvP-Combat-Tag** das Starten von `/spawn` sperrt; gültiger neuer PvP-Schaden erneuert die Frist für Angreifer und Opfer.
4. Ein **60-Minuten-Cooldown** **nur nach erfolgreichem Teleport** beginnt und Neustarts überlebt.
5. Keine Monster-Nähe-Prüfung oder Combat-Logging-Bestrafung implementiert wurde.
6. Alle Testfälle einschließlich des echten Paper-26.3-Client-Tests und der DB-Restartprüfung bestanden wurden.
7. Die gesamte Plugin-JAR weiterhin ohne externe Plugin-Abhängigkeiten gebaut/geladen werden kann.
8. Dokumentation, Testnachweise, Changelog und Rollback-Schritte vorliegen.
9. Produktionsinstallation weiterhin ein **separates Human Approval Gate** bleibt.

## 9. Arbeits- und Sicherheitsregeln

Für alle tatsächlichen Änderungs-/Deploy-Schritte das bestehende Illunium-Arbeitsmuster beibehalten:

```text
READ-ONLY / ISTSTAND
→ Design / Candidate
→ Diff / Review
→ Implementieren
→ Unit-Tests
→ Paper-Staging / Gameplay-Tests
→ Dokumentation
→ Rollback-Check
→ Manuelle Freigabe
→ Production (nur nach Freigabe)
```

- Git Commit, Push, Merge und Production-Deploy bleiben getrennte Freigaben.
- Keine Secrets im Chat, Git, CI-Logs oder Build-Artefakten.
- Keine Änderung an gemeinsamen Jenics-/Edge-/WireGuard-/Firewall-/Proxmox-Grenzen.
- Keine Migration ohne zuvor geprüften Test-/Recovery-Pfad.
- Java-/Paper-Builds pinnen, keine ungetesteten Updates nebenbei.
- Für serverseitige Schritte Ausführungsort, WireGuard-Zustand, Read-only/Mutation und erwarteten Testnachweis nennen.

## 10. Technische Referenzen

- Bestehende Repo-Quellen: [`MVP.md`](./MVP.md), [`Implementation_Plan.md`](./Implementation_Plan.md), [`Patch_v1.1_Implementation_Plan.md`](./Patch_v1.1_Implementation_Plan.md)
- Paper-API-Dokumentation: https://docs.papermc.io/paper/dev/api/
- Paper-Javadocs (Version 26.3): https://jd.papermc.io/paper/26.3/
- Bewegungs- und Teleport-Events: `org.bukkit.event.player.PlayerMoveEvent`, `PlayerTeleportEvent`.
- Schadens-Events: `org.bukkit.event.entity.EntityDamageEvent`, `EntityDamageByEntityEvent`; projektilbedingte Spielerattribution nur nach getesteter API-Zuordnung.

---

**Produktentscheidung für v1.2:** `/spawn` bleibt als Community-Komfortfunktion bestehen. Die **10 Sekunden Warmup**, **60 Minuten Cooldown**, **Abbruch bei Bewegung/Schaden** und ein **30 Sekunden langer, nur auf `/spawn` wirkender PvP-Combat-Tag** verhindern den einfachen Sofort-Escape. Monster in der Nähe sind kein Ausschlussgrund. Das Projekt führt **kein** Anti-Combat-Logging ein.
