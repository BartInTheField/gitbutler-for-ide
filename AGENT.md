# Agent guide — GitButler Monorepo (IntelliJ & VSCode)

Monorepo containing GitButler integrations for JetBrains IDEs (`jetbrains/`) and VSCode (`vscode/`), powered by a shared Kotlin Multiplatform core library (`core/`). All GitButler operations shell out to the `but` CLI (0.22.0+) with `--json`. Active only when the project is on the `gitbutler/workspace` branch.

## Commands

### Core Library (`core/`)

```bash
./gradlew :core:allTests                              # unit tests for common, JVM, and JS targets
./gradlew :core:jsNodeProductionLibraryDistribution   # builds Node-requireable JS package in core/build/dist/js/productionLibrary/
```

### JetBrains Plugin (`jetbrains/`)

```bash
./gradlew :jetbrains:test          # unit + CLI integration tests
./gradlew :jetbrains:buildPlugin   # produces jetbrains/build/distributions/*.zip (NOT `build`)
./gradlew :jetbrains:runIde        # sandbox IDE with the plugin installed
```

### VSCode Extension (`vscode/`)

```bash
./gradlew :core:jsNodeProductionLibraryDistribution   # build JS dependency first
cd vscode && npm install && npm run compile          # compile VSCode TypeScript extension
```

Gradle needs JDK 21 (`kotlin { jvmToolchain(21) }`). If there is no system Java, point `JAVA_HOME` at one (e.g. `JAVA_HOME=/opt/homebrew/opt/openjdk@21` on macOS/Homebrew).

---

## Non-negotiable rules

1. **Every behavior change ships with tests, and you run them** (`./gradlew :core:allTests`, `./gradlew :jetbrains:test`) before declaring done. Pure parsing/mapping logic → unit test in `:core`; anything touching the real `but` CLI contract → integration test in `:jetbrains` (see below).
2. **Every new feature or user-visible change updates `docs/`** — extend the matching page (`docs/virtual-branch-commit.md`, `docs/tool-window.md`) or add a new page for a new surface.
3. **README.md and plugin descriptions stay lean.** `README.md` and `<description>` in `jetbrains/src/main/resources/META-INF/plugin.xml` get at most a one-line mention of a new feature; full explanations live in `docs/` and the README links to them. Never grow either into a manual.
4. **Every PR adds a `CHANGELOG.md` entry** to the appropriate module changelog (`core/CHANGELOG.md`, `jetbrains/CHANGELOG.md`, `vscode/CHANGELOG.md`) under `## Unreleased` in the matching section (Features / Fixes / Internal improvements). CI blocks PRs that don't touch at least one changelog file.

---

## Architecture

- **`core/`** — Kotlin Multiplatform library (JVM + JS targets) containing CLI orchestration, models, parsing, and path mapping.
  - `commonMain/` — `ButClient`, `ButCommands`, `ButJsonParser`, `ButModel`, `ButPathMapper`, `ButExecutableResolver`, `ButEnvironment`, `PlatformPaths`.
  - `jvmMain/` — JVM platform paths.
  - `jsMain/` — `GitButlerCore` (JS export facade), `NodeButEnvironment`, JS platform paths.
  - `commonTest/`, `jvmTest/` — Unit tests for CLI parsing, path mapping, command construction, and executable resolution.
- **`jetbrains/`** — IntelliJ Platform plugin consuming `:core` (JVM target).
  - `core/` — `GitButlerService` (project service).
  - `commit/` — Commit-window integration (`GitButlerCheckinHandlerFactory`/`GitButlerCheckinHandler`, `GitButlerBranchComboAction`, `GitButlerCommitSelection`).
  - `toolwindow/` — Workspace tool window (`GitButlerToolWindowFactory`, `GitButlerStatusPanel`, `GitButlerTreeDnDSupport`).
  - `branchmenu/` — Native Git branch context menu actions (`GitButlerApplyBranchAction`, `GitButlerUnapplyBranchAction`).
  - `src/test/.../integration/` — Integration tests against real CLI in Docker (`ButStatusIntegrationTest.kt`).
- **`vscode/`** — VSCode extension (TypeScript/Node) consuming the `:core` Kotlin/JS package (`gitbutler-core`).

---

## Testing

### Unit tests (`core/src/commonTest/`, `core/src/jvmTest/`)

`ButJsonParserTest`, `ButPathMapperTest`, `ButExecutableResolverTest`, `ButCommandsTest` — plain JUnit tests against captured JSON fixtures. Fast, no Docker. Extend these when changing parsing, path mapping, or command construction.

### Integration tests (`jetbrains/src/test/.../integration/ButStatusIntegrationTest.kt`)

These validate the plugin's CLI contract end-to-end against the **real** GitButler CLI:

- Testcontainers builds a Debian container that installs the latest `but` via `gitbutler.com/install.sh` (no version pin exists; latest-by-design to catch contract drift — the version under test is printed at startup).
- Each test creates a **real git repository** inside the container via the `freshRepo(name)` helper (`but setup --init`), makes real file changes and branches, then runs the plugin's **own** `ButCommands` argument lists, feeds the real JSON through `ButJsonParser`/`ButPathMapper`, and asserts on the parsed `WorkspaceStatus` model.
- Pattern for a new test: `freshRepo` → arrange with `exec(repo, ...)` shell steps → act through `but(repo, ButCommands.xxx(...))` → assert on `status(repo)`. Always go through `ButCommands`, never hand-written arg lists — the point is testing what the plugin actually sends.
- **Self-skipping:** the whole class skips (never fails) when Docker is unavailable, via a hardened `Assume` in `@BeforeClass`. Keep it that way: construct anything Testcontainers-related lazily in `@BeforeClass`, never in static init (static init throws `java.lang.Error` on Docker-less machines and breaks the skip).
- Locally under colima, `jetbrains/build.gradle.kts` derives `DOCKER_HOST` from the docker context and disables Ryuk — don't remove that block.

Any change to `ButCommands`, `ButJsonParser`, `ButPathMapper`, or a new `but` subcommand needs a matching integration test that exercises it against the real CLI.

---

## CI & Release Policy

- **CI Workflow** (`.github/workflows/ci.yml`):
  - `changelog`: Gate verifying that PRs add at least one line under `## Unreleased` in `core/CHANGELOG.md`, `jetbrains/CHANGELOG.md`, or `vscode/CHANGELOG.md`.
  - `core`: Runs `./gradlew :core:allTests`.
  - `jetbrains`: Runs `./gradlew :jetbrains:test :jetbrains:buildPlugin`.
  - `vscode`: Runs `./gradlew :core:jsNodeProductionLibraryDistribution` and `cd vscode && npm install && npm run compile`.

- **Release Workflow** (`.github/workflows/release.yml`):
  - Triggered manually via `workflow_dispatch` on `main`.
  - Runs full test suite for all modules.
  - Computes CalVer version (`YYYY.M.D.N`).
  - Builds IntelliJ plugin ZIP (`:jetbrains:buildPlugin`) and packages VSCode `.vsix` extension (`npx @vscode/vsce package`).
  - Assembles a single GitHub release body with up to three sections (`## Core`, `## JetBrains`, `## VSCode`) by reading the `## Unreleased` section of each module's `CHANGELOG.md`.
  - Cuts all three `CHANGELOG.md` files (stamping `## <VERSION> - <DATE>` and resetting `## Unreleased`), commits with `[skip ci]`, and pushes back to `main`.

---

## Gotchas

- `but commit` (0.22) succeeds by exit code 0 and emits `{commitId, changeId, branch}` — `ButJsonParser.parseCommitResult` reads `commitId` and treats a missing id as a still-successful commit. Rejections (dependency locks, merged-upstream) come back as exit 1 with a plain-text stderr message, not a JSON `rejected` array. `but push` failure output is plain text, not JSON.
- Detect Commit-and-Push via `executor.id == "Git.Commit.And.Push.Executor"` — the class `git4idea.checkin.GitCommitAndPushExecutor` is Kotlin-`internal`, don't reference it.
- The `CheckinHandlerFactory` must always return the real handler (never a dummy): it runs once at commit-UI creation, possibly before git repos register.
- Branch names in `Presentation.setText` need `setText(text, false)` — `_`/`&` are otherwise eaten as mnemonics.

---

## Version control

The repo itself is GitButler-managed (`gitbutler/workspace`). Use the `but` CLI (or the gitbutler skill, if available) for branches, commits, and pushes — not raw `git commit`. The remote is GitHub, so `but pr new <branch-id>` works and is the way to open PRs (it pushes first and sets stack bases); don't use `gh pr create` for stacked branches.
