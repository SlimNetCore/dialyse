#!/usr/bin/env node
// ddd-query — answer targeted questions from .ddd/scan/graph.json without reading source.
//
//   node tools/ddd-scan/query.ts module <id>             files, in/out edges, terms, tests, tables
//   node tools/ddd-scan/query.ts seam <modA> <modB>      file-level edges A→B and B→A (extraction seams)
//   node tools/ddd-scan/query.ts file <path>             imports, importers, symbols, entrypoints
//   node tools/ddd-scan/query.ts term <word>             where a business term appears (language/polysemy)
//   node tools/ddd-scan/query.ts cluster <Cn>            files of a candidate cluster
//   node tools/ddd-scan/query.ts entrypoints [module]    observable behaviours to characterize
//   node tools/ddd-scan/query.ts untested [module]       code files with no test importing them
import {readFileSync} from 'node:fs';
import {join, resolve} from 'node:path';
import type {FileFacts} from './lib.ts';

const argv = process.argv.slice(2);
const dirFlag = argv.indexOf('--scan');
const scanDir = resolve(dirFlag >= 0 ? argv.splice(dirFlag, 2)[1] : '.ddd/scan');
const [cmd, a, b] = argv;
const g = JSON.parse(readFileSync(join(scanDir, 'graph.json'), 'utf8'));
const files: Record<string, FileFacts> = JSON.parse(readFileSync(join(scanDir, 'files.json'), 'utf8'));
const deps: Record<string, string[]> = g.deps;
const mods: string[] = g.modules.map((m: any) => m.id);
const moduleOf = (p: string): string => g.fileModule[p] ?? '(unknown)';
const out = (s: string) => console.log(s);

switch (cmd) {
    case 'module': {
        const m = g.modules.find((x: any) => x.id === a);
        if (!m) {
            out(`unknown module. known: ${mods.join(', ')}`);
            break;
        }
        out(`# ${m.id}: ${m.files} files, ${m.loc} loc, ${m.tests} tests, I=${m.instability}`);
        out(`terms: ${m.terms.join(', ')}`);
        out(`externals: ${m.externals.map((e: any) => e.join('(') + ')').join(', ')}`);
        out('depends on: ' + g.edges.filter((e: any) => e.from === a).map((e: any) => `${e.to}(${e.weight})`).join(', '));
        out('used by:    ' + g.edges.filter((e: any) => e.to === a).map((e: any) => `${e.from}(${e.weight})`).join(', '));
        out('tables: ' + g.tables.filter((t: any) => moduleOf(t.file) === a).map((t: any) => t.table).join(', '));
        out('files: ' + Object.keys(files).filter((p) => !files[p].isTest && moduleOf(p) === a).slice(0, 60).join(', '));
        break;
    }
    case 'seam': {
        for (const [x, y] of [[a, b], [b, a]]) {
            out(`## ${x} → ${y}`);
            for (const [from, ds] of Object.entries(deps)) if (moduleOf(from) === x && !files[from]?.isTest)
                for (const d of ds) if (moduleOf(d) === y) out(`- ${from} → ${d}`);
        }
        break;
    }
    case 'file': {
        const f = files[a];
        if (!f) {
            out('unknown file');
            break;
        }
        out(`${a} [${f.lang}] ${f.loc} loc  test=${f.isTest}`);
        out(`classes: ${f.classes.join(', ')}`);
        out(`funcs: ${f.funcs.slice(0, 40).join(', ')}`);
        out(`imports: ${(deps[a] ?? []).join(', ')}`);
        out(`imported by: ${Object.entries(deps).filter(([, v]) => v.includes(a)).map(([k]) => k).join(', ')}`);
        out(`entrypoints: ${f.entrypoints.map((e) => `${e.kind}:${e.route}`).join(', ')}`);
        break;
    }
    case 'term': {
        const w = a.toLowerCase();
        for (const f of Object.values(files)) {
            const hits = [...f.classes, ...f.funcs, ...f.tableDefs].filter((s) => s.toLowerCase().includes(w));
            if (hits.length && !f.isTest) out(`${moduleOf(f.path)}\t${f.path}\t${hits.slice(0, 6).join(', ')}`);
        }
        break;
    }
    case 'cluster': {
        const c = g.clusters.find((x: any) => x.id === a);
        if (!c) {
            out('unknown cluster');
            break;
        }
        out(`${c.id}: ${c.size} files, ${c.loc} loc, cohesion ${c.cohesion}\nterms: ${c.terms.join(', ')}\n${c.files.join('\n')}`);
        break;
    }
    case 'entrypoints':
        for (const e of g.entrypoints) if (!a || e.module === a) out(`${e.module}\t${e.kind}\t${e.route}\t${e.file}`);
        break;
    case 'untested': {
        const covered = new Set<string>();
        for (const [from, ds] of Object.entries(deps)) if (files[from]?.isTest) ds.forEach((d) => covered.add(d));
        for (const f of Object.values(files)) if (!f.isTest && f.lang !== 'sql' && !covered.has(f.path) && (!a || moduleOf(f.path) === a)) out(`${f.loc}\t${f.path}`);
        break;
    }
    default:
        out('commands: module <id> | seam <A> <B> | file <path> | term <word> | cluster <Cn> | entrypoints [module] | untested [module]   (--scan <dir>)');
}
