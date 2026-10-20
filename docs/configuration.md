# Konfiguration

`plugins/IlluniumCustom/config.yml` wird beim ersten Start aus der mitgelieferten Vorlage angelegt. Änderungen gelten **nach Neustart** (kein Hot-Reload). Ungültige Werte, unbekannte Module oder eine fehlerhafte Staff-Liste verhindern den Start des ganzen Plugins mit einer präzisen Meldung im Log; es werden keine Daten gelöscht. Secrets gehören nicht in diese Datei, das MVP braucht keine.

```yaml
modules:                      # <modul>: true|false; fehlende Module sind aktiv
  recipes: true
  groups: true
  marriage: true
  playtime: true
  whitelistguard: true
  sit: true

storage:
  filename: data.db           # SQLite-Datei im Pluginordner, nur einfacher Dateiname

marriage:
  offline-symbol: "💔"         # Tablist-Zeichen bei offline Partner, nicht leer; Fallback z. B. "✕❤"

whitelistguard:
  check-interval-seconds: 30  # 5..3600
  staff:                      # geschützte Server-Staff-Accounts (keine Gruppen-Mods)
    - {uuid: "069a79f4-44e9-4726-a5be-fca90e38aaf5", name: "Notch"}
```

| Schlüssel | Standard | Regel |
| --- | --- | --- |
| `modules.<id>` | `true` | nur bekannte Module, nur `true`/`false`. Ein deaktiviertes Modul behält seine Daten in `data.db`. |
| `storage.filename` | `data.db` | `^[A-Za-z0-9_-][A-Za-z0-9._-]*$`, liegt immer im Pluginordner |
| `marriage.offline-symbol` | `💔` | nicht leer; falls der Vanilla-Font `💔` nicht darstellt, `"✕❤"` setzen |
| `whitelistguard.check-interval-seconds` | `30` | 5–3600; Prüfung ohne Netzwerkzugriff |
| `whitelistguard.staff` | `[]` | Liste von `{uuid, name}`. `uuid` mit Bindestrichen, Version 4 (Mojang-Account, keine Offline-UUID), keine Duplikate; `name` 1–16 Zeichen `A-Z a-z 0-9 _`. |

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
