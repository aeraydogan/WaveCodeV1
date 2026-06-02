# Android Root Cause Debugger Minimal Token Skill

## Purpose

Use this skill when debugging Android bugs with logs/code while minimizing token usage.

## Response Format

Always answer in this compact format:

1. Root Cause
- one paragraph maximum

2. Evidence
- cite exact log lines or code paths
- no long explanation

3. Minimal Fix
- files to modify
- methods to modify
- exact change list

4. What Not To Change
- list only important exclusions

5. Validation
- expected logs or test steps

## Rules

Do not:
- repeat the whole project history
- write broad architecture unless requested
- rewrite unrelated files
- propose multiple large alternatives
- add dependencies unless necessary
- change public APIs unless necessary

Prefer:
- small diff
- targeted logs
- root cause first
- one fix at a time
