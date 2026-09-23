import { _electron as electron, type ElectronApplication, type Page } from 'playwright-core';
import { execFileSync } from 'child_process';
import * as assert from 'assert';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

/**
 * UI end-to-end smoke against VSCodium (the open-source VS Code build) instead of Microsoft's
 * VS Code. ExTester can only download MS VS Code, so we drive VSCodium directly with
 * Playwright's Electron API — it attaches to any Electron binary via the built-in CDP, so no
 * ChromeDriver-version matching is needed. Launches VSCodium with this extension loaded from
 * source, opens the GitButler view, screenshots the running instance, and asserts the tree
 * rendered. Needs a display (run under xvfb in CI).
 *
 *   CODIUM_BIN=/usr/bin/codium node out-ui/ui/codium-smoke.js
 */
function seedWorkspace(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-codium-'));
  const git = (...a: string[]) => execFileSync('git', a, { cwd: dir, stdio: 'ignore' });
  git('init', '-q', '-b', 'main', '.');
  git('config', 'user.email', 'ui@example.com');
  git('config', 'user.name', 'ui');
  fs.writeFileSync(path.join(dir, 'a.txt'), 'hello\n');
  git('add', '-A');
  git('commit', '-qm', 'init');
  let hasBut = true;
  try {
    execFileSync('but', ['--version'], { stdio: 'ignore' });
  } catch {
    hasBut = false;
  }
  if (hasBut) {
    execFileSync('but', ['setup', '--init'], { cwd: dir, stdio: 'ignore' });
    fs.appendFileSync(path.join(dir, 'a.txt'), 'a working-tree change\n');
  }
  return dir;
}

async function main(): Promise<void> {
  const codium = process.env.CODIUM_BIN || '/usr/bin/codium';
  const extensionRoot = path.resolve(__dirname, '../..'); // -> vscode/
  const workspace = seedWorkspace();
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-codium-user-'));
  const extensionsDir = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-codium-exts-'));
  const shotDir = path.join(extensionRoot, 'screenshots');
  fs.mkdirSync(shotDir, { recursive: true });

  const app: ElectronApplication = await electron.launch({
    executablePath: codium,
    args: [
      `--extensionDevelopmentPath=${extensionRoot}`,
      `--user-data-dir=${userDataDir}`,
      `--extensions-dir=${extensionsDir}`,
      '--disable-workspace-trust',
      '--skip-release-notes',
      '--skip-welcome',
      '--disable-telemetry',
      '--no-sandbox',
      '--disable-gpu',
      workspace,
    ],
  });

  try {
    const win: Page = await app.firstWindow();
    await win.waitForSelector('.monaco-workbench', { timeout: 60_000 });

    // Open the GitButler activity-bar view container (aria-label = its title "GitButler").
    await win.locator('.activitybar a[aria-label*="GitButler" i]').first().click();

    // Give the view a moment to run `but status` and render, then capture the instance.
    await win.locator('.monaco-workbench .part.sidebar').waitFor({ timeout: 30_000 });
    await win.waitForTimeout(3_000);
    const shot = path.join(shotDir, 'gitbutler-view-codium.png');
    await win.screenshot({ path: shot });

    // The view's title bar is the extension's toolbar: assert every action we contribute is
    // actually surfaced there (VS Code uses each command's title as the action's aria-label).
    const toolbarActions = await Promise.all(
      (await win.locator('.part.sidebar .pane-header .actions-container a.action-label').all()).map(a =>
        a.getAttribute('aria-label'),
      ),
    );
    console.log('toolbar actions:', JSON.stringify(toolbarActions));
    for (const expected of ['Refresh', 'Pull Workspace', 'New Virtual Branch', 'Commit to virtual branch', 'Push Branch']) {
      assert.ok(
        toolbarActions.includes(expected),
        `expected a '${expected}' action in the view title bar; found ${JSON.stringify(toolbarActions)}`,
      );
    }

    const sidebarText = (await win.locator('.part.sidebar').innerText().catch(() => '')).replace(/\s+/g, ' ').trim();
    console.log('sidebar text:', sidebarText.slice(0, 200));
    console.log('product:', await app.evaluate(async ({ app: a }) => a.getName()));
    console.log('screenshot:', shot);

    assert.ok(/GITBUTLER/i.test(sidebarText), `expected the GitButler view to be open; sidebar was: "${sidebarText}"`);
    console.log('CODIUM UI SMOKE OK');
  } finally {
    await app.close();
    fs.rmSync(workspace, { recursive: true, force: true });
    fs.rmSync(userDataDir, { recursive: true, force: true });
    fs.rmSync(extensionsDir, { recursive: true, force: true });
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
