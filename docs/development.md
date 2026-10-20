# Entwicklung

## Build

```bash
./gradlew clean check build --no-daemon
```

- Gradle Wrapper und Java-25-Toolchain sind festgelegt; `paper-api` ist exakt auf `26.3.build.207-stable` gepinnt (`compileOnly`).
- CI (`.github/workflows/build.yml`) führt denselben Befehl aus und stellt die JAR als Artefakt bereit. Es gibt **keinen** Deploy-Schritt.

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
