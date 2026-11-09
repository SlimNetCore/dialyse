#!/usr/bin/env node
// Pipeline state helper — keeps .ddd/state.json consistent without spending LLM tokens on JSON edits.
//   node tools/ddd-scan/state.ts init <legacy|greenfield>
//   node tools/ddd-scan/state.ts set <dotted.path> <value>     e.g. set phases.scan done | set gates.strategic approved
//   node tools/ddd-scan/state.ts get <dotted.path>
//   node tools/ddd-scan/state.ts show                          status table + next recommended command
import {existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';

const file = resolve(process.env.CLAUDE_PROJECT_DIR ?? '.', '.ddd/state.json');
const [cmd, a, b] = process.argv.slice(2);

const PHASES: { key: string; cmd: string; modes: string[]; gate?: string }[] = [
    {key: 'init', cmd: '/ddd-init', modes: ['legacy', 'greenfield']},
    {key: 'scan', cmd: '/ddd-scan', modes: ['legacy']},
    {key: 'discover', cmd: '/ddd-discover', modes: ['legacy', 'greenfield']},
    {key: 'strategic', cmd: '/ddd-strategic', modes: ['legacy', 'greenfield'], gate: 'strategic'},
    {key: 'tactical', cmd: '/ddd-tactical <context>', modes: ['legacy', 'greenfield'], gate: 'tactical'},
    {key: 'architecture', cmd: '/ddd-architecture', modes: ['legacy', 'greenfield']},
    {key: 'plan', cmd: '/ddd-strangle-plan', modes: ['legacy'], gate: 'plan'},
    {key: 'slices', cmd: '/ddd-safety-net <slice> → /ddd-slice <slice>', modes: ['legacy', 'greenfield']},
    {key: 'verify', cmd: '/ddd-verify', modes: ['legacy', 'greenfield']},
];

const load = (): any => (existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : null);
const save = (s: any) => {
    mkdirSync(resolve(file, '..'), {recursive: true});
    writeFileSync(file, JSON.stringify(s, null, 2));
};
const parse = (v: string) => {
    try {
        return JSON.parse(v);
    } catch {
        return v;
    }
};

if (cmd === 'init') {
    if (load()) {
        console.log('state already exists');
        process.exit(0);
    }
    const mode = a === 'greenfield' ? 'greenfield' : 'legacy';
    save({
        mode,
        phases: Object.fromEntries(PHASES.filter((p) => p.modes.includes(mode)).map((p) => [p.key, p.key === 'init' ? 'done' : 'pending'])),
        gates: {},
        slices: {},
        contexts: {}
    });
    console.log(`state initialised (${mode})`);
} else if (cmd === 'set' || cmd === 'get') {
    const s = load();
    if (!s) {
        console.error('no state: run /ddd-init');
        process.exit(2);
    }
    const keys = a.split('.');
    let o = s;
    for (const k of keys.slice(0, -1)) o = o[k] ??= {};
    if (cmd === 'get') console.log(JSON.stringify(o[keys.at(-1)!] ?? null)); else {
        o[keys.at(-1)!] = parse(b);
        save(s);
        console.log('ok');
    }
} else if (cmd === 'show') {
    const s = load();
    if (!s) {
        console.log('No pipeline yet. Run /ddd-init.');
        process.exit(0);
    }
    let next: string | undefined;
    console.log(`mode: ${s.mode}`);
    for (const p of PHASES.filter((x) => x.modes.includes(s.mode))) {
        const st = s.phases[p.key] ?? 'pending';
        const g = p.gate ? s.gates[p.gate] ?? 'pending' : '';
        console.log(`${st === 'done' ? '✔' : st === 'in-progress' ? '…' : '○'} ${p.key.padEnd(13)} ${st.padEnd(12)} ${p.gate ? `gate:${g}` : ''}`);
        if (!next && (st !== 'done' || (p.gate && g !== 'approved'))) next = st === 'done' ? `/ddd-gate ${p.gate} approve` : p.cmd;
    }
    const sl = Object.entries<string>(s.slices ?? {});
    if (sl.length) {
        console.log('slices:');
        sl.forEach(([k, v]) => console.log(`  ${k}: ${v}`));
    }
    const cx = Object.entries<string>(s.contexts ?? {});
    if (cx.length) {
        console.log('contexts (tactical):');
        cx.forEach(([k, v]) => console.log(`  ${k}: ${v}`));
    }
    console.log(`next → ${next ?? '/ddd-verify (all phases complete)'}`);
} else console.log('usage: state.ts init <mode> | set <path> <value> | get <path> | show');
