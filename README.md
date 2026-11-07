# MegaCityLight 1.0.2

Permission-free, persistent night vision for MegaCityCraft. Built against the
Paper 26.3 build 244 public API. No libraries or other plugins need installing.

Target server: PaperMC 26.3 build 244; Java 25.

## Install

1. Stop the Minecraft server.
2. Remove the old Light plugin JAR from `plugins/` so it cannot compete for `/light`.
3. Put `MegaCityLight-1.0.2.jar` in `plugins/`.
4. Start the server normally. No permissions or OP configuration is needed.

## Commands

| Command | Behavior |
| --- | --- |
| `/light` | Toggle based on whether night vision is actually applied. |
| `/light on` or `/light enable` | Enable it; repeated use keeps it on. |
| `/light off` or `/light disable` | Disable it; repeated use keeps it off. |
| `/light version` | Show the active plugin version; works in-game and in the console. |

Arguments are case-insensitive and support tab completion. Night-vision commands only
affect the player running them; console use shows an explanatory message.
`/light version` works from either the console or a player, without permissions.
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
- `/light` checks the actual night-vision effect: if present, it turns it off;
  if absent, it turns it on, even when an older saved preference says on.
- External removal by a plugin (including the effect-clear operation used by
  Essentials `/heal`) switches the saved preference off on the next tick if
  night vision is still absent. It stays off across respawn, reconnect, and
  restart until enabled again. Another effect applied before that check is
  respected. Cancelled removals do not disable the saved choice.
- Death and the respawn transition preserve the enabled preference. Replacing
  a finite potion during `/light on` does not disable the preference.
- There is no periodic polling or automatic reapplication after external clears.

Keep `players.yml` when upgrading or moving this plugin. The old Light plugin's
saved choices are not automatically imported.

## Version compatibility

`api-version: '26.3'` declares the minimum supported API, not an exact-version
lock. Future Paper versions can load it if they retain the public APIs used here.
The plugin avoids Minecraft internals, reflection, version-specific packets,
and server-version checks to minimize the need for updates. Future breaking
API changes can still require an update; future releases have not been tested.
This release compiles against `paper-api:26.3.build.244-stable` and requires Java 25.
The minimum declared API is now 26.3; this release will not load on Paper 26.2.

## Build and test

Use JDK 25 and Maven 3.9 or newer:

```sh
mvn clean package
```

The installable file is `target/MegaCityLight-1.0.2.jar`. Paper dependencies are
provided by the server; test dependencies are not bundled into the JAR.

The automated suite uses MockBukkit for Paper 26.3. It covers non-OP access,
toggle and idempotent aliases, invalid input, independent players, infinite
duration and display flags, death, milk and cancelled consumption, reconnects,
plugin disable/enable, UUID-based disk persistence, and queued restore tasks.
MockBukkit does not reproduce vanilla's post-event effect removal, so those
removals are explicitly simulated in the tests. The release passed 28 automated tests and a startup, command, and shutdown
smoke test on Paper 26.3 build 244. A full client/server gameplay test is not included.
Additional regressions cover actual-effect toggling after a heal-style clear,
external removal persistence, cancelled removals, death-caused removals, and
replacements on the same tick.

## Quick in-game check

Join as a non-OP player and run `/light`. Verify night vision and the icon, then
run `/light on` twice to confirm it stays on. Respawn, reconnect, and restart
to check restoration. Drink milk and confirm it stays off after respawn and
reconnect. Run `/light on`, then Essentials `/heal`, and verify that one `/light` command
turns night vision back on. Run `/light enable` to restore it, then `/light disable` twice to
confirm it stays off.

## Developer metadata

`/version MegaCityLight` uses Paper's built-in plugin information command and
shows version 1.0.2, author Tommy10606, and website
https://github.com/tommy10606/MegaCityLight. Access to Paper's built-in `/version`
command follows the server's own permissions; `/light version` is open to everyone.

## GitHub update checks

The plugin checks the public GitHub repository's latest published stable release
once per server boot (after one second). There are no periodic checks or messages. HTTP runs asynchronously with
five-second connection and read timeouts, so it does not block the game thread.

Each successful check prints one of these console messages:

```text
[MegaCityLight] Update Available!
[MegaCityLight] Running the most up to date version.
```

When an update is available, an additional line gives the installed version,
release tag, and download link. Notifications are console-only. Nothing is
automatically downloaded or installed. Network failures and rate limits produce
a concise warning; the next check happens on the next server boot. If no public stable
release exists, it prints that the update status is unknown. The checker does not
incorrectly call an unavailable release up to date.

Publish a **GitHub Release**, not just a Git tag. Use tags like `v1.0.1`,
`v1.0.2`, or `v1.1.0`, leave it out of draft/prerelease status, and mark the
intended stable version as GitHub's latest release. Version numbers are compared
numerically, so `v1.10.0` is newer than `v1.9.0`. Drafts, prereleases, and
unsupported tag formats are not announced as updates. Public releases require
no GitHub token. The checker needs outbound HTTPS access to `api.github.com`.

To disable checks, edit `plugins/MegaCityLight/config.yml` and restart:

```yaml
update-checker:
  enabled: false
```

For future source releases, change the version in `pom.xml`; Maven fills the
plugin metadata from that value. `/light version` reads the active metadata.
