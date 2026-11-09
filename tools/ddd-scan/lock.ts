// Cross-process counting semaphore (mkdir-based, works on Windows/macOS/Linux, no dependency).
// A resource "maven" with 2 slots lets at most 2 heavy builds run at once, whatever process or worktree starts them.
import {mkdirSync, readFileSync, rmSync, statSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms));

export function slotsFor(root: string, resource: string): number {
    try {
        const c = JSON.parse(readFileSync(resolve(root, '.ddd/config.json'), 'utf8'));
        return Math.max(1, Number(c.locks?.[resource] ?? 1));
    } catch {
        return 1;
    }
}

/** Waits for a free slot; returns the release function. Stale locks (> 45 min) are reclaimed. */
export async function acquire(root: string, resource: string): Promise<() => void> {
    const base = resolve(root, '.ddd/locks');
    mkdirSync(base, {recursive: true});
    const n = slotsFor(root, resource);
    for (; ;) {
        for (let i = 0; i < n; i++) {
            const dir = resolve(base, `${resource}-${i}`);
            try {
                mkdirSync(dir);
                writeFileSync(resolve(dir, 'pid'), String(process.pid));
                return () => rmSync(dir, {recursive: true, force: true});
            } catch {
                try {
                    if (Date.now() - statSync(dir).mtimeMs > 45 * 60_000) rmSync(dir, {recursive: true, force: true});
                } catch { /* raced */
                }
            }
        }
        await sleep(1500);
    }
}
