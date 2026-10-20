# IlluniumCustom

Eigenständiges Paper-Plugin für die Illunium Forever-World. Produktvertrag: [`MVP.md`](./MVP.md), Umsetzung: [`Implementation_Plan.md`](./Implementation_Plan.md).

## Voraussetzungen

- Java **25**
- Paper **26.3**, getestet mit Build **207** (`26.3.build.207-stable`)
- Keine weiteren Plugins

## Bauen

```bash
./gradlew clean check build --no-daemon
```

Ergebnis: `build/libs/IlluniumCustom-<version>.jar`. Details und lokaler Teststart: [`docs/development.md`](./docs/development.md).

## Permissions

| Permission | Verwendung | Standard |
| --- | --- | --- |
| `illuniumcustom.use` | Öffentliche Plugin-Kommandos | `true` |
| `illuniumcustom.admin` | Notfall-/Diagnose-Commands | `op` |
