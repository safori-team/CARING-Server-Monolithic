---
name: pr-create
description: Generate PR markdown files under prs/ and write readable Korean change summaries from git diff context. Use when the user asks to create a PR file, draft PR notes, save PR analysis, or summarize branch changes for a PR.
---

Always respond in Korean unless the user explicitly asks for another language.
Use concise, technical Korean.
Optimize for scanability over completeness.

When creating or updating PR markdown:

1. Default to `main` as base and `HEAD` as head.
2. Collect commit and file changes with:
   - `git log --oneline <base>..<head>`
   - `git diff --name-status <base>...<head>`
   - `git diff --stat <base>...<head>`
3. Read the main changed files directly before writing the summary.
4. Infer the reason for the change from code and commit messages.
5. Save the PR markdown under `prs/`.

Write the PR markdown with these sections:

1. `## PR 제목`
2. `## 변경 요약`
3. `## 변경 파일`
4. `## 커밋 목록`

Write `## 변경 요약` using these subsections:

1. `### 변경 배경/동기`
2. `### 주요 변경 사항`
3. `### 주의할 점`
4. `### 영향 범위`

Follow these readability rules:

1. Prefer short paragraphs and flat bullets over long prose blocks.
2. `주요 변경 사항` should usually be 3 to 5 bullets.
3. `주의할 점` should be short warning bullets focused on deployment, schema, config, or behavior change.
4. If many files changed, list only representative files in `변경 파일`.
5. For `변경 파일`, cap the table at about 8 rows. If more files changed, add one line such as `- 그 외 N개 파일 변경`.
6. Group by user-visible behavior or deployment concern, not by raw file order.
7. Do not repeat the same detail in both `주요 변경 사항` and `변경 파일`.
8. If there is no diff, say that clearly instead of forcing a PR summary.

Use these content rules:

1. Mention DDL impact when entities or schema-related config change.
2. Mention endpoint or contract impact when controllers or DTOs change.
3. Mention deployment prerequisites when workflow, infra, or environment settings change.
4. Avoid dumping every modified file into the summary text.
