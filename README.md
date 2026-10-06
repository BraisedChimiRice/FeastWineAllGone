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

## Supports
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

## License

The source code is made available under the **PolyForm Noncommercial License 1.0.0**.

You may study, modify, create derivative works from, and redistribute this project for permitted **noncommercial** purposes, subject to the license terms. Commercial use is not granted. Redistributions and derivative versions must preserve the applicable license terms (or its official URL) and all `Required Notice:` lines supplied with the project.

Required Notice: Copyright © 2026 BraisedChimiRice (黄焖基米饭). Original project: FeastWineAllGone / 亿宴酊蒸.

See [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE). Third-party projects remain under their own licenses and are not redistributed in this repository.

## Credits

Created by **Braised Chimi Rice**.

This is an independent add-on project. Touhou Little Maid and the supported compatibility mods belong to their respective authors.
