# Betrieb

Gilt für Testserver und Forever-World. **Produktions-Rollouts sind ein separater, manuell freigegebener Schritt**; nichts in diesem Repository deployt automatisch.

## Versionen (Release Candidate 0.1.0-rc.1)

| | |
| --- | --- |
| Paper | 26.3 Build 207 (`26.3-207-main@bc68acd`, API `26.3.build.207-stable`) |
| Java | 25 (getestet: Temurin 25.0.4.1) |
| SQLite-Schema | v2 ab Patch v1.2 (`V1__initial_schema.sql`, `V2__spawn_cooldowns.sql`); 0.1.0-rc.1 und v1.1 kennen nur v1 |
| Config | Vorlage aus dieser Version, ohne Versionsfeld; neue Schlüssel haben Standardwerte |
| JAR | `IlluniumCustom-0.1.0-rc.1.jar`, SHA-256 aus `SHA256SUMS`/`IlluniumCustom.jar.sha256` des CI-Builds; lokal reproduzierbar |

## Rollout-Ablauf

1. CI grün, JAR + Hash + `build-info.txt` aus dem Build des freizugebenden Commits.
2. Auf dem **separaten Testserver** installieren, Startup-Smoke (Log ohne `ERROR`/`SEVERE` des Plugins, `/ic status` zeigt alle Module) und Gameplay-Abnahme (unten).
3. Review und dokumentierte Freigabe.
4. Vor dem Rollout sichern: alte Plugin-JAR, `plugins/IlluniumCustom/` (online per `.backup`, s. u.) und `whitelist.json`.
5. Server stoppen, JAR ersetzen, starten. Frisches Log prüfen, `/ic status`, kurze Spieltests.
6. Bei FAIL: stoppen, Ursache dokumentieren, Rollback (s. u.), erneut prüfen.

Kein Hot-Reload: Plugin-Updates und Config-Änderungen immer per Server-Neustart.

## Backup

Zu sichern sind `plugins/IlluniumCustom/data.db`, `plugins/IlluniumCustom/config.yml` und `whitelist.json` (gehört zum Server-Backup, nicht zur Plugin-DB).

- **Online** (Server läuft): `sqlite3 plugins/IlluniumCustom/data.db ".backup /pfad/backup.db"` — konsistenter Snapshot über die SQLite-Backup-API. Danach `sqlite3 /pfad/backup.db "PRAGMA integrity_check"` → `ok`.
- **Offline** (Server gestoppt): `data.db` plus vorhandene `data.db-wal`/`data.db-shm` gemeinsam kopieren.
- Niemals nur `data.db` im laufenden Betrieb kopieren (WAL-Modus, Snapshot wäre unvollständig).

## Restore

1. Server stoppen.
2. `data.db` durch das Backup ersetzen, alte `data.db-wal`/`data.db-shm` löschen.
3. Passende `config.yml` und ggf. `whitelist.json` zurückspielen.
4. Starten, Log und `/ic status` prüfen.

Was nach einem Restore fehlt: alle Änderungen nach dem Backup-Zeitpunkt (Gruppen, Ehen, Playtime). Einladungen, Anträge und Bestätigungen sind ohnehin flüchtig.

## Rollback

- **Nur JAR**, wenn die Ziel-JAR dasselbe Schema kennt (siehe Tabelle oben / `/ic status`).
- Kennt die alte JAR ein neueres Schema in `data.db` nicht, bricht sie den Start mit `Database schema vN is newer than supported vM` ab und deaktiviert sich, ohne Daten anzufassen. Dann: alte JAR **plus** DB-Backup aus der Zeit vor dem Update zurückspielen.
- Vor jedem schemaändernden Update ist ein DB-Backup Pflicht. Patch v1.2 hebt das Schema auf v2: Rollback auf eine v1.1-JAR braucht das Backup von vorher (dabei gehen nur seither gesetzte Spawn-Cooldowns verloren); die DB nie von Hand auf v1 zurücksetzen.

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
