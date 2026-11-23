# Emberfall GitHub Agent Dispatch MVP

This folder contains declarative task packets consumed by
`.github/workflows/emberfall-agent-dispatch.yml`.

## Fast path

1. Add the repository Actions secret `COPILOT_AGENT_TOKEN`.
2. Use a user-to-server fine-grained PAT with repository access and read/write
   permissions for Actions, Contents, Issues and Pull Requests. GitHub's Copilot
   agent-task API does not accept a GitHub App installation token.
3. Commit one JSON request file to this folder on
   `docs/emberfall-release-governance`. That push starts one dispatch workflow.
4. The workflow validates the packet, calls the Copilot cloud-agent task API,
   and uploads the request/response receipt as a 30-day artifact.
5. Copy the returned task ID into a status request file under
   `maestro/status-requests/` or run the status workflow manually.

Example request:

```json
{
  "schema_version": "1.0",
  "task_id": "AO-04",
  "role": "RESEARCHER",
  "base_ref": "docs/emberfall-release-governance",
  "create_pull_request": false,
  "prompt": "A bounded task with exact scope, baseline, acceptance criteria and return format."
}
```

Allowed roles: `RESEARCHER`, `ARCHITECT`, `BUILDER`,
`INDEPENDENT_REVIEWER`. Only a `BUILDER` request may set
`create_pull_request: true`.

## Status lookup

Create `maestro/status-requests/<name>.json`:

```json
{ "task_api_id": "COPILOT_TASK_ID" }
```

The status workflow fetches the current state and preserves the API response as
a 30-day artifact.

## Current dispatched packets

- `AO-01-researcher.json` — workflow and Maestro inventory.
- `AO-02-architect.json` — SQLite/WAL checkpoint and resume design.
- `AO-03-reviewer.json` — dispatch workflow security/correctness review.

## Important

- These workflows use GitHub Copilot cloud agent via the public-preview agent
  tasks API. Repository Copilot availability, billing and PAT permissions must
  be enabled for dispatch to succeed.
- A pushed request is not proof that the agent started. Check the Actions run,
  HTTP result and receipt artifact.
- The current MVP stores receipts as Actions artifacts; Maestro SQLite
  cross-run persistence is still a separate AO-02 engineering task.
- Agents cannot merge or release. Product Owner and human merge gates remain.
