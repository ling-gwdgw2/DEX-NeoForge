# Contributing to DEX

Thank you for your interest in contributing to **DEX**! We appreciate bug reports, feature requests, documentation improvements, and code contributions.

---

## Code of Conduct

Please be respectful, constructive, and helpful when participating in discussions, reporting issues, or submitting code.

---

## Reporting Issues

If you encounter a bug or unexpected behavior:
1. Search existing issues to ensure the problem hasn't already been reported.
2. Open a new issue with a clear, descriptive title.
3. Include:
   - Minecraft version (`1.21.1`)
   - NeoForge version (e.g. `21.1.93`)
   - DEX mod version (e.g. `1.0.0`)
   - Other installed mods (if diagnosing a conflict)
   - Crash log or `debug.log` if applicable
   - Steps to reproduce the issue

---

## Development & Pull Requests

### Workflow
1. Fork the repository on GitHub.
2. Clone your fork locally:
   ```bash
   git clone https://github.com/<your-username>/DEX-NeoForge.git
   cd DEX-NeoForge
   ```
3. Create a feature branch:
   ```bash
   git checkout -b feature/my-new-feature
   ```
4. Make your changes adhering to existing code conventions and formatting.
5. Verify that the project compiles cleanly:
   ```bash
   ./gradlew build
   ```
6. Commit your changes with concise and descriptive commit messages.
7. Push to your branch and submit a Pull Request.

### Code Style Guidelines
- Maintain Java 21 conventions.
- Keep rendering, indexing, and GUI logic decoupled according to the architecture outlined in [ArchitectureEN.md](ArchitectureEN.md).
- Avoid heavy allocations or complex calculations inside live UI render loops.
- Use thread-safe collections when indexing or registering cross-thread resources.

---

## Submitting Plugins / Addons

If you are developing an addon or adding compatibility for your mod:
- Please refer to [docs/DEX_API_GUIDE.md](docs/DEX_API_GUIDE.md) for how to write `@DexPlugin` implementations.
