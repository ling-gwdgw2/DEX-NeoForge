# DEX — High-Performance Item & Recipe Viewer

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net/)
[![Mod Loader](https://img.shields.io/badge/Mod_Loader-NeoForge_21.1.x-orange.svg)](https://neoforged.net/)
[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![License: AGPL-3.0](https://img.shields.io/badge/License-AGPL--3.0-purple.svg)](LICENSE)

**DEX** is a next-generation Item & Recipe Viewer mod for Minecraft (NeoForge 1.21.1) featuring instant in-memory search, mod origin categorization, crafting tree decomposition, and seamless JEI plugin compatibility without bloat.

---

## ✨ Features

- ⚡ **Zero-Lag Search Engine:**
  - Employs a precomputed in-memory cache (`ItemSearchEntry`) evaluated in under 1 millisecond.
  - Smooth 60–144 FPS typing response even across modpacks with 20,000+ items.
- 📦 **Mod Origin Sidebar:**
  - Dynamic vertical sidebar categorizing items by their originating mod.
  - One-click filtering by mod icon.
- 📖 **Instant Recipe & Usage Viewer:**
  - $O(1)$ reverse lookup maps for outputs and ingredients.
  - Press `R` on any item to view crafting recipes.
  - Press `U` on any item to view recipes that consume it as an ingredient.
  - Supports Crafting Table, Furnace, Blast Furnace, Smoker, Campfire, Stonecutter, Smithing Table, Potion Brewing, Villager Trades, and Mob Drops.
- 🌲 **Recursive Crafting Tree Decomposition:**
  - Break down complex multi-tier items down to raw base materials.
  - Built-in cycle detection (up to depth 6) to handle looping recipes safely.
  - Batch quantity multipliers: `[x1]`, `[x4]`, `[x16]`, and `[x64]`.
- 🔌 **Native DEX Plugin API & Fake JEI Host Bridge:**
  - Modern, lightweight `@DexPlugin` annotation scanning via NeoForge ASM.
  - Built-in **JEI Compatibility Bridge** (`com.dex.compat.jei`): Automatically loads and adapts recipes from mods that only provide JEI plugins (Create, Thermal, Mekanism, Botania, etc.) through crash-resilient dynamic proxies—no standalone JEI installation required!
- 🎯 **Non-Intrusive Layout:**
  - Dynamically calculates container screen bounds (`AbstractContainerScreen`) to guarantee zero visual overlap with the player inventory or custom container slots.
  - Focus-safe search bar: keys like `E`, `Q`, and numbers don't close screens or drop items while typing.
  - Toggle overlay visibility with `Ctrl + O`.

---

## 🔍 Search Syntax

DEX supports intuitive, multi-token query evaluation using AND logic:

| Prefix / Format | Description | Example |
|---|---|---|
| `keyword` | Matches item name or ID | `diamond`, `sword` |
| `@mod` | Filters by Mod ID or display name | `@create`, `@minecraft` |
| `#tag` | Filters by item tag | `#c:ingots`, `#c:ores` |
| `-keyword` | Negative exclusion prefix | `iron -ingot` (finds iron items that aren't ingots) |
| `"..."` | Exact quoted phrase | `"raw iron"`, `"oak planks"` |
| *Combinations* | Combine any tokens | `@create gear -#c:plates` |

---

## ⌨️ Controls & Keybindings

| Key / Action | Function |
|---|---|
| `R` (Hovering item) | Show recipes that produce this item |
| `U` (Hovering item) | Show recipes that consume this item (Usages) |
| `Ctrl + O` | Toggle DEX overlay visibility (Show / Hide) |
| `Left Click` (Search box) | Focus search box and start typing |
| `Right Click` (Search box) | Clear search query instantly |
| `ESC` | Release search box focus (or close screen if unfocused) |
| `Mouse Wheel / Arrows` | Navigate grid and recipe pages |

---

## 🏗️ Architecture & Documentation

DEX is designed with a strictly decoupled layered architecture (Data Layer, Compatibility Layer, and Presentation Layer).

- 📘 **[Architecture Specification (English)](ArchitectureEN.md)**
- 📕 **[Architecture Specification (ภาษาไทย)](Architecture.md)**
- 🛠️ **[DEX API Guide for Mod Developers](docs/DEX_API_GUIDE.md)**

---

## 🛠️ Building from Source

### Prerequisites
- JDK 21 or newer (Temurin, Oracle, or OpenJDK)
- Internet connection for initial NeoForge and dependency resolution

### Build Instructions
Clone the repository and build using the included Gradle wrapper:

```bash
git clone https://github.com/ling-gwdgw2/DEX-NeoForge.git
cd DEX-NeoForge
./gradlew build
```

The compiled mod JAR will be located in:
```
build/libs/dex-1.0.0+1.21.1.jar
```

---

## 🤝 Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on code style, issue reporting, and pull requests.

---

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.  
See the [LICENSE](LICENSE) file for details.
