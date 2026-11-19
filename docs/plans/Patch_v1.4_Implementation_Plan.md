# IlluniumCustom — Patch v1.4 Implementation Plan

> **Stand:** 25.10.2026\
> **Status:** Code und automatisierte Prüfungen umgesetzt; echte Client-E2E und Produktionsfreigabe offen. Ausführungsnachweis: `docs/development.md`. Der folgende Plan dokumentiert die ursprünglichen Anforderungen; kein Produktionsdeployment.\
> **Repository:** https://github.com/illuuuuusion/illunium-custom\
> **Geprüfte Basis:** `main`, Git-Tree `e45c10531acc5a4be88cad0cf58db118d87d7e30` (read-only über GitHub abgeglichen)\
> **Runtime:** Paper / Minecraft Java **26.3**, Java **25**, weiterhin eine JAR ohne erforderliche Drittplugins\
> **Bezug:** `MVP.md`, `Implementation_Plan.md`, `Patch_v1.1_Implementation_Plan.md`, `Patch_v1.2_Implementation_Plan.md`, `Patch_v1.3_Implementation_Plan.md`\
> **Scope:** Illunium Forever-World; bestehende Vanilla-Whitelist, Whitelist-Staging, Vanish und Spawn-Regeln unverändert

## 0. Ziel und Produktentscheidungen

Patch v1.4 ergänzt drei vom Projekt gewünschte Funktionen:

1. **Gemeinsame Gruppen-Enderchest:** `/gec`, ab **vier Gruppenmitgliedern**, alle Gruppenrollen dürfen dieselbe Kiste verwenden.
2. **Gruppenchat:** `/g <Nachricht>` verschickt eine private Nachricht an aktuell online befindliche Mitglieder derselben Gruppe.
3. **Farbige Leaderboards:** vorhandene `/leaderboard`-Ausgaben werden übersichtlicher und farbiger, ohne die Statistikberechnung zu verändern.

### 0.1 Konkrete MVP-Regeln

| Frage | Festlegung für v1.4 |
| --- | --- |
| Mindestgröße für `/gec` | **4 eingetragene Gruppenmitglieder insgesamt**, inklusive Leader und Mods; Offline-Mitglieder zählen mit |
| Inventargröße | **27 Slots** wie bei einer persönlichen Enderchest |
| Geteilter Bestand | **Eine gemeinsame Kiste pro stabiler Gruppen-ID**, kein persönlicher Enderchest-Ersatz |
| Zugriff | Leader, Mod und Member gleichermaßen; keine Leader-exklusiven Slots |
| Zugriffsort | `/gec` überall in der Welt, solange der Spieler die Bedingungen erfüllt |
| Bei Rückfall auf 1–3 Mitglieder | **Nur Entnahme**, keine neuen Einlagerungen; kein automatisches Löschen vorhandener Items |
| Auflösung einer Gruppe | **Nicht erlaubt, solange die Gruppen-Kiste Items enthält**; Kiste zunächst leeren. Gilt auch für Admin-Disband |
| Gruppenchat | Nur aktuelle Gruppe, nur online, **nicht gespeichert**, keine Discord-/Lumen-Brücke |
| `/g` und Vanish | Versteckte OPs dürfen keine Gruppen-Nachricht senden, die ihre Anwesenheit offenbart; erst `/unvanish` |
| Leaderboards | Nur **Chat-Rendering** ändern; 11 Kategorien, Werte, Top 10, Pagination und OP-Ausschluss bleiben erhalten |
| Konfiguration | `groups.gec.enabled`, `groups.gec.min-members`, `groups.chat.enabled`; Farbschema zunächst fest, keine zusätzliche UI |

**Balancing-Hinweis:** Eine jederzeit abrufbare Gruppen-Kiste ist Remote-Lagerung und kann im Survival auch in Gefahrensituationen verwendet werden. Der Auftrag enthält **keinen** zusätzlichen Warmup, Cooldown oder Combat-Tag für `/gec`. Solche Einschränkungen nicht ungefragt in v1.4 einführen; separat diskutieren, falls die Community später Missbrauch feststellt.

### 0.2 Nicht-Ziele

- Keine physische Enderchest im Spawn, keine Übernahme oder Modifikation von `Player#getEnderChest()`.
- Keine 54-Slot-Doppelkiste, Upgrade-Stufen, Gruppenkasse, Claims, Economy, Itemversand oder Itemsynchronisation zwischen Servern.
- Keine Chathistorie, Gruppen-DMs an Offline-Mitglieder, Chat-Channel-Toggle oder Discord-Anbindung.
- Keine neue PvP-Statistik, keine Sidebar, Hologramme, Scoreboard-Manipulation, Rewards oder Statistik-Resets.
- Keine automatische Datenlöschung oder Migration der vorhandenen Gruppen-, Marriage- oder Leaderboard-Daten.

## 1. Verifizierter Repository-Iststand

Der folgende Abschnitt beschreibt den **gelesenen Quellcode auf `main`**, nicht den Status einer laufenden Forever-World-Instanz.

| Bereits vorhanden | Relevanz |
| --- | --- |
| `core/module/FeatureModule.java`, `Modules.java`, `ModuleContext.java` | Modul-Lifecycle; vorhandenes `GroupsModule` erweitern statt ein zweites Gruppen-Subsystem zu erzeugen |
| `groups/Group.java`, `GroupService.java`, `GroupRepository.java` | Stabile `long groupId`, UUID-Mitglieder, Rollen und gemeinsamer In-Memory-Gruppenstatus |
| `groups/GroupsModule.java`, `GroupCommand.java`, `GroupPolicy.java` | `/group`-Commands und Rechte; hier `/g` und `/gec` anbinden |
| `storage/Database.java` | Eine SQLite-Connection auf dediziertem DB-Executor, geordnete Transaktionen, bisher **V1–V4** |
| `core/VisibilityService.java` | Online-Sichtbarkeit bei Vanish, für `/g` relevant |
| `core/command/Chat.java` | Generische Meldungen bislang `info` grau, `ok` grün, `error` rot |
| `leaderboard/LeaderboardModule.java` | `/leaderboard` bzw. `/lb`; `execute()` baut bisher weitgehend **graue Textzeilen** |
| `leaderboard/LeaderboardDefinition.java` | Elf Kategorien samt Bezeichnungen und Maßeinheiten |
| `leaderboard/RankingSnapshot.java` | Sortierung/Ränge, OP-Ausschluss wird beim Anzeigen angewandt |
| `src/main/resources/config.yml` | Aktivierbare bestehende Module, `leaderboard.cache-seconds` |

**Tatsächlich auf `main` vorhanden:** v1.3-Features einschließlich `LeaderboardModule`, `WhitelistStagingModule`, `AutoVanishPolicy`, SpawnService und Datenbankmigration V4. `GroupChat` und `GroupChest` sind im geprüften Tree **noch nicht** vorhanden. Die v1.4-Implementierung muss vom zu diesem Zeitpunkt aktuellen Branch ausgehen und den Tree vor Änderungen erneut abgleichen.

## 2. P1 — Gruppen-Enderchest `/gec`

### 2.1 Nutzerverhalten

**Anwendungsfall:** Vier Freunde sind Mitglieder derselben Gruppe. Jeder kann `/gec` eingeben und unabhängig von Position/Dimension auf **denselben** 27-Slot-Inhalt zugreifen.

```text
/gec
```

- Mitgliedschaft wird **serverseitig bei jedem Aufruf** über die UUID geprüft; bloßer Besitz eines Gruppentags reicht nicht.
- 4+ Mitglieder: Inventar öffnen, volle Einlagerungs- und Entnahmerechte.
- 1–3 Mitglieder und noch nie freigeschaltete Kiste: Fehlermeldung „Gruppen-Enderchest ab 4 Mitgliedern verfügbar (aktuell X/4).“
- War die Gruppe bereits auf 4+ und enthält die Kiste noch Items, sind bei 1–3 Mitgliedern **ausschließlich Entnahmen** möglich. Es darf nichts hineingelegt werden. Ohne gespeicherte Items bleibt `/gec` gesperrt.
- Spieler ohne Gruppe und Spieler anderer Gruppen erhalten niemals Zugriff auf diese Kiste.
- Der **bestehende** `Player#getEnderChest()`-Bestand bleibt separat und unverändert.
- Der Titel kann die Gruppe enthalten, z. B. `Gruppenkiste • NOVA`; der Gruppentag darf in der Gruppenfarbe gerendert werden.

### 2.2 Gemeinsames Inventar und Gleichzeitigkeit

- Eine **einzige lebende `Inventory`-Instanz je aktiver Gruppen-ID** auf dem Paper-Main-Thread; **kein** separater Snapshot pro Betrachter. Damit sehen zwei gleichzeitig geöffnete GUIs die tatsächlichen Änderungen unmittelbar.
- Für die Kennzeichnung `InventoryHolder` mit `groupId` und eindeutiger Session-/Holder-Identität verwenden; **nicht** Inventartitel als Sicherheitsmerkmal. Paper empfiehlt Custom InventoryHolder für Plugin-Inventare.
- `Bukkit.createInventory(holder, 27, Component)` verwenden. Inventar und `ItemStack`-Objekte ausschließlich auf dem Server-Hauptthread erstellen, lesen und ändern.
- Asynchronen DB-Ladevorgang **pro Gruppe deduplizieren**: Während der erste Zugriff lädt, warten weitere `/gec`-Aufrufe auf dieselbe Future. Erst nach erfolgreichem Laden auf dem Main-Thread öffnen.
- Nach dem asynchronen Laden noch einmal prüfen: Spieler online, noch Mitglied **derselben Gruppen-ID**, Gruppengröße/Zugriffsmodus aktuell und Plugin aktiviert. Nicht auf veraltete Mitgliedschaft vertrauen.
- `InventoryClickEvent`, `InventoryDragEvent`, `InventoryCloseEvent`, Quit, Modul-Disable und relevante Gruppenänderungen berücksichtigen; eine Verschiebung nicht mitten im Click-Event durch unsichere Inventory-Operationen erzwingen, nötige View-Änderungen auf den nächsten Tick verschieben.
- **Entnahme-Modus:** sämtliche Wege zum Einlagern verhindern (normaler Klick, Shift-Klick aus Spielerinventar, Drag, Hotbar-/Offhand-Swap, Double-Click/Collect, Creative-Aktionen und andere relevante Click-Typen); Entnahme zu erlaubten Spieler-Slots ermöglichen. Dieser Modus benötigt echte Client-E2E-Tests.
- Bei Austritt/Kick/Disband aller betroffenen Zuschauer: offene GUIs sicher schließen; anschließend Zugriff und Modus erneut validieren. Bei Rückfall unter vier Mitglieder sofort Einlagen blockieren, auch wenn das GUI bereits offen war.

### 2.3 Dauerhafte Speicherung

**Neue Migration:** `src/main/resources/db/migrations/V5__group_chests.sql` und V5 an **das Ende** von `Database.MIGRATIONS` anhängen; vorhandene V1–V4 niemals ändern.

Vorgesehenes Datenmodell (Bezeichner können bei der Implementierung präzisiert werden):

```sql
CREATE TABLE group_chests (
    group_id INTEGER PRIMARY KEY REFERENCES groups(id) ON DELETE CASCADE,
    contents BLOB NOT NULL,
    occupied_slots INTEGER NOT NULL DEFAULT 0 CHECK (occupied_slots BETWEEN 0 AND 27),
    revision INTEGER NOT NULL DEFAULT 0,
    saved_at INTEGER NOT NULL
);

-- Zusätzlicher DB-Schutz gegen unbeabsichtigtes Löschen gefüllter Kisten.
CREATE TRIGGER protect_nonempty_group_chest
BEFORE DELETE ON groups
WHEN EXISTS (
    SELECT 1 FROM group_chests
    WHERE group_id = OLD.id AND occupied_slots > 0
)
BEGIN
    SELECT RAISE(ABORT, 'group chest is not empty');
END;
```

Diese SQL-Skizze ist **kein fertiger produktionsreifer Migrations-Commit**. Die Implementierung muss Migration, Tests und ggf. Fehlermeldungsabbildung an den konkreten DB-Wrapper anpassen.

- Das Inventarformat enthält **exakt 27 Slots**, explizite Formatversion, pro Slot Leer-/Item-Kennung und Item-Payload; keine Java-Objektserialisierung ohne Versionsschema.
- Vorzugsweise Paper/Bukkit-NBT-Itemserialisierung (`ItemStack#serializeAsBytes` / `deserializeBytes`) **auf dem Hauptthread**, damit Namen, Verzauberungen, Data Components und spezielle Itemdaten erhalten bleiben; nur unveränderliche `byte[]` an den DB-Thread übergeben. API-Verfügbarkeit gegen den tatsächlich gepinnten Paper-Build prüfen.
- Niemals „bei Deserialisierungsfehler leere Kiste erstellen“. **Fail closed**: Kiste für die Gruppe sperren, Fehler loggen, Backup-/Recovery-Anweisung anzeigen; keine Daten überschreiben.
- Datenbank speichert das komplette Inventar atomar als Blob plus `occupied_slots` und `revision`; nicht jeden Slot in beliebiger Reihenfolge separat überschreiben.
- Speichern nach der tatsächlichen Änderung als **Main-Thread-Snapshot**, nicht blind innerhalb des `InventoryClickEvent`; Änderungen sinnvoll bündeln/debouncen, aber bei letztem Schließen und Shutdown final flushen.
- Die bestehende Single-Thread-DB-Queue garantiert die Reihenfolge der eingereihten Transaktionen. Zusätzlich Revisionsnummer/Generation pro Kiste verwenden, damit verspätete Lade-/Speicherantworten keinen neueren Main-Thread-Zustand überschreiben.
- Erst nachdem die DB den letzten Snapshot bestätigt hat, bei keiner aktiven Session den Cache freigeben. Während `onDisable()` alle Inventare sauber schließen, letzte Snapshots einreihen und die bestehende DB-Close-Queue abarbeiten lassen.
- Kein Promise absoluter Crash-Konsistenz zwischen Vanilla-Spielerinventar und separat gespeicherter Gruppen-Kiste: harte Prozess-/Host-Abstürze können wie bei anderen virtuellen Inventaren kurz zuvor ausgeführte Itemtransfers verlieren oder duplizieren. Änderungen zeitnah checkpointen; regelmäßig Backups, Restore-Test und begrenzte Failure-Injection-Tests ausführen.

### 2.4 Gruppengröße, Austritte und Auflösung

Hier besteht ein **konkreter Eingriffspunkt** im jetzigen Code: `GroupService.apply(...)` meldet der Appearance-Komponente nur betroffene Spieler. Bei einer Änderung von 4 auf 3 erhalten unveränderte Gruppenmitglieder dort **keinen** zuverlässigen allgemeinen Änderungs-Callback. Daher:

1. Einen eigenen **Gruppen-Lifecycle-Observer** (z. B. `GroupChangeListener` für `oldGroup, newGroup`) nach **jeder committeten Gruppenmutation** ergänzen. Appearance-Updates bleiben unverändert; Chest hört auf den neuen Observer.
2. Vor dem normalen `/group disband` und dem Adminpfad `/ic group disband` den Chest-Zustand **transaktionssicher** prüfen: Nicht erlaubt, wenn eine Kiste noch Items enthält. Keine Ausnahmen, die Daten still löschen.
3. Bei offenen GUIs oder unbestätigten Dirty-Snapshots nicht auf einen möglicherweise veralteten DB-Leerstand verlassen. Disband in diesem Fall vorerst ablehnen und geordnetes Schließen/Flush verlangen; anschließend erneut versuchen.
4. Sind alle 27 Slots leer und der Zustand sicher committed, darf die Gruppe aufgelöst werden. Die leere Chest-Zeile darf dann per FK-Cascade verschwinden. So kann auch eine eventuell später wiederverwendete numerische Gruppen-ID **keine Items der alten Gruppe übernehmen**.
5. Wenn die Mitgliederzahl auf 3 fällt, behalten die verbleibenden Mitglieder den Entnahme-Zugang. So kann niemand sein Eigentum dauerhaft einschließen. Bei erneuten 4+ Mitgliedern wieder volle Nutzung.
6. Eine aus der Gruppe entfernte Person verliert auch dann den Zugriff, wenn sie die Kiste vorher geöffnet hat. Eine bereits abgeschlossene Entnahme wird nicht automatisch zurückgeholt.

Eine Kiste ist gemeinschaftlicher Besitz ohne individuelle Besitzrechte. In v1.4 werden absichtliche Entnahmen anderer Gruppenmitglieder **nicht** automatisch rückgängig gemacht.

### 2.5 Technische Integration

Vorgeschlagene neue Klassen im bestehenden `groups`-Paket:

```text
src/main/java/de/illunium/custom/groups/
├── GroupChestCommand.java
├── GroupChestService.java
├── GroupChestRepository.java
├── GroupChestHolder.java
├── GroupChestInventoryListener.java
├── GroupChestCodec.java
├── GroupChangeListener.java
└── GroupChatCommand.java
```

- Die Feature-Lifecycle-Verantwortung bleibt bei `GroupsModule`; `/gec` und `/g` dort registrieren. Das globale `Modules.create()` muss nicht um zwei neue unabhängige Fachmodule erweitert werden.
- `GroupChestService` nutzt existierende `GroupService.groupOf(UUID)`/Gruppen-ID und `context.database()`; `GroupService` muss keine SQL-Inhalte des Chat-Features kennen.
- Die Disband-Sperre ist eine **ausdrückliche Integration** im bestehenden Gruppen-Domainservice, geschützt zusätzlich durch den DB-Trigger. Änderungen an `GroupPolicy` sind für die gewöhnlichen Rollenrechte nicht notwendig: `Leader`, `Mod`, `Member` haben die gleichen Chest-Rechte.
- Optionaler Diagnosebefehl `/ic gec status <gruppe>` kann Mitgliedszahl, belegte Slots, Dirty-Zustand und Revision ausgeben; keine rohe Itemdaten-Ausgabe ins Log.
- Keine externen Plugins, keine neuen Serverdienste.

## 3. P2 — Gruppenchat `/g <Nachricht>`

### 3.1 Benutzerverhalten

```text
/g Wir treffen uns bei der Base.
```

Beispielausgabe (mit Adventure-Komponenten, nicht als farbcodierter Rohstring):

```text
[G] [NOVA] renko: Wir treffen uns bei der Base.
```

- Nur Mitglieder einer Gruppe dürfen senden; kein Gruppenchat für gruppenlose Spieler.
- Jeder online befindliche Spieler derselben Gruppe erhält die Nachricht, einschließlich Absender. Welt/Dimension spielen keine Rolle.
- Alle drei Rollen haben dieselben Chat-Rechte. Nachrichten gehen **nicht** in den öffentlichen Chat und nicht an andere Gruppen.
- Keine Offline-Zustellung oder Verlaufsspeicherung; spätestens mit Serverneustart sind ausstehende Nachrichten weg.
- Ohne Text: verständliche `/g <Nachricht>`-Hilfe. Leere bzw. reine Leerzeichen-Nachrichten ablehnen.
- Nur Text, kein Benutzereingabe-Parsing als MiniMessage, Legacy-`§` oder sonstiges Chat-Markup. Maximal 256 Unicode-Codepoints nach Trim; durch den Server übermittelte Zeichen serverseitig sicher in `Component.text(...)` einsetzen.
- Keine neue Permission-Pflicht neben `illuniumcustom.use` und existierender Gruppenmitgliedschaft.
- Optional kurzer **Anti-Spam-Abstand von 1 Sekunde pro Sender** (konfigurierbar/bei Bedarf deaktivierbar); keine globale Gruppen-Sperre.
- `VisibilityService` beachten: Versteckter OP darf **nicht** per `/g` versehentlich öffentlich als online erkennbar werden. Senden im Vanish ablehnen; empfangen durch versteckte Gruppenmitglieder ist zulässig, ohne sie anderen gegenüber aufzudecken. Nachrichten verwenden keine Online-Mitgliederliste.
- Mitgliederlisten **pro Sendevorgang neu** aus der bestätigten Gruppen-ID lesen, nicht beim Login einfrieren. Später beigetretene Mitglieder erhalten spätere Nachrichten; gekickte Mitglieder erhalten keine weiteren.

### 3.2 Implementierung

- `GroupChatCommand implements BasicCommand`; registriert in `GroupsModule.enable(...)` mit `registerCommand("g", ...)`.
- Nur `Player` als Absender; Konsole erhält Usage-Fehler. Keine serverseitige globale Chat-Intercept-Logik.
- Eingabeargumente in der Originalreihenfolge mit Leerzeichen verknüpfen; nicht ungewollt alle Mehrfachleerzeichen zerstören, soweit Paper die Eingabe bereits tokenisiert. Falls exakte Zeichenfolge benötigt wird, Command-API passend modellieren.
- Gruppentag-Farbe aus `Group.color()` übernehmen; Prefix `[G]` beispielsweise violett, Spielername weiß, Nachricht hellgrau. Kein Erzwingen von Pseudorängen oder Kick-Messages im Chat.
- Für `Player#sendMessage(Component)` alle Online-Empfänger auf dem Main-Thread kontaktieren; keine DB-Transaktion notwendig.
- Beim deaktivierten `groups.chat.enabled` ist der Command nicht registriert.

## 4. P3 — Farbige Leaderboards

### 4.1 Gewünschte Darstellung

Bisher erzeugt `LeaderboardModule.execute(...)` praktisch alle Ranglistenzeilen via `Chat.info(...)`, also grau. **Nur die Darstellung ändern**: Der Service, der Werte liest/sortiert, bleibt unangetastet.

Visualisierung als Minecraft-Chat (Beispielwerte, keine echten Spielerstatistiken):

```text
Illunium » Leaderboard: Diamanterze abgebaut
   #1  renko        482 Erze
   #2  diaaar       391 Erze
   #3  Jaevy        286 Erze
   #4  minigalla    254 Erze
   ...
Dein Rang: #8 — 114 Erze                 Seite 1/2
```

**Farbschema als Adventure-Komponenten:**

| Teil | Stil |
| --- | --- |
| Überschrift / `Illunium` | Aqua/Türkis (`#4DD0E1`) + Goldakzent |
| Kategoriename | thematischer Akzent (z. B. Diamanten Türkis, Spielzeit Violett, XP Grün), lesbar auf dunklem Chat |
| Rang `#1` | Gold (`#FFD54F`) |
| Rang `#2` | Silber/Hellgrau (`#BFC7D5`) |
| Rang `#3` | Bronze (`#CD9B6D`) |
| Rang `#4` und niedriger | gedämpftes Grau (`#AEB7C2`) |
| Spielernamen | Weiß/Hellgrau; eigene Zeile optional dezent hervorgehoben |
| Statistikwerte | Hellgrün (`#9EE6A4`) oder kategoriebezogener Akzent |
| Historie/Cache-Hinweise und Pagination | Grau, möglichst wenig visuelles Rauschen |
| Fehler und nicht verfügbare Kategorie | Bestehender roter/neutraler Stil |

- `/leaderboard` und `/lb` geben übersichtliche, farbige **anklickbare Kategorien** aus; bestehende `ClickEvent.runCommand`-Funktion erhalten.
- `/leaderboard <kategorie> [seite]` behält **Top 10 pro Seite**, Pagination, eigene Platzierung und Sortierung bei.
- Ränge werden **nicht** aufgrund der Anzeige neu berechnet. Gleichstände gemäß bestehender `RankingSnapshot`-Logik erhalten.
- OP-Ausschluss, Vanish-Filter, Offline-Spieler und die Herkunftshinweise („Vanilla-Historie“, „ab v1.3“) bleiben exakt wie bisher.
- Keine Modifikation an `LeaderboardRepository`, `VanillaStatisticReader`, `CustomMetricTracker`, Datenbankschema oder Cache-Ablauf.
- Kein Regenbogen-Spam und keine zusätzlichen Chat-Nachrichten während der normalen Spielzeit: Farben erscheinen nur auf bewussten `/leaderboard`-Aufruf.
- Namen, Zahlen und Rang **immer textuell anzeigen**, sodass Rankings auch bei abweichenden Clientfarben/Farbsehschwäche verständlich bleiben.

### 4.2 Code-Ort

- Präsentationsfunktionen aus `LeaderboardModule.execute(...)` extrahieren, zum Beispiel `LeaderboardMessageFormatter` mit `header`, `entry`, `ownRank`, `navigation`, `overviewEntry`.
- Reine Formatter-Funktionen mit `Component` und `TextColor`/`NamedTextColor` implementieren; keinen Bedarf für neue Libraries.
- Die existierende Klasse `Chat` bleibt für allgemeine Pluginmeldungen neutral. **Nicht** die globale `Chat.info()`-Farbe ändern, da dadurch ungewollt Gruppen-, Spawn- und Whitelist-Meldungen verändert würden.
- Adventure-Komponenten mit Textern behalten; keine ungeprüften Rohstrings als Format-/MiniMessage-Code aus Spielerprofilen ausführen.

## 5. Konfiguration

Im bestehenden `src/main/resources/config.yml` additiv unter `groups:` ergänzen; vorhandene Keys beibehalten:

```yaml
groups:
  gec:
    enabled: true
    min-members: 4
    slots: 27  # in v1.4 fest und nicht über Config veränderbar
  chat:
    enabled: true
    message-max-length: 256
    cooldown-seconds: 1

# leaderboard.cache-seconds bleibt unverändert;
# der neue Leaderboard-Stil gilt automatisch bei eingeschaltetem Modul.
```

**Validierungsregeln:** `groups.gec.min-members` muss in dieser Produktversion **genau 4** sein; `slots` **genau 27** (künftige Upgrades sind ein neuer Patch). `message-max-length` 1–256; `cooldown-seconds` 0–10. Ungültige Konfiguration darf nicht stillschweigend zu einer anderen Regel führen. Die vier zusätzlichen Config-Werte bleiben optional mit obigen Defaults, damit bestehende Installationen kompatibel starten.

## 6. Phasenplan

### Phase 0 — Baseline und Sicherheitsnetz

1. `git status`, Regeln/Workflows, `main`-HEAD, bestehende `MVP.md`, `Patch_v1.1/1.2/1.3`, `CHANGELOG.md`, `README.md`, `docs/` prüfen.
2. Bestehende Tests ausführen: `./gradlew clean check build --no-daemon` mit Java 25; Ergebnisse dokumentieren. Nicht behaupten, dass sie erfolgreich liefen, ohne sie tatsächlich ausgeführt zu haben.
3. Backup der Staging-`data.db` und Weltdaten erstellen; niemals die Produktions-DB für Entwicklung verwenden.
4. Änderung als isolierten Patch-Branch vorbereiten, nicht direkt produktiv ausrollen.

**Gate 0:** Baseline nachvollziehbar, Plugin startet auf isoliertem Paper 26.3, DB-Version V4 nachweisbar.

### Phase 1 — Schema V5 und Inventar-Codec

1. Neue `V5__group_chests.sql` erstellen, Migration anhängen, bestehende Migrationen unverändert lassen.
2. Einen versionierten Codec für 27 Slot-Items auf Basis der unterstützten Item-NBT-Serialisierung bauen.
3. SQL-Repository mit Laden, Insert-on-first-use, Revision, Snapshot-Update, Slotanzahl und Fehlererkennung ergänzen.
4. Crash-/Migrationstest: V4 → V5 erhält alle Gruppen-, Whitelist-, Leaderboard- und Spawn-Daten.

**Gate 1:** Metadaten/Spezialitems überstehen Save/Load und Neustart; unlesbare Payload sperrt statt zu löschen.

### Phase 2 — `/gec` mit geteilter Session

1. `GroupChestHolder`, `GroupChestService`, Lade-Future-Deduplizierung, Main-Thread-Inventory und Command einführen.
2. Rechte und Vierergrenze implementieren; mehrere Viewer öffnen dieselbe Instanz.
3. Inventory-Events, `InventoryCloseEvent`, Quit, Dirty/Revisionen und periodische Speicherung abdecken.
4. Rückfall auf 1–3 Mitglieder: Entnahme-only inkl. aller Input-Wege und live geöffneten Sessions.

**Gate 2:** Kein Zugriff durch Fremde, gleiche Inhalte bei parallelen Spielern, keine unerlaubte Einlagerung unter vier Mitgliedern.

### Phase 3 — Gruppen-Lifecycle und Disband-Schutz

1. Eigenen Post-Commit-Observer für `GroupService.apply(...)` ergänzen.
2. Entfernte Mitglieder schließen und verlieren Zugriff; auch offline Member zählen weiterhin zur Vierergrenze.
3. Gruppenauflösung in `confirm` und `adminDisband` bei gefüllter/ungespeicherter Kiste verhindern; DB-Trigger absichern.
4. Kiste nach Leerung aufräumen, Gruppen-ID-Wiederverwendung testen.

**Gate 3:** Keine heimliche Item-Löschung bei Leave, Kick, Disband oder Admin-Kommandos.

### Phase 4 — Gruppenchat

1. `/g <Nachricht>` als eigenständigen Command innerhalb `GroupsModule` registrieren.
2. Aktuelle UUID-Mitgliedschaft pro Nachricht, `VisibilityService`, 256-Zeichen-Grenze, Nachrichtenformat und optionalen Cooldown umsetzen.
3. Tests für Gruppe A/B, Offline-Mitglieder, Austritt/Kick, OP im Vanish und Konsolenaufruf schreiben.

**Gate 4:** Kein Mitgliedschaftsleck und keine Nachricht an andere Gruppen oder öffentliche Chats.

### Phase 5 — Leaderboard-Design

1. `LeaderboardMessageFormatter` extrahieren.
2. Farbige Kategorien, Top 3, andere Ränge, Eigene-Platzierung, Link-/Page-Controls gestalten.
3. Renderer-Tests mit Adventure-Komponenten erstellen.
4. Alle elf vorhandenen Kategorien und die unveränderte Datenlogik kontrollieren.

**Gate 5:** Leaderboard deutlich lesbarer; Werte, OP-Filter, Sortierung und Cache unverändert.

### Phase 6 — Integration, Doku und Release-Kandidat

1. `README.md`, `docs/commands.md`, `docs/configuration.md`, `docs/architecture.md`, `docs/operations.md`, `CHANGELOG.md` ergänzen.
2. Java-25-Gradle-Build, Unit-Tests, SQLite-Migrationstests, Paper-Smoke-Test und **E2E mit mindestens vier echten Clients** durchführen.
3. Item-Spezialfälle und Inventar-Input-Matrix manuell prüfen, ebenso Disconnect, Restart und testweisen Crash/Recovery.
4. Kein automatisches Deployment in die Forever-World; Test- und Produktionsfreigabe trennen.

**Gate 6:** Alle Pflichtfälle grün; erst danach kontrollierter Rollout mit DB-Backup und Rollback-Plan.

## 7. Konkrete Abnahmetests

### 7.1 Gruppen-Enderchest

- [ ] Eine Gruppe mit drei Mitgliedern kann **keine leere** `/gec` öffnen.
- [ ] Das vierte Mitglied (auch wenn offline) schaltet die geteilte Kiste frei.
- [ ] Zwei bis vier gleichzeitige Zuschauer sehen dieselben 27 Slots live, ohne getrennte Inventare.
- [ ] Leader, Mod, Member können jeweils Items einlagern/entnehmen.
- [ ] Andere Gruppen und gruppenlose Spieler kommen nicht an fremde Items.
- [ ] `/ec`/persönliche Vanilla-Enderchest bleibt unangetastet.
- [ ] Bei Kick oder Austritt sinkt die Gruppe auf drei: bisherige Kiste nur noch **Entnahme**, neue Einlagen über Klick, Shift, Drag, Hotbar, Double-Click und Creative abgelehnt.
- [ ] Bei Wiederanstieg auf vier ist normales Einlagern wieder möglich.
- [ ] Entferntes Mitglied kann nicht über offenes GUI weiterarbeiten.
- [ ] Unveränderte verbleibende Mitglieder erhalten den geänderten Kistenmodus trotz bestehender Appearance-Callbacks.
- [ ] `/group disband` und `/ic group disband` sind bei gefüllter Kiste nicht erfolgreich.
- [ ] Nach vollständiger Leerung funktioniert Disband; eine spätere neue Gruppe kann keine alten Items erben.
- [ ] Inventar persistiert über Stop/Start und Rejoin inklusive verzauberter/custom Items und Datacomponents.
- [ ] Simultane Entnahmen, Shift-Clicks, Klick-Spam, Abbruch beim Laden und Last-Viewer-Close erzeugen bei **sauberem** Shutdown weder stille Datenverluste noch doppelte Stacks.
- [ ] Fehlerhafte Itemdaten sperren die Kiste und überschreiben keinen Speicherstand.
- [ ] Fehlgeschlagenes DB-Speichern erzeugt einen sichtbaren Fehler und verhindert riskante Cache-Freigabe/Disband.

### 7.2 Gruppenchat

- [ ] `/g Hallo Gruppe` erreicht alle online befindlichen Mitglieder derselben Gruppe und sonst niemanden.
- [ ] Nachricht wird nicht im normalen Chat gesendet und nicht persistiert.
- [ ] Gruppentag übernimmt die per `/group color` konfigurierte Farbe einschließlich `#RRGGBB`.
- [ ] Sender ohne Gruppe, Konsole oder unsichtbarer OP können nicht ungewollt senden.
- [ ] Gruppenwechsel, Kick und Leave ändern den Adressatenkreis sofort.
- [ ] Falsche/fehlende Nachricht, Zeichenlimit und Cooldown liefern verständliche Fehler.
- [ ] Keinerlei MiniMessage-/Farbcode-Injection durch Spielereingaben.

### 7.3 Leaderboards

- [ ] `/leaderboard` und `/lb` zeigen alle elf Kategorien, farbige Namen und weiterhin klickbare Befehle.
- [ ] Gold/Silber/Bronze für die ersten drei; spätere Ränge weniger auffällig; Werte lesbar.
- [ ] `diamonds`, `placed`, `mined`, `playtime`, `distance`, `mobs`, `deaths`, `totems`, `spawn`, `fishing`, `peaklevel` weiterhin funktional.
- [ ] Top 10, Seitenwechsel, Gleichstände, eigene Platzierung, Offline-Daten und OP-Ausschluss unverändert.
- [ ] `/leaderboard`-Aufruf verändert keine Zahlen, Datenbankzeilen oder Cache-Bedingungen.
- [ ] Keine Veränderung des normalen Chat-, Spawn- oder Whitelist-Nachrichtenstils.

### 7.4 Regression und Betrieb

- [ ] V4 → V5 Migration hält alle existierenden Daten intakt; zweiter Start migriert nicht erneut.
- [ ] `./gradlew clean check build --no-daemon` erfolgreich (Java 25, gepinnte Paper-API).
- [ ] Keine Pflichtabhängigkeit von LuckPerms, PlaceholderAPI, ProtocolLib oder einer weiteren Plugin-JAR.
- [ ] Modul- und Feature-Deaktivierung deaktivieren ihre Commands/Listener sauber und zerstören keine Daten.
- [ ] Whitelist-Staging/Launch, Auto-Vanish, `/spawn`-Cooldown, `/sit`, Marriage und Gruppenrechte bleiben funktional.
- [ ] Vor Deployment: aktuelle DB-/Welt-Backups, Hash der neuen JAR, Rollback getesteter Stand.

## 8. Deployment / Rollback / Dokumentation

- **Keine automatische Produktionsfreigabe.** Erst isolierten Paper-26.3-Testserver und Clienttests verwenden.
- Vor v1.4: vollständiges Backup von `plugins/IlluniumCustom/data.db` (inklusive SQLite-WAL/SHM konsistent sichern) und Welt; Server dafür sauber stoppen oder SQLite-Backup-API verwenden.
- Die Migration nach V5 wird nur vorwärts ausgeführt. Die alte v1.3-JAR versteht Schema V5 laut aktuellem `Database.migrate` **nicht** und verweigert den Start; für einen Rollback muss das **passende V4-Datenbank-Backup** zusammen mit der alten JAR wiederhergestellt werden. Keine manuelle Löschung von Migrationseinträgen.
- Nach Rollout Einlogg-/Gruppentest, Kisten-Funktion, Gruppenchat, Leaderboard-Anzeige und Serverlog prüfen; bei Fehlern serverseitig geordnet stoppen.
- Kein Push, Merge, Release oder Server-Restart durch den Plan selbst.

## 9. Definition of Done

Patch v1.4 ist fertig, wenn **alle drei** neuen Anforderungen (gemeinsame `/gec` ab vier Mitgliedern mit sicherer Speicherung und Item-Rückgewinnung unter vier; privater `/g`-Chat; farbige, funktional unveränderte Leaderboards) in der vorhandenen modularen Pluginarchitektur umgesetzt, automatisiert und mit echten Clients getestet, dokumentiert und separat für die Forever-World freigegeben sind.

---

**Technische Referenzen:** [PaperMC Custom InventoryHolders](https://docs.papermc.io/paper/dev/custom-inventory-holder/), [Paper Inventories](https://docs.papermc.io/paper/dev/api/inventories/), [Paper API](https://jd.papermc.io/paper/26.3/). Maßgeblich für implementierte Klassen und Kommandos bleibt der beim Implementierungsstart erneut geprüfte Repository-Stand.
