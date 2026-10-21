# Commands und Verhalten

Alle Spieler-Commands benötigen `illuniumcustom.use` (Standard: jeder), alle `/ic`-Commands `illuniumcustom.admin` (Standard: OP). Gruppenrollen sind **keine** Bukkit-Permissions. Alle Commands haben Tab-Completion; Regelverstöße werden mit konkreter Fehlermeldung abgelehnt, ohne etwas zu ändern.

## Admin (`/illuniumcustom`, Alias `/ic`)

| Command | Effekt |
| --- | --- |
| `/ic status` | Version, Schema-Version, aktive Module |
| `/ic group inspect <group>` | gespeicherten Zustand und Leader-Invariante prüfen |
| `/ic group setleader <group> <player>` | Leader neu setzen (Ziel muss Mitglied sein; bisheriger Leader wird Mod) |
| `/ic group disband <group>` | Gruppe sofort auflösen |

Jede Admin-Änderung wird mit Absender geloggt (`[admin] <name> ...`). Auch aus der Konsole nutzbar.

## Recipes (`recipes`)

Shapeless-Rezept `1 × Quartz-Block → 4 × Nether Quartz` (Key `illuniumcustom:quartz_from_quartz_block`), im 2×2- und 3×3-Feld, für alle Spieler ohne Permission. Nur der normale `QUARTZ_BLOCK` zählt, nicht Säule, gemeißelter, glatter Block oder Ziegel. Vanilla-Rezepte bleiben unverändert. Weitere Rezepte: `RecipesModule.recipes()` ergänzen.

## Groups (`groups`)

Soziale Gruppen mit Name (1–16 Zeichen), Tag (1–5 Zeichen, jeweils `A-Z a-z 0-9 _ -`, case-insensitiv eindeutig) und Farbe (eine der 16 Minecraft-Farben oder `#RRGGBB`, Standard `white`). Pro Spieler höchstens eine Gruppe, genau ein Leader, beliebig viele Mods.

| Command | Effekt | Rolle |
| --- | --- | --- |
| `/group create <name> <tag>` | Gruppe gründen | gruppenlos |
| `/group list` · `/group info [group]` | Gruppen / Gruppeninfo | jeder (auch Konsole) |
| `/group members` | Mitglieder mit Rollen | Mitglied |
| `/group invite <player>` · `/group uninvite <player>` | Einladung (5 Minuten gültig, nur an Online-Spieler) senden/zurückziehen | Leader, Mod |
| `/group accept <group>` · `/group deny <group>` | Einladung annehmen/ablehnen | Eingeladener |
| `/group kick <player>` | Member entfernen (Leader auch Mods) | Leader, Mod |
| `/group promote <player>` · `/group demote <player>` | Member ↔ Mod | Leader |
| `/group rename <name>` · `/group tag <tag>` · `/group color <color\|#RRGGBB>` | Darstellung ändern | Leader |
| `/group transfer <player>` · `/group disband` | Leader übertragen (alter Leader wird Mod) / auflösen, jeweils mit Bestätigung | Leader |
| `/group confirm` · `/group cancel` | Bestätigung (30 Sekunden gültig) / Abbruch | Leader |
| `/group leave` | Gruppe verlassen; Leader müssen vorher übertragen oder auflösen | Mod, Member |

Kick/Promote/Demote/Transfer funktionieren auch für offline Mitglieder (letzter bekannter Name). Einladungen und offene Bestätigungen sind flüchtig und verfallen beim Neustart. Bei `accept` wird erneut geprüft: Frist, Gruppe existiert noch, Einladender ist noch Leader/Mod, Spieler ist noch gruppenlos.

Farben: Name case-insensitiv (`red`, `Light_Purple`) oder genau sechsstelliges Hex **mit** `#` (`#ff8800`); `FF8800`, `#FFF`, `#FFFFFFFF`, `#GG0000`, Format-Codes und MiniMessage werden abgelehnt. Gespeichert wird `NAME` bzw. `#RRGGBB` in Großbuchstaben; ein Hex-Wert, der genau einer Standardfarbe entspricht (`#FF5555`), wird als diese Farbe gespeichert. Tab-Completion schlägt die 16 Namen vor.

Admin-Recovery: siehe oben (`/ic group ...`).

## Marriage (`marriage`)

| Command | Effekt |
| --- | --- |
| `/marry <player>` | Antrag an einen Online-Spieler (5 Minuten gültig, pro Empfänger nur einer) |
| `/marry accept` · `/marry deny` | Antrag annehmen/ablehnen |
| `/marry info [player]` | Beziehungsstatus (auch offline Spieler, auch Konsole mit Name) |
| `/divorce` · `/divorce confirm` · `/divorce cancel` | Einseitige Scheidung mit Bestätigung (30 Sekunden) |

Höchstens eine Ehe pro UUID, keine Selbstheirat; Ehen überstehen Quit, Namenswechsel und Neustart, Anträge nicht.

**Herzpartikel:** Sneaken beide Ehepartner in derselben Welt mit höchstens 3,0 Blöcken Abstand (`distanceSquared <= 9`), erscheinen etwa einmal pro Sekunde wenige `HEART`-Partikel zwischen und über ihnen, sichtbar für alle Umstehenden; ein Ereignis pro Paar. Keine Partikel, wenn nur einer sneakt, bei größerem Abstand, anderer Welt, offline oder verborgenem (vanished) Partner.

## Playtime (`playtime`)

| Command | Effekt |
| --- | --- |
| `/playtime` | Eigene Spielzeit |
| `/playtime <player>` | Spielzeit eines bekannten Spielers, auch offline (letzter bekannter Name; Konsole nur mit Name) |

Gezählt wird ab der ersten Aktivierung des Plugins, sekundengenau, Ausgabe als `1d 2h 3m`; die laufende Session ist sofort enthalten. Gespeichert wird beim Quit, alle 60 Sekunden und beim geordneten Shutdown. Bei einem Absturz fehlt höchstens die Zeit seit dem letzten Checkpoint (≤ 60 s pro Spieler); doppelt gezählt wird nichts. Kein Leaderboard, keine AFK-Erkennung.

## Whitelist Guard (`whitelistguard`)

Die **native** Minecraft-Whitelist bleibt die einzige Zugangsliste; es gibt keine zweite Liste und keine eigenen Access-Commands. Spieler werden mit Vanilla verwaltet: `/whitelist add|remove|list|reload` (funktioniert auch für Accounts, die noch nie gejoint sind, solange Mojang erreichbar ist).

- Beim Start und alle `check-interval-seconds` (Standard 30): `white-list` und `enforce-whitelist` werden eingeschaltet, falls aus (Warnung im Log, Wert landet in `server.properties`).
- `staff`: geschützte Server-Staff-Accounts (**nicht** Gruppen-Mods) als `{uuid, name}`. Jeder Eintrag wird pro Start einmal bei Mojang geprüft (UUID muss existieren und aktuell diesen Namen tragen). Nur geprüfte Einträge werden nach einem versehentlichen `/whitelist remove` wieder eingetragen. Fehlerhafte oder nicht auflösbare Einträge werden nie freigeschaltet und als `ERROR` geloggt. Offline-Mode-UUIDs (Version 3) werden abgelehnt.
- Das Plugin entfernt oder ändert **keine** anderen Whitelist-Einträge und vergibt keine Rechte. Ein Operator/die Konsole kann die Whitelist weiterhin bewusst ändern; der Guard repariert Fehlkonfiguration, schützt aber nicht vor böswilligen Admins.
- `online-mode=false` wird bei jedem Start als `ERROR` gemeldet.

Betriebsbasis und Launch-Ablauf: [`operations.md`](./operations.md).

## Sit (`sit`)

| Command | Effekt |
| --- | --- |
| `/sit` | Hinsetzen; erneut `/sit` steht wieder auf |
| `/unsit` | Aufstehen |

Nur auf festem Untergrund (Block, Stufe, Treppe, Teppich, …), nicht beim Fliegen/Gleiten, Schwimmen, in Wasser/Lava, beim Reiten, im Bett, im Fall oder als Zuschauer. Aufstehen per `/unsit` oder `/sit` (der Spieler steht danach wieder an der Stelle, an der er sich gesetzt hat) oder per **neu gedrückter** Taste W/A/S/D, Leertaste, Sneak oder Sprint: dann sofort freie Steuerung ohne Rücksetzen, damit der erste Schritt nicht zurückspringt. Mausbewegung allein und Tasten, die schon beim Hinsetzen gehalten wurden, beenden das Sitzen nicht; Eingaben in den ersten 5 Ticks nach dem Hinsetzen werden ignoriert. Der Sitz endet außerdem bei Schaden, Tod, Teleport, Weltwechsel, Quit und Plugin-Stop.

## Vanish (`vanish`)

| Command | Effekt |
| --- | --- |
| `/vanish` | Für andere Spieler verbergen; war man sichtbar, sehen alle einmal gelb `Name left the game` |
| `/unvanish` | Wieder sichtbar (Tablist, Entity), **ohne** Join-Nachricht |

- Nur **Vanilla-Operatoren** (`isOp()`, bei jeder Ausführung geprüft, nur im Spiel). Keine Bukkit-Permission, Gruppenrolle oder `illuniumcustom.admin` reicht; für Nicht-OPs ist der Command nicht sichtbar. Wiederholte Aufrufe sind idempotent (keine doppelte Nachricht).
- Jeder OP joint **verborgen**: keine Join-Nachricht, kein Tablist-Eintrag, keine Entity für andere Spieler (`hidePlayer` pro Zuschauer, auch für später joinende). Der Zustand ist flüchtig: nach `/unvanish` gilt er bis `/vanish` oder Quit, beim nächsten Login wieder verborgen.
- Verborgene OPs: keine Quit-, Todes- und Advancement-Nachricht; nicht in Serverlisten-Spielerzahl/-Probe, nicht in Completions von `/marry`, `/group invite`, `/playtime`; `/marry`/`/group invite` an sie meldet „nicht online“; Partner sehen das Offline-Herz, `/marry info` zeigt „offline“, keine Herzpartikel.
- Verliert ein verborgener Spieler OP, wird er innerhalb einer Sekunde sichtbar. Beim Plugin-Stop werden alle wieder sichtbar.
- Kein Spectator-, God- oder NoClip-Modus. Konsole und Log sehen weiter alles.
- **Grenzen:** Chat, Blockänderungen, Geräusche und direkte Interaktion verraten Anwesenheit. `/playtime <OP>` wächst weiterhin (Checkpoint alle 60 s). Das Gruppen-Team auf dem Haupt-Scoreboard enthält den Namen auch im Paket an andere Clients (nicht sichtbar, nur per Protokollanalyse). Vanilla-`/list` und Vanilla-Namensvorschläge filtern laut CraftBukkit nach `canSee`; Abnahme mit echten Clients steht aus.

## Spawn (`spawn`)

| Command | Effekt |
| --- | --- |
| `/spawn` | Teleport zum Welt-Spawn nach 10 Sekunden Warmup (`illuniumcustom.use`, nur Spieler) |

Ziel ist der Welt-Spawn, den Vanilla `/setworldspawn` setzt (`Server#getRespawnWorld`, Standard: Overworld), bei jedem Aufruf neu gelesen — nie Bett/Anker. Kein eigener Spawn, kein `/setspawn`. Ist der Spawn-Block nicht frei (fester Boden, zwei freie Blöcke ohne Flüssigkeit/Feuer), wird die Oberkante derselben Spalte versucht, sonst abgelehnt (beim Start und erneut beim Teleport). Der Spawn-Chunk wird asynchron geladen. Sitzende werden beim Teleport vom Sit-Modul abgesetzt; Vanish bleibt unverändert.

Regeln (Patch v1.2, feste Werte, OPs ohne Ausnahme):

- **Warmup 10 s:** Startmeldung, danach Restzeit in der Actionbar. Teleport frühestens nach 10 s (monotone Uhr; Server-Lag verlängert, verkürzt nie). Erneutes `/spawn` zeigt die Restzeit, startet nichts neu.
- **Abbruch ohne Cooldown** bei Positionsänderung > 0,001 Block (X/Y/Z; Kopfdrehen und Sneaken auf der Stelle zählen nicht), wirksamem Schaden jeder Art (Endschaden > 0, z. B. kein Abbruch bei vollständig geblocktem Schildtreffer), PvP-Beteiligung, fremdem Teleport, Weltwechsel, Tod, Quit und Plugin-Stop. Abbruch hat Vorrang, auch während der Spawn-Chunk für den Teleport lädt.
- **Combat-Tag 30 s:** Fügt ein Spieler einem anderen wirksamen Schaden zu (Nahkampf oder alles, was Paper per `DamageSource#getCausingEntity` einem Spieler zuordnet, z. B. Pfeile), werden **beide** getaggt; jeder weitere Treffer setzt 30 s ab jetzt. Solange verweigert `/spawn` den Start mit Restzeit. Monster-, Umwelt- und Selbstschaden taggen nie (brechen aber einen laufenden Warmup ab).
- **Cooldown 60 min** pro UUID, beginnt erst nach erfolgreichem Teleport; Anzeige `in 42 min 18 s`. Gespeichert in `spawn_cooldowns` (SQLite), übersteht Stop/Start. Abgebrochene, blockierte oder per Event gecancelte Teleports kosten nichts.
- **Bewusst nicht:** keine Monster-Nähe-Prüfung, keine Logout-Strafe, kein Combat-Body. Der Combat-Tag ist flüchtig und endet beim Quit; ein Relog umgeht ihn (akzeptierter Tradeoff, kein Anti-Combat-Log gewünscht).

| Antwort | Bedeutung |
| --- | --- |
| `Teleporting to spawn in 10 seconds. Don't move!` | Warmup läuft |
| `Teleport to spawn cancelled: you moved.` / `you took damage.` / `you are in combat.` / `you were teleported.` | Abbruch, kein Cooldown |
| `You are in combat: /spawn is locked for 24 s.` | Combat-Tag aktiv |
| `/spawn is available again in 42 min 18 s.` | Cooldown aktiv |
| `Teleported to spawn.` → `Next /spawn in 60 minutes.` | Erfolg; zweite Zeile erst nach dem DB-Commit |
| `Your cooldown could not be saved; /spawn stays locked anyway.` | DB-Fehler (Log `SEVERE`), Sperre bleibt im Speicher |

## Difficulty (Vanilla, kein Modul)

Es gibt **keinen** Plugin-Command; Vanilla `/difficulty <peaceful|easy|normal|hard>` persistiert bereits (Gate P6, siehe [`development.md`](./development.md)). Der Wert steht in den Weltdaten, `difficulty=` in `server.properties` gilt nur für neu erzeugte Welten und überschreibt beim Start nicht. Auf Paper wirkt `/difficulty` **nur auf die Welt, in der der Befehl ausgeführt wird** (Konsole: Overworld). Für alle Dimensionen:

```text
difficulty hard
execute in minecraft:the_nether run difficulty hard
execute in minecraft:the_end run difficulty hard
```

## Anzeige (Nametag/Tablist)

- Über dem Kopf: `[TAG] Name` (Tag in Gruppenfarbe, auch Hex), Tablist: `[TAG] Name ❤`.
- Herz nur in der Tablist, für alle Zuschauer: rotes `❤` wenn der Partner online und nicht verborgen ist, sonst graues `💔` (`marriage.offline-symbol`).
- Kompatibilität mit anderen Plugins, die Scoreboard-Teams oder Tablist-Namen setzen, ist kein MVP-Ziel (Details: [`architecture.md`](./architecture.md)).
