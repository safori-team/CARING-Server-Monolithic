# AGENTS.md (Codex)

## Project
CARING Server - a monolithic backend for a care service. Spring Boot 3.4.1, Java 17.

## Task Routing
Codex routes requests to the tasks below.

| task | spec | trigger |
|---|---|---|
| `branch_compare` | `agents/branch_compare.md` | branch comparison, differences from `main`, merge risk, change analysis |
| `pr_create` | `agents/pr_create.md` | creating a PR, PR summary, PR write-up |

- If branch comparison and PR creation are requested together, run `branch_compare` first, then `pr_create`.
- If the request is ambiguous, start with `branch_compare`.

## Defaults
- Base: `main`, Head: `HEAD`
- To compare a different local branch: `--head-ref <branch>`

## PR Analysis Flow
1. Check changed files with `git diff <base>...<head>`
2. Read the key changed files directly to understand the intent of the code
3. Write the `## Change Summary` section in `prs/*.md` in English

### Change Summary Structure
- **Background / Motivation**: Why this change is needed (inferred from commit messages and code)
- **Key Changes**: Core changes as bullet points (what, why, and how)
- **Notes / Cautions**: Breaking changes, new dependencies, design changes
- **Impact Scope**: Effects on existing functionality

### Writing Rules
- Write in English, technical but easy to read
- Do not turn the summary into a file list; that belongs in a separate section
- Focus on the intent behind the code changes

## Output
- `branch_compare`: commit delta, file diff, short risk notes.
- `pr_create`: `prs/*.md` path, change summary.
