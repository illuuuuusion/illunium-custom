# Architektur

Eine Paper-JAR, intern getrennte Module mit explizitem Vertrag (`core.module.FeatureModule`) und einer festen Registry (`core.module.Modules`). Reihenfolge: `recipes`, `groups`, `marriage`, `playtime`, `whitelistguard`, `sit`. Module sprechen nicht gegenseitig SQL und rufen keine fremden Commands auf; gemeinsame Dienste kommen über `ModuleContext`.

```text
Paper events / commands
        │
Feature Commands/Listener ──► Feature Service (Regeln, Policy)
                                   ├──► Database (SQLite, eigener Thread)
                                   └──► AppearanceService (nur Daten melden)
                                              └──► Haupt-Scoreboard / Tablist
```

## Kern

- **Bootstrap** (`IlluniumCustomPlugin`): Config laden/validieren → SQLite öffnen + migrieren → `PlayerIdentityService` → Module in Registry-Reihenfolge aktivieren → `/ic` registrieren. Jeder Fehler deaktiviert das **ganze** Plugin (kein Teilstart). Disable: Module in umgekehrter Reihenfolge, danach DB-Queue abarbeiten und schließen.
- **Storage** (`storage.Database`): eine SQLite-Connection auf einem eigenen Thread (`IlluniumCustom-DB`); `transaction(work)` läuft geordnet, committet oder rollt zurück und liefert ein `CompletableFuture`. In `work` **keine** Bukkit-API; Folgeschritte mit `.thenAcceptAsync(..., context.mainThread())` zurück auf den Server-Thread. Nur Startup blockiert auf Storage.
- **Migrationen**: `src/main/resources/db/migrations/V<n>__*.sql`, in `Database.MIGRATIONS` angehängt. Ausgelieferte Skripte nie ändern, sondern neue additive Migration. Ein neueres Schema als bekannt oder kollidierende Tabellen brechen den Start ab; es wird nichts gelöscht.
- **Identitäten**: UUID ist die Identität, `last_known_name` wird beim Join aktualisiert. `findByName` kann bei Namenswechseln mehrere UUIDs liefern.
- **Mutationen** (Muster aus `groups`): Der Service prüft alle Regeln **in** der Transaktion gegen den committeten Zustand und lädt die betroffene Zeile im selben Commit neu; der In-Memory-Stand wird erst danach auf dem Main Thread ersetzt. Gleichzeitige Commands laufen dadurch geordnet und hinterlassen keine halben Änderungen; Constraints in der DB sind die zweite Sicherung. Regelverstöße sind `UserException` (Text geht an den Spieler), `context.reply(...)` liefert Erfolg/Fehler auf dem Main Thread aus und loggt alles andere als `SEVERE`.
- **Appearance** (`appearance.AppearanceService`, Core-Service vor den Modulen): einziger Eigentümer von Nametag/Tablist. Module melden nur Daten (`setPrefix` = Nametag + Tablist, `setSuffix` = nur Tablist) für Online-Spieler; Offline-Meldungen werden ignoriert, Module melden beim Join erneut. Jede Änderung rendert genau einen Spieler und nur, wenn sich der Wert geändert hat. Entscheidung: **Haupt-Scoreboard** (alle Zuschauer sehen dasselbe, keine per-Viewer-Boards nötig), ein Team `illuniumcustom_<uuid>` pro Spieler mit Prefix; in der Tablist ein expliziter `playerListName` (Prefix + Name + Suffix). Der Vanilla-Client zeigt bei gesetztem Tablist-Namen die Team-Dekoration nicht zusätzlich an, daher kein doppelter Tag. Das Haupt-Scoreboard wird mit der Welt gespeichert, deshalb räumt der Start alle eigenen Teams auf.
- **Flüchtige Anfragen** (Einladungen, Bestätigungen): `core.ExpiringMap`, gültig solange `jetzt < erstellt + TTL`, nur Main Thread, kein Persistieren.


## Grenzen

- **Kein Hot-Reload.** Paper 26.3 hat kein `/reload` mehr; ein Disable/Enable zur Laufzeit (z. B. durch Plugin-Manager) scheitert an der Command-Registrierung. Das Plugin meldet das als `ERROR`, deaktiviert sich und räumt seine Rezepte auf. Änderungen immer per Server-Neustart.


## Module mit Weltzustand

- **Whitelist Guard** (`whitelistguard`): kein eigener Speicher. `WhitelistGuard.check()` läuft beim Start und per Bukkit-Timer auf dem Main Thread, liest und setzt nur `Server#hasWhitelist`/`isWhitelistEnforced` und `OfflinePlayer#isWhitelisted` der verifizierten Staff-UUIDs. Die Mojang-Prüfung (`PlayerProfile#update`, async) läuft einmal pro Start; ihr Ergebnis wird über `context.mainThread()` übernommen. Ohne Verifikation wird nie eingetragen.
- **Sit** (`sit`): flüchtiger Zustand `Map<Spieler-UUID, Seat>` auf dem Main Thread; Eintrag vorhanden = `SITTING`. Sitz = unsichtbarer Marker-ArmorStand mit PDC-Tag `illuniumcustom:seat` und `persistent=false` (wird nie mit dem Chunk gespeichert). Aufräumen: Disable, Quit (vor dem Speichern der Spielerdaten), Tod, Teleport, Weltwechsel, Schaden, Dismount, Entity-Unload; Orphans (z. B. nach Absturz als Fahrzeug in den Spielerdaten) beim Start, Entity-Load und Join. Gelöscht werden nur ArmorStands mit unserem Tag.
- **Appearance**: siehe oben; einziger Eigentümer der Teams `illuniumcustom_*` und der vom Plugin gesetzten Tablist-Namen. Ein Spieler, den ein anderes Plugin oder `/team` in ein eigenes Team setzt, verliert dort beim Dekorieren seine Teamzugehörigkeit — Kompatibilität mit anderen Scoreboard-Plugins ist kein MVP-Ziel.

## Security-Grenzen

- Eingaben: Gruppenname/Tag per ASCII-Regex und Länge, Farben aus fester Liste, Spieler per UUID; Staff-Config nur mit Mojang-UUID (v4) und Profilname. Alle SQL-Statements sind konstante Strings mit gebundenen Parametern.
- Permissions: jeder Spieler-Command prüft `illuniumcustom.use`, `/ic` prüft `illuniumcustom.admin`. Gruppenrollen sind Fachrollen und geben keine Server-Rechte; Staff-Schutz entsteht nur aus `config.yml`.
- Threading: Bukkit-API nur auf dem Main Thread; DB-Arbeit und Profil-Lookups liefern Futures, deren Folgeschritte über `context.mainThread()` laufen und nach dem Disable verworfen werden.
- Fremdes wird nicht gelöscht: nur eigene Teams (Präfix), eigene Rezepte (Namespace), eigene Sitz-Entities (PDC-Tag); Whitelist-Einträge werden nie entfernt.
- Der Guard schützt nicht vor einem böswilligen Operator oder Konsolenzugang.
