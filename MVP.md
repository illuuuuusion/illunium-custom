# IlluniumCustom — MVP v1.0

> **Status:** verbindlicher Funktionsentwurf zur Umsetzung, Stand 20.10.2026  
> **Repository:** https://github.com/illuuuuusion/illunium-custom  
> **Ziel:** Paper / Minecraft Java Edition **26.3**, **Java 25**  
> **Einsatz:** Illunium Forever-World; unabhängig vom separaten Kingdoms-Plugin  
> **Begleitdokument:** [`Implementation_Plan.md`](./Implementation_Plan.md)

## 1. Ziel und Grundsätze

`IlluniumCustom` ist **ein eigenständiges, leicht erweiterbares Paper-Plugin** für die Forever-World. Es bündelt kleine Community-Funktionen sowie eine schmale Ergänzung zur servereigenen Whitelist. Das MVP muss unmittelbar spielbar, verständlich bedienbar, stabil und ohne weitere Minecraft-Plugins einsetzbar sein.

**Enthaltene Module:**

1. Custom Recipes — Quartz-Block zu Nether Quartz.
2. Groups — frei erstellbare Gruppen mit Tag, Farbe, Hierarchie und Verwaltung.
3. Playtime — Spielzeit für eigene und andere Accounts einsehen.
4. Marriage — gegenseitige Partnerschaften mit Online-Anzeige in der Tablist.
5. Whitelist Guard — **native Minecraft-Whitelist**; dauerhaft aktiv, geschützte Staff-Accounts.
6. Sit — Hinsetzen per `/sit` an geeigneten Standorten.

### 1.1 Nicht verhandelbare Randbedingungen

- **Paper 26.3**, Java-Toolchain/Runtime **25**; für Test/Produktion wird ein **konkreter getesteter Paper-Build gepinnt**.
- **Eine JAR**, ein Plugin-Lebenszyklus, intern klar getrennte Module; neue Features sollen ohne Änderungen in fremden Feature-Modulen ergänzt werden können.
- **Keine Laufzeitabhängigkeit von anderen Minecraft-Plugins** (kein LuckPerms, PlaceholderAPI, ProtocolLib, EssentialsX usw.). Als JAR eingebundene Java-Bibliotheken sind zulässig.
- **Keine Verbindung zu Lumen, Discord, Websites, Datenbanken anderer Anwendungen oder externen Services für die regulären Spielfunktionen.** Native Minecraft-Profilauflösung bei `/whitelist add` bleibt Sache des Servers.
- Alle Spielerbeziehungen und persistente Identitäten werden per **UUID** geführt; angezeigte Minecraft-Namen sind nicht die Identität.
- Keine verpflichtenden Client-Mods und **kein verpflichtendes Resourcepack**.
- Administration erfolgt über native Minecraft-Kommandos, eigene Plugin-Commands und Konsole — keine GUI-Pflicht.
- Forever-World und bestehende Gameserver-/Infrastruktur-Sicherheitsgrenzen bleiben unangetastet.

### 1.2 Umfang und Priorität

Die sechs Funktionen sind MVP-Pflicht. Qualität und vorhersehbares Verhalten gehen vor Zusatzfunktionen. Keine Economy, kein Claim-System, kein Plugin-übergreifendes Synchronisieren, keine Proxy-Integration.

## 2. Custom Recipes

### 2.1 Quartz-Rezept

| Eingabe | Ausgabe | Typ |
| --- | --- | --- |
| `1 × QUARTZ_BLOCK` | `4 × QUARTZ` (Nether Quartz) | Shapeless |

**Regeln:**

- Funktioniert im 2×2- und 3×3-Crafting-Feld.
- Nur der normale Quartz-Block (`QUARTZ_BLOCK`) zählt; keine Säulen, gemeißelten oder glatten Varianten.
- Vanilla-Rezepte werden nicht entfernt oder verändert.
- Jeder Spieler darf das Rezept benutzen; keine Gruppen- oder Admin-Permission nötig.
- Das eigene Rezept hat einen eindeutigen Namespaced Key, damit Registrierungen konfliktarm bleiben.
- Das Modul stellt eine einfache Erweiterungsstelle für spätere zusätzliche Rezepte bereit.

**Abnahme:** Exakt vier Quartz pro Block, keine Rückverwandlung unerwünschter Quartz-Varianten, kein mehrfaches Registrieren nach Pluginstart.

## 3. Groups

Gruppen dienen **ausschließlich der sozialen Organisation und Sichtbarkeit**. Es gibt keine Claims, gemeinsamen Inventare, Teleports, Gruppenkassen oder PvP-Regeln.

### 3.1 Datenmodell und Validierung

| Feld | Regel |
| --- | --- |
| Anzeigename der Gruppe | 1–16 Zeichen, Regex `^[A-Za-z0-9_-]{1,16}$` |
| Tag | 1–5 Zeichen, Regex `^[A-Za-z0-9_-]{1,5}$` |
| Farbe | Eine erlaubte Minecraft-Standardtextfarbe; standardmäßig `WHITE` |
| Interne ID | Stabile Gruppen-ID, unabhängig von Name und Tag |
| Leader | Exakt **ein** Mitglied |
| Mods | Null bis beliebig viele |
| Members | Beliebig viele |
| Mitgliedschaft | Höchstens **eine Gruppe** pro Minecraft-UUID |
| Eindeutigkeit | Gruppenname und Tag, jeweils **ohne Unterscheidung von Groß-/Kleinschreibung**, serverweit eindeutig |

Namen und Tags enthalten **keine Leerzeichen, Farb-, Formatierungs- oder Unicode-Steuerzeichen**. Änderungen werden serverseitig validiert und atomar gespeichert. Gruppenname und Tag dürfen unabhängig voneinander umbenannt werden.

### 3.2 Rollen und Rechte

Die Gruppenrollen `LEADER`, `MOD`, `MEMBER` sind **interne Fachrollen**, **keine** Bukkit-/LuckPerms-Serverrollen.

| Aktion | Leader | Mod | Member |
| --- | :---: | :---: | :---: |
| Gruppeninformationen/Mitglieder einsehen | Ja | Ja | Ja |
| Spieler einladen / Einladung zurückziehen | Ja | Ja | Nein |
| Reguläre Members kicken | Ja | Ja | Nein |
| Mods kicken | Ja | Nein | Nein |
| Name, Tag oder Farbe verändern | Ja | Nein | Nein |
| Member zu Mod befördern | Ja | Nein | Nein |
| Mod zu Member zurückstufen | Ja | Nein | Nein |
| Leader übertragen | Ja | Nein | Nein |
| Gruppe auflösen | Ja | Nein | Nein |
| Gruppe verlassen | Nur durch Transfer/Auflösung | Ja | Ja |

**Zusatzregeln:**

- Nur der Leader kann andere Moderatoren verwalten; niemand kann den Leader kicken oder demoten.
- Ein Transfer ist nur an ein existierendes Mitglied möglich; nach erfolgreichem Transfer ist der bisherige Leader ein `MOD` und es gibt weiterhin genau einen Leader.
- `transfer` und `disband` benötigen eine **explizite Bestätigung** durch den ausführenden Leader (max. 30 Sekunden gültig).
- Ein Leader kann die Gruppe nicht ohne Transfer verlassen. Bei Auflösung werden Mitgliedschaften und Tag-Darstellungen entfernt.
- Serverseitige Notfallkorrekturen können durch eng begrenzte Admin-Befehle erfolgen; diese sind **keine** regulären Gruppenrechte.

### 3.3 Gruppen-Commands

| Command | Effekt | Erforderliche Rolle |
| --- | --- | --- |
| `/group create <name> <tag>` | Eigene Gruppe erstellen | Gruppenlos |
| `/group list` | Gruppen auflisten | Jeder |
| `/group info [name]` | Gruppeninfos; ohne Argument eigene Gruppe | Jeder |
| `/group members` | Mitglieder und Rollen anzeigen | Gruppenmitglied |
| `/group invite <player>` | Einladung senden | Leader, Mod |
| `/group uninvite <player>` | Offene Einladung zurückziehen | Leader, Mod |
| `/group accept <group>` | Einladung dieser Gruppe annehmen | Eingeladener |
| `/group deny <group>` | Einladung ablehnen | Eingeladener |
| `/group kick <player>` | Erlaubtes Mitglied entfernen | Leader, Mod (nur Member) |
| `/group promote <player>` | Member zu Mod | Leader |
| `/group demote <player>` | Mod zu Member | Leader |
| `/group rename <name>` | Gruppennamen ändern | Leader |
| `/group tag <tag>` | Gruppentag ändern | Leader |
| `/group color <color>` | Gruppenfarbe ändern | Leader |
| `/group transfer <player>` | Leader-Transfer anstoßen | Leader |
| `/group disband` | Auflösung anstoßen | Leader |
| `/group confirm` | Pending Transfer/Auflösung bestätigen | Betroffener Leader |
| `/group cancel` | Pending Transfer/Auflösung abbrechen | Betroffener Leader |
| `/group leave` | Gruppe verlassen | Mod, Member |

Begrenzte Admin-/Konsolencommands (Permission `illuniumcustom.admin`):

- `/ic group inspect <group>` — Diagnosedaten und Rollen prüfen.
- `/ic group setleader <group> <player>` — verwaisten/falsch zugewiesenen Leader korrigieren; Ziel muss Mitglied sein.
- `/ic group disband <group>` — Gruppe im Notfall auflösen.

Keine separate Gruppen-GUI; alle Commands erhalten Tab-Completion und konkrete Fehlermeldungen.

### 3.4 Einladungen

- Einladung nur an **online befindliche** Spieler; Ziel muss gruppenlos sein.
- Gültigkeitsdauer: **exakt fünf Minuten** ab Einladung.
- Mehrere Gruppen können denselben Spieler einladen; Annahme nennt ausdrücklich die Gruppe.
- Beim Beitritt erlöschen alle anderen offenen Einladungen dieses Spielers.
- Annahme prüft **erneut**: Frist, Gruppenzugehörigkeit, Zielgruppe noch vorhanden, einladender Berechtigter zum Einladungszeitpunkt und Ziel noch gruppenlos.
- Ausstehende Einladungen sind flüchtig und verfallen spätestens beim Plugin-Restart; keine Datenbank-Persistenz erforderlich.

### 3.5 Nametag und Tablist

**Nametag über dem Spieler:** `§Farbe[TAG] §rSpielername` (sinngemäß mit Components gerendert).  
**Tablist:** `[TAG] Spielername <optional Marriage-Symbol>`.

Beispiel:

```text
Über dem Spieler:  [NOVA] Steve
Tablist:           [NOVA] Steve ❤
Spieler ohne Gruppe: Alex 💔
```

- Tag und Klammern erscheinen in der gewählten Gruppenfarbe; der Vanilla-Spielername wird nicht umbenannt.
- Keine Gruppenzugehörigkeit bedeutet kein Gruppen-Prefix.
- Tags erscheinen für **alle Zuschauer**, nicht nur für Gruppenmitglieder.
- Änderungen, Join/Quit, Kick, Transfer, Auflösung und Pluginstart aktualisieren die Anzeigen korrekt.
- Das `appearance`-Modul ist **alleiniger Eigentümer** der von diesem Plugin verwalteten Team-/Tab-Anzeige. Groups und Marriage liefern lediglich Darstellungsdaten.
- Kompatibilität mit anderen Plugins, die selbst Scoreboard-Teams/Tablisten verändern, ist nicht garantiert; sie sind keine Abhängigkeit und gehören nicht zur MVP-Abnahme.

## 4. Playtime

### 4.1 Commands und Verhalten

| Command | Effekt | Berechtigung |
| --- | --- | --- |
| `/playtime` | Eigene Spielzeit anzeigen | Jeder Spieler |
| `/playtime <player>` | Spielzeit eines bekannten Spielers anzeigen, auch offline | Jeder Spieler |

- Erfasst wird die **Spielzeit seit erstmaliger Aktivierung des Plugins**, nicht rückwirkend historische Vanilla-Statistik.
- Aktive Sessionzeiten werden auch vor dem nächsten Persistenzcheckpoint korrekt angezeigt.
- Zeiten werden intern sekundengenau erfasst und als Tage/Stunden/Minuten ausgegeben.
- Offline-Abfrage über gespeicherte UUID und zuletzt bekannte Spielernamen; bei Mehrdeutigkeit/unbekanntem Namen klare Fehlermeldung.
- Speicherung bei Quit, planmäßiger Checkpoint mindestens alle 60 Sekunden und bei geordnetem Shutdown.
- Nach abruptem Crash kann maximal die noch nicht gespeicherte laufende Sessiondauer seit letztem Checkpoint fehlen; keine Doppelzählung nach Neustart.
- Kein Leaderboard, keine AFK-Erkennung und keine externe Profil-/Statistik-API.

## 5. Marriage

### 5.1 Regeln

- Eine UUID kann maximal **eine** aktive Partnerschaft haben.
- Antrag nur an online befindliche Spieler; keine Selbstheirat.
- Anträge gelten **fünf Minuten**, werden nicht über Neustarts gespeichert.
- Heirat erfordert aktive Zustimmung des Gegenübers.
- Eine Scheidung ist einseitig möglich, erfordert vor Ausführung eine Bestätigung und entfernt die Partnerschaft für beide.
- Kein Teleport, keine Items, keine Economy, keine Claims und keine Gameplay-Vorteile.
- Partnerschaften bleiben bei Quit, Namenswechsel und Restart bestehen.

### 5.2 Commands

| Command | Effekt |
| --- | --- |
| `/marry <player>` | Antrag versenden |
| `/marry accept` | Offenen Antrag annehmen |
| `/marry deny` | Antrag ablehnen |
| `/marry info [player]` | Beziehungsstatus ansehen |
| `/divorce` | Scheidung anstoßen |
| `/divorce confirm` | Scheidung bestätigen |
| `/divorce cancel` | Scheidung abbrechen |

Es ist pro Empfänger höchstens ein aktiver Antrag möglich; ein neuer Antrag überschreibt ihn **nicht** stillschweigend.

### 5.3 Heart in der Tablist

- **Jeder Spieler sieht den Marriage-Status aller anderen Spieler** in seiner Tablist.
- Verheiratet + Partner online: rotes **`❤`**.
- Verheiratet + Partner offline: **`💔`** bzw. ein getestetes, ohne Resourcepack sichtbar unterscheidbares Ersatzzeichen wie `✕❤`, falls `💔` im Vanilla-Client nicht gerendert wird.
- Unverheiratet: kein Zeichen.
- Das Marriage-Zeichen steht **nur in der Tablist**, nicht über dem Kopf.
- Auf Join/Quit beider Partner, Heirat, Scheidung und Reconnect wird zeitnah aktualisiert.
- Die Aktualisierung darf **keinen Gruppen-Prefix duplizieren oder löschen**.

## 6. Native Whitelist und Staff-Schutz

### 6.1 Produktentscheidung

**Minecrafts native Whitelist ist und bleibt die einzige Quelle der Wahrheit.** Die frühere Idee einer zweiten `Managed List` mit `/access add`, `enableall`, `disableall` und einer gesonderten aktiv/inaktiv geführten Spielerliste **entfällt**.

Spieler werden **direkt mit Vanilla** aufgenommen:

```text
/whitelist add <MinecraftName>
/whitelist remove <MinecraftName>
/whitelist list
/whitelist reload
```

Das funktioniert grundsätzlich auch für Accounts, die **noch nie gejoint** sind, sofern Paper/Minecraft das Online-Profil erfolgreich auflösen kann. Das Plugin stellt **keine eigene Kontoauflösung** zur Verfügung.

**Serverkonfiguration:**

```properties
online-mode=true
white-list=true
enforce-whitelist=true
```

**Wichtig für den Launch:** `/whitelist add` berechtigt den Spieler **sofort** zum Beitritt. Wer die Namen bereits vor der Öffnung eintragen will, muss den **öffentlichen Minecraft-Zugriff bis zum Launch gesperrt halten** (Server noch aus bzw. externe Erreichbarkeit nicht freigeschaltet). Die Whitelist ersetzt kein separates zeitgesteuertes Launch-Gate.

### 6.2 Verantwortung des Plugins

Das kleine Modul `whitelistguard` übernimmt nur:

1. Prüfung bei Start, dass native Whitelist und `enforce-whitelist` eingeschaltet sind; andernfalls aktivieren, warnend loggen und erneut prüfen.
2. Konfigurationsgetriebene **geschützte Staff-UUIDs** (Server-Mod-/Admin-Accounts, **nicht** Gruppen-Moderatoren); die zugehörigen bestätigten Accountprofile müssen in der nativen Whitelist stehen.
3. Bei Startup und periodischer Konsistenzprüfung (z. B. alle 30 Sekunden): versehentlich entfernte Staff-Einträge in der **nativen** Whitelist wiederherstellen und die Schutzparameter prüfen. Keine anderen Einträge verändern.
4. Bei ungültiger Staff-Konfiguration deutlich **fail-closed/warnend** reagieren; keine ungeprüften oder frei erfundenen UUIDs freischalten.
5. Status-/Diagnose-Logs ohne private Tokens und ohne Chatspam.

**Nicht Aufgabe des Plugins:** die Vanilla-Befehle ersetzen, eine zweite Spieler-Zugriffsdatenbank pflegen, `/whitelist off` als beabsichtigtes Feature anbieten, Vanilla-Whitelist-Einträge automatisch löschen oder OP-Rechte verleihen.

**Admin-/Security-Grenze:** Ein vollständig privilegierter Operator bzw. die Serverkonsole kann native Sicherheitsfunktionen verändern. Der Guard repariert Fehlkonfigurationen, ist aber **kein Schutz vor einem böswilligen Serveradministrator**. Host/Proxy-Port-Freigaben werden weiterhin außerhalb dieses Plugins verwaltet.

### 6.3 Abnahme

- Nicht whitelisted: Join abgelehnt; eingetragener Spieler: Join zugelassen.
- Neuer Vanilla-Whitelist-Eintrag funktioniert ohne vorherigen Join.
- Spielereinträge bleiben im nativen `whitelist.json` und werden durch das Plugin **nicht dupliziert**.
- Staff-Einträge überstehen Restart und werden nach versehentlichem Entfernen wieder in die native Whitelist aufgenommen.
- Gruppen-Mods erhalten keine Staff-Ausnahme.
- `white-list=true` und `enforce-whitelist=true` bleiben betriebliche Grundvorgabe.

## 7. Sit

- `/sit`: Spieler setzt sich an einer **geeigneten, sicheren Position mit tragfähiger Fläche** hin.
- Erneutes `/sit` oder `/unsit`: Spieler steht auf.
- Keine Berechtigung außer normalem Spielerzugang.
- Nicht möglich während Fliegen, Schwimmen, Reiten, freiem Fall, in Flüssigkeiten oder an unsicherem Standort.
- Sitzstatus endet bei Aufstehen/Bewegung, Schaden, Tod, Teleport, Weltwechsel, Quit, Server-/Plugin-Shutdown.
- Benutzte interne Sitz-Entities werden **nur vom eigenen Plugin** erzeugt, markiert und zuverlässig entsorgt; auch nach Absturz werden verwaiste Sitze bei Laden betroffener Chunks beseitigt.
- Keine Persistenz von Sitzpositionen, keine Resourcepacks und keine NMS-/Minecraft-Server-Forks.
- Erfolgreiche Abnahme mit mehreren gleichzeitig sitzenden Spielern, Chunks entladen/neu laden und wiederholtem `/sit`.

## 8. Architektur und Erweiterbarkeit

### 8.1 Ein Plugin, getrennte Module

```text
IlluniumCustom (1 JAR)
├── bootstrap / core
│   ├── config + validation
│   ├── command registration
│   ├── permissions
│   ├── lifecycle + logging
│   └── module contract / module registry
├── appearance       # einziges Render-Modul für Teams und Tablist
├── recipes
├── groups
├── playtime
├── marriage
├── whitelistguard
├── sit
└── storage          # SQLite + Migrationen + Repositories
```

**Erweiterungsvertrag:**

- Jedes Feature definiert seine Commands, Listener, Services und ggf. Datenschnittstellen innerhalb seines eigenen Modulpakets.
- Bootstrap registriert Module über eine kleine explizite Registry; keine reflektionsbasierte Plugin-Suche oder schwergewichtiges Framework.
- Feature-Module kommunizieren nur über schlanke, wohldefinierte Interfaces bzw. interne Events, nicht über gegenseitige Datenbank-Tabellen oder Commands.
- Neue Module können hinzugefügt oder per Konfiguration deaktiviert werden, ohne andere Module funktional umbauen zu müssen.
- Gemeinsame Darstellung (Gruppen + Marriage), UUID-/Namensauflösung, Speicherung, Task-Scheduling und Fehlerbehandlung werden zentral bereitgestellt.
- Fehlstarts kritischer persistenter Module sind sichtbar; kein scheinbar erfolgreicher Start mit still verworfenen Daten.
- Die Architektur ist **erweiterbar, nicht mikroservice-orientiert**: keine Netzwerkschnittstelle, kein eigener Prozess pro Feature.

### 8.2 Speicherung

- **SQLite**, lokal als Plugin-Datei; benötigter JDBC-Treiber wird in die JAR eingebunden (kein separater DB-Server).
- Daten: `groups`, `group_memberships`, `marriages`, `playtime` sowie Datenbankschema-Version; **keine zweite Whitelist-Datenbank**.
- Transaktionen und Integritätsbedingungen für eindeutige Tags/Namen, eine Mitgliedschaft pro Spieler, genau einen Leader und disjunkte Marriage-Paare.
- Schema-Migrationen von Anfang an versioniert; SQLite-Backups und Wiederherstellung dokumentiert.
- Asynchrone DB-I/O ohne Bukkit-/Paper-API-Aufrufe aus asynchronen Threads; Verzeichnis-/Dateirechte dem Gameserver-User entsprechend.
- Für Staff-Schutz genügt eine kleine konfigurierbare Liste geprüfter UUID/Name-Paare; keine persistente parallele Spielerzugangsliste.

### 8.3 Permissions

| Permission | Verwendung | Standard |
| --- | --- | --- |
| `illuniumcustom.admin` | Notfall-/Diagnose-Commands | `op` |
| `illuniumcustom.use` | Öffentliche Plugin-Kommandos (group/playtime/marry/sit) | `true` |

Interne Gruppenrollen werden **nicht** als Bukkit-Permissions abgebildet; sie werden auf Fachaktion und Zielrolle geprüft. Eigene Permission-Nodes ersetzen keine globalen Serverberechtigungen.

## 9. Nicht-Ziele

- Keine Lumen-/Discord-Anbindung oder API.
- Kein Multi-Server-Abgleich von Gruppen, Marriage, Playtime oder Whitelist.
- Keine GUI, Web-App, GUI-Frameworks oder PlaceholderAPI.
- Keine Gruppen-Claims, Gruppenrechte an Blöcken, Economy, Gruppenteleports oder Gruppenchat.
- Kein Marriage-Gameplay-Bonus und kein Playtime-Leaderboard.
- Kein Anti-Cheat, Anti-Xray, WorldGuard-/LuckPerms-Ersatz.
- Keine parallele Whitelist-Verwaltung oder Massentoggle-Logik (`/access`/`enableall`/`disableall`).
- Keine Unterstützung für andere Minecraft-Versionen, Folia oder Proxy-Stacks im MVP.

## 10. Definition of Done / Akzeptanz

**MVP `DONE` nur wenn:**

- [ ] JAR mit Java 25 reproduzierbar baut, auf fest gepinntem Paper 26.3 sauber startet und stoppt; ohne andere Plugins.
- [ ] Quartz-Rezept produziert ausschließlich `4 × QUARTZ` aus `1 × QUARTZ_BLOCK`.
- [ ] Gruppen erstellen/anzeigen/umbenennen/taggen/färben/einladen/kicken/promoten/demoten/transferieren/verlassen/auflösen mit rollenbasierten Positiv-/Negativtests.
- [ ] Namen/Tags halten ASCII-Regex, Längen, Case-insensitive-Eindeutigkeit und UUID-Mitgliedschaft ein.
- [ ] Einladung läuft nach fünf Minuten ab; Rechte und Zielzustand werden beim Akzeptieren erneut geprüft.
- [ ] Nametag und Tablist zeigen Prefix ohne doppelte Darstellung; Marriage-Herz ist für alle sichtbar und folgt Partner-Online-Status.
- [ ] `/playtime [player]` zeigt eigene/fremde inklusive Offline-Zeiten; Zeit ist nach Restart erhalten.
- [ ] Heiratsantrag, Annahme, Ablehnung und Scheidung funktionieren, inklusive Paar-Eindeutigkeit.
- [ ] Vanilla `/whitelist add` funktioniert vor dem ersten Join; keine zweite Whitelist angelegt.
- [ ] Native Whitelist bleibt eingeschaltet; Staff-Accounts werden anhand geprüfter Profile geschützt.
- [ ] `/sit` und `/unsit` sind stabil; keine verwaisten Sitz-Entities nach Exit/Restart/Chunk-Reload.
- [ ] SQLite-Migration, Crash-/Restart-/Restore-Szenarien und Fehlerlogs sind getestet.
- [ ] Tests auf dem **separaten Testserver**, anschließend manuelle Freigabe; keine ungetestete automatische Produktionseinspielung.
- [ ] `README.md` enthält Setup, Commands, Permissions, Config, Backup/Restore; neue Features können gemäß Modulvertrag hinzugefügt werden.

## 11. Bewusst dokumentierte Annahmen

1. **Native Whitelist statt zweiter Zugriffsliste** ist die letzte Produktentscheidung. Die ursprünglichen Bulk-Toggle-Commands entfallen; ein Launch-Gate wird betrieblich über Nicht-Erreichbarkeit vor Freigabe gelöst.
2. **Staff** meint hier über Konfiguration festgelegte, gesondert berechtigte Minecraft-Mod-/Admin-Accounts — **nicht** die gruppeninternen `MOD`-Rollen.
3. `💔` ist ein **Darstellungsziel**; der tatsächliche Vanilla-Client-Font wird praktisch getestet. Eine sichtbar unterscheidbare Fallback-Darstellung ist zulässig, ein Resourcepack nicht erforderlich.
4. In diesem Repository befindet sich zum Zeitpunkt der Dokumenterstellung **noch kein Quellcode**; die Spezifikation ist die Grundlage, nicht die Beschreibung bereits implementierten Verhaltens.

## 12. Referenzen

- [PaperMC — Project setup (Paper-API/Java 25)](https://docs.papermc.io/paper/dev/project-setup/)
- [PaperMC — Developer API](https://docs.papermc.io/paper/dev/api/)
- [PaperMC — GitHub](https://github.com/PaperMC/Paper)
