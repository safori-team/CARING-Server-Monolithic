# pr_create

## Triggers
- "Create a PR"
- "Write a PR"
- "Summarize this PR"
- "Generate a PR description"

## Goal
Analyze the changes in the current branch and create a PR description markdown file in the `prs/` directory.

## Input Rules
- Default base: `main`
- Default head: `HEAD` (current branch)
- File name: `prs/<branch_name>.md`

## Execution Steps

### Step 1: Collect Changes
```bash
git log --oneline <base>...<head>
git diff --name-status <base>...<head>
git diff <base>...<head>
```

### Step 2: Analyze the Code
- Read the key changed files directly to understand the intent of the code
- Infer the motivation for the changes from commit messages

### Step 3: Write the PR Markdown
Create `prs/<branch_name>.md` using the template below:

```markdown
## PR Title
<A concise one-line title>

## Change Summary

### Background / Motivation
<Why this change is needed>

### Key Changes
- <Core change 1: what, why, and how>
- <Core change 2>

### Notes / Cautions
- <Breaking changes, new dependencies, design changes, etc.>

### Impact Scope
- <Impact on existing functionality>

## Changed Files
| Status | File |
|---|---|
| A/M/D | path/to/file |

## Commit List
- <hash> <message>
```

## Writing Rules
- Write in English
- Keep it technical but easy to read
- The `Change Summary` should focus on the intent of the code changes, not just list files
- If entity changes exist, explicitly mention DDL impact
- If API changes exist, explicitly mention endpoint changes

## Output Format
1. Generated file path (`prs/*.md`)
2. Suggested PR title
3. 1 to 3 key review points
