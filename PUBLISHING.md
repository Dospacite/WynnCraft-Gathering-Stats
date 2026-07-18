# Publishing

The `publish.yml` workflow builds once and publishes the same remapped JAR to GitHub Releases, Modrinth, and CurseForge.

## One-time setup

1. Create the **WynnCraft Gathering Stats** project on Modrinth and CurseForge using the description in `README.md`, the root `logo.png`, the MIT license, Fabric as the loader, and Minecraft 1.21.11 as the game version.
2. In the GitHub repository settings, add these Actions variables:
   - `MODRINTH_ID`: the Modrinth project ID or slug.
   - `CURSEFORGE_ID`: the numeric CurseForge project ID.
3. Add these Actions secrets:
   - `MODRINTH_TOKEN`: a Modrinth personal access token with version creation permission.
   - `CURSEFORGE_TOKEN`: a CurseForge API token.

The workflow declares Wynntils 4.2.2 or newer as a required Modrinth dependency and Wynntils as a required CurseForge dependency.

## Release process

1. Update `mod_version` in `gradle.properties`.
2. Add the release notes to `CHANGELOG.md`.
3. Commit and push the changes to `main`.
4. Tag that commit with the same version prefixed by `v`, then push the tag:

   ```sh
   git tag v1.0.1
   git push origin v1.0.1
   ```

The workflow rejects a tag if it does not match `mod_version`. Successful runs create a public GitHub Release and upload the version to both marketplaces.
