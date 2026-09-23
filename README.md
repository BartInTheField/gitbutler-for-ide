<div align="center">

<img src="jetbrains/src/main/resources/META-INF/pluginIcon.svg" width="96" height="96" alt="GitButler mark" />

# GitButler for IDEs

### Work with GitButler virtual branches without leaving your IDE.

A monorepo containing GitButler integrations for JetBrains IDEs and VSCode, powered by a shared Kotlin Multiplatform core library.

<br/>

[![License: MIT](https://img.shields.io/badge/License-MIT-74D3D1.svg?style=flat-square)](LICENSE)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ%20Platform-2025.1%2B-74D3D1?style=flat-square&logo=intellijidea&logoColor=white)](https://plugins.jetbrains.com/)
[![Built with Kotlin](https://img.shields.io/badge/Kotlin-JDK%2021-74D3D1?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![CI: GitHub Actions](https://img.shields.io/badge/CI-GitHub%20Actions-74D3D1?style=flat-square&logo=githubactions&logoColor=white)](https://github.com/BartInTheField/gitbutler-for-ide/actions)

</div>

> [!NOTE]
> **Unofficial** integrations — not affiliated with or endorsed by GitButler.
> The GitButler mark is used under its [CC0-1.0 brand assets](https://github.com/gitbutlerapp/gitbutler-brand-assets).

---

## 🧩 Monorepo Modules

| Module | Location | Target | Description |
|---|---|---|---|
| `:core` | `core/` | KMP (JVM + JS) | Core GitButler CLI orchestration, JSON parsing, models, and path mapping |
| `:jetbrains` | `jetbrains/` | JVM (IntelliJ) | IntelliJ Platform plugin integrating GitButler into JetBrains IDEs |
| `vscode` | `vscode/` | Node (VSCode) | VSCode extension consuming the `:core` Kotlin/JS compiled package |

---

## 🛠 Building & Development

### Core Library (`:core`)

Run unit tests across JVM and JS targets, or build the Kotlin/JS production library:

```bash
./gradlew :core:allTests
./gradlew :core:jsNodeProductionLibraryDistribution
```

### JetBrains Plugin (`:jetbrains`)

Build the IntelliJ plugin ZIP distribution or launch a sandbox IDE:

```bash
./gradlew :jetbrains:test          # unit + integration tests
./gradlew :jetbrains:buildPlugin   # produces jetbrains/build/distributions/*.zip
./gradlew :jetbrains:runIde        # launch sandbox IDE with plugin
```

Install in IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and select `jetbrains/build/distributions/gitbutler-intellij-<version>.zip`.

### VSCode Extension (`vscode`)

Build the Kotlin/JS library first, then compile the VSCode extension:

```bash
./gradlew :core:jsNodeProductionLibraryDistribution
cd vscode && npm run build
```

---

## ✨ Features (JetBrains Plugin)

- 🔍 **Zero-config detection** — activates only on a `gitbutler/workspace` branch; stays completely dormant otherwise
- 🎯 **Inline branch selector** — always-visible virtual-branch combo in the commit toolbar, beside the Amend toggle
- ✅ **Commits exactly what you check** — selected files are mapped to GitButler change IDs via `but status`
- 🚀 **Commit and Push…** — also pushes the virtual branch (`but push`)
- 🌳 **GitButler tool window** — live `but status` tree with VCS-colored file rows, commits expandable into their changed files, and diff-on-double-click
- ✂️ **History edits from the tree** — right-click a commit for Rename, Uncommit or Show in Git Log; right-click a file inside a commit for Uncommit File
- 🖱️ **Drag and drop** — drag uncommitted changes onto a branch to preselect it in the commit UI, or onto a commit to amend them into it
- 🧰 **Workspace actions in-IDE** — Pull Workspace, New Virtual Branch, Commit to Branch and Push Branch on the tool-window toolbar, Unapply Branch in the branch context menu, and new virtual branches from either the toolbar or the commit window — see [the tool window docs](docs/tool-window.md)
- 🌿 **GitButler submenu in the Git branch menu** — Apply / Unapply a branch right from the IDE's native branch context menu (remote-only branches can be applied too)
- 🔄 **Auto-refreshing** — the tool window re-renders on git repository changes (500 ms debounced) and via a manual Refresh button
- 🔔 **Clear notifications** — committed / committed & pushed / push failed / commit failed; a failed commit never loses your message
- 💾 **Remembers your last-used branch** per project

---

## 📦 Requirements

| | |
|---|---|
| **JetBrains IDE** | IntelliJ IDEA 2025.1+ (Community or Ultimate) |
| **VSCode** | VSCode 1.85+ |
| **CLI** | [GitButler `but`](https://docs.gitbutler.com/cli-overview) 0.22.0+ on your `PATH` (on Windows resolved via PATHEXT, e.g. `but.exe`/`but.cmd`; also auto-detected in `~/.local/bin`, `/opt/homebrew/bin`, `/usr/local/bin`) |
| **Project** | Set up with GitButler (`but setup`) |

---

## ⚙️ How it works

All GitButler operations go through the `but` CLI with `--json`, orchestrated by the shared `:core` library:

| Step | Command |
|---|---|
| List branches, map files → change IDs; render tree | `but status -f --json` |
| Commit exactly the selected changes | `but commit -b <branch> -m <message> --json <change-ids>` |
| Push (via *Commit and Push*, or tool-window context menu) | `but push <branch> --json` |
| Pull Workspace toolbar button | `but pull --json` |
| New Virtual Branch toolbar button | `but branch new <name> --json` |
| Unapply Branch context menu | `but unapply <branch> --json` |
| GitButler submenu in the Git branch menu | `but apply <branch> --json` / `but unapply <branch> --json` |
| Rename Commit context menu | `but reword <commit> -m <message> --json` |
| Uncommit / Uncommit File context menu | `but uncommit <commit-or-file-in-commit id> --json` |
| Amend by dropping changes onto a commit | `but amend -t <commit> --json <change-ids>` |

---

## 📚 Documentation

**JetBrains plugin**
- [Virtual-branch commit](docs/virtual-branch-commit.md) — the commit-window integration
- [GitButler tool window](docs/tool-window.md) — the workspace tree, toolbar, context menus and drag-and-drop
- [Git branch menu](docs/git-branch-menu.md) — the GitButler Apply/Unapply submenu in the native branch context menu

**VSCode extension**
- [GitButler for VSCode](docs/vscode.md) — the workspace tree view and commands, and how the extension consumes the shared `:core` package

---

## 🔄 Continuous integration & Releases

[GitHub Actions](https://github.com/BartInTheField/gitbutler-for-ide/actions) (`.github/workflows/ci.yml`) tests every PR and push to `main`, and requires each PR to add a `CHANGELOG.md` entry to at least one module (`core/CHANGELOG.md`, `jetbrains/CHANGELOG.md`, `vscode/CHANGELOG.md`). Releases live in a separate `release.yml` triggered manually from the Actions tab: it builds CalVer-versioned (`YYYY.M.D.<build>`) IntelliJ plugin ZIP and VSCode `.vsix` artifacts, assembling a single GitHub release with three sections (`## Core`, `## JetBrains`, `## VSCode`) from the three unreleased changelog sections.

---

## 📄 License

[MIT](LICENSE) © 2026 BartInTheField

<div align="center"><sub>Built with 🎀 for the GitButler workflow.</sub></div>
