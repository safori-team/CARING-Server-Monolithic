---
name: branch-compare
description: Compare the current branch against main or another branch, summarize commit and file differences, and highlight key risks in Korean. Use when the user asks for branch comparison, differences from main, merge analysis, or a quick diff summary.
---

Always respond in Korean unless the user explicitly asks for another language.
Keep the output short, technical, and easy to scan.

When handling branch comparison:

1. Default to `main...HEAD`.
2. Run `git rev-list --left-right --count <base>...<head>` to quantify commit delta.
3. Run `git diff --name-status <base>...<head>` and `git diff --stat <base>...<head>`.
4. Read the main changed files directly before summarizing risks.
5. If useful, check merge shape with `git merge-tree $(git merge-base <base> <head>) <base> <head>`.

Present results in this order:

1. 비교 대상
2. 커밋 차이
3. 대표 변경 파일
4. 핵심 리스크

Follow these rules:

1. Show only the most important changed files, not the entire diff.
2. Limit risk points to at most 3 short bullets.
3. Call out config, schema, API, dependency, and security changes first.
4. If there is no diff, say that clearly.
