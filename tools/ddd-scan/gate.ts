#!/usr/bin/env node
// gate — run the project's quality commands ONCE, in parallel across resources, with a lock per resource,
// and write a machine-readable result that reviewers READ instead of re-running tests.
//
//   node tools/ddd-scan/gate.ts <name> [--only a,b] [--timeout 900]
//   → .ddd/reviews/gate-<name>.json  (+ prints a 10-line summary)
//
// Config (.ddd/config.json):
//   "commands": { "gate": [
//     { "name": "backend-affected", "cmd": "mvnd -q -pl {{backendModules}} -am verify", "lock": "maven", "timeoutSec": 900 },
//     { "name": "frontend-affected", "cmd": "npx ng test --watch=false --browsers=ChromeHeadless --include {{frontendSpecs}}", "lock": "node" } ] }
// Placeholders {{x}} are replaced from `.ddd/gate-vars.json` (written by the orchestrator: affected modules/specs).
// Entries with the same lock run one after the other; different locks run concurrently. Locks are also
// cross-process (mkdir-based), so parallel sub-agents/worktrees never run two Maven builds at once.
import {existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {spawn} from 'node:child_process';
import {resolve} from 'node:path';
import {acquire} from './lock.ts';

const argv = process.argv.slice(2);
const name = argv[0] && !argv[0].startsWith('--') ? argv[0] : 'current';
const flag = (n: string) => {
    const i = argv.indexOf(`--${n}`);
    return i >= 0 ? argv[i + 1] : undefined;
};
const root = resolve(process.env.CLAUDE_PROJECT_DIR ?? '.');
const cfg = JSON.parse(readFileSync(resolve(root, '.ddd/config.json'), 'utf8'));
const vars: Record<string, string> = existsSync(resolve(root, '.ddd/gate-vars.json')) ? JSON.parse(readFileSync(resolve(root, '.ddd/gate-vars.json'), 'utf8')) : {};
const only = flag('only')?.split(',');
const entries: {
    name: string;
    cmd: string;
    lock?: string;
    timeoutSec?: number
}[] = (cfg.commands?.gate ?? []).filter((e: any) => !only || only.includes(e.name));
if (!entries.length) {
    console.error('no commands.gate in .ddd/config.json');
    process.exit(2);
}
const defaultTimeout = Number(flag('timeout') ?? 900);
const fill = (s: string) => s.replace(/\{\{(\w+)\}\}/g, (_, k) => vars[k] ?? '');
const tail = (s: string, n = 40) => s.split('\n').slice(-n).join('\n');

async function run(e: (typeof entries)[number]) {
    const waited0 = Date.now();
    const release = e.lock ? await acquire(root, e.lock) : () => {
    };
    const waitedSec = Math.round((Date.now() - waited0) / 1000);
    const t0 = Date.now();
    const cmd = fill(e.cmd);
    return new Promise<any>((done) => {
        const p = spawn(cmd, {shell: true, cwd: root, env: process.env});
        let buf = '';
        const add = (d: Buffer) => {
            buf += d.toString();
            if (buf.length > 400_000) buf = buf.slice(-200_000);
        };
        p.stdout.on('data', add);
        p.stderr.on('data', add);
        const to = setTimeout(() => {
            p.kill();
            buf += '\n[gate] TIMEOUT';
        }, (e.timeoutSec ?? defaultTimeout) * 1000);
        p.on('close', (code) => {
            clearTimeout(to);
            release();
            done({
                name: e.name,
                cmd,
                exit: code,
                ok: code === 0,
                durationSec: Math.round((Date.now() - t0) / 1000),
                lockWaitSec: waitedSec,
                tail: tail(buf)
            });
        });
    });
}

// serialize entries that share a lock only through the lock itself; everything else is concurrent
const results = await Promise.all(entries.map(run));
const out = {gate: name, at: new Date().toISOString(), ok: results.every((r) => r.ok), results};
mkdirSync(resolve(root, '.ddd/reviews'), {recursive: true});
writeFileSync(resolve(root, `.ddd/reviews/gate-${name}.json`), JSON.stringify(out, null, 2));
console.log(`gate ${name}: ${out.ok ? 'PASS' : 'FAIL'}`);
for (const r of results) console.log(`  ${r.ok ? '✔' : '✗'} ${r.name} ${r.durationSec}s${r.lockWaitSec > 5 ? ` (waited ${r.lockWaitSec}s for lock)` : ''}`);
for (const r of results.filter((x) => !x.ok)) console.log(`\n--- ${r.name} (tail)\n${tail(r.tail, 25)}`);
process.exit(out.ok ? 0 : 1);
