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

Soziale Gruppen mit Name (1–16 Zeichen), Tag (1–5 Zeichen, jeweils `A-Z a-z 0-9 _ -`, case-insensitiv eindeutig) und Farbe (eine der 16 Minecraft-Farben, Standard `white`). Pro Spieler höchstens eine Gruppe, genau ein Leader, beliebig viele Mods.

| Command | Effekt | Rolle |
| --- | --- | --- |
| `/group create <name> <tag>` | Gruppe gründen | gruppenlos |
| `/group list` · `/group info [group]` | Gruppen / Gruppeninfo | jeder (auch Konsole) |
| `/group members` | Mitglieder mit Rollen | Mitglied |
| `/group invite <player>` · `/group uninvite <player>` | Einladung (5 Minuten gültig, nur an Online-Spieler) senden/zurückziehen | Leader, Mod |
| `/group accept <group>` · `/group deny <group>` | Einladung annehmen/ablehnen | Eingeladener |
| `/group kick <player>` | Member entfernen (Leader auch Mods) | Leader, Mod |
| `/group promote <player>` · `/group demote <player>` | Member ↔ Mod | Leader |
| `/group rename <name>` · `/group tag <tag>` · `/group color <color>` | Darstellung ändern | Leader |
| `/group transfer <player>` · `/group disband` | Leader übertragen (alter Leader wird Mod) / auflösen, jeweils mit Bestätigung | Leader |
| `/group confirm` · `/group cancel` | Bestätigung (30 Sekunden gültig) / Abbruch | Leader |
| `/group leave` | Gruppe verlassen; Leader müssen vorher übertragen oder auflösen | Mod, Member |

Kick/Promote/Demote/Transfer funktionieren auch für offline Mitglieder (letzter bekannter Name). Einladungen und offene Bestätigungen sind flüchtig und verfallen beim Neustart. Bei `accept` wird erneut geprüft: Frist, Gruppe existiert noch, Einladender ist noch Leader/Mod, Spieler ist noch gruppenlos.

Admin-Recovery: siehe oben (`/ic group ...`).

## Marriage (`marriage`)

| Command | Effekt |
| --- | --- |
| `/marry <player>` | Antrag an einen Online-Spieler (5 Minuten gültig, pro Empfänger nur einer) |
| `/marry accept` · `/marry deny` | Antrag annehmen/ablehnen |
| `/marry info [player]` | Beziehungsstatus (auch offline Spieler, auch Konsole mit Name) |
| `/divorce` · `/divorce confirm` · `/divorce cancel` | Einseitige Scheidung mit Bestätigung (30 Sekunden) |

Höchstens eine Ehe pro UUID, keine Selbstheirat; Ehen überstehen Quit, Namenswechsel und Neustart, Anträge nicht.

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

Nur auf festem Untergrund (Block, Stufe, Treppe, Teppich, …), nicht beim Fliegen/Gleiten, Schwimmen, in Wasser/Lava, beim Reiten, im Bett, im Fall oder als Zuschauer. Aufstehen per Sneaken, `/unsit` oder `/sit`; der Sitz endet außerdem bei Schaden, Tod, Teleport, Weltwechsel, Quit und Plugin-Stop. Nach dem Aufstehen steht der Spieler wieder an der Stelle, an der er sich gesetzt hat.

## Anzeige (Nametag/Tablist)

- Über dem Kopf: `[TAG] Name` (Tag in Gruppenfarbe), Tablist: `[TAG] Name ❤`.
- Herz nur in der Tablist, für alle Zuschauer: rotes `❤` wenn der Partner online ist, sonst graues `💔` (`marriage.offline-symbol`).
- Kompatibilität mit anderen Plugins, die Scoreboard-Teams oder Tablist-Namen setzen, ist kein MVP-Ziel (Details: [`architecture.md`](./architecture.md)).
