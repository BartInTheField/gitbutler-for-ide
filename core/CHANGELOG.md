# Changelog

Every PR must add an entry under `## Unreleased` in the matching section
(Features / Fixes / Internal improvements) — CI blocks PRs that don't touch
this file. On release, the Unreleased section becomes the release body.

## Unreleased

### Features

### Fixes

### Internal improvements

## 2026.8.9.1 / 2026.8.9.2 - 2026-08-09

### Features

- Extracted the GitButler CLI logic (commands, JSON parsing, models, path mapping, CLI orchestration) into a Kotlin Multiplatform `:core` library targeting JVM and JS.

### Fixes

### Internal improvements

- The process-execution seam (`ButEnvironment.run`, `ButClient`) is `suspend`-based
  (kotlinx-coroutines), so the JS host spawns `but` asynchronously (`execFile`) without
  blocking the caller, while the JVM host runs it blocking on a background dispatcher.
