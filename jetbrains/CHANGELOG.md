# Changelog

Every PR must add an entry under `## Unreleased` in the matching section
(Features / Fixes / Internal improvements) — CI blocks PRs that don't touch
this file. On release, the Unreleased section becomes the release body.

## Unreleased

### Features

- The GitButler tool window's toolbar now carries **New Virtual Branch…**, **Commit to Branch**
  and **Push Branch** next to Refresh and Pull Workspace, so those actions are discoverable
  instead of right-click-only. They stay visible and gray out when the selection doesn't fit.
- **Commit to Branch** (toolbar and branch context menu) preselects the branch in the commit
  toolbar's GitButler combo and opens the Commit tool window, checking exactly the selected
  change rows. Commit and Push act on the branch the selection belongs to, so they also work
  from a commit, a file inside a commit, or an assigned change.
- **New Virtual Branch…** creates an empty virtual branch (`but branch new`) from the tool
  window — a lane to drag changes onto or commit to.
- The commit toolbar's branch combo gained a **New branch…** item: name a branch that doesn't
  exist yet and `but commit -b <name>` creates it with the commit. Removes the documented
  "no way to create a new virtual branch from the commit window" limitation.

### Fixes

### Internal improvements

- The commit-window handoff (preselect branch, open Commit tool window, replace the checked
  inclusion) moved out of the drag-and-drop support into `GitButlerCommitLauncher`, shared by
  the drop-onto-branch gesture and the new Commit to Branch action. The tree-selection rules
  behind the branch-scoped actions moved to `GitButlerTreeSelection` so they are unit-tested
  without a Swing fixture.

## 2026.8.9.1 - 2026-08-09

### Features

### Fixes

### Internal improvements

## 2026.8.5.1 - 2026-08-05

### Features

### Fixes

- Detect the GitButler CLI on Windows, where `but` on PATH resolves to `but.exe`
  (or a `but.cmd`/`but.bat` shim) via PATHEXT; the extension now probes those
  suffixes on Windows so the CLI is found when it works from the terminal.
  ([#14](https://github.com/BartInTheField/gitbutler-for-ide/issues/14))

### Internal improvements

- The plugin no longer bundles the Kotlin stdlib or JetBrains annotations (~2.3 MB); they
  are provided by the IntelliJ Platform at runtime. Only the shared `:core` and
  kotlinx-serialization are bundled. Verified with the JetBrains Plugin Verifier.

## 2026.8.4.1 - 2026-08-04

### Features

### Fixes

### Internal improvements

* Removed the use of internal and deprecated jetbrains API's

## 2026.7.31.1 - 2026-07-31

### Features

### Fixes

- Compatibility with GitButler `but` 0.22.0, which overhauled the CLI: commands now
  use `--json` (was `--format json`), `but commit` takes changes positionally with
  `-b/--branch` for the target branch, `but amend` uses `-t/--target`, and the commit
  result is a flat `{commitId, ...}` object. Requires `but` 0.22.0+.

### Internal improvements

## 2026.7.27.1 - 2026-07-27

### Features

- Rename a commit from the tool window: right-click a commit row → **Rename
  Commit** opens a dialog prefilled with the current message and runs
  `but reword`. ([#6](https://github.com/BartInTheField/gitbutler-for-ide/issues/6))
- Uncommit from the tool window: right-click a commit row → **Uncommit** runs
  `but uncommit`, moving the commit's changes back into the working tree so
  they can be reassigned to another virtual branch. ([#7](https://github.com/BartInTheField/gitbutler-for-ide/issues/7))
- Drag-and-drop in the tool window: drag uncommitted changes onto a branch to
  preselect it and open the IDE commit UI with exactly the dragged files
  checked, or onto a commit to amend them into it (`but amend`) after a
  suppressible confirmation; CLI errors (e.g. locked files) surface as
  notifications. ([#5](https://github.com/BartInTheField/gitbutler-for-ide/issues/5))
- Commits in the tool window expand into the files they changed: double-click
  a file to see its diff for that specific commit, or right-click it →
  **Uncommit File** (`but uncommit <file-in-commit id>`) to move just that
  file's change back to the working tree. Jumping to a commit in the Git Log
  moved from double-click to a **Show in Git Log** context-menu entry.

### Fixes

### Internal improvements

- Migrated the repository to GitHub and rewrote CI from Woodpecker to GitHub
  Actions: `ci.yml` runs the changelog gate and tests on PRs and pushes to
  main, while releases live in a separate manually triggered `release.yml`.

## 2026.7.24.1 - 2026-07-24

### Features
- Workspace tool window gains actions: a **Pull Workspace** toolbar button
  (`but pull`) and a per-branch right-click menu with **Unapply**
  (`but unapply`) and **Push** (`but push`). All run as background tasks with
  success/error balloons and a tree refresh, gated by a single in-flight flag
  so operations never overlap; Pull is disabled outside a GitButler workspace.
  See [docs/tool-window.md](docs/tool-window.md).
- GitButler submenu in the IDE's native Git branch context menu: Apply and
  Unapply the selected branch via `but apply` / `but unapply`. Local branches
  get both actions; remote branches get Apply (by short name, hidden when a
  same-named local branch exists). Works on both the 2025.1 and 2026.1 IDE
  lines. See [docs/git-branch-menu.md](docs/git-branch-menu.md).

### Fixes

### Internal improvements

- Local integration tests no longer silently self-skip with Docker installed:
  the test JVM overrides the IDE-pinned JNA native library (incompatible with
  Testcontainers' JNA) and `docker-java.properties` pins Docker API 1.41 so
  Docker Engine 29+ accepts the client while pre-25 daemons keep working.
- Releases are cut manually instead of on every push to main, and CI gates PRs
  on a CHANGELOG.md entry; the Unreleased section becomes the release body and
  is cut back into the changelog after each release.
