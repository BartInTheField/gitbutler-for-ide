import { _electron as electron, type ElectronApplication, type Page } from 'playwright-core';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

/**
 * Feature walkthrough of the VSCode extension in VSCodium: seeds a populated GitButler
 * workspace (a virtual branch with a commit, plus a separate unassigned change), then drives
 * the UI and captures screenshots of each state into vscode/screenshots/. Used to eyeball the
 * feature and find UX gaps. Needs a display (xvfb) and the `but` CLI.
 */
function seedPopulatedWorkspace(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-scn-'));
  const git = (...a: string[]) => execFileSync('git', a, { cwd: dir, stdio: 'ignore' });
  const but = (...a: string[]) =>
    execFileSync('but', a, { cwd: dir, encoding: 'utf8', env: { ...process.env, BUT_OUTPUT_FORMAT: 'json' } });
  git('init', '-q', '-b', 'main', '.');
  git('config', 'user.email', 'scn@example.com');
  git('config', 'user.name', 'scn');
  fs.writeFileSync(path.join(dir, 'README.md'), '# demo\n');
  git('add', '-A');
  git('commit', '-qm', 'init');
  but('setup', '--init');

  // Commit one file onto a virtual branch, leave another change unassigned.
  fs.writeFileSync(path.join(dir, 'feature.ts'), 'export const hello = 1;\n');
  const status = JSON.parse(but('status', '-f', '--json'));
  const change = (status.uncommittedChanges || []).find((c: { filePath: string }) => c.filePath === 'feature.ts');
  but('commit', '-b', 'feature-login', '-m', 'feat: add hello', '--json', change.cliId);
  fs.appendFileSync(path.join(dir, 'README.md'), 'work in progress\n');
  return dir;
}

async function shot(win: Page, name: string, dir: string): Promise<string> {
  const p = path.join(dir, name);
  await win.screenshot({ path: p });
  return p;
}

async function main(): Promise<void> {
  const codium = process.env.CODIUM_BIN || '/usr/share/vscodium/codium';
  const extensionRoot = path.resolve(__dirname, '../..');
  const workspace = seedPopulatedWorkspace();
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-scn-user-'));
  const extensionsDir = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-scn-exts-'));
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
    const win = await app.firstWindow();
    await win.waitForSelector('.monaco-workbench', { timeout: 60_000 });
    await win.locator('.activitybar a[aria-label*="GitButler" i]').first().click();
    await win.locator('.part.sidebar').waitFor({ timeout: 30_000 });
    await win.waitForTimeout(2_500);
    console.log('screenshot:', await shot(win, '01-tree-collapsed.png', shotDir));

    // Expand every collapsible tree node (two passes to reveal nested commit -> files).
    for (let pass = 0; pass < 3; pass++) {
      const twisties = win.locator('.part.sidebar .monaco-tl-twistie.collapsible.collapsed');
      const n = await twisties.count();
      for (let i = 0; i < n; i++) {
        await twisties.nth(0).click().catch(() => undefined);
        await win.waitForTimeout(300);
      }
    }
    await win.waitForTimeout(1_000);
    console.log('screenshot:', await shot(win, '02-tree-expanded.png', shotDir));

    const rows = await win.locator('.part.sidebar .monaco-list-row').allInnerTexts();
    console.log('TREE ROWS:', JSON.stringify(rows.map((r) => r.replace(/\s+/g, ' ').trim()).filter(Boolean)));

    // Command palette — evaluate command discoverability/naming.
    await win.keyboard.press('Control+Shift+P');
    await win.waitForTimeout(600);
    await win.keyboard.type('GitButler');
    await win.waitForTimeout(1_200);
    console.log('screenshot:', await shot(win, '03-command-palette.png', shotDir));
    const cmds = await win.locator('.quick-input-list .monaco-list-row').allInnerTexts();
    console.log('COMMANDS:', JSON.stringify(cmds.map((c) => c.replace(/\s+/g, ' ').trim()).filter(Boolean)));
    await win.keyboard.press('Escape');

    console.log('SCENARIOS OK');
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
