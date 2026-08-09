'use strict';

// End-to-end test of the compiled `gitbutler-core` (Kotlin/JS) package driving the REAL
// `but` CLI against a REAL, freshly-created GitButler workspace — the VSCode counterpart of
// the JetBrains `ButStatusIntegrationTest`. It exercises the exact code path the extension
// uses at runtime: GitButlerCore -> NodeButEnvironment (execFile) -> real `but` -> parse.
//
// Self-skips (never fails) when the `but` CLI is not installed, mirroring how the JetBrains
// integration tests self-skip without Docker.

const { test, before, after } = require('node:test');
const assert = require('node:assert');
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const core = require('gitbutler-core');
const GitButlerCore = core.me.inthefield.gitbutlerforjetbrains.core.GitButlerCore;

function hasBut() {
  try {
    execFileSync('but', ['--version'], { stdio: 'ignore' });
    return true;
  } catch {
    return false;
  }
}

const skip = hasBut() ? false : 'the `but` CLI is not installed (install it to run the e2e)';

function unwrap(jsonStr) {
  const env = JSON.parse(jsonStr);
  if (!env.ok) {
    throw new Error(`core returned error: ${env.error}`);
  }
  return env.value;
}

let repo;

before(() => {
  if (skip) return;
  repo = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-e2e-'));
  const git = (...a) => execFileSync('git', a, { cwd: repo, stdio: 'ignore' });
  git('init', '-q', '-b', 'main', '.');
  git('config', 'user.email', 'e2e@example.com');
  git('config', 'user.name', 'e2e');
  fs.writeFileSync(path.join(repo, 'a.txt'), 'hello\n');
  git('add', '-A');
  git('commit', '-qm', 'init');
  // Turn the plain git repo into a GitButler workspace (checks out gitbutler/workspace).
  execFileSync('but', ['setup', '--init'], { cwd: repo, stdio: 'ignore' });
});

after(() => {
  if (repo) fs.rmSync(repo, { recursive: true, force: true });
});

test('detects a real GitButler workspace', { skip }, () => {
  const gb = new GitButlerCore(repo);
  assert.strictEqual(gb.isGitButlerWorkspace(), true);
  assert.strictEqual(typeof gb.findExecutable(), 'string');
});

test('statusJson reflects a real uncommitted change', { skip }, async () => {
  fs.appendFileSync(path.join(repo, 'a.txt'), 'a working-tree change\n');
  const gb = new GitButlerCore(repo);
  const status = unwrap(await gb.statusJson());
  const paths = status.uncommittedChanges.map((c) => c.filePath);
  assert.ok(paths.includes('a.txt'), `expected a.txt in ${JSON.stringify(paths)}`);
});

test('commit routes a real change onto a virtual branch', { skip }, async () => {
  const gb = new GitButlerCore(repo);
  const abs = path.join(repo, 'a.txt');
  const commitId = unwrap(await gb.commit('feature-e2e', 'e2e: commit a.txt', [abs]));
  assert.strictEqual(typeof commitId, 'string');

  const status = unwrap(await gb.statusJson());
  const branchNames = status.branches.map((b) => b.name);
  assert.ok(
    branchNames.includes('feature-e2e'),
    `expected a virtual branch 'feature-e2e' in ${JSON.stringify(branchNames)}`,
  );
  const branch = status.branches.find((b) => b.name === 'feature-e2e');
  assert.ok(branch.commits.length >= 1, 'expected at least one commit on the virtual branch');
  const stillUncommitted = status.uncommittedChanges.map((c) => c.filePath);
  assert.ok(!stillUncommitted.includes('a.txt'), 'a.txt should be committed, not uncommitted');
});
