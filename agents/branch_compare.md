# branch_compare

## Triggers
- "Compare branches"
- "What is different from main?"
- "What are the merge risks?"
- "Analyze the changes"

## Goal
Identify the differences between the base branch and the target branch, and summarize the risks that may arise during merge.

## Input Rules
- Default base: `main`
- Default head: `HEAD`
- If the user specifies branches, use those branches

## Execution Steps

### Step 1: Check Commit Differences
```bash
git rev-list --left-right --count <base>...<head>
git log --oneline <base>...<head>
```

### Step 2: Check File Changes
```bash
git diff --name-status <base>...<head>
git diff --stat <base>...<head>
```

### Step 3: Analyze Risks
Focus on the following categories among the changed files:
- **Configuration changes**: `application.yml`, `build.gradle`, `SecurityConfig`, etc.
- **Entity changes**: Added, removed, or changed JPA entity fields -> DDL impact
- **API changes**: Added, removed, or changed controller endpoints or signatures -> client impact
- **Dependency changes**: Added, removed, or version-changed libraries in `build.gradle`
- **Security changes**: Changes to security, authentication, or authorization logic

### Step 4: Check Merge Conflict Possibility
```bash
git merge-tree $(git merge-base <base> <head>) <base> <head>
```

## Output Format
1. **Comparison Range**: `<base>...<head>`
2. **Commit Delta**: ahead/behind counts
3. **Key Changed Files**: top file list (Added/Modified/Deleted)
4. **Risk Factors**: up to 5 items with severity (HIGH/MEDIUM/LOW)
5. **Merge Conflicts**: list of expected conflict files, or "No conflicts"
