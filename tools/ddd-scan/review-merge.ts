#!/usr/bin/env node
// review-merge — merge the JSON findings of the parallel reviewers: dedupe, rank, verdict, one markdown report.
//   node tools/ddd-scan/review-merge.ts <slice>
// Reads  .ddd/reviews/<slice>/*.json   each: { "reviewer": "domain", "findings": [ {severity,file,line,summary,evidence,fix} ] }
//        .ddd/reviews/gate-<slice>.json (optional)
// Writes .ddd/reviews/<slice>/REPORT.md and prints a ≤ 15-line summary.
import {existsSync, readdirSync, readFileSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';

const slice = process.argv[2];
if (!slice) {
    console.error('usage: review-merge.ts <slice>');
    process.exit(2);
}
const root = resolve(process.env.CLAUDE_PROJECT_DIR ?? '.');
const dir = resolve(root, `.ddd/reviews/${slice}`);
if (!existsSync(dir)) {
    console.error(`no reviews in ${dir}`);
    process.exit(2);
}

const RANK: Record<string, number> = {blocker: 0, major: 1, minor: 2, nit: 3};
const all: any[] = [];
for (const f of readdirSync(dir).filter((x) => x.endsWith('.json'))) {
    try {
        const j = JSON.parse(readFileSync(resolve(dir, f), 'utf8'));
        for (const x of j.findings ?? []) all.push({...x, reviewer: j.reviewer ?? f});
    } catch {
        all.push({
            severity: 'major',
            reviewer: f,
            file: f,
            line: 0,
            summary: 'invalid reviewer output (not JSON)',
            evidence: '',
            fix: 'rerun this reviewer'
        });
    }
}
const key = (x: any) => `${x.file}:${x.line ?? 0}:${String(x.summary).toLowerCase().replace(/\W+/g, ' ').slice(0, 60)}`;
const merged = new Map<string, any>();
for (const x of all) {
    const k = key(x);
    const prev = merged.get(k);
    if (!prev) merged.set(k, {...x, reviewers: [x.reviewer]});
    else {
        prev.reviewers.push(x.reviewer);
        if ((RANK[x.severity] ?? 9) < (RANK[prev.severity] ?? 9)) prev.severity = x.severity;
    }
}
const rows = [...merged.values()].filter((x) => x.evidence || x.severity === 'nit' || x.summary).sort((a, b) => (RANK[a.severity] ?? 9) - (RANK[b.severity] ?? 9));
let gate: any = null;
const gp = resolve(root, `.ddd/reviews/gate-${slice}.json`);
if (existsSync(gp)) gate = JSON.parse(readFileSync(gp, 'utf8'));
const count = (s: string) => rows.filter((r) => r.severity === s).length;
const verdict = (gate && !gate.ok) || count('blocker') ? 'fail' : count('major') ? 'pass-with-actions' : 'pass';

const md = [`# Revue ${slice}`, '', `**Verdict : ${verdict}** — blockers ${count('blocker')} · majors ${count('major')} · minors ${count('minor')} · nits ${count('nit')}${gate ? ` · gate ${gate.ok ? 'PASS' : 'FAIL'}` : ''}`, '',
    '| sév. | domaine | constat | preuve | correction |', '|---|---|---|---|---|',
    ...rows.map((r) => `| ${r.severity} | ${r.reviewers.join('+')} | ${r.summary} | ${r.file}:${r.line ?? 0} ${r.evidence ? '— ' + String(r.evidence).replace(/\|/g, '\\|').slice(0, 120) : ''} | ${String(r.fix ?? '').replace(/\|/g, '\\|')} |`)].join('\n');
writeFileSync(resolve(dir, 'REPORT.md'), md);
console.log(`review ${slice}: ${verdict} — blockers ${count('blocker')}, majors ${count('major')}, minors ${count('minor')}, nits ${count('nit')}${gate ? `, gate ${gate.ok ? 'PASS' : 'FAIL'}` : ''}`);
rows.filter((r) => RANK[r.severity] <= 1).slice(0, 8).forEach((r) => console.log(`  [${r.severity}] ${r.file}:${r.line ?? 0} ${r.summary}`));
console.log(`report: .ddd/reviews/${slice}/REPORT.md`);
process.exit(verdict === 'fail' ? 1 : 0);
