# EMBERFALL — Governed Agent Orchestration

**Status:** Proposed operating architecture; implementation must be validated against the pinned Maestro and GitHub workflow contracts before activation.  
**Date:** 2026-11-23  
**Authority:** Product Owner decisions, accepted ADRs, `AGENTS.md`, Maestro v0.3.0 and the current release-readiness gates.

## 1. Decision

Use **GitHub Actions as the dispatch and execution plane; Maestro as the increment lifecycle and acceptance authority; GitHub Issues/PRs as the visible work and delivery interface; and the Product Owner as the scope, merge and release authority.**

Do not replace Maestro with a second orchestration database. Do not start a new Maestro workflow on every push. A coherent increment has one durable workflow identity and one current cycle; individual agent sessions and Actions runs are disposable execution attempts linked to that identity.

The system must gather independent inputs, dispatch bounded work to suitable agents, collect structured returns, validate evidence, and stop at explicit gates. It must never infer completion from an agent saying “done”.

## 2. Responsibilities and authority

| Component / role | Owns | Must not do |
|---|---|---|
| Product Owner (human) | Product intent, accepted decisions, scope changes, exception/risk disposition, merge and release authorization | Delegate final product authority to an agent |
| Maestro | Increment identity, lifecycle stage, work items, assignments, candidate binding, Mastery and Repetition records | Pretend it has launched an agent when only a packet was created |
| GitHub Actions orchestrator | Validate dispatch envelope, restore/checkpoint state, schedule workers, collect outputs, enforce deterministic gates | Invent game rules or approve its own generated implementation |
| Researcher | Source-backed research, options, evidence and uncertainty | Modify gameplay baseline or silently choose a product direction |
| Architect | Translate accepted product rules into technical contracts and bounded work | Reopen accepted ADRs without new evidence and Product Owner authorization |
| Builder | Implement one bounded task in an isolated branch/worktree | Merge, self-review or change protected baseline |
| Independent Reviewer | Adversarially inspect exact candidate SHA and evidence | Review its own work or rely only on the builder's narrative |
| QA / GateRunner | Reproducible deterministic checks, runtime evidence and regression gates | Treat static inspection as runtime proof |
| Synthesis / Orchestrator | Reconcile findings, contradictions, provenance and next action | Hide disagreement or convert uncertainty into PASS |

## 3. Execution topology

```text
Product Owner / accepted baseline
              |
              v
       Maestro increment
       GROUNDING stage
              |
              v
  Orchestrator: validate envelope,
  dependencies, SHA and permissions
              |
      +-------+--------+
      |       |        |
      v       v        v
  Research  Architect  Risk/UX/QA
  agents    agent      input agents
      |       |        |
      +-------+--------+
              |
              v
  Synthesis: reconcile evidence,
  write bounded Momentum work items
              |
              v
     Maestro MOMENTUM stage
              |
              v
  One Builder -> isolated branch -> PR
              |
              v
  Exact-SHA deterministic CI / GateRunner
              |
       +------+------+
       |             |
       v             v
  Independent    Additional QA /
  Mastery review  device validation
       |             |
       +------+------+
              |
              v
  Maestro MASTERY decision
              |
       PASS / CONDITIONAL?
              |
              v
  Repetition packet + durable checkpoint
              |
              v
  Product Owner acceptance / next increment
```

Parallelism is for genuinely independent research, architecture critique, test design and asset/legal investigation. There is **one authoritative builder candidate per increment at a time** unless the Architect explicitly splits work into non-overlapping modules with separate contracts. Never let several agents edit the same branch or shared files concurrently.

## 4. GitHub workflow set

Implement these as small, composable workflows after inspecting the existing Actions files and Maestro CLI/API at the pinned commits. Names below are proposed identifiers, not a claim that the files already exist.

| Workflow | Trigger | Responsibility |
|---|---|---|
| `emberfall-orchestrate.yml` | `workflow_dispatch`; selected issue/PR events | Validate task/cycle, restore state, create dispatch envelope, choose adapter, record run |
| `emberfall-research.yml` | `workflow_call` and controlled dispatch | Run a bounded source-backed research agent; return structured report and citations |
| `emberfall-architect.yml` | `workflow_call` | Analyze accepted contracts, dependencies, design alternatives and work decomposition |
| `emberfall-build.yml` | Explicit dispatch after Momentum authorization | Start one builder in an isolated branch/worktree; open a draft PR; return candidate SHA |
| `emberfall-review.yml` | Candidate-ready event / explicit dispatch | Run an independent reviewer against exact SHA, diff, test evidence and acceptance contract |
| `emberfall-gates.yml` | PR opened/synchronized and explicit dispatch | Run deterministic project-specific checks and publish exact-SHA evidence |
| `emberfall-reconcile.yml` | Workflow completion / manual recovery | Validate returns, update Maestro checkpoint and portfolio, or mark blocked |

Use `workflow_call` for reusable worker units and `workflow_dispatch` for explicit operator-controlled starts and recovery. Use `workflow_run` or narrowly scoped PR/issue events for follow-up, not an unbounded push-trigger loop. Serialize state-mutating runs with a concurrency key based on task and cycle; do not cancel a run while it is writing a state checkpoint.

The orchestrator must be idempotent. A repeated event with the same `task_id + cycle_id + role + attempt` must not silently create a duplicate workflow or duplicate agent session. Store dispatch IDs and provider session IDs before retrying. Set bounded retry counts and a terminal BLOCKED state.

## 5. Agent dispatch adapters

Use an adapter interface so the process is not coupled to one model vendor:

### Adapter A — GitHub Copilot cloud agent

Best initial coding adapter if Copilot cloud agent is enabled for this repository/account. Create a detailed GitHub Issue with the complete task contract, then assign Copilot; GitHub documents that assigning an issue starts a cloud-agent session and results in a reviewable PR. The issue must exist before assignment, and all required context must be in the issue at assignment time.

### Adapter B — GitHub Agentic Workflows (`gh-aw`)

Evaluate as the multi-provider adapter for research, triage, review preparation and controlled follow-up. Its Markdown workflow definitions compile to Actions workflows and support engines including Copilot, Claude, Codex and Gemini. Safe outputs separate agent-proposed operations from privileged GitHub write jobs. It also supports dispatching allowlisted workflows and creating Copilot agent sessions.

Treat `gh-aw` as an optional/preview adapter until its exact version, security model, billing, secrets and failure/recovery behavior have been tested in this repository. Pin the compiler and generated lock files; review both source and compiled workflow. Do not allow an agent to dispatch arbitrary workflow names, refs or repositories.

### Adapter C — Direct provider CLI/API in an isolated Actions job

Use only where a provider has a supported CLI/API, credentials are stored as Actions secrets, and the job can enforce a bounded prompt, tool allowlist, timeout, output schema and least-privilege permissions. This adapter must report actual provider/model/session identity. Never pass repository write credentials to the model process.

### Adapter D — Human / manual execution

A first-class fallback for unavailable providers, sensitive decisions and independent review. A manual review is recorded with the real reviewer identity and evidence. Do not fake a reviewer assignment to satisfy Maestro.

**Copilot dispatch caveat:** current `gh-aw` documentation states that Copilot cloud-agent assignment/session creation requires a suitable fine-grained or classic PAT; GitHub App installation tokens are not accepted for that operation. Store only the minimum required token as a protected repository/environment secret. Confirm the current permission list at setup time. The normal Actions `GITHUB_TOKEN` should not be assumed to have this capability.

## 6. Dispatch envelope — required contract

Every agent invocation receives a versioned JSON envelope. No agent should be started from an informal one-line prompt.

Required fields:

- `schema_version`, `task_id`, `cycle_id`, `work_item_id`, `attempt`, `role`.
- `repository`, `base_ref`, immutable `base_sha`, and allowed candidate branch prefix.
- `product_baseline_refs`: game spec, Product Owner decisions, accepted ADRs, task packet and relevant prior review/repetition records.
- `objective`, `in_scope`, `out_of_scope`, `allowed_paths`, `forbidden_paths`.
- `acceptance_criteria`, `required_checks`, `expected_return_schema`, `stop_conditions`.
- `dependencies` and artifact references with SHA-256 hashes.
- `provider`, actual `model_id`, `agent_session_id`, `prompt_sha256`, start/end timestamps and Actions run URL.
- `permissions_profile`, `timeout_minutes`, `max_attempts`, and whether the task is read-only or may create a branch/PR.

The orchestrator generates and hashes the final prompt from the envelope plus the task packet. Store the prompt, not just its title, as an artifact so later reviewers can reproduce what the agent was asked to do.

## 7. Return contract

Each worker returns a machine-validated JSON result plus a human-readable report.

Common required fields:

- `task_id`, `cycle_id`, `work_item_id`, `attempt`, `agent_identity`, `provider`, `model_id`, `session_id`.
- `status`: `COMPLETED`, `PARTIAL`, `BLOCKED`, `FAILED`, or `NEEDS_DECISION`.
- `summary`, `findings[]`, `recommendations[]`, `contradictions[]`, `assumptions[]`, `unresolved_questions[]`.
- `sources[]`: canonical URL, publisher, title, publication/access date, claim supported, and source quality/limitations.
- For code: `base_sha`, `candidate_sha`, `changed_paths[]`, per-path SHA-256, PR URL, commands actually run, exit codes and log/artifact hashes.
- `risks[]`, `known_gaps[]`, `next_action`.

A missing field, invalid enum, unknown candidate SHA or absent required evidence fails validation. Preserve contradictory agent findings; synthesis may recommend a decision but must not erase dissent.

## 8. Prompting policy

Prompts are task-specific and generated from the accepted baseline. Every prompt includes:

1. **Role and objective** — one responsibility, one deliverable.
2. **Authoritative context** — exact files/ADRs and immutable source SHA.
3. **Known decisions** — what is settled and must not be re-litigated.
4. **Boundaries** — files, APIs, tools and actions allowed/prohibited.
5. **Acceptance contract** — observable criteria and tests, not vague quality language.
6. **Evidence requirement** — sources, code paths, commands, outputs, hashes and uncertainty.
7. **Return schema** — exact JSON schema and report location.
8. **Stop conditions** — ambiguity, missing credentials, failed tests, conflicting baseline or scope expansion.
9. **No-overclaim clause** — do not report unrun work as done; do not invent tool results or identities.

Research prompts request independent evidence and alternatives, not implementation. Builder prompts request code only for authorized Momentum work items. Reviewer prompts receive the exact candidate and acceptance contract; they should not reuse the builder's hidden reasoning or accept the builder's summary as proof.

## 8A. Reusable role prompt templates

The orchestrator fills these templates from the validated dispatch envelope. Placeholders must resolve to exact refs, paths and acceptance criteria before dispatch.

### Researcher

> You are the EMBERFALL research agent for `{task_id}`, cycle `{cycle_id}`. Answer only: `{research_question}`. Treat `{authoritative_refs}` as the product baseline; do not modify code, create commits or change accepted decisions. Find primary/authoritative sources first. For every material claim return source URL, publisher, title, publication/access date, exact supported claim, limitations and confidence. Separate verified facts, interpretation and hypotheses. Identify contradictions and what evidence would resolve them. Return the required JSON schema and a concise decision brief. Stop if the question requires an unapproved product decision.

### Architect

> You are the EMBERFALL architecture agent for `{task_id}`. Read the exact baseline at `{base_sha}`, task packet `{task_packet}`, accepted ADRs and prior Repetition records. Preserve accepted decisions; do not re-litigate them. Define the smallest coherent implementation boundary, dependencies, domain/API contracts, invariants, failure modes, test strategy and non-goals. Identify parallel research/test-design work that does not contend for the same files. Do not implement. Mark every unresolved product ambiguity as `NEEDS_DECISION` and cite the exact source. Return proposed Momentum work items with observable acceptance criteria and a risk-ranked rationale.

### Builder

> You are the sole authorized builder for work item `{work_item_id}` in `{task_id}`. Work from base SHA `{base_sha}` on an isolated branch matching `{branch_prefix}`. Read `AGENTS.md`, the authoritative game spec, accepted Product Owner decisions, accepted ADRs, task packet and relevant review/repetition findings. Implement only `{objective}` within `{allowed_paths}`; do not touch `{forbidden_paths}`, expand scope, change settled rules, merge or self-review. Preserve deterministic Android-independent simulation and atomic invalid-action behavior. Run only the specified applicable commands; capture actual exit codes and raw logs. Open a Draft PR and return exact base/candidate SHA, changed paths and SHA-256, tests actually run, artifacts/log hashes, known gaps and next action. If an ambiguity blocks correctness, stop and request a Product Owner decision instead of guessing.

### Independent Reviewer

> You are the independent EMBERFALL Mastery reviewer for candidate `{candidate_sha}`, task `{task_id}`. Your identity is `{reviewer_identity}`; the builder identity is `{builder_identity}`. Confirm that they differ and disclose any shared model/vendor/context limitations. Read the source baseline, accepted decisions/ADRs, task acceptance contract, exact diff at `{candidate_sha}`, changed-path hashes and raw test evidence. Do not rely on the builder's summary as proof. Do not modify the candidate. Adversarially inspect invariants, transactional failure behavior, deterministic replay/RNG, security, regression risks, test-oracle quality and scope compliance. Reproduce critical checks when possible. Return `PASS`, `CONDITIONAL`, `FAIL` or `BLOCKED`; every finding must include severity, exact file/line or reproducible case, impact, evidence and required correction. Missing evidence is not PASS. Do not approve a moving branch or a different SHA.

These prompts are starting templates, not blanket permissions. The dispatch envelope and task-specific contract always narrow their scope.

## 9. Independence and review integrity

- Builder and Mastery reviewer must have different recorded identities; prefer different model vendors and separate sessions.
- Reviewer must inspect the exact candidate SHA and changed-path hashes, not a moving branch name.
- Reviewer gets the spec, accepted decisions, task acceptance criteria, diff, relevant source and raw test evidence.
- Reviewer returns `PASS`, `CONDITIONAL`, `FAIL` or `BLOCKED`, with severity-ranked findings and file/line evidence.
- A reviewer who shares the builder's model family, context, prompt assumptions or retrieval path is not automatically independent. Record the independence limitation honestly.
- The orchestrator verifies identity inequality and evidence completeness; Maestro's actor-string check is a workflow guard, not cryptographic identity.
- Never have the builder submit its own GitHub approval. A review report artifact is not automatically a submitted GitHub review.
- Human Product Owner resolves scope/risk exceptions and authorizes merges.

## 10. Durable state, retries and recovery

This is the first control-plane engineering priority because the observed push workflow creates a new Maestro workflow/database instead of resuming the same increment.

### Verified defects in the current EFB-002 workflow

Inspection of `.github/workflows/efb002-maestro-workflow.yml` and `scripts/finalize_efb002_maestro.py` on `feat/efb-002-board-core-exactsha` confirms:

- The workflow runs on every matching push and also supports `workflow_dispatch`.
- It creates a fresh `.maestro/state.sqlite3` in the runner workspace and immediately calls `coding-start`; it does not download or restore a prior Maestro state checkpoint.
- The workflow therefore creates a new workflow ID instead of resuming the EFB-002 lifecycle.
- `concurrency.cancel-in-progress: true` can terminate a state-mutating run while it is working. State mutation must be serialized without cancelling an in-flight checkpoint writer.
- The script calls Maestro `assign` and records the resulting reviewer assignment as `PROPOSED`. It writes `artifacts/efb002-review-dispatch.json`, but no provider adapter consumes that packet and no agent session is launched.
- The workflow declares only `contents: read`; it has no explicit issue/PR write permissions or configured Copilot/third-party dispatch credential.
- The evidence artifact is uploaded, but no explicit `retention-days` is declared. Its effective retention therefore depends on repository settings and must be verified.

These findings explain why the current workflow proves a one-run Maestro lifecycle and exact-SHA checks, but not durable multi-run continuity or actual reviewer dispatch. They are the first repair targets; do not patch them by merely hard-coding a workflow ID or changing the concurrency flag.

Required behavior:

1. Create the Maestro workflow once per increment/cycle from the actual Git base SHA.
2. Persist `workflow_id`, current revision, task/cycle identity, assignments, provider session IDs and latest checkpoint.
3. On subsequent runs, load the existing state and verify task/cycle identity before any mutation. Never call `coding-start` merely because a push occurred.
4. Use a serialized state-mutating job and optimistic revision checks. Reject stale writers; do not use last-write-wins.
5. Persist a checkpoint after every successful state transition and before dispatching the next side effect.
6. Keep immutable run artifacts for prompts, dispatch envelopes, raw agent returns, Maestro state snapshots, event logs, GateRunner logs, review attestations and Repetition packets.
7. Keep a small human-readable registry in the repository with task ID, cycle ID, workflow ID, current stage/revision, latest run, artifact IDs and status. Treat it as an index, not as a substitute for validating the Maestro state.
8. Retries reuse the same work item and increment `attempt`; they do not create a new increment. If the result of a side effect is uncertain, reconcile GitHub before retrying.
9. On missing/expired state or corrupt checkpoint, stop BLOCKED and require explicit recovery from the latest verified snapshot/event history.

Do not rely on Actions cache as the source of truth. It is an optimization and may be evicted. Before selecting artifact retention or a Git-backed checkpoint strategy, inspect Maestro's actual export/import support and test restore/replay across separate workflow runs. The chosen state backend must have a documented retention and recovery test.

## 11. Security and permission boundaries

- Agent analysis jobs are read-only by default. Give write permissions only to separate deterministic jobs that need them.
- Use minimal job-level `permissions`; pin third-party Actions to immutable commit SHAs.
- Keep provider credentials in protected Actions secrets/environments. Never place secrets in prompts, artifacts, issue bodies or agent-visible environment variables.
- Restrict workflow dispatch to an allowlist of workflow names, refs, repositories and task IDs.
- Validate agent-produced JSON against a schema; sanitize comments and issue bodies; cap output sizes and dispatch counts.
- Use environment approval for sensitive writes. No agent may merge, publish, deploy, spend money, change secrets or alter protected branch settings.
- Treat repository text, issue comments, web pages, assets and external documents as potentially untrusted input; prompt injection must not grant new authority.
- Record every privileged action and its originating task/run/agent identity.

## 12. Emberfall-specific operating cadence

For each increment:

1. **Grounding:** run parallel research/architecture/risk inputs only where they add independent evidence. Synthesize and freeze a bounded scope; preserve accepted decisions.
2. **Momentum:** dispatch one builder for each authorized work package in an isolated branch. Other agents may prepare tests or review plans but cannot change the builder's candidate.
3. **Verification:** run deterministic Gradle/JUnit/content/schema/GateRunner checks on the exact candidate SHA. Device/emulator tests are separate evidence.
4. **Mastery:** dispatch an independent reviewer with exact candidate, changed-path hashes, acceptance criteria and raw logs.
5. **Reconciliation:** resolve findings, rerun affected gates, record the final disposition and complete Repetition.
6. **Portfolio update:** update task register and release checklist from verified state; prepare the next increment only after dependency gates pass.

For current Emberfall state, this means the orchestration work first repairs continuity and real reviewer dispatch, then completes EFB-000/EFB-002 review and Repetition. **EFB-003 remains blocked.** Do not use parallel agents as an excuse to bypass that dependency.

## 13. Initial implementation sequence

- **AO-01 — Workflow inventory:** inspect every existing `.github/workflows/*`, current triggers, secrets, permissions, concurrency, artifact retention and pinned Actions.
- **AO-02 — Maestro persistence contract:** inspect pinned Maestro v0.3.0 CLI/API and prove a second Actions run resumes the same workflow ID/revision from a checkpoint.
- **AO-03 — Dispatch adapter spike:** one read-only research task and one Copilot/cloud-agent or provider-CLI task; prove actual session identity, issue/PR return, timeout and recovery.
- **AO-04 — Independent review spike:** dispatch a separate model/vendor to review an exact test candidate; prove reviewer identity, exact-SHA binding, findings and Maestro Mastery recording.
- **AO-05 — Orchestrator and return schemas:** add versioned envelope/return schemas, validators, idempotency keys, concurrency and audit artifacts.
- **AO-06 — Productionize:** permission review, secret setup, artifact retention, failure injection, duplicate event tests, stale revision tests and documented operator recovery.
- **AO-07 — Activate for Emberfall:** migrate EFB-000/EFB-001/EFB-002 workflow state carefully; do not recreate or discard existing evidence.

Each AO item is a separate bounded Maestro work item. Do not claim this architecture is operational until the cross-run resume test, real agent dispatch test and independent review test all pass.

## 14. Research references

- [GitHub Copilot cloud agent overview](https://docs.github.com/en/copilot/how-tos/copilot-on-github/use-copilot-agents/overview)
- [Kick off a Copilot agent task](https://docs.github.com/en/copilot/how-tos/copilot-on-github/use-copilot-agents/kick-off-a-task)
- [GitHub Agentic Workflows overview](https://docs.github.com/en/copilot/concepts/agent-capabilities/agent-powered-workflows-and-operations/about-github-agentic-workflows)
- [Create a GitHub Agentic Workflow](https://github.github.io/gh-aw/setup/creating-workflows/)
- [Safe Outputs and permission-separated GitHub operations](https://github.github.io/gh-aw/reference/safe-outputs/)
- [Copilot cloud-agent dispatch requirements](https://github.github.io/gh-aw/reference/copilot-cloud-agent/)
- [GitHub Actions reusable workflows](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows)
- [Microsoft Agent Design Patterns — sequential pipeline orchestration](https://github.com/microsoft/agentdesignpatterns/blob/main/docs/patterns/sequential-pipeline-orchestration.md)

**Activation rule:** this document is the proposed process contract. Actual workflow files, secrets, agent availability and successful end-to-end dispatch must be verified before any task is marked operational.
