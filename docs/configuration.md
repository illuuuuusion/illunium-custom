# Konfiguration

`plugins/IlluniumCustom/config.yml` wird beim ersten Start aus der mitgelieferten Vorlage angelegt. Änderungen gelten **nach Neustart** (kein Hot-Reload). Ungültige Werte, unbekannte Module oder eine fehlerhafte Staff-Liste verhindern den Start des ganzen Plugins mit einer präzisen Meldung im Log; es werden keine Daten gelöscht. Secrets gehören nicht in diese Datei, das MVP braucht keine.

```yaml
modules:                      # <modul>: true|false; fehlende Module sind aktiv
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

storage:
  filename: data.db           # SQLite-Datei im Pluginordner, nur einfacher Dateiname

marriage:
  offline-symbol: "💔"         # Tablist-Zeichen bei offline Partner, nicht leer; Fallback z. B. "✕❤"

whitelistguard:
  check-interval-seconds: 30  # 5..3600
  staff:                      # geschützte Server-Staff-Accounts (keine Gruppen-Mods)
    - {uuid: "069a79f4-44e9-4726-a5be-fca90e38aaf5", name: "Notch"}

vanish:
  auto-vanish-default: true
  auto-vanish-overrides:
    "11111111-1111-4111-8111-111111111111": false # Platzhalter: eigener Survival-Admin

leaderboard:
  cache-seconds: 60           # 30..3600 Sekunden nach abgeschlossenem Snapshot
```

| Schlüssel | Standard | Regel |
| --- | --- | --- |
| `modules.<id>` | `true` | nur bekannte Module, nur `true`/`false`. Ein deaktiviertes Modul behält seine Daten in `data.db`. |
| `vanish.auto-vanish-default` | `true` | nur Boolean; automatisches Verbergen ausschließlich für Vanilla-OPs |
| `vanish.auto-vanish-overrides` | `{}` | kanonische UUID mit Bindestrichen → Boolean; Namen, Strings wie `"false"` und ungültige UUIDs verhindern den Start |
| `leaderboard.cache-seconds` | `60` | Ganzzahl 30–3600; alte Snapshots bleiben während der nächsten Berechnung verfügbar |
| `storage.filename` | `data.db` | `^[A-Za-z0-9_-][A-Za-z0-9._-]*$`, liegt immer im Pluginordner |
| `marriage.offline-symbol` | `💔` | nicht leer; falls der Vanilla-Font `💔` nicht darstellt, `"✕❤"` setzen |
| `whitelistguard.check-interval-seconds` | `30` | 5–3600; Prüfung ohne Netzwerkzugriff |
| `whitelistguard.staff` | `[]` | Liste von `{uuid, name}`. `uuid` mit Bindestrichen, Version 4 (Mojang-Account, keine Offline-UUID), keine Duplikate; `name` 1–16 Zeichen `A-Z a-z 0-9 _`. |

`spawn` hat keine eigenen Schlüssel: Warmup 10 s, Cooldown 60 min und Combat-Tag 30 s sind feste Produktwerte aus Patch v1.2; Cooldowns liegen pro UUID in `data.db`, nicht in der Config.

## Staff-Einträge

- UUID und aktuellen Namen z. B. aus der `whitelist.json` übernehmen, nachdem der Account per `/whitelist add <Name>` eingetragen wurde, oder aus `usercache.json` nach einem Join.
- Pro Start prüft das Plugin jeden Eintrag einmal bei Mojang (UUID muss existieren und aktuell den konfigurierten Namen tragen, Groß-/Kleinschreibung egal). Nur geprüfte Einträge werden geschützt, d. h. nach einem `/whitelist remove` innerhalb eines Prüfintervalls wieder eingetragen.
- Log-Meldungen: `Staff <name> (<uuid>) verified and protected` (OK), `could not be resolved` (UUID unbekannt oder Mojang nicht erreichbar → bis zum nächsten Neustart ungeschützt), `belongs to '<x>', not '<name>'` (Namenswechsel oder Tippfehler → Config korrigieren). Bestehende Whitelist-Einträge bleiben in jedem Fall unverändert.
- Vom Guard eingetragene Accounts erscheinen in `whitelist.json` ggf. mit leerem Namen, bis sie einmal gejoint sind; `/whitelist remove <Name>` funktioniert trotzdem.

## `server.properties` (Betriebsbasis)

```properties
online-mode=true
white-list=true
enforce-whitelist=true
```

`white-list` und `enforce-whitelist` setzt der Guard beim Start und zyklisch wieder auf `true` (Warnung im Log, persistiert in `server.properties`). `online-mode=false` kann er nicht ändern und meldet es bei jedem Start als `ERROR`.

## Patch v1.3

Die UUIDs im Beispiel sind Platzhalter. Für einen sichtbaren Survival-Admin die echte Minecraft-Java-UUID auf `false` setzen und den Server vollständig neu starten. Ohne diese neuen Schlüssel bleibt jeder OP automatisch verborgen. Das beeinflusst nur den nächsten Join, keine manuelle Vanish-Entscheidung während der Sitzung und keine OP-Berechtigung. Nicht-OPs werden nie automatisch verborgen. Alle OPs bleiben aus öffentlichen Leaderboards ausgeschlossen, auch sichtbare Survival-Admins.

`leaderboard` hat stets zehn Zeilen pro Seite und keinen OP-Teilnahme-Schalter. Ohne `playtime` bleibt nur diese Kategorie nicht verfügbar. Mit `modules.leaderboard: false` werden weder Commands noch Listener/Snapshots aktiviert; persistente Zähler bleiben erhalten. Bei `modules.whiteliststaging: false` bleiben sowohl SQLite-Vormerkungen als auch vorhandene native Freigaben unverändert. Staging hat keine zusätzlichen Konfigurationswerte.
