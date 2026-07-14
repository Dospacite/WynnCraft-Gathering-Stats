<p align="center">
  <img src="logo.png" alt="WynnCraft Gathering Stats logo" width="256">
</p>

# WynnCraft Gathering Stats

Show your session stats while gathering in Wynncraft!

WynnCraft Gathering Stats is a client-side Fabric mod for Minecraft 1.21.11. It uses Wynntils' profession and bomb models and renders its information as a native, configurable Wynntils overlay.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.4 or newer
- Wynntils 4.2.2 or newer
- Java 21

The mod is compiled and tested against its minimum supported Wynntils version, 4.2.2. Because Wynntils does not expose public third-party feature registration, this mod uses a small mixin shim against its `FeatureManager` internals. A future Wynntils update that changes those internals may require a compatibility update.

## Displayed statistics

- XP per node: average of the latest 20 gathering XP gains.
- Seconds per node: average of the latest 20 completion-to-completion intervals, including traversal time.
- XP until level up: supplied by Wynntils' profession model.
- Time until level up: predicted whole nodes multiplied by average seconds per node.
- Nodes until level up: remaining XP divided by average XP per node, rounded up.

The header shows the active gathering profession and current-world Profession XP and Profession Speed bombs. Wynntils reports the server-adjusted XP, so XP bombs are never applied twice.

## Usage

Tracking begins on the first Mining, Woodcutting, Farming, or Fishing XP event. Samples reset when changing profession, character, or world, and after five minutes without gathering a node.

Profession XP bomb changes reset only XP samples. Profession Speed bomb changes reset only timing samples. Use `/gatherstats reset` to reset the session manually.

Open the Wynntils overlay editor and select **WynnCraft Gathering Stats** to configure its position, size, enabled state, render order, font scale, background, and text shadow.

## Installation

1. Install Fabric Loader for Minecraft 1.21.11.
2. Install Wynntils 4.2.2 or newer.
3. Put the WynnCraft Gathering Stats JAR in the Minecraft `mods` directory.

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
