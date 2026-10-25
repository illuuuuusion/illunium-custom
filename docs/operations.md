# Betrieb

Gilt für Testserver und Forever-World. **Produktions-Rollouts sind ein separater, manuell freigegebener Schritt**; nichts in diesem Repository deployt automatisch.

## Versionen (Release Candidate 0.1.0-rc.1)

| | |
| --- | --- |
| Paper | 26.3 Build 207 (`26.3-207-main@bc68acd`, API `26.3.build.207-stable`) |
| Java | 25 (getestet: Temurin 25.0.4.1) |
| SQLite-Schema | v5 ab Patch v1.4 (V5 Gruppenkisten); v1.3 kennt v4 (Leaderboards/Staging); v1.2 kennt v2, 0.1.0-rc.1/v1.1 nur v1 |
| Config | Vorlage aus dieser Version, ohne Versionsfeld; neue Schlüssel haben Standardwerte |
| JAR | `IlluniumCustom-0.1.0-rc.1.jar`, SHA-256 aus `SHA256SUMS`/`IlluniumCustom.jar.sha256` des CI-Builds; lokal reproduzierbar |

## Rollout-Ablauf

1. CI grün, JAR + Hash + `build-info.txt` aus dem Build des freizugebenden Commits.
2. Auf dem **separaten Testserver** installieren, Startup-Smoke (Log ohne `ERROR`/`SEVERE` des Plugins, `/ic status` zeigt alle Module) und Gameplay-Abnahme (unten).
3. Review und dokumentierte Freigabe.
4. Vor dem Rollout sichern: alte Plugin-JAR, `plugins/IlluniumCustom/` (online per `.backup`, s. u.) und `whitelist.json`/`ops.json`.
5. Server stoppen, JAR ersetzen, starten. Frisches Log prüfen, `/ic status`, kurze Spieltests.
6. Bei FAIL: stoppen, Ursache dokumentieren, Rollback (s. u.), erneut prüfen.

Kein Hot-Reload: Plugin-Updates und Config-Änderungen immer per Server-Neustart.

## Backup

Zu sichern sind `plugins/IlluniumCustom/data.db`, `plugins/IlluniumCustom/config.yml` sowie `whitelist.json` und `ops.json` (gehören zum Server-Backup, nicht zur Plugin-DB).

- **Online** (Server läuft): `sqlite3 plugins/IlluniumCustom/data.db ".backup /pfad/backup.db"` — konsistenter Snapshot über die SQLite-Backup-API. Danach `sqlite3 /pfad/backup.db "PRAGMA integrity_check"` → `ok`.
- **Offline** (Server gestoppt): `data.db` plus vorhandene `data.db-wal`/`data.db-shm` gemeinsam kopieren.
- Niemals nur `data.db` im laufenden Betrieb kopieren (WAL-Modus, Snapshot wäre unvollständig).

## Restore

1. Server stoppen.
2. `data.db` durch das Backup ersetzen, alte `data.db-wal`/`data.db-shm` löschen.
3. Passende `config.yml` und ggf. `whitelist.json`/`ops.json` zurückspielen.
4. Starten, Log und `/ic status` prüfen.

Was nach einem Restore fehlt: alle Änderungen nach dem Backup-Zeitpunkt (Gruppen, Ehen, Playtime). Einladungen, Anträge und Bestätigungen sind ohnehin flüchtig.

## Rollback

- **Nur JAR**, wenn die Ziel-JAR dasselbe Schema kennt (siehe Tabelle oben / `/ic status`).
- Kennt die alte JAR ein neueres Schema in `data.db` nicht, bricht sie den Start mit `Database schema vN is newer than supported vM` ab und deaktiviert sich, ohne Daten anzufassen. Dann: alte JAR **plus** DB-Backup aus der Zeit vor dem Update zurückspielen.
- Vor jedem schemaändernden Update ist ein DB-Backup Pflicht. Patch v1.2 hebt das Schema auf v2: Rollback auf eine v1.1-JAR braucht das Backup von vorher (dabei gehen nur seither gesetzte Spawn-Cooldowns verloren); die DB nie von Hand auf v1 zurücksetzen. v1.3 benötigt entsprechend das v2-Backup für ein Rollback auf v1.2; eigene neue Metriken und Staging-Vorgänge seit dem Backup gehen dabei verloren, native Launch-Freigaben nur bei Wiederherstellung der nativen Whitelist.

## Absturz

- Playtime: es fehlt höchstens die Zeit seit dem letzten Checkpoint (≤ 60 s pro Spieler), nichts wird doppelt gezählt.
- Gruppen und Ehen: jede bestätigte Änderung ist committet; halbe Änderungen gibt es nicht.
- Spawn: laufende Warmups enden ohne Teleport und ohne Cooldown. Ein Cooldown, dessen Commit beim Absturz noch ausstand, kann fehlen (Best Effort).
- Nametag-Teams und Sitz-Entities aus der abgestürzten Sitzung werden beim nächsten Start bzw. beim Laden der Chunks/beim Join entfernt.

## Whitelist und Launch

- Spieler ausschließlich mit Vanilla verwalten: `/whitelist add|remove|list|reload`. `/whitelist add <Name>` funktioniert ohne vorherigen Join, solange Mojang erreichbar ist, und berechtigt **sofort**.
- Mit `enforce-whitelist=true` kickt `/whitelist remove` bzw. `/whitelist reload` online befindliche, nicht mehr gelistete Spieler.
- **Launch-Runbook:** Namen vorab nur eintragen, solange der Minecraft-Endpunkt öffentlich **nicht** erreichbar ist (Server aus bzw. externer Zugang zu). Danach den öffentlichen Zugang manuell öffnen. Ports, Firewall und Proxy werden außerhalb dieses Plugins verwaltet.
- Staff-Accounts in `whitelistguard.staff` eintragen (siehe [`configuration.md`](./configuration.md)); nach dem Start im Log `verified and protected` prüfen.

## Gameplay-Abnahme (Testserver, Vanilla-Client)

Mit mindestens drei Testaccounts (A, B, C), keinem weiteren Plugin, A als OP, B und C ohne OP. Ergebnis mit Datum, Paper-Build und JAR-Hash in [`development.md`](./development.md) festhalten.

- [ ] Whitelist: C nicht gelistet → Join abgelehnt; `/whitelist add C` (C nie gejoint) → Join klappt; `/whitelist remove C` während C online → Kick.
- [ ] Staff: A in `staff`, `/whitelist remove A` → nach ≤ 30 s wieder gelistet; Gruppen-Mod B erhält dadurch nichts.
- [ ] B (kein OP): `/ic status` und `/ic group ...` abgelehnt.
- [ ] Rezept: Quartz-Block im 2×2-Inventarfeld und an der Werkbank → 4 Quartz; Säule/gemeißelt/glatt → nichts.
- [ ] Gruppen: create/invite/accept/kick/promote/demote/rename/tag/color/transfer/disband mit A/B/C; Tag in Gruppenfarbe **genau einmal** über dem Kopf und in der Tablist; nach Kick/Disband/Quit/Rejoin korrekt, keine Geistertags.
- [ ] Marriage: A heiratet B → beide sehen bei beiden rotes `❤`; B quit → A zeigt für alle das Offline-Symbol (prüfen, ob `💔` gerendert wird, sonst `offline-symbol: "✕❤"`); Rejoin → `❤`; Scheidung entfernt beide Herzen; Gruppen-Tag bleibt unverändert.
- [ ] Playtime: Session über einen Checkpoint (> 60 s), Quit, Neustart → Zeit erhalten, nicht doppelt; `/playtime B` offline.
- [ ] Sit: `/sit` auf Block, Stufe, Treppe; sichtbar sitzend; `/sit` erneut und Sneaken stehen auf; zwei Spieler gleichzeitig; abgelehnt im Wasser, beim Fliegen (Kreativ), im Fall, auf dem Pferd; Schaden/Teleport/Weltwechsel/Quit/Neustart beenden das Sitzen ohne übrig gebliebene ArmorStands (`/execute if entity @e[type=armor_stand]`).
- [ ] Spawn (Patch v1.2, B und C): `/spawn` 10 s still → Teleport frühestens nach 10 s, danach Ablehnung mit ~60 min; Abbruch ohne Cooldown bei Laufen, Springen, Wegschieben, Fallschaden, Lava, Zombie-Treffer; **kein** Abbruch bei Kopfdrehen, Sneaken auf der Stelle, Zombie in der Nähe ohne Treffer; B schlägt C bzw. trifft per Bogen → beide 30 s gesperrt, neuer Treffer nach 20 s erneuert; Quit im Combat-Tag + Rejoin → keine Strafe, Tag weg; Neustart nach Teleport → Cooldown bleibt; `/setworldspawn` geändert → neues Ziel; `/spawn` sitzend → keine ArmorStands übrig; A im Vanish → bleibt verborgen. Bewegungstoleranz (0,001 Block) dabei bestätigen oder in `SpawnService.MOVE_TOLERANCE_SQUARED` anpassen.
- [ ] Kombination: Gruppenänderung + Heirat + Join/Quit + Playtime + `/sit` gleichzeitig, danach Neustart; Log ohne `ERROR`/`SEVERE`.

## Projektstart mit Whitelist-Staging (Patch v1.3)

1. Vor Installation/Launch konsistent sichern: `whitelist.json`, `ops.json`, `plugins/IlluniumCustom/config.yml`, SQLite per Online-Backup (inkl. Integritätsprüfung) oder bei gestopptem Server DB samt WAL/SHM. Weltstatistiken ebenfalls ins Server-Backup aufnehmen. Vorherige JAR und passende DB-Version aufbewahren.
2. Anmeldungen manuell aus Discord übernehmen: `/ic whitelist stage <MinecraftName>`. Erfolg muss ausdrücklich `STAGED` und **keine Zugangsfreigabe** melden. Fehlerhafte/unerreichbare Profile später erneut versuchen; keine Offline-UUIDs erfinden. Ein ausschließlich vorgemerkter Account muss am Testserver weiterhin den Whitelist-Kick erhalten.
3. `/ic whitelist staged [seite]` und `preview` kontrollieren. Nativ bereits erlaubte Tester/Staff bleiben erlaubt. Vanilla `/whitelist add` gewährt sofort Zugang und ist kein Staging-Befehl.
4. Nach fachlicher Freigabe einmal `/ic whitelist launch`. Währenddessen kein `unstage`; neu gestagete Accounts gehören erst zum nächsten Launch. Ein paralleler Launch wird abgewiesen. Whitelist und Enforcement bleiben eingeschaltet.
5. `/ic whitelist status` bis DONE/PARTIAL prüfen, `staged` auf Einzelfehler kontrollieren. Native `/whitelist list` und eine **lesende** Kontrolle von `whitelist.json` müssen korrekte UUIDs und nichtleere Namen zeigen. Stichproben: frisch freigeschalteter Account kann joinen; nicht vorgemerkter Account bleibt gesperrt; Staff unverändert.
6. Bei Teilfehlern `/ic whitelist retry`. Erneut Status und native Einträge prüfen. Bei unterbrochenem DB-Commit/gesperrtem Batch sauber neu starten: Recovery bestätigt schon vorhandene Freigaben, fehlende bleiben FAILED bis zum ausdrücklichen Retry. Keine Live-JSON-Edits als Reparatur.
7. Nach APPLIED ist ausschließlich die native Whitelist zuständig: `/whitelist remove <Name>` bleibt wirksam, alte APPLIED-Einträge werden nicht wiederhergestellt. Nur konfigurierte geschützte Staff folgen weiterhin dem Guard. Staging kann anschließend deaktiviert werden, ohne Freigaben zu ändern.

Die Audit-Tabelle und Serverlogs enthalten Akteur, UUID, verifizierten Namen, Aktion, Ergebnis und Zeitpunkt. Profilauflösung hat zwei Worker, eine Queue für höchstens 32 weitere Anfragen und 15 s Antwort-Timeout; der Launch verarbeitet sequenziell höchstens einen Account je Main-Thread-Fortsetzung. Bei Mojang-Ausfall bleiben Einträge offen/fehlerhaft. Status-Reads verändern keinen Zugang.

## v1.3-Abnahme vor Produktionsfreigabe

- [ ] Zwei normale Clients und zwei OPs: Default-OP joint ohne öffentliche Nachricht/Tablist/Entity, UUID-Opt-out sichtbar mit normaler Join-Nachricht. Beide können manuell vanischen, sichtbares `/vanish` erzeugt genau eine gelbe Leave-Meldung; Opt-out nach Quit wieder sichtbar. De-OP innerhalb einer Sekunde sichtbar.
- [ ] Neuer Viewer sieht keine verborgenen OPs; Marriage-Herzen/Partikel, Ping, Completions und Gruppen-Ausgaben verraten keine versteckte Anwesenheit. Sichtbarer OP bleibt sichtbar, aber in allen elf Leaderboards ausgeschlossen.
- [ ] Platzierungen einschließlich Bett/Tür, gecancelte Aktionen, Totem in beiden Händen/ohne Totem/gecancelt; XP-Aufstieg, Tod und XP-Ausgabe; Neustart mit gespeicherten Rekorden.
- [ ] Erfolgreicher v1.2-Spawn zählt genau einmal, Warmup-/Combat-/Ziel-/Event-Abbruch nie. Bestehende v1.2-Client-Testmatrix weiterhin abnehmen.
- [ ] Reale nie gejointe Accounts: STAGED ohne Zugang, Launch mit UUID + Name, Join nach APPLIED, Teilfehler/Retry, manueller nativer Remove und Restart ohne Reaktivierung. Nicht-OP mit `illuniumcustom.admin` darf weder Staging noch Launch bedienen.
- [ ] Profil-Ausfall, DB-Ausfall, großer Offline-Bestand auf Zielhardware; Tickprofil dokumentieren. Cache zeigt bewusst keine garantierten Livewerte.
- [ ] Konsistentes Backup und Rollback mit passender JAR/DB am Testserver, dokumentierte manuelle Produktionsfreigabe.

Eigene Block-Deltas werden alle 5 s sowie bei Quit/Disable geschrieben; Totem/XP/Spawn stoßen zusätzlich einen Flush an. Bei DB-Fehlern bleiben Deltas im Speicher und werden erneut versucht; ein Prozessabsturz oder dauerhaft defekte DB beim Shutdown kann noch nicht bestätigte Deltas verlieren. Spawn-IDs werden dauerhaft zur Deduplizierung behalten; derzeit keine automatische Bereinigung. Keine historischen Werte schätzen.

## Patch v1.4: Gruppenkisten, Recovery und Freigabe

Vor jedem Update **DB und vollständige Welten inklusive playerdata** sowie bisherige JAR und Konfiguration konsistent sichern. Für SQLite die Backup-API verwenden; für einen gemeinsamen DB-/Weltstand den Server sauber stoppen. Auf isolierter Kopie V4 → V5, zweiter Start, Inventar-Spezialitems und Restore prüfen. Die v1.3-JAR lehnt Schema V5 ab: Rollback benötigt die v1.3-JAR mit passendem **V4-Backup** und zeitlich passendem Welt-/Spielerstand. Keine Migrationseinträge löschen oder gefüllte Kisten manuell entfernen.

Bei `Gruppenkiste gesperrt` oder Speicherfehler: betroffene Gruppe nicht auflösen. Server geordnet stoppen, aktuelle DB/WAL/SHM und Welten sichern, Log und Gruppen-ID/Revision prüfen. Bei beschädigtem Blob den Originaldatensatz erhalten; auf einer separaten Kopie einen bekannten guten vollständigen Backupstand mit passender JAR wiederherstellen, `PRAGMA integrity_check` sowie Kisten- und Spielerbestände prüfen. Erst nach erfolgreichem Restore-Test kontrolliert neu starten. Niemals durch eine leere Kiste oder pauschales SQL-Update „reparieren“. Fehlgeschlagene Speichervorgänge werden sichtbar gemeldet und geloggt; der Cache bleibt bis Stop gesperrt.

Kisten-Snapshots folgen einer erlaubten Änderung im nächsten Tick; bei geordnetem Stop wird final geschrieben und die DB-Queue abgearbeitet. Vanilla-Spielerinventar und Plugin-SQLite bilden **keine gemeinsame Transaktion**. Harte Host-/Prozessabstürze können letzte Transfers verlieren oder duplizieren; bei Recovery müssen beide Bestände gemeinsam geprüft werden. Der Prüfplugin injiziert begrenzt SQL-Schreibfehler und beschädigte Payloads; ein echter Prozessabsturz während Client-Transfers bleibt Teil der manuellen Abnahme.

**Offen vor Produktionsfreigabe: mindestens vier echte Vanilla-Clients.** Die Checklisten in `Patch_v1.4_Implementation_Plan.md` vollständig abnehmen: gemeinsames Live-Inventar und Rollen, alle Klick-/Shift-/Drag-/Hotbar-/Offhand-/Double-Click-/Creative-/Bundle-Wege beim Wechsel 4 → 3 → 4, Kick/Leave bei offenem GUI, Disconnect während Laden/Speichern, parallele Entnahmen, Neustart und kontrollierter Crash/Restore, Sonderitems/Data Components, privater Gruppenchat mit Vanish und visuelle Leaderboards. Außerdem bisherige Whitelist-, Spawn-, Sit-, Marriage- und Gruppenrechte-Flows wiederholen. Automatisierte Prüfungen und synthetische Zuschauer erfüllen dieses Gate nicht. Kein Produktionsrollout oder Serverneustart der Forever-World durch diesen Patch.
