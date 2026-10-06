# Touhou Little Maid: Feast & Wine Gone

A Forge 1.20.1 add-on for **Touhou Little Maid** that expands maid dining, drinking, food interaction, tavern behavior, and related quality-of-life systems.

> Mod ID: `feastwineallgone`

## Features

- Autonomous seated dining and idle eating behavior
- Tavern and placed-drink interactions
- Favorite-food attention and begging behavior
- Meal progression and meal rewards
- Night-time wine stealing behavior
- Fruit tasting and selected food-block compatibility
- Drunk sleeping and wake-up interactions
- Per-maid behavior settings and toolbox item
- Compatibility layers for Touhou Little Maid, Kaleidoscope Tavern, Kaleidoscope Cookery, and Farmer's Delight

## Requirements

- Minecraft 1.20.1
- Forge 47.x
- Touhou Little Maid 1.5.1–1.5.3
- Kaleidoscope Tavern 1.1.x–1.2.x
- Kaleidoscope Cookery 1.4.1–1.6.x

## Optional compatibility

- Farmer's Delight 1.3.4+ is optional and only required for its compatibility features

## Building from source

Java 17 is required.

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

Dependencies are resolved automatically through public Maven repositories. You do **not** need to place third-party mod JARs in a local `libs` directory.

The compiled mod will be written to `build/libs/`.

## Development

Generate or refresh IDE run configurations with ForgeGradle as usual, then run the client configuration from your IDE.

This project uses Sponge Mixin and generates a production refmap during compilation.

## Copyright and permissions

**Copyright © 2026 BraisedChimiRice (黄焖基米饭). All rights reserved.**

FeastWineAllGone / 亿宴酊蒸 is a proprietary project. No general permission is granted to copy, modify, redistribute, relicense, port, fork, or create derivative works from the project's original source code or original assets.

Official compiled releases may be downloaded and used for ordinary personal gameplay.

If you want to create a port, fork, derivative mod, modified build, adaptation, compatibility project using FWAG code/assets, redistribute the project, or include it in a modpack, **contact BraisedChimiRice (黄焖基米饭) for prior written permission** unless a separate express permission published by the author already covers your intended use.

Public access to this README, the copyright/authorization notice, or release information does not itself grant derivative-work or redistribution rights.

See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE) for the copyright and authorization terms.

## Credits

Created by **Braised Chimi Rice**.

This is an independent add-on project. Touhou Little Maid and the supported compatibility mods belong to their respective authors.
