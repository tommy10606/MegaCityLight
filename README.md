# MegaCityLight 1.0.0

Permission-free, persistent night vision for MegaCityCraft. Built against the
Paper 26.2 public API. No libraries or other plugins need installing.

## Install

1. Stop the Minecraft server.
2. Remove the old Light plugin JAR from `plugins/` so it cannot compete for `/light`.
3. Put `MegaCityLight-1.0.0.jar` in `plugins/`.
4. Start the server normally. No permissions or OP configuration is needed.

## Commands

| Command | Behavior |
| --- | --- |
| `/light` | Toggle your saved night-vision setting. |
| `/light on` or `/light enable` | Enable it; repeated use keeps it on. |
| `/light off` or `/light disable` | Disable it; repeated use keeps it off. |

Arguments are case-insensitive and support tab completion. The command only
affects the player running it; console use shows an explanatory message.
Invalid arguments show usage without changing the setting. Messages match the
provided examples: gold `Lights ` followed by green `on!` or red `off!`.

## Behavior

- New players start with the setting off.
- Enabled players receive actual infinite Night Vision I, with the icon visible
  and particles hidden. It does not expire or need periodic refreshing.
- Settings are identified by UUID and saved immediately to
  `plugins/MegaCityLight/players.yml`; player name changes do not reset them.
- Reconnecting and restarting the server preserve the choice.
- Death does not switch the setting off; night vision returns after respawn.
- Successfully drinking milk switches the setting off permanently until the
  player enables it again. Cancelled consumption does not switch it off.
- Other food does not change the setting. Milk retains vanilla behavior and
  clears other effects too.
- Enabling replaces any existing night-vision potion. Disabling removes current
  night vision, while leaving other potion effect types alone.
- `/light` toggles the saved plugin setting, rather than detecting whether a
  player currently has night vision from a normal potion.
- There is no polling task that fights vanilla milk or other plugins removing
  effects. An external effect-clearing command can remove the effect until the
  player uses `/light on`, reconnects, or respawns.

Keep `players.yml` when upgrading or moving this plugin. The old Light plugin's
saved choices are not automatically imported.

## Version compatibility

`api-version: '26.2'` declares the minimum supported API, not an exact-version
lock. Future Paper versions can load it if they retain the public APIs used here.
The plugin avoids Minecraft internals, reflection, version-specific packets,
and server-version checks to minimize the need for updates. Future breaking
API changes can still require an update; future releases have not been tested.
Paper 26.2 runs on Java 25; this JAR is compiled for Java 25.

## Build and test

Use JDK 25 and Maven 3.9 or newer:

```sh
mvn clean package
```

The installable file is `target/MegaCityLight-1.0.0.jar`. Paper dependencies are
provided by the server; test dependencies are not bundled into the JAR.

The automated suite uses MockBukkit for Paper 26.2. It covers non-OP access,
toggle and idempotent aliases, invalid input, independent players, infinite
duration and display flags, death, milk and cancelled consumption, reconnects,
plugin disable/enable, UUID-based disk persistence, and queued restore tasks.
MockBukkit does not reproduce vanilla's post-event effect removal, so those
removals are explicitly simulated in the tests. A full live client/server
gameplay test is not included.

## Quick in-game check

Join as a non-OP player and run `/light`. Verify night vision and the icon, then
run `/light on` twice to confirm it stays on. Respawn, reconnect, and restart
to check restoration. Drink milk and confirm it stays off after respawn and
reconnect. Run `/light enable` to restore it, then `/light disable` twice to
confirm it stays off.
