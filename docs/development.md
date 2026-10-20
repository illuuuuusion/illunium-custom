# Entwicklung

## Build

```bash
./gradlew clean check build --no-daemon
```

- Gradle Wrapper und Java-25-Toolchain sind festgelegt; `paper-api` ist exakt auf `26.3.build.207-stable` gepinnt (`compileOnly`).
- CI (`.github/workflows/build.yml`) führt denselben Befehl aus und stellt die JAR als Artefakt bereit. Bei Push auf `main` ersetzt der Job `release` das Pre-Release `latest` (Tag wird auf den neuen Commit verschoben) mit `IlluniumCustom.jar` und `.sha256`. Es gibt **keinen** Deploy auf einen Server.

## Lokaler Teststart

Nur auf einem lokalen bzw. getrennten Testserver, nie gegen die Forever-World.

```bash
mkdir -p run/plugins && cd run
curl -o paper.jar https://fill-data.papermc.io/v1/objects/2df3b846f70716907d380915cc026667a9cadedf99b1ec680b0c5ff0e6132526/paper-26.3-207.jar
echo "2df3b846f70716907d380915cc026667a9cadedf99b1ec680b0c5ff0e6132526  paper.jar" | sha256sum -c
echo eula=true > eula.txt
cp ../build/libs/IlluniumCustom-*.jar plugins/
java -jar paper.jar --nogui
```

`run/` ist in `.gitignore`. Erwartung im Log: `Enabling IlluniumCustom`, beim `stop` `Disabling IlluniumCustom`, keine `ERROR`/`SEVERE`-Zeilen des Plugins.

## Architektur-Kern

- **Bootstrap** (`IlluniumCustomPlugin`): Config laden/validieren → SQLite öffnen + migrieren → `PlayerIdentityService` → Module in Registry-Reihenfolge aktivieren → `/ic` registrieren. Jeder Fehler deaktiviert das **ganze** Plugin (kein Teilstart). Disable: Module in umgekehrter Reihenfolge, danach DB-Queue abarbeiten und schließen.
- **Storage** (`storage.Database`): eine SQLite-Connection auf einem eigenen Thread (`IlluniumCustom-DB`); `transaction(work)` läuft geordnet, committet oder rollt zurück und liefert ein `CompletableFuture`. In `work` **keine** Bukkit-API; Folgeschritte mit `.thenAcceptAsync(..., context.mainThread())` zurück auf den Server-Thread. Nur Startup blockiert auf Storage.
- **Migrationen**: `src/main/resources/db/migrations/V<n>__*.sql`, in `Database.MIGRATIONS` angehängt. Ausgelieferte Skripte nie ändern, sondern neue additive Migration. Ein neueres Schema als bekannt oder kollidierende Tabellen brechen den Start ab; es wird nichts gelöscht.
- **Identitäten**: UUID ist die Identität, `last_known_name` wird beim Join aktualisiert. `findByName` kann bei Namenswechseln mehrere UUIDs liefern.

## Neues Modul hinzufügen

1. Paket `de.illunium.custom.<feature>` mit einer `FeatureModule`-Implementierung (`id()`, `enable(ModuleContext)`, `disable()`).
2. Eintrag in `core/module/Modules.create()` und `modules.<id>: true` in `src/main/resources/config.yml` (`ModulesTest` prüft beides).
3. Eigene Einstellungen unter `<id>:` in `config.yml`; sie kommen als `context.settings()` an und werden in `enable` validiert.
4. Persistente Daten nur über eine neue Migration; kein SQL auf Tabellen anderer Module.
5. Commands über `context.plugin().registerCommand(...)` (Paper `BasicCommand`), Listener über den PluginManager. `disable()` muss auch nach einem fehlgeschlagenen `enable` aufräumen.

## Grenzen

- **Kein Hot-Reload.** Paper 26.3 hat kein `/reload` mehr; ein Disable/Enable zur Laufzeit (z. B. durch Plugin-Manager) scheitert an der Command-Registrierung. Das Plugin meldet das als `ERROR`, deaktiviert sich und räumt seine Rezepte auf. Änderungen immer per Server-Neustart.

## Verifikation Phase 0–2 (20.10.2026, Paper 26.3 Build 207, Temurin 25.0.4.1)

- `./gradlew clean check build --no-daemon`: 18 Tests grün, JAR bei zwei Builds bytegleich.
- Lokaler Testserver ohne andere Plugins: Enable/Disable ohne Plugin-Fehler, zweiter Start migriert nicht erneut, ungültige Config bricht den Start sichtbar ab, `recipes: false` registriert nichts.
- Rezept per `Server#getCraftingRecipe`/`craftItemResult` mit Wegwerf-Prüfplugin: verschiedene Gitterpositionen und Stack-Eingabe → genau 4 Quartz; Säule, gemeißelt, glatt, Ziegel und 2 Blöcke kein Treffer; Vanilla `4 Quartz → Quartz-Block` intakt; ein vorab unter unserem Key registriertes Fremdrezept wird ersetzt; genau ein Rezept im Namespace `illuniumcustom` nach jedem Start. Die API akzeptiert nur 3×3-Matrizen; das echte 2×2-Inventarfeld wird im Client-E2E (Phase 8) abgenommen.
