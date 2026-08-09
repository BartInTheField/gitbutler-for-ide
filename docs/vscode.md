# GitButler for VSCode

The VSCode extension (`vscode/`) brings the GitButler workspace into VS Code, powered by
the same `:core` logic as the JetBrains plugin — compiled to JavaScript (Kotlin/JS) and
bundled into the extension. All GitButler operations shell out to the `but` CLI with
`--json`, exactly like the JetBrains plugin.

## Where to find it

The extension contributes a **GitButler** view container on the activity bar with a
**gitbutlerWorkspace** tree view. When the open folder isn't checked out on
`gitbutler/workspace`, the tree shows a single **"Not a GitButler workspace"** row.

## The tree

Mirrors `but status`:

- **Unassigned changes** — uncommitted changes not assigned to any stack (computed as the
  set difference, by change id, between all uncommitted changes and every stack's assigned
  changes).
- **`<branch name>`** — one node per applied virtual branch (context value `branch`).
- **Commits** — each commit under its branch, expandable into the files it changed.
- **Assigned changes** — the changes staged to a stack.

## Commands

| Command | ID | What it runs |
|---|---|---|
| Refresh | `gitbutler.refresh` | Re-reads `but status` and repaints the tree |
| Pull Workspace | `gitbutler.pull` | `but pull` |
| Push Branch | `gitbutler.push` | `but push <branch>` (from a branch row or a quick-pick) |
| Apply Branch | `gitbutler.apply` | `but apply <branch>` (branch name via input box) |
| Unapply Branch | `gitbutler.unapply` | `but unapply <branch>` |
| Commit to virtual branch | `gitbutler.commit` | `but commit -b <branch> -m <message> <ids>` (quick-pick branch + files, input message) |

Failures surface as VS Code error toasts carrying the CLI's message; the tree refreshes
after every successful mutation.

## How it consumes `:core`

`extension.ts` imports the compiled Kotlin/JS package `gitbutler-core`
(`me.inthefield.gitbutlerforjetbrains.core.GitButlerCore`) and `await`s its methods, which
return `Promise<string>` JSON envelopes (`{ ok: true, value? }` / `{ ok: false, error }`).
The core resolves the repository (`git rev-parse`), finds the `but` binary, runs it, parses
the JSON and maps file paths — identical logic to the JetBrains plugin, shared not
reimplemented. The `but` process runs asynchronously (`child_process.execFile` bridged
through a Kotlin `suspend` function), so awaiting `pull`/`push` never blocks the extension
host.

The build bundles the core (and its Kotlin/serialization/coroutines runtime) into a single
self-contained `out/extension.js` via esbuild, so the packaged `.vsix` needs no
`node_modules` at runtime:

```bash
./gradlew :core:jsNodeProductionLibraryDistribution   # produce the JS package
cd vscode && npm run build                             # install + typecheck + bundle
```

## Known limitations

- No commit-window / SCM-provider integration yet — committing is driven from the command
  palette / view, not the native Source Control panel.

## Testing

Two tiers, both against the **real** `but` CLI (mirroring the JetBrains integration suite):

### Core e2e — logic against real `but`

`test/e2e/` drives the compiled `gitbutler-core` package against a real GitButler workspace,
exercising the exact runtime path the extension uses (`GitButlerCore` → `NodeButEnvironment`
`execFile` → real `but` → parse). Two provisioning modes:

- `core.e2e.test.cjs` — uses the `but` on your `PATH`; self-skips if it is not installed.
- `core.container.e2e.test.cjs` — hermetic: builds a Docker image (Node + `but` via
  `gitbutler.com/install.sh`, the same recipe as the JetBrains image) and runs the core
  inside it; self-skips if Docker is unavailable.

```bash
cd vscode && npm run build && npm run test:e2e
```

### UI e2e — the extension in a real editor

Two interchangeable drivers open the extension in a real editor, open the GitButler
activity-bar view, assert the workspace tree renders, and screenshot the running instance
(the "headless Chrome" layer — the editor is an Electron/Chromium app). Both need a display,
so run under `xvfb` on headless CI. Both seed a live workspace when `but` is on `PATH`.

- **VSCodium (default in CI)** — `test/ui/codium-smoke.ts`, driven by Playwright's Electron
  API. It attaches to any Electron binary via the built-in CDP, so it runs against the
  open-source **VSCodium** build (or any VS Code) with no ChromeDriver-version matching.
  Point `CODIUM_BIN` at the real Electron binary (e.g. `/usr/share/vscodium/codium`, not the
  `bin/codium` CLI wrapper).

  ```bash
  cd vscode && npm run build
  CODIUM_BIN=/usr/share/vscodium/codium xvfb-run -a npm run test:ui:codium
  ```

- **Microsoft VS Code (optional)** — `test/ui/gitbutler-view.test.ts`, driven by
  [ExTester](https://github.com/redhat-developer/vscode-extension-tester). Richer page
  objects, but ExTester only downloads Microsoft's VS Code (it cannot target VSCodium).

  ```bash
  cd vscode && npm run build && xvfb-run -a npm run test:ui
  ```

CI (`.github/workflows/ci.yml`): job `vscode` runs the core e2e; job `vscode-ui` downloads
VSCodium, runs the Playwright UI test under `xvfb`, and uploads `vscode/screenshots/` as an
artifact.
