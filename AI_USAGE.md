# AI Usage

This file is a running log, updated as work happens (not reconstructed at the end).

## Tools used
| Tool | Used for |
|---|---|
| Claude (claude.ai chat) | Planning, architecture and data-model design, scaffolding code, review of design decisions |
| (add others: Cursor, Claude Code, Copilot, ...) | |

## What AI generated vs what I reviewed or changed
Be specific. For every milestone, record: what was generated, what I read line by line, what I changed, and why.

| Milestone | AI-generated | Reviewed / changed by me | Notes |
|---|---|---|---|
| Planning | Initial engineering plan (PLAN.md), ADR drafts | Chose the stack, the time model and the free-tier hosting approach | Decisions are in `docs/adr/` |
| M0 | Project skeleton, error model, correlation-id filter, CI, tests | _fill in: what I read, ran and changed_ | Not yet run by the author at the time of writing; verify before relying on it |

## Notable prompts and workflows
- Gave the AI the assignment brief and my CV, asked for a senior-level plan with milestones and "done when" criteria, then worked milestone by milestone.
- _Add prompts that were effective, and cases where the AI was wrong and I corrected it._

## Things I verified myself
- _Examples: ran the full test suite, read all authorization and recurrence code, tested the MCP tools with a real client._
