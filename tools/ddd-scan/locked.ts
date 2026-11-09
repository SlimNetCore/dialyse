#!/usr/bin/env node
// locked — run ONE heavy command under a resource slot, so parallel agents never overload the machine.
//   node tools/ddd-scan/locked.ts <resource> -- <command...>
//   e.g. locked.ts maven -- mvnd -q -pl orders -Dtest=OrderTest test
//        locked.ts node  -- npx ng test --watch=false --include src/app/orders/**/*.spec.ts
// Slots per resource: .ddd/config.json → "locks": { "maven": 2, "node": 1 }  (default 1)
import {spawn} from 'node:child_process';
import {resolve} from 'node:path';
import {acquire} from './lock.ts';

const argv = process.argv.slice(2);
const sep = argv.indexOf('--');
if (sep < 1 || sep === argv.length - 1) {
    console.error('usage: locked.ts <resource> -- <command...>');
    process.exit(2);
}
const root = resolve(process.env.CLAUDE_PROJECT_DIR ?? '.');
const t0 = Date.now();
const release = await acquire(root, argv[0]);
const waited = Math.round((Date.now() - t0) / 1000);
if (waited > 3) console.error(`[locked] waited ${waited}s for a free "${argv[0]}" slot`);
const q = (a: string) => (/[\s()<>&|;"'`$*?]/.test(a) ? `"${a.replace(/(["\\])/g, '\\$1')}"` : a);
const p = spawn(argv.slice(sep + 1).map(q).join(' '), {shell: true, stdio: 'inherit', cwd: process.cwd()});
const stop = () => {
    release();
};
p.on('close', (code) => {
    stop();
    process.exit(code ?? 1);
});
process.on('SIGINT', () => {
    p.kill();
    stop();
    process.exit(130);
});
