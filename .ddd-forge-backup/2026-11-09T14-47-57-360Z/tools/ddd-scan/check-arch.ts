#!/usr/bin/env node
// check-arch — architecture fitness function. Fails (exit 1) on NEW violations of:
//   1. hexagonal layer rules (domain ← application ← infrastructure/interface)
//   2. forbidden frameworks inside the domain layer
//   3. cross-bounded-context imports that bypass the context's public API
//
//   node tools/ddd-scan/check-arch.ts [root] [--rules .ddd/arch-rules.json] [--update-baseline]
// Legacy code: list it under "exclude"; known old violations go to the baseline (ratchet) so only new ones fail CI.
import {existsSync, readFileSync, writeFileSync, mkdirSync} from 'node:fs';
import {dirname, resolve} from 'node:path';
import {buildResolver, externalName, extractFacts, globToRegExp, readText, walk} from './lib.ts';

const argv = process.argv.slice(2);
const flag = (n: string, d: string) => {
    const i = argv.indexOf(`--${n}`);
    return i >= 0 ? argv[i + 1] : d;
};
const root = resolve(argv.find((a, i) => !a.startsWith('--') && !(argv[i - 1] ?? '').startsWith('--')) ?? '.');
const rulesPath = resolve(root, flag('rules', '.ddd/arch-rules.json'));
const baselinePath = resolve(root, '.ddd/arch-baseline.json');
const hookMode = argv.includes('--hook');
if (hookMode && !existsSync(rulesPath)) process.exit(0); // PostToolUse: stay silent until /ddd-architecture ran
if (!existsSync(rulesPath)) {
    console.error(`missing ${rulesPath} — run /ddd-architecture first`);
    process.exit(2);
}

interface Rules {
    layers: Record<string, string[]>;
    allow: Record<string, string[]>;
    forbiddenExternalInLayer?: Record<string, string[]>;
    contexts?: { root: string; publicApi: string[] };
    exclude?: string[];
}

const rules: Rules = JSON.parse(readFileSync(rulesPath, 'utf8'));
const layerRe = Object.entries(rules.layers).map(([l, gs]) => [l, gs.map(globToRegExp)] as const);
const excl = (rules.exclude ?? []).map(globToRegExp);
const layerOf = (p: string) => layerRe.find(([, rs]) => rs.some((r) => r.test(p)))?.[0];
const ctxRoot = rules.contexts?.root.replace(/\/$/, '');
const ctxOf = (p: string) => ctxRoot && p.startsWith(ctxRoot + '/') ? p.slice(ctxRoot.length + 1).split('/')[0] : undefined;
const apiRe = (rules.contexts?.publicApi ?? []).map(globToRegExp);

const facts = walk(root).map((w) => extractFacts(w, readText(w.abs), ''));
const resolveImport = buildResolver(facts);
const violations: string[] = [];
for (const f of facts) {
    if (f.isTest || excl.some((r) => r.test(f.path))) continue;
    const from = layerOf(f.path);
    const fromCtx = ctxOf(f.path);
    for (const spec of f.imports) {
        const target = resolveImport(spec, f);
        if (target) {
            const to = layerOf(target);
            if (from && to && !(rules.allow[from] ?? []).includes(to)) violations.push(`LAYER   ${f.path} (${from}) → ${target} (${to})`);
            const toCtx = ctxOf(target);
            if (fromCtx && toCtx && fromCtx !== toCtx && !apiRe.some((r) => r.test(target)))
                violations.push(`CONTEXT ${f.path} (${fromCtx}) → ${target} (${toCtx}) bypasses public API`);
        } else if (from) {
            const ext = externalName(f.lang, spec);
            if (ext && (rules.forbiddenExternalInLayer?.[from] ?? []).some((bad) => ext === bad || ext.startsWith(bad)))
                violations.push(`FRAMEWORK ${f.path} (${from}) imports ${ext}`);
        }
    }
}
const uniq = [...new Set(violations)].sort();
if (argv.includes('--update-baseline')) {
    mkdirSync(dirname(baselinePath), {recursive: true});
    writeFileSync(baselinePath, JSON.stringify(uniq, null, 1));
    console.log(`baseline updated: ${uniq.length} known violations frozen`);
    process.exit(0);
}
const base = new Set<string>(existsSync(baselinePath) ? JSON.parse(readFileSync(baselinePath, 'utf8')) : []);
const fresh = uniq.filter((v) => !base.has(v));
if (hookMode) { // exit 2 feeds stderr back to Claude so it fixes the violation immediately
    if (fresh.length) {
        console.error(`arch-check: ${fresh.length} NEW architecture violation(s):\n${fresh.slice(0, 15).map((v) => `  ✗ ${v}`).join('\n')}\nFix them (see .claude/rules/11-hexagonal-dependencies.md).`);
        process.exit(2);
    }
    process.exit(0);
}
console.log(`arch-check: ${uniq.length} violations (${base.size} baselined, ${fresh.length} new)`);
fresh.slice(0, 50).forEach((v) => console.log(`  ✗ ${v}`));
process.exit(fresh.length ? 1 : 0);
