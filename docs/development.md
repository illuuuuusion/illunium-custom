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
