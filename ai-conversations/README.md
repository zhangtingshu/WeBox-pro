# AI programming conversation record — `ai-conversations/`

**PRD §7.2, deliverable 6.** This directory holds the **complete, tool-native raw export** of the
AI coding session that produced this submission, from the first user instruction through the final
verification of the delivered package.

## What is here

| File | Role |
|------|------|
| `session-2026-11-11-workbuddy-native.jsonl` | **The evidence.** A verbatim copy of the coding tool's own session log. |
| `README.md` | This provenance note. It is documentation, not a conversation turn. |

Nothing else is shipped in this directory. §7.2 explicitly rejects screenshots, excerpts,
fragments and **after-the-fact summaries** — so there is no reconstructed transcript, no condensed
playbook and no derived appendix here. (The separately authored "how to talk to an AI coding tool"
script is a candidate deliverable, not part of the session log; it lives in `docs/PROMPT-DECK.md`.)

## Provenance & integrity

| Field | Value |
|-------|-------|
| Tool | WorkBuddy — Claude-based coding agent (Claude Code-style tool loop) |
| Session ID | `242f76ea-5f39-457d-8f0a-f056fc0f6e2c` |
| Source log | `~/.workbuddy/projects/Users-zhangtingshu-WorkBuddy-2026-11-11-11-48-38/<sessionId>.jsonl` |
| Workspace | `/Users/zhangtingshu/WorkBuddy/2026-11-11-11-48-38` |
| Copy method | Verbatim byte copy — no filtering, re-ordering, editing, trimming or redaction |
| Records | **1230** |
| File size | 6,381,973 bytes |
| SHA-256 | `795c7aa687c039277c43188542f0875fba6a453e54bbb121d0974b87b52af8e3` |
| Time span | 2026-11-11 11:48:39 CST → 2026-11-11 13:34:26 CST |
| Message records | 75 (of which 26 are user turns) |
| Screenshots / excerpts / summaries | **none** |

### Record types

| Type | Count |
|------|-------|
| `function_call` | 383 |
| `function_call_result` | 381 |
| `reasoning` | 232 |
| `file-history-snapshot` | 137 |
| `message` | 75 |
| `session-meta` | 21 |
| `ai-title` | 1 |

Each `function_call` carries the full tool arguments; each `function_call_result` carries the raw,
unedited tool output (compile errors, build logs, test results, curl responses, failed attempts).
`file-history-snapshot` entries preserve per-file history. Nothing "uninteresting" was removed —
the rubric rejects excerpts.

## Why this satisfies §7.2

- It is the coding tool's **native export**, not a hand-written recap or a post-hoc narrative.
- It covers the whole challenge: requirement reading, architecture decisions, all code writes,
  every build/compile failure and fix, all test runs, and the final documentation + verification.
- Failed attempts are kept verbatim; they are part of the engineering record.
- Only one AI coding tool was used, so one export covers the entire session. If the challenge is
  re-run with additional tools, **append** each tool's native export here as another file — do not
  replace this one.
- The path is also declared in the root `README.md` (§7.2 checklist, item 6).

## Verify it yourself

```bash
cd ai-conversations
wc -l session-2026-11-11-workbuddy-native.jsonl          # 1230 records
shasum -a 256 session-2026-11-11-workbuddy-native.jsonl  # 795c7aa687c03927...
python3 -c "import json,collections; rows=[json.loads(l) for l in open('session-2026-11-11-workbuddy-native.jsonl',encoding='utf-8') if l.strip()]; print(len(rows), collections.Counter(r.get('type') for r in rows))"
```

## Native export paths for other tools (reference)

| Tool | Export path |
|------|-------------|
| WorkBuddy (used here) | `~/.workbuddy/projects/<project>/<sessionId>.jsonl` |
| Claude Code | `~/.claude/projects/<project>/*.jsonl` |
| Cursor | Chat panel → Export / `cursor-chat` extension → full `.md` transcript |
| Windsurf / Cline | Cascade / Chat export → `.md` or `.json` |
| ChatGPT | Settings → Data controls → Export data → `conversations.json` |
| GitHub Copilot Chat | VS Code Chat → Export chat transcript |

Keep original filenames and timestamps; never trim turns.
