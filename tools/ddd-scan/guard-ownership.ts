#!/usr/bin/env node
// PreToolUse hook (Edit|Write|MultiEdit): when parallel workers run, each may only write inside its own paths.
// Exit 2 = block + message fed back to the agent.
//
// .ddd/slices/<slice>/ownership.json:  { "slice-domain": ["src/contexts/orders/domain/**", "src/test/**/orders/domain/**"], ... }
// The active slice is read from .ddd/active-slice (one line, written by /ddd-ship).
// The hook identifies the calling sub-agent through `agent_type` in the hook input. If the host does not provide it,
// the hook does nothing (fail-open) and ownership is enforced only by the agent prompts — see docs/fast-flow.md.
import {existsSync, readdirSync, readFileSync, statSync} from 'node:fs';
import {join, relative, resolve} from 'node:path';
import {globToRegExp} from './lib.ts';

let input: any = {};
try {
    input = JSON.parse(readFileSync(0, 'utf8'));
} catch {
    process.exit(0);
}
const cwd = resolve(input.cwd ?? process.env.CLAUDE_PROJECT_DIR ?? '.');
const agent: string | undefined = input.agent_type ?? input.agent_name;
const target = input.tool_input?.file_path;
if (!agent || !target) process.exit(0);

// Active slice: .ddd/active-slice (main tree) — or, in a git worktree where that untracked file is absent,
// the most recent .ddd/slices/*/ownership.json that declares this agent (ownership.json is committed with the contract).
const activeFile = join(cwd, '.ddd/active-slice');
let slice = existsSync(activeFile) ? readFileSync(activeFile, 'utf8').trim() : '';
if (!slice) {
    const base = join(cwd, '.ddd/slices');
    const cands = existsSync(base) ? readdirSync(base).filter((d) => existsSync(join(base, d, 'ownership.json')) && agent in JSON.parse(readFileSync(join(base, d, 'ownership.json'), 'utf8')))
        .sort((x, y) => statSync(join(base, y, 'ownership.json')).mtimeMs - statSync(join(base, x, 'ownership.json')).mtimeMs) : [];
    slice = cands[0] ?? '';
}
const ownFile = join(cwd, `.ddd/slices/${slice}/ownership.json`);
if (!slice || !existsSync(ownFile)) process.exit(0);
const own: Record<string, string[]> = JSON.parse(readFileSync(ownFile, 'utf8'));
if (!own[agent]) process.exit(0); // agent not under ownership control (e.g. reviewers are read-only by prompt)

const rel = relative(cwd, resolve(cwd, target)).replace(/\\/g, '/');
if (rel.startsWith('.ddd/reviews/') || rel.startsWith('.ddd/slices/')) process.exit(0); // reports/results are always writable
if (own[agent].some((g) => globToRegExp(g).test(rel))) process.exit(0);

console.error(`BLOCKED by ownership rule: agent "${agent}" may not write "${rel}" (slice ${slice}).\n` +
    `Allowed: ${own[agent].join(', ')}.\nIf the file belongs to another worker, report it as a contract change in your summary instead of editing it.`);
process.exit(2);
