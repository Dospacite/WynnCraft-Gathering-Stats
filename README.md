<p align="center">
  <img src="logo.png" alt="WynnCraft Gathering Stats logo" width="256">
</p>

# WynnCraft Gathering Stats

An in-game session overlay for Wynncraft gathering. Track your pace, material output, level progress, and—optionally—estimated Trade Market value without leaving the game.

WynnCraft Gathering Stats is a client-side Fabric mod for Minecraft 1.21.11. It uses Wynntils' profession and bomb models and renders its information as a native, configurable Wynntils overlay.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.4 or newer
- Wynntils 4.2.2 or newer
- Java 21

Optional:

- WynnVentory 2.2.1 or newer, for live Trade Market valuation in LE per hour.

The mod is compiled and tested against its minimum supported Wynntils version, 4.2.2. Because Wynntils does not expose public third-party feature registration, this mod uses a small mixin shim against its `FeatureManager` internals. A future Wynntils update that changes those internals may require a compatibility update.

## What it tracks

- XP per node: rolling average of recent gathering XP gains.
- Seconds per node: rolling average of recent completion-to-completion intervals, including traversal time.
- ★ items per hour: estimated one-star material output over the rolling node window.
- ★★ items per hour: estimated two-star material output over the rolling node window.
- ★★★ items per hour: estimated three-star material output over the rolling node window.
- LE per hour: the estimated value of that material output when WynnVentory is installed. Each material and star tier is valued separately using WynnVentory's lowest live Trade Market price by default.
- XP until level up: supplied by Wynntils' profession model.
- Time until level up: predicted whole nodes multiplied by average seconds per node.
- Nodes until level up: remaining XP divided by average XP per node, rounded up.

The header shows the active gathering profession and current-world Profession XP and Profession Speed bombs. Wynntils reports the server-adjusted XP, so XP bombs are never applied twice.

All rate-based values use the same rolling node window. The default is 20 nodes, which gives a useful balance between responsiveness and stability. You can choose any positive window size in the feature settings.

## WynnVentory integration

WynnVentory is optional. When it is present, the overlay adds **LE / HR**, an estimate of the Trade Market value of the materials gathered per hour. The calculation keeps each material name and star tier separate before applying its market price, then converts the result from emeralds to liquid emeralds.

The default price source is the **lowest current listing**. You can instead choose moving median, 80% trimmed average, or average in the feature settings. The LE row is omitted when WynnVentory is absent and displays `—` while any required price is still loading, rather than showing a partial total.

## Usage

Tracking begins on the first Mining, Woodcutting, Farming, or Fishing XP event. Samples reset when changing profession, character, or world, and after five minutes without gathering a node.

The rolling node window defaults to 20 nodes and can be changed in Wynntils settings. Changing its size clears the current samples. Profession XP bomb changes reset the XP window so boosted and unboosted gains are never mixed. Profession Speed bomb changes reset timing and item-rate samples so the hourly estimates reflect the new gathering speed. Use `/gatherstats reset` to reset the session manually.

Open Wynntils settings and select **WynnCraft Gathering Stats** to configure the feature. The three material tiers can be enabled independently.

| Setting | Purpose |
| --- | --- |
| Node Window Size | Number of recent nodes used for XP, timing, item, and LE rates. Default: 20. Changing it clears current samples. |
| Trade Market Price | WynnVentory price metric used for LE / HR. Default: lowest listing. |
| Display toggles | Turn the header, bomb status, or any individual stat row on or off. |

Open the Wynntils overlay editor to configure the display's position, size, enabled state, render order, font scale, background, and text shadow.

## Diagnostics

The following client commands help diagnose material-rate issues. They do not require a Wynncraft connection or any gathering to run.

| Command | Purpose |
| --- | --- |
| `/gatherstats debug status` | Shows the active window, item-row toggles, tracker sample counts, item-rate values, WynnVentory availability, and the latest captured label result. |
| `/gatherstats debug selftest` | Parses a built-in current Maple Paper label (which deliberately has no tier), supplies a simulated tier-2 MaterialItem update, and simulates two nodes. A successful test reports a non-empty ★★ hourly rate. |
| `/gatherstats debug capture` | Enables live diagnostics for the next relevant label events. It writes a concise result to the client log. |
| `/gatherstats debug last` | Shows the most recently captured label's raw and formatted text plus parsed amount, material, tier, and record/skip reason. |
| `/gatherstats debug capture-off` | Disables live label diagnostics. |

For a real gathering test, enable capture, gather a single node, then run `/gatherstats debug last`. The raw and formatted labels are escaped so formatting codes are visible and can be reported directly in an issue.

## Reset behavior

- Changing gathering profession, character, or world clears the session.
- Five minutes without a gathering node clears current samples.
- Profession XP bomb changes clear the XP window so boosted and unboosted gains never mix.
- Profession Speed bomb changes clear timing, item-rate, and LE-rate samples.
- `/gatherstats reset` clears the current session manually.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Install Wynntils 4.2.2 or newer.
3. Optionally install WynnVentory 2.2.1 or newer to enable LE per hour.
4. Put the WynnCraft Gathering Stats JAR in the Minecraft `mods` directory.

## Building

```sh
./gradlew clean build
```

The distributable remapped JAR is written to `build/libs/wynngatheringstats-<version>.jar`.

## Publishing

Tagged releases can be published to GitHub Releases, Modrinth, and CurseForge by the included GitHub Actions workflow. See [PUBLISHING.md](PUBLISHING.md) for the one-time marketplace setup and release process.

## License

Copyright © 2026 Dospacite. Licensed under the [MIT License](LICENSE).

WynnCraft Gathering Stats is an independent community project and is not affiliated with or endorsed by Wynncraft or Wynntils. Wynncraft and Wynntils are separate projects distributed under their respective terms.
