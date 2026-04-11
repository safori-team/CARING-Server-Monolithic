# AGENTS.md (Codex)

## Project
CARING Server is a Spring Boot 3.4.1 / Java 17 monolithic backend.

## Task Routing
Use the skill specs below.

| task | spec | trigger |
|---|---|---|
| `branch_compare` | `.agents/skills/branch-compare/SKILL.md` | branch comparison, changes from `main`, merge risk, quick diff analysis |
| `pr_create` | `.agents/skills/pr-create/SKILL.md` | PR markdown creation, PR summary, PR write-up |

- If the user asks for both comparison and PR writing, do `branch_compare` first, then `pr_create`.
- Default comparison range is `main...HEAD`.

## PR Writing Principles
- Always write PR summaries in Korean unless the user explicitly asks for English.
- Prefer short sections and flat bullets over long paragraphs.
- If many files changed, list only representative files in `변경 파일` and summarize the rest.
- `주요 변경 사항` and `주의할 점` should be scannable in a few bullets, not dense prose.
- Focus on intent, behavior change, deployment impact, and review risk.
