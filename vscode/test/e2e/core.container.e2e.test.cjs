'use strict';

// Hermetic end-to-end test: builds a Docker image with Node + the REAL `but` CLI, mounts
// the compiled gitbutler-core package into it, and drives the core against a real GitButler
// workspace created inside the container. This is the VSCode counterpart of the JetBrains
// Testcontainers integration suite (`ButStatusIntegrationTest`) — same image recipe
// (debian/node + `gitbutler.com/install.sh`), exercising the JS core instead of the JVM one.
//
// Self-skips (never fails) when Docker is unavailable, mirroring the JetBrains suite.

const { test, before, after } = require('node:test');
const assert = require('node:assert');
const { execFileSync } = require('node:child_process');
const path = require('node:path');

let dockerUp = true;
try {
  execFileSync('docker', ['info'], { stdio: 'ignore' });
} catch {
  dockerUp = false;
}
const skip = dockerUp ? false : 'Docker is not available (needed for the hermetic container e2e)';

const distDir = path.resolve(__dirname, '../../../core/build/dist/js/productionLibrary');
const contextDir = path.join(__dirname, 'container');

let container;
let GenericContainer;

async function exec(cmd) {
  const res = await container.exec(['bash', '-lc', cmd]);
  if (res.exitCode !== 0) {
    throw new Error(`exec failed (${res.exitCode}): ${cmd}\n${res.output}`);
  }
  return res.output;
}

before(async () => {
  if (skip) return;
  ({ GenericContainer } = require('testcontainers'));
  const image = await GenericContainer.fromDockerfile(contextDir).build();
  container = await image
    .withCopyDirectoriesToContainer([{ source: distDir, target: '/opt/core' }])
    .withCopyFilesToContainer([{ source: path.join(contextDir, 'runner.cjs'), target: '/runner.cjs' }])
    .withCommand(['sleep', 'infinity'])
    .start();

  await exec(
    'mkdir -p /work && cd /work && git init -q -b main . && echo hello > a.txt && ' +
      'git add -A && git commit -qm init && but setup --init && echo change >> a.txt',
  );
});

after(async () => {
  if (container) await container.stop();
});

test('core detects the workspace and a real change (in container)', { skip, timeout: 600_000 }, async () => {
  const out = await exec('node /runner.cjs /work status');
  const parsed = JSON.parse(out.trim().split('\n').pop());
  assert.strictEqual(parsed.workspace, true, 'isGitButlerWorkspace should be true');
  const paths = parsed.status.uncommittedChanges.map((c) => c.filePath);
  assert.ok(paths.includes('a.txt'), `expected a.txt in ${JSON.stringify(paths)}`);
});

test('core commits a real change onto a virtual branch (in container)', { skip, timeout: 600_000 }, async () => {
  const commitOut = await exec('node /runner.cjs /work commit');
  const env = JSON.parse(commitOut.trim().split('\n').pop());
  assert.strictEqual(env.ok, true, `commit should succeed: ${env.error || ''}`);

  const statusOut = await exec('node /runner.cjs /work status');
  const parsed = JSON.parse(statusOut.trim().split('\n').pop());
  const branchNames = parsed.status.branches.map((b) => b.name);
  assert.ok(branchNames.includes('feature-e2e'), `expected feature-e2e in ${JSON.stringify(branchNames)}`);
});

test('core creates an empty virtual branch (in container)', { skip, timeout: 600_000 }, async () => {
  const out = await exec('node /runner.cjs /work newBranch');
  const env = JSON.parse(out.trim().split('\n').pop());
  assert.strictEqual(env.ok, true, `newBranch should succeed: ${env.error || ''}`);

  const statusOut = await exec('node /runner.cjs /work status');
  const parsed = JSON.parse(statusOut.trim().split('\n').pop());
  const branch = parsed.status.branches.find((b) => b.name === 'feature-empty');
  assert.ok(branch, `expected feature-empty in ${JSON.stringify(parsed.status.branches.map((b) => b.name))}`);
  // Empty collections are omitted from the envelope (encodeDefaults=false), same as the
  // extension's own `(x || [])` reads.
  assert.strictEqual((branch.commits || []).length, 0, 'a brand new branch has no commits');
});

test('core rejects an invalid branch name before reaching the CLI (in container)', { skip, timeout: 600_000 }, async () => {
  const out = await exec('node /runner.cjs /work badBranchName');
  const env = JSON.parse(out.trim().split('\n').pop());
  assert.strictEqual(env.ok, false, 'a name with whitespace must be rejected');
  assert.match(env.error, /whitespace/i, `unexpected message: ${env.error}`);
});
