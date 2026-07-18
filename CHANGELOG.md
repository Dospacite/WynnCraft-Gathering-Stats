# Changelog

All notable changes to WynnCraft Gathering Stats are documented here.

## Unreleased

### Added

- A crafting profession display for all eight crafting professions with rolling XP per craft, XP and crafts until the next tenth level, and projected material and ingredient requirements.
- Projected Trade Market costs for every crafting input when WynnVentory is installed, formatted in EB below one LE and LE at or above one LE.

### Changed

- Gathering, crafting, and combat now share one Wynntils overlay element, so position and styling only need to be configured once. Gathering has display priority, followed by combat, then crafting.
- The shared overlay now defaults to the vertically centered right edge, away from the commonly used top-left minimap position.
- Crafting samples reset when the selected base materials, their tiers, or the ingredient setup changes.
- Profession XP bomb transitions reset crafting XP samples only when a craft is completed under the new bomb state. Profession Speed bombs halve projected material use with rounding down and never reduce ingredient use.

## 1.0.1 - 2026-07-15

This update adds an optional combat statistics display alongside the existing gathering overlay. Combat tracking is enabled by default and can be turned off in Wynntils settings.

### Added

- A combat overlay with XP / HR, Items / HR, XP until level up, estimated time until level, XP per kill, Kills per HR, and LE / HR.
- `/gatherstats track <itemName>` for selecting the item used by Items / HR and LE / HR. Suggestions are populated dynamically from Wynntils data and items observed in game.
- Pickup-only item tracking. An item counts only when your character collects it, whether Wynncraft places it in the ingredient pouch or normal inventory. Items left on the ground or collected by another player do not count.
- A configurable activity timeout, set to five minutes by default, for hiding and resetting inactive gathering and combat sessions.

### Changed

- Gathering statistics now take display priority. Gathering any resource immediately replaces the combat overlay, which remains suppressed until the gathering activity timeout expires.
- LE / HR remains optional and is shown only when WynnVentory is installed.

### Fixed

- Combat XP / HR and time until level now populate from kill data when Wynntils delays or batches direct combat XP updates.
- Missing Trade Market data for one material tier no longer makes the entire gathering LE / HR estimate unavailable. Unpriced materials are skipped while priced materials continue to contribute.

## 1.0.0 - 2026-07-14

### Added

- Native Wynntils overlay for gathering session statistics.
- XP per node and traversal-inclusive seconds per node rolling averages.
- XP, time, and node estimates until the next profession level.
- Profession XP and Profession Speed bomb detection.
- Automatic session resets and `/gatherstats reset`.
- Support for Minecraft 1.21.11, Fabric, and Wynntils 4.2.2 or newer.
