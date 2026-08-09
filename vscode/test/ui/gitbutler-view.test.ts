import { ActivityBar, TreeItem, VSBrowser } from 'vscode-extension-tester';
import { expect } from 'chai';
import { execFileSync } from 'child_process';
import * as fs from 'fs';
import * as os from 'os';
import * as path from 'path';

/**
 * UI end-to-end test driven by ExTester (vscode-extension-tester): it downloads a real
 * VS Code, loads this extension, and drives the actual UI through ChromeDriver — VS Code is
 * an Electron/Chromium app, so this is the "headless Chrome" layer. Runs headless in CI
 * under xvfb (`xvfb-run -a npm run test:ui`).
 *
 * It seeds a REAL GitButler workspace with the `but` CLI when available (so the tree shows
 * live branches/changes), otherwise opens a plain folder (tree shows "Not a GitButler
 * workspace"). Either way the extension must load, register its view, and render a tree —
 * and we capture a screenshot of the running VS Code instance.
 */
function seedWorkspace(): string {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'gb-ui-'));
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

describe('GitButler view (UI e2e)', function () {
  this.timeout(180_000);
  let folder: string;

  before(async () => {
    folder = seedWorkspace();
    await VSBrowser.instance.openResources(folder);
    await VSBrowser.instance.waitForWorkbench();
  });

  it('opens the GitButler activity-bar view and renders a tree', async () => {
    const control = await new ActivityBar().getViewControl('GitButler');
    expect(control, 'GitButler activity-bar control should exist').to.not.be.undefined;

    const view = await control!.openView();
    const sections = await view.getContent().getSections();
    expect(sections.length, 'the view should have at least one section').to.be.greaterThan(0);

    const items = (await sections[0].getVisibleItems()) as TreeItem[];
    const labels = await Promise.all(items.map((i) => i.getLabel()));

    // Capture the running VS Code instance for the artifact.
    await VSBrowser.instance.takeScreenshot('gitbutler-view');

    expect(labels.length, `rendered tree labels: [${labels.join(', ')}]`).to.be.greaterThan(0);
  });
});
