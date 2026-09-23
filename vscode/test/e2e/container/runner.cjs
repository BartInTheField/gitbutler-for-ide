'use strict';

// Runs INSIDE the e2e container: loads the compiled gitbutler-core (mounted at /opt/core)
// and drives it against the real `but` CLI in the repo passed as argv[2]. Prints the raw
// JSON envelope from the requested op to stdout so the host test can assert on it.
//
//   node /runner.cjs <repoDir> <op>        op = status | commit | newBranch | badBranchName
const core = require('/opt/core/gitbutler-core.js');
const GitButlerCore = core.me.inthefield.gitbutlerforjetbrains.core.GitButlerCore;

(async () => {
  const repo = process.argv[2];
  const op = process.argv[3] || 'status';
  const gb = new GitButlerCore(repo);
  if (op === 'commit') {
    process.stdout.write(await gb.commit('feature-e2e', 'e2e: commit a.txt', [`${repo}/a.txt`]));
  } else if (op === 'newBranch') {
    process.stdout.write(await gb.newBranch('feature-empty'));
  } else if (op === 'badBranchName') {
    // Rejected by the core's own validation; `but` is never spawned.
    process.stdout.write(await gb.newBranch('bad name'));
  } else {
    const env = JSON.parse(await gb.statusJson());
    process.stdout.write(JSON.stringify({ workspace: gb.isGitButlerWorkspace(), ok: env.ok, status: env.value, error: env.error }));
  }
})().catch((e) => {
  process.stderr.write(String(e && e.stack ? e.stack : e));
  process.exit(1);
});
