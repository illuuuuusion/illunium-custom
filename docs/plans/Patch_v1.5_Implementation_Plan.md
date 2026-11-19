# IlluniumCustom — Patch v1.5 Implementation Plan

> **Stand:** 19.11.2026  
> **Status:** Umsetzungsplan, **nicht implementiert / nicht deployed**  
> **Repository:** https://github.com/illuuuuusion/illunium-custom  
> **Geprüfte Baseline:** `main`, Git-Tree `6bb213a49fca4ae254958cc95492fe23f049309a` (read-only geprüft am 19.11.2026)  
> **Ziel:** Illunium Forever-World · Minecraft Java / Paper **26.3** · Java **25** · eine eigenständige JAR ohne weitere Pflichtplugins  
> **Vorgänger:** `MVP.md`, `Implementation_Plan.md`, `Patch_v1.1_Implementation_Plan.md` bis `Patch_v1.4_Implementation_Plan.md`  
> **Änderungsumfang:** Beacons · Chat-Emotes · zehn Spielzeit-Namensfarben · fünf zusätzliche Rezepte

## 0. Ziel und verbindlicher Umfang

Patch v1.5 ergänzt Komfort- und Baufeatures, ohne aus der Forever-World einen PvP-/Economy-Server zu machen.

| ID | Feature | Sollzustand |
|---|---|---|
| B1 | Beacon-Strahl | Spieler können den **von ihnen selbst platzierten** Beacon mit einem Command umschalten, **wenn sie direkt auf dem Beacon stehen**. Effekt und Strahl sind unabhängig. |
| B2 | Überdachte Beacons | Ein korrekt gebauter Beacon kann seine ausgewählten Effekte auch unter einer undurchsichtigen Decke geben, ohne sichtbaren Strahl; **Stufe-II-Effekte bei vollständiger Pyramide** funktionieren wie in Vanilla. |
| E1 | Chat-Emotes | Discord-artige Kürzel wie `:skull:` werden in lesbare Unicode-Symbole umgewandelt; Tab bietet passende Kürzel an, soweit mit dem Vanilla-Client möglich. |
| P1 | Spielzeit-Namensfarbe | **10 feste Stufen** basierend auf kumulierter vorhandener `/playtime`-Zeit, sichtbar in Tab und über dem Kopf; Gruppen-Tag und Marriage-Herzen bleiben erhalten. |
| R1 | Rezepte | Fünf neue Vanilla-Material-Rezepte einschließlich eines Ofenrezepts. |

**Einschränkungen und Qualitätsregeln:** Keine Client-Mods, kein verpflichtendes Resourcepack, keine Drittplugins, keine Lumen-/Discord-Anbindung, keine neuen Spielerrollen, keine Änderungen an Whitelist/Vanish/Spawn/Leaderboard-Messung. Bestehende SQLite-Daten und Weltblöcke bleiben erhalten. **Vor jeder technischen Zusage für B1 muss ein Machbarkeits-Gate bestanden werden** (§ 3); eine reine API-Funktion „Strahl aus, Effekt an“ ist nicht als gesichert anzunehmen.

### 0.1 Umsetzung in bestehenden Modulen

- `src/main/java/de/illunium/custom/recipes/RecipesModule.java`: bisher nur Quartz-Rezept (`List<CraftingRecipe>`). Für ein zusätzliches Furnace-Rezept muss die Registry auf einen passenden gemeinsamen `Recipe`-Typ erweitert werden.
- `appearance/AppearanceService.java`: schreibt gegenwärtig Gruppen-Prefix und Marriage-Suffix. **Nur dieser Service** darf künftig die Spieler-Namensfarbe komponieren; kein zweiter Scoreboard-Schreiber.
- `playtime/PlaytimeModule.java` + `PlaytimeReadService.java`: SQLite-Zeit seit Einführung des Plugins; Checkpoint alle 60 s. Über die vorhandene Read-Schnittstelle aufbauen, nicht auf Vanilla-Spielzeit ausweichen.
- `groups/GroupChatCommand.java`: private `/g`-Nachrichten existieren; Emote-Formatierung soll dort ebenfalls greifen, ohne Gruppen-/Vanish-Schutz zu verändern.
- `core/module/Modules.java`: zentrale Registrierung. Neue Module für Beacons und Emotes, optional ein Modul für Namensfortschritt, jeweils mit eigenem Enable/Disable.
- `storage/Database.java`: additive Migrationen V1–V5 vorhanden. Alte Migrationen **nicht ändern**; für neue relationale Persistenz bei Bedarf **V6** ergänzen.
- `src/main/resources/config.yml`: Feature-Toggles und jeweils getrennte Settings ergänzen; unbekannte Module werden weiterhin validiert.
- `build.gradle.kts`: Paper API derzeit **`26.3.build.207-stable`** gepinnt. Diese Version zunächst als Basis testen. Neue API-Methoden zuerst **gegen genau diesen Build** kompilieren; spätere Upgrades sind eine separate, begründete Änderung.

### 0.2 Verbindlicher Sicherheits- und Entwicklungsablauf

1. Vor der Implementierung aktuelle Repo-Regeln und `git status` prüfen; die obige Baseline ist ein Snapshot, kein Ersatz für eine erneute Codeinspektion.
2. Separater Git-Branch und **isolierter Paper-Testserver** mit Wegwerf-Welt; keine Tests auf Forever-World.
3. Betroffene Funktionen mit automatischen Tests prüfen; Vanilla-Client-E2E ist bei Beacon/Tab/Emote **Pflicht**.
4. Kein Commit/Push/Merge/Release/Produktivdeployment ohne gesonderte Freigabe. CI auf `main` erzeugt bereits ein `latest`-Pre-Release; ein Push kann deshalb einen neuen Entwicklungsdownload auslösen.
5. Vor Migration/Deployment Backup von Plugin-DB **und** Weltdaten; Rollback-Kompatibilität ausdrücklich testen.

---

## 1. Beacon — Produktvertrag

### 1.1 Commands und Rechte

| Command | Zweck | Voraussetzung |
|---|---|---|
| `/beacon beam off` | Eigenen Beacon-Strahl unsichtbar schalten | Spieler, **Owner**, direkt auf dem Beacon stehend |
| `/beacon beam on` | Eigenen Beacon-Strahl wieder anzeigen (sofern freie Sicht nach oben) | Wie oben |
| `/beacon beam toggle` | Aktuellen Wunschzustand umschalten | Wie oben |
| `/beacon info` | Eigentümer, Strahlwunsch, aktuelle Pyramidenstufe und Effektstatus | Direkt auf Beacon; Owner für Aktionen, Leserechte für alle auf diesem Beacon |
| `/ic beacon setowner <spieler>` | **Optionaler Admin-Recovery-Command**, um einen bereits vor v1.5 platzierten Beacon einem UUID-Account zuzuweisen | OP/Admin mit `illuniumcustom.admin`, steht selbst auf dem Beacon; Aktion auditieren |

**Standortdefinition:** Der Beacon-Block befindet sich **unmittelbar unter den Füßen** des ausführenden Spielers (derselbe X/Z-Block, Y−1). Kein Suchradius, kein Remote-Befehl, kein „von daneben“, keine Zielauswahl per Blick. Der Befehl ohne gültigen Beacon zeigt eine präzise Fehlermeldung.

**Eigentümer:** Der **tatsächliche Platzierer** wird bei einem erfolgreichen, nicht gecancelten `BlockPlaceEvent` anhand seiner UUID festgehalten. Ein Beacon-Item im Inventar überträgt keinen früheren Owner. OP-Rechte oder eine Gruppenrolle verleihen **keinen automatischen Zugriff auf fremde Beacon-Toggles**. Bereits vor v1.5 gesetzte Beacons haben keine glaubhaft rekonstruierbare Owner-UUID und sind ohne administrativen Recovery-Vorgang **nicht claimbar**. UUID statt Spielername; Namewechsel ohne Rechteverlust.

**Persistenz:** Owner-UUID und gewünschter Strahlzustand (`beamVisible=true` als Default) möglichst direkt im Beacon-`TileState`/`PersistentDataContainer` speichern, nach Chunk-Unload und Serverneustart wiederherstellen. Bei Abbau geht die blockgebundene Information verloren; bei erneutem Platzieren wird der neue Platzierer Owner. Keine neuen Blockzustandsmarker auf anderen Blocks setzen. Bei beschädigter/fehlender PDC keine unkontrollierte Freischaltung.

### 1.2 Beacons unter einer Decke

- Ein **gültiger Beacon-Pyramidenaufbau** (Stufe 1–4) bleibt Pflicht; ohne Pyramide keine Effekte.
- Undurchsichtige Blöcke über dem Beacon dürfen die Wirkung **nicht** mehr verhindern. Der originale sichtbare Strahl bleibt unter dem Dach unsichtbar.
- Effektwahl über das bekannte **Vanilla-Beacon-Menü**, soweit dieses im überdachten Zustand die Auswahl tatsächlich zuverlässig speichert. Das Plugin erzeugt **keine kostenlosen Effekte** und umgeht keine Stufen-/Zahlungsbedingungen.
- Gleiche Primäreffekte, Reichweite, Dauer und Aktualisierungslogik wie Vanilla. **Pyramidenstufe 4**: Primäreffekt II, wenn derselbe Primäreffekt als Sekundäreffekt gewählt wurde; alternativ normale Regeneration als Sekundäreffekt. Bei Stufe 1–3 **kein** Stufe-II-Boost.
- Ändert sich die Pyramide oder wird der Beacon abgebaut, wird die neue Situation beim nächsten Update berücksichtigt. Effekte laufen höchstens ihre verbleibende normale Wirkzeit aus.
- Beacons funktionieren in allen unterstützten Dimensionen, soweit dort die Vanilla-Block- und Pyramidenregeln gelten. Nur geladene Chunks verarbeiten; kein globales Force-Load.
- Keine Effekte bei anderen Beacon-Blöcken, keinen doppelten Stacking-Boost und keine unerwartete Löschung stärkeren/anderweitig gewährten Trankeffekts.
- Berechnungen serverseitig, ohne globale Partikel/Broadcasts, um Vanish-Positionen nicht offenzulegen.

### 1.3 Strahl **ohne** Dach ausschalten: Machbarkeits-Gate

**Technische Ausgangslage:** In Vanilla sind Beacon-Strahl und Beacon-Aktivierung eng gekoppelt. Es gibt im verwendeten öffentlichen Paper-API keine bereits bestätigte `setBeamVisible(false)`-Funktion für einen einzelnen Beacon bei fortbestehender Vanilla-Wirkung. Eine opake Decke oder Tinted Glass stoppt zwar den normalen Strahl, deaktiviert aber normalerweise auch den Beacon. Die Wirkung kann serverseitig simuliert werden; eine separate **rein visuelle Ausblendung je Beacon ohne Veränderung sichtbarer Weltblöcke** ist dagegen nicht garantiert.

**Pflicht-Spike vor Feature-Bau:**

1. Einen v1.5-Prototype mit zwei unveränderten Vanilla-Clients und der **gepinnten Paper-Version** erstellen.
2. Prüfen, ob die Darstellung einzelner Beacon-Strahlen gezielt für **alle Zuschauer** zuverlässig unterdrückt werden kann, ohne tatsächliche Weltblöcke zu verändern, Client-Geisterblöcke, Blockkollisionen, Grafikfehler oder Resourcepack-Pflicht zu erzeugen.
3. Dazu serverseitige Paket-/Block-Entity-Möglichkeiten nur nach sauberem API-/Wartungsreview prüfen. Kein unkontrollierter NMS-Hack, keine ProtocolLib-Pflicht, keine unsichtbare dauerhafte Bauänderung.
4. Wenn die Technik funktioniert: rein visuelles Flag persistieren, auch nach Join/Teleport/Chunk-Reload neu anwenden; Effekte unverändert. Test bei Glas-/Farbglas-Säulen und mehreren Beacon-Strahlen.
5. **Wenn sie nicht funktioniert:** B1 als **BLOCKED** dokumentieren. Keine falsche Erfolgsmeldung „Strahl deaktiviert“, obwohl er sichtbar bleibt. Vorschlag zur Produktentscheidung: echter Tinted-Glass-Abschluss mit ersetzten Effekten, oder ein separat genehmigter Ressourcen-/Client-Ansatz. **Nicht stillschweigend** die Welt manipulieren oder B1 als fertig deklarieren.

**Ziel bleibt** `/beacon beam off` bei freiem Himmel mit erhaltenen Effekten. Die Unsicherheit betrifft die technisch mögliche Umsetzung, **nicht** die gewünschte Funktion.

### 1.4 Technischer Aufbau für überdachte Beacons

- Eigenständiges Paket `beacons/`: `BeaconModule`, `BeaconOwnership`, `BeaconRegistry`, `BeaconEffectService`, `BeaconBeamController`, `BeaconCommand` (konzeptionell).
- Auf `BlockPlaceEvent` (nach bestätigter Platzierung), `BlockBreakEvent`, `ChunkLoadEvent`/`ChunkUnloadEvent`, Welt-Load/Unload, Pistons/Explosionen/Blockzustandswechsel und Beacon-GUI-Änderungen reagieren. Keine dauerhaft ungebremste Suche in sämtlichen Welten.
- Vorhandene Beacons über **geladene Chunk-Tile-Entities** registrieren, auch die vor Installation gesetzten; ältere Beacons unter Dach sollen nicht wegen fehlenden Owners von den Effekten ausgeschlossen sein.
- Pyramidengrad und gewählte Primär-/Sekundärwirkung über `org.bukkit.block.Beacon` und Paper-Effektwechsel-Event lesen. Validieren, was die API bei überdachtem/inaktivem Beacon tatsächlich zurückliefert. **Falls die Auswahl unter Dach nicht lesbar oder nicht neu setzbar ist**, kontrollierten Speicher/Snapshot auf dem TileState entwerfen und dokumentieren; die bezahlte Vanilla-Effektwahl bleibt Voraussetzung. Keine stillen Annahmen über interne Beacon-NBT-Felder.
- Beim Unterscheiden von aktiven/unaktiven Beacons nur **zusätzlich** für solche mit verdecktem Himmel wirken, die Vanilla selbst gerade nicht versorgt. Keine doppelte Gewährung bei erneut freiem Himmel.
- Wirkung in Vanilla-typischen Intervallen (und mit nachvollziehbarer begrenzter Tick-Budgetierung), Radius abhängig vom Tier; Berechnung erst anhand realer Paper-Servertests verbindlich festlegen.
- Stufe-II-Logik mit Primär-/Sekundäreffekt und Effekt-Amplifier (`1` = Stufe II) testen; Event- und Weltzustände auf Main Thread, keine Bukkit-Weltzugriffe auf DB-Thread.
- Eine Stufe-4-Pyramide unter vollständiger Decke **muss** nach Neustart denselben ausgewählten Stufe-II-Effekt wiedergeben, sonst B2 nicht abgenommen.

### 1.5 Beacon-Abnahme (echter Client zwingend)

- [ ] A stellt Beacon; A steht direkt darauf und kann umschalten; B (auch Gruppenleader/OP) darf es nicht.
- [ ] A steht daneben/über anderem Block: Befehl verweigert. Offline-/umbenannter Owner bleibt korrekt.
- [ ] Kein Anspruch auf alte, nicht verlässlich zugeordnete Beacons; Recovery nur mit Admin-Audit.
- [ ] Freier Himmel, Strahl an/off über zwei Clients; nach Relog/Chunk-Unload/Restart unverändert.
- [ ] Beam `off` beeinflusst weder Effekte noch Farbe/Glas-Säule; Beam `on` stellt Darstellung korrekt wieder her.
- [ ] Unter opakem Dach: **kein Strahl**, aber die gewählten Effekte für andere Spieler innerhalb der Reichweite.
- [ ] Stufen 1/2/3/4 im offenen und überdachten Zustand; Stufe II nur bei korrekter Stufe-4-Konfiguration.
- [ ] Pyramidenabbau, Beacon-Abbau, Explosion und Blockwechsel stoppen unberechtigte Effekte.
- [ ] Nach Serverneustart unter Dach ausgewählte Wirkung inkl. Regeneration oder II korrekt.
- [ ] Mehrere Beacons überlagern sich ohne unfaire Verstärkung; Ticks bleiben unter Last stabil.
- [ ] Wenn B1-Spike scheitert, **keine** unehrliche Release-Abnahme.

---

## 2. Chat-Emotes mit Tabcompletion

### 2.1 Sichtbares Verhalten

Spieler geben im normalen öffentlichen Chat oder im vorhandenen Gruppenchat `/g` einen **exakten Textalias** ein. Vor der Anzeige beim Empfänger wird der Alias durch das konfigurierte Unicode-Zeichen ersetzt. Beispiel: `GG :skull:` wird als `GG 💀` angezeigt, **wenn** der Vanilla-Client das Glyph korrekt darstellen kann. Keine Emote-Rechte, kein externes Emoji-System und kein Resourcepack-Zwang.

**Beispiele für die anfängliche, bewusst kleine Whitelist** (exakte Liste über `config.yml` pflegbar):

| Alias | Symbol | Bemerkung |
|---|---|---|
| `:skull:` | `💀` | Pflichtalias, Vanilla-Glyph-Test; ggf. dokumentierter Fallback `☠` |
| `:heart:` | `❤` | Vanilla-nahe Darstellung |
| `:broken_heart:` | `💔` | Glyph-Test / optionaler Fallback |
| `:smile:` | `☺` | Einfaches, gut lesbares Zeichen |
| `:sad:` | `☹` | Einfaches Zeichen |
| `:star:` | `★` | Stern |
| `:check:` | `✔` | Bestätigung |
| `:cross:` | `✖` | Ablehnung |
| `:sun:` | `☀` | Sonne |
| `:music:` | `♫` | Noten |

- Weitere Alias-Mappings nur nach Glyph-Test und Validierung; keine volle Discord-/Unicode-Emoji-Datenbank in v1.5.
- Nur **vollständige** Aliase ersetzen: `:skull:` ja, `:skull` oder `:skullish:` nein. Groß-/Kleinschreibung im MVP bewusst **case-insensitiv**; Eingabe wird nicht als MiniMessage, Markdown, URL oder Farbcodes interpretiert.
- Mehrere Emotes in einer Nachricht sind möglich; unbekannte `:codes:` bleiben exakt erhalten. Keine Rekursion in erzeugten Symbols/anderen Aliases, keine Ersetzung von Chat-Commands.
- Emojis/Glyphs können vom Vanilla-Font unterschiedlich dargestellt werden. **Echte Discord-Farbgrafiken sind ohne Resourcepack nicht garantiert**. Fallback ist ein lesbares Vanilla-Zeichen; kein kaputtes Rechteck als ausgelieferter „Erfolg“.
- Kein globaler Chat-Toggle notwendig. Modul über `modules.emotes` aktivierbar.

### 2.2 Tabcompletion

Paper stellt `Player#addCustomChatCompletions(Collection<String>)` und `removeCustomChatCompletions(...)` bereit. Beim Join die gültigen Kürzel registrieren; bei Quit/Disable die eigenen Vorschläge sauber entfernen, **nicht** `setCustomChatCompletions(...)` verwenden, da das Vorschläge anderer Systeme überschreiben könnte. Zunächst prüfen, dass diese Methoden im aktuell gepinnten API-Build existieren.

**Gewünschter Client-Flow:** `:sk` + TAB → `:skull:`. Verhalten in normalem Chat **mit realem 26.3-Client testen**. Für `/g :sk...` nach Bedarf zusätzlich eigene `BasicCommand#suggest`-Vorschläge implementieren; Chat-Completion-APIs garantieren nicht automatisch dieselbe Completion in Command-Argumenten. Bei Unmöglichkeit eines Falles muss die konkrete Grenze dokumentiert werden.

### 2.3 Integration mit signiertem Chat

- Modul `emotes/` mit `EmoteMap`, `EmoteFormatter`, `EmotesModule`, `EmoteChatListener`, optional `EmoteSuggestionService`.
- Bei öffentlichem Chat Paper `AsyncChatEvent` und `ChatRenderer` prüfen; **signierten Originaltext nicht einfach überschreiben**, damit Signaturen, Reporting und Clientvertrauen nicht grundlos beschädigt werden. Änderungen auf Darstellungsebene müssen im realen Client auf „modified/unsigned“-Hinweise getestet werden.
- Nicht auf dem async Chat-Thread ungeprüft Bukkit-Weltzugriffe ausführen. Formatierung ist pure Text-/Adventure-Component-Transformation; Daten/Config werden als unveränderliche Momentaufnahme bereitgestellt.
- Bei `/g` denselben `EmoteFormatter` direkt verwenden, **ohne** private Nachricht in Public Chat umzuleiten. Vorhandene Gruppenmitgliedschaft, Vanish-Ausschlüsse, Cooldown und Länge unverändert.
- Gruppenchats dürfen keine öffentliche Anzeige auslösen. Falls gleichzeitig Namensfarben in Chat genutzt werden, einen **zentralen Renderer** komponieren, nicht zwei Listener gegeneinander überschreiben.
- Eingabelimits gegen absurd große Aliaslisten/Spam; keine freie MiniMessage- oder JSON-Komponenten-Injektion.

### 2.4 Emote-Abnahme

- [ ] `:skull:` + TAB im normalen Chat ergänzt wie erwartet; `/g :sk...` gesondert geprüft.
- [ ] `:skull:` wird visuell korrekt gerendert oder ein expliziter Glyph-Fallback ist konfiguriert und getestet.
- [ ] Multiple Aliases, unbekannte Aliases, URLs/normaler Text und Groß-/Kleinschreibung funktionieren.
- [ ] `/g` bleibt strikt privat und zeigt Emotes nur berechtigten Gruppenmitgliedern.
- [ ] Chat-Signatur/Reporting/Message-Trust mit zwei Vanilla-Clients getestet; keine unerwartete Warnung.
- [ ] Beim Disable verschwinden nur die vom Plugin eingefügten Completions.

---

## 3. Namen färben nach Spielzeit — 10 Forever-World-Stufen

### 3.1 Progression (verbindlicher Default)

Es zählt die **kumulierte Spielzeit des bereits vorhandenen IlluniumCustom-Playtime-Moduls**: tatsächlich verbundene Zeit seit Einführung dieses Moduls, **kein Import älterer Vanilla-Spielzeit** und kein Zurücksetzen pro Saison. Alle Schwellen **inklusive**; ein Spieler mit 150:00 h erhält Stufe 5. Die Stufen sind so gewählt, dass anfangs Fortschritt sichtbar ist und die letzten Stufen echte Forever-World-Langzeitziele sind.

| Stufe | Ab Spielzeit | Farbe (`NamedTextColor`) | Hex (Minecraft-Palette) | Stil |
|---:|---:|---|---|---|
| 1 | 0 h | `GRAY` | `#AAAAAA` | Neu angekommen |
| 2 | 10 h | `WHITE` | `#FFFFFF` | Eingelebt |
| 3 | 30 h | `GREEN` | `#55FF55` | Aktiv |
| 4 | 75 h | `DARK_AQUA` | `#00AAAA` | Entdecker |
| 5 | 150 h | `AQUA` | `#55FFFF` | Erfahren |
| 6 | 300 h | `BLUE` | `#5555FF` | Veteran |
| 7 | 500 h | `LIGHT_PURPLE` | `#FF55FF` | Langzeitspieler |
| 8 | 800 h | `RED` | `#FF5555` | Elite |
| 9 | 1.200 h | `GOLD` | `#FFAA00` | Legende |
| 10 | 2.000 h | `YELLOW` | `#FFFF55` | Forever-World-Legende |

**Warum NamedTextColor statt freiem RGB:** Vanilla-Nametags werden im existierenden Haupt-Scoreboard über `Team` realisiert. Dessen Spieler-Namensfarbe unterstützt **benannte Minecraft-Farben**; die gruppeneigene Tag-Farbe bleibt davon unabhängig und kann weiterhin `#RRGGBB` sein. So werden Name über Kopf und in Tab möglichst konsistent gezeigt. Die Palette ist bewusst gut unterscheidbar und vermeidet schwer lesbares Dunkelblau/Schwarz als Endstufe.

### 3.2 Anzeigevertrag

- **Über Kopf:** `[GRUPPENTAG] Spielername` — Gruppen-Tag behält die **Gruppenfarbe**, nur der eigentliche **Spielername** wechselt nach Spielzeit.
- **Tablist:** identische Reihenfolge; vorhandenes Marriage-Herz als **Suffix** bleibt erhalten und behält seine Rot/Grau-Farbe. Kein doppelter Gruppen-Tag.
- **Ohne Gruppe:** Nur der Spielername wird gefärbt. **Ohne Marriage:** Kein Herz-Platzhalter. Der Vanilla-Spielername selbst/UUID bleibt unverändert.
- **Vanish:** Unsichtbare OPs bleiben für andere vollständig verborgen; Farbupdates dürfen keinen Tablist-Eintrag/Join-Broadcast auslösen.
- **Chat:** Wenn technisch signaturverträglich, kann der vorhandene Absendername konsistent eingefärbt werden. Diese Darstellung darf nicht den eigentlichen Chatinhalt oder die Signatur unzulässig verändern. Verbindliche Mindestabnahme ist **Nametag + Tab**; öffentliche Chat-Einfärbung separat als E2E-Bonus prüfen und bei Problemen zunächst weglassen.
- **OPs:** Die Namensfarbe basiert auf echter Spielzeit auch bei OP-Konten; sie gewährt keine Vorteile und ändert **nicht** den bestehenden Ausschluss von OPs aus Leaderboards.
- Farben gelten für alle Spieler gleich, unabhängig von Gruppenrolle, Hochzeit, Dimension oder bereits gestarteter Welt.

### 3.3 Architektur und Synchronisierung

- Eine read-only `PlaytimeReadService`-Abhängigkeit in ein neues `playtimecolors/`-Modul injizieren, vergleichbar mit `LeaderboardModule(playtime)` in `Modules.create()`; **kein direkter SQL-Zugriff auf private Tabellen fremder Module**.
- `appearance/AppearanceService` minimal um `setNameColor(UUID, NamedTextColor)` ergänzen. Der **einzige Renderer** komponiert Gruppen-Prefix, Team-NameColor, Tab-NameColor und Marriage-Suffix. Er muss auch für Spieler **ohne** Prefix eine Team-Zuordnung für die Nametag-Farbe verwalten; `close()`/Quit räumen zuverlässig auf.
- Vorhandenen `Team#color(NamedTextColor)` einsetzen; Gruppen-Prefix mit eigenem `Component`-Farbwert erhalten. `player.playerListName(...)` verwendet `Component.text(name, tierColor)`; bei fehlender Farbe Vanilla-Default.
- Beim Join nach bekanntem DB-/Live-Playtime-Wert initialisieren; bei Pluginstart auch bereits online befindliche Spieler, bei Quit Farbe aus Memory entfernen. Regelmäßig **höchstens einmal pro Minute** die aktuell online befindlichen Spieler auf Schwellenüberschreitungen prüfen (oder effizienten Service-Callback verwenden), kein DB-Read pro Tick/Spieler.
- Live-Zeit über `PlaytimeReadService.snapshot()` einschließen, damit der neue Rang nicht erst nach langer Verzögerung sichtbar wird. Resultate aus Async-Storage nur auf dem Main Thread in Appearance anwenden.
- Speichern eines gesonderten „Level“ in SQLite ist **nicht nötig**, da die Schwelle deterministisch aus der bestehenden Spielzeit berechnet wird. Die Stufe bleibt nach Restart aus demselben Zeitkonto erhalten.
- Wenn `playtime` deaktiviert ist: `playtimecolors` startet nicht als teilweise funktionierender Dienst; klare Warnung/Config-Validierung statt willkürlich jeder Name grau.
- Konfiguration `playtimecolors.enabled` bzw. `modules.playtimecolors`, optionale rein optische Schwellen/Palette nur mit strikter Validierung und **zehn** aufsteigenden Einträgen. Default-Tabelle ist maßgeblich.

### 3.4 Abnahme

- [ ] Exakte Grenzen: 0, 9:59:59, 10:00:00, 29:59:59, 30:00:00, …, 2.000 h.
- [ ] Zehn unterscheidbare Farben in Tab und über Kopf, ohne doppelten Prefix.
- [ ] Gruppen-Tag mit Hexfarbe ist **nicht** von Namensfarbe betroffen; Marriage-Herz bleibt korrekt.
- [ ] Spieler ohne Gruppe und Spieler ohne Marriage werden trotzdem korrekt gefärbt.
- [ ] Offline/Online, Restart, Weltwechsel und Vanish/Unvanish ohne Anzeigelecks.
- [ ] Bereits gespeicherte Playtime bestimmt sofort die passende Stufe; keine Resets/zusätzlichen Doppelzählungen.
- [ ] Kein nennenswerter Tick-Aufwand bei 50+ Online-Spielern; keine async Bukkit-Aufrufe.

---

## 4. Zusätzliche Custom Recipes

### 4.1 Exakte Rezepte

Alle neuen normalen Crafting-Rezepte sind **shapeless** (beliebige Anordnung im 2×2-/3×3-Feld). Nur die angegebenen **normalen Materialien** zählen; keine Varianten durch Tags, wenn nicht ausdrücklich genannt.

| ID / Key-Suffix | Zutaten | Ergebnis | Typ |
|---|---|---|---|
| `tuff_from_basalt_gravel` | **2 × `BASALT` + 2 × `GRAVEL`** | **4 × `TUFF`** | Shapeless 2×2 / 3×3 |
| `cinnabar_from_sulfur_dye` | **4 × `SULFUR` + 1 × `RED_DYE`** | **4 × `CINNABAR`** | Shapeless **3×3** (5 Zutaten) |
| `calcite_from_stone_bonemeal` | **2 × `STONE` + 2 × `BONE_MEAL`** | **2 × `CALCITE`** | Shapeless 2×2 / 3×3 |
| `deepslate_from_cobble_basalt` | **2 × `COBBLESTONE` + 2 × `BASALT`** | **4 × `COBBLED_DEEPSLATE`** | Shapeless 2×2 / 3×3 |
| `dead_bush_from_fern` | **1 × `FERN`** | **1 × `DEAD_BUSH`** | **Furnace / Schmelzofen** (normaler Ofen) |

**Festlegung der nicht genannten Menge:** Für Cobbled Deepslate war keine Ausgabemenge vorgegeben; der Plan nimmt **4 Stück** an (vier Eingabeblöcke → vier Ausgabeblöcke). Dies vor Implementierung in der Rezept-Abnahme bestätigen; sonst nur diesen Wert anpassen.

**Wichtig zu Cinnabar/Sulfur:** In Java 26.2 wurden **Sulfur- und Cinnabar-Blöcke** eingeführt. Im Rezept sind `SULFUR` und `CINNABAR` die normalen Block-Items, **keine losen Mineralien, kein Potent Sulfur** und keine neuen, durch das Plugin erfundenen Itemtypen. Die exakten `Material`-Konstanten gegen die gepinnte Paper-API bauen und auf dem 26.3-Testserver prüfen.

**Ofen:** `FurnaceRecipe` mit `FERN` als Input, `DEAD_BUSH` als Output, Erfahrung **0**, Garzeit **200 Ticks** (10 Sekunden als konservativer Default), normaler Brennstoffverbrauch. Kein Blast Furnace/Smoker-Rezept. Bereits vorhandene Vanilla-Rezepte werden nicht ersetzt.

### 4.2 Technische Umsetzung

- `RecipesModule.recipes(...)` bisher `List<CraftingRecipe>`; in eine `List<Recipe>` bzw. geeignete Registrierungs-Abstraktion umbauen, sodass `ShapelessRecipe` **und** `FurnaceRecipe` erlaubt sind.
- Keys im Namespace `illuniumcustom` registrieren; vorhandener Key `quartz_from_quartz_block` bleibt unverändert. Vor eigener Key-Registrierung ein eventuelles älteres eigenes Rezept unter **demselben** Key entfernen, aber niemals Vanilla-/fremde Rezepte löschen.
- Alle Zutatenmengen über wiederholte `addIngredient(Material)`-Aufrufe korrekt registrieren, nicht als Stack-Menge eines einzelnen Slots missverstehen.
- Rezept-Registry auf Disable sauber entfernen; bei Neustart genau **sechs** eigene Rezepte (bestehendes Quartz + fünf neue), nicht doppelt.
- 2×2-Rezepte in realer Spieler-Crafting-UI prüfen; fünf Zutaten passen **nur in 3×3**.
- Für Rezepte mit möglicher Vanilla-Kollision die tatsächliche Crafting-Auswahl prüfen. Nur klar dokumentierte Konflikte beheben; keine Vanilla-Rezept-Deregistrierung als Standard.
- `Recipe`-Unit-/Integrationstests für Zutaten, Anzahl, Namespace, Cook-Time, Experience und Wiederanmeldung.

### 4.3 Rezept-Abnahme

- [ ] Quartz-Rezept 1→4 funktioniert weiterhin unverändert.
- [ ] Tuff: exakt 2 Basalt + 2 Gravel → 4 Tuff; jede Anordnung, keine Varianten.
- [ ] Cinnabar: exakt 4 Sulfur + 1 Red Dye → 4 Cinnabar; nur 3×3.
- [ ] Calcite: exakt 2 Stone + 2 Bone Meal → 2 Calcite; weder Cobblestone noch Smooth Stone.
- [ ] Cobbled Deepslate: exakt 2 Cobblestone + 2 Basalt → **4** Cobbled Deepslate.
- [ ] Ein Farn im normalen Ofen → ein toter Busch nach 200 Ticks, ohne XP; kein Smoker/Blast Furnace.
- [ ] Keine Duplikate, keine fremden/Vanilla-Rezepte überschrieben, keine Fehler beim Restart.

---

## 5. Zielstruktur und Konfiguration

```text
src/main/java/de/illunium/custom/
├── beacons/
│   ├── BeaconModule.java
│   ├── BeaconCommand.java
│   ├── BeaconOwnership.java
│   ├── BeaconRegistry.java
│   ├── BeaconEffectService.java
│   └── BeaconBeamController.java    # Erst nach Machbarkeits-Gate
├── emotes/
│   ├── EmotesModule.java
│   ├── EmoteMap.java
│   ├── EmoteFormatter.java
│   └── EmoteChatListener.java
├── playtimecolors/
│   ├── PlaytimeColorsModule.java
│   └── PlaytimeColorPolicy.java
├── appearance/AppearanceService.java  # Bestehenden Single-Writer erweitern
├── recipes/RecipesModule.java         # Bestehende Registry erweitern
└── core/module/Modules.java           # Neue Module registrieren
```

**Konfigurationsvorschlag:**

```yaml
modules:
  recipes: true
  groups: true
  marriage: true
  playtime: true
  whitelistguard: true
  whiteliststaging: true
  sit: true
  vanish: true
  spawn: true
  leaderboard: true
  beacons: true
  emotes: true
  playtimecolors: true

beacons:
  allow-covered-effects: true
  beam-toggle-enabled: true # Nur freigeben, wenn der technische B1-Test bestanden ist
  effect-refresh-ticks: 80

emotes:
  enabled-in-public-chat: true
  enabled-in-group-chat: true
  tab-completion: true
  # Nur geprüfte / darstellbare Glyphs produktiv aktivieren.
  aliases:
    skull: '💀'
    heart: '❤'
    broken_heart: '💔'
    smile: '☺'
    sad: '☹'
    star: '★'
    check: '✔'
    cross: '✖'
    sun: '☀'
    music: '♫'

playtimecolors:
  # Mindestens 10 Einträge; je Stufe hours aufsteigend und color ein NamedTextColor.
  tiers:
    - {hours: 0, color: GRAY}
    - {hours: 10, color: WHITE}
    - {hours: 30, color: GREEN}
    - {hours: 75, color: DARK_AQUA}
    - {hours: 150, color: AQUA}
    - {hours: 300, color: BLUE}
    - {hours: 500, color: LIGHT_PURPLE}
    - {hours: 800, color: RED}
    - {hours: 1200, color: GOLD}
    - {hours: 2000, color: YELLOW}
```

**Wichtig:** Die Angaben sind **Soll-Konfiguration**, nicht bereits existierende Keys. API- und Config-Validierung in den jeweiligen Modulen implementieren. Bei ungültigen Farben/Schwellen/Aliases sauber abbrechen und verständlich loggen. `beam-toggle-enabled` erst nach erfolgreichem Spike produktiv aktivieren; andernfalls keine irreführenden Commands registrieren.

---

## 6. Inkrementeller Implementierungsplan

### Phase 0 — Baseline und isolierte Testumgebung

- Aktuelle `main`-Version, Git-SHA, Gradle-/Java-/Paper-Version dokumentieren; alle bestehenden Tests (`./gradlew clean check build --no-daemon`) auf eigenem Branch **vor** Änderungen laufen lassen.
- Kopie von Weltdaten/DB nur für Staging; keine Live-Dateien beschreiben.
- Reale Paper-26.3-/Vanilla-26.3-Clients für Beacon/Tab-Rendering und Emote-Glyphs einrichten.
- **Gate 0:** Baseline reproduzierbar, v1.4 nicht regressiert.

### Phase 1 — Beacon-Machbarkeit **vor** Backend-Implementierung

- B1 visuelle Unterdrückung eines einzelnen Strahls bei fortbestehender Wirkung real prototypen.
- B2 prüfen: `Beacon#getTier`, aktuelle/gespeicherte Primary-/Secondary-Wahl unter Dach und nach Restart, Level-II-Regeln, verfügbare Events.
- Dokumentieren: API-only / stabile Erweiterung / NMS-Bedarf / unterbunden. **Kein** Shipping eines halbfunktionierenden Toggles.
- **Gate 1:** B1 technisch nachgewiesen oder ausdrücklich als Blocker mit offener Produktentscheidung; B2 mit klarer zuverlässiger Datenstrategie.

### Phase 2 — Recipe-Modul

- Fünf neue Rezept-Definitionen + Ofen-Support mit stabilen Keys.
- Unit-/Paper-Rezepttests, z. B. 2×2 und 3×3 real.
- **Gate 2:** Sechs Rezepte inkl. Quartz korrekt, kein Vanilla-Regressionsschaden.

### Phase 3 — Spielzeit-Namensfarben

- `PlaytimeColorPolicy` als pure Testklasse (Grenzwerte, monotone zehn Stufen), Service injizieren.
- `AppearanceService` um Team-Farbe und farbige Tab-Komposition erweitern; Prüfung mit Hex-Gruppen-Tag, Marriage, Vanish.
- Periodisches Aktualisieren effizient, Playtime-Snapshot inklusive Livezeit.
- **Gate 3:** Farb- und Anzeigetests mit zwei Clients sowie Restart grün.

### Phase 4 — Emotes und Chat-Completion

- Alias-Parser, Mapping-Validation, shared Formatter.
- Chat-Renderer samt Signatur-/Trust-Test und `/g`-Integration.
- Paper `addCustomChatCompletions` auf gepinntem Build verifizieren und in normalem Chat + `/g` testen.
- **Gate 4:** Korrekte sichtbare Symbole, keine Privacy-/Signatur-Regression.

### Phase 5 — Beacon-Implementierung

- Owner-UUID und Beam-Wunsch speichern, Commands und Admin-Recovery.
- Überdachte Beacon-Effektberechnung unter Validierung der echten Pyramide, Spielereffektwahl und Stufe II.
- Beam-Umschaltung **nur** gemäß bestandenem Phase-1-Konzept, sonst B1 blockiert.
- Chunk-/Block-/Welt-Lifecycle, Cleanup und Lasttests.
- **Gate 5:** B1 und B2 unabhängig per E2E abgenommen. Wenn B1 nicht umsetzbar, kein „v1.5 vollständig“ behaupten.

### Phase 6 — Regression, Doku, Paketierung

- Vollständiges `clean check build`; Vanilla-Paper-Smoke-Tests (kein anderes Plugin), zwei echte Minecraft-Clients.
- `README.md`, `docs/commands.md`, `docs/configuration.md`, `docs/architecture.md`, `docs/development.md`, `CHANGELOG.md` nach Implementierung aktualisieren.
- Keine bestehenden Migrationen ändern. Bei neuer SQLite-Tabelle ausschließlich additive V6-Migration, Backup + Migrations- und Rollbackprobe.
- Prüfen: `/gec` 4er-Schwelle, `/g`, Leaderboards, Whitelist-Staging, Vanish-Opt-out, `/sit`, `/spawn`-Cooldown, Marriage, Quartz bleiben unberührt.
- **Gate 6:** Keine Datenverluste, kein Chat-Leak, keine Duplikate/Crash-Artefakte.

### Phase 7 — Manuelle Release-Freigabe

- Staging-Protokoll und bekannte Einschränkungen vorlegen, Diff prüfen, menschliche Freigabe abwarten.
- Backup, kontrollierter Serverstop, einmaliges Plugin-JAR-Update, Start-Log- und E2E-Sanity-Check. Bei Fehlern vorheriges JAR plus **kompatiblen DB-/Weltsnapshot** zurückspielen.
- **Gate 7:** Erst nach bestandenen Feature-Gates und bewusster Freigabe darf v1.5 als produktionsbereit bezeichnet werden.

---

## 7. Testmatrix und Definition of Done

| Bereich | Positivtest | Negativ-/Grenzfall |
|---|---|---|
| Beacon Ownership | Platzierer toggelt direkt über eigenem Beacon | Fremder Spieler, daneben, alter Beacon ohne Owner, OP ohne Ownership |
| Beacon beam | Nur bestimmter Strahl verschwindet; anderer bleibt, Effekte bleiben | Chunkwechsel, Restart, Glas/überdacht, keine Geisterblöcke |
| Beacon roof | Wirkung unter voller Decke | Fehlende/ungültige Pyramide, Beacon zerbrochen, falsche Dimension |
| Beacon II | Haste II/Speed II nach regelkonformer Stufe-4-Auswahl | II bei Stufe 3 verboten, Regeneration korrekt |
| Emote | `:skull:` und mehrere Aliases | Unbekannter Alias, fehlerhafte Config, fehlende Glyphs |
| Emote TAB | Chat-Vorschlag erscheint und vervollständigt | `/g`-Vorschläge, Client ohne Emoji-Unterstützung |
| Emote Chat | Public und `/g` berechtigt; signierte Nachricht stabil | Keine private Nachricht im Public, keine Rechte-Umgehung |
| NameColor | Zehn Werte und Übergänge; Gruppen-Tag/Marriage intakt | Ohne Gruppe, Offline, Vanish, Restart, fehlendes Playtime-Modul |
| Recipes | Fünf neue Rezepte + Quartz | Falsche Zutaten/Varianten, Furnace vs Smoker, Double-Registration |
| Gesamt | Aktuelle v1.4 Commands und DB-Schema bleiben intakt | Kein Tick-Lag, kein beschädigter Weltblock, keine Logs mit Secrets |

**Definition of Done:** Alle gelieferten Features existieren mit verifizierten Commands, klaren Fehlermeldungen, automatisierten Tests, realem Client-Test und Dokumentation; persistente States überleben Neustarts, bestehende Module bleiben funktionsfähig. **B1-Strahlsteuerung darf nicht als implementiert gelten, solange das gesonderte technische Machbarkeits-Gate offen ist.**

---

## 8. Quellen und verifizierte technische Annahmen

Diese Links sind externe Referenzen für die **Planung**, keine Aussage, dass die Features bereits in `illunium-custom` programmiert sind:

- Paper Developer Guide — Chat-Events und `ChatRenderer`: https://docs.papermc.io/paper/dev/chat-events/
- Paper Developer Guide — signierte Nachrichten: https://docs.papermc.io/paper/dev/component-api/signed-messages/
- Paper `Player`-API 26.3 — `addCustomChatCompletions` / `removeCustomChatCompletions`: https://jd.papermc.io/paper/26.3/org/bukkit/entity/Player.html
- Paper/Spigot `Team`-API — `color(NamedTextColor)`: https://jd.papermc.io/paper/26.2/org/bukkit/scoreboard/Team.html
- Paper Beacon API und Effektwahl, Version gegen gepinnten Build verifizieren: https://jd.papermc.io/paper/26.3/org/bukkit/block/Beacon.html
- Minecraft 26.2 Release Notes (Vanilla Sulfur-/Cinnabar-**Block**-Sets): https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2
- Technische Referenz für eine serverseitige Überbrückung verdeckter Beacon-Effekte über Tinted Glass (keine einzubauende Abhängigkeit): https://modrinth.com/plugin/simplenobeambeacon

**Revisionshinweis:** Das Original-MVP bleibt als historisches Basisdokument erhalten. Dieser Patch ersetzt keine bereits ausgelieferten Regeln, soweit er sie nicht ausdrücklich erweitert. Die konkrete technische Implementierung wird erst nach Code- und Client-Tests freigegeben.
