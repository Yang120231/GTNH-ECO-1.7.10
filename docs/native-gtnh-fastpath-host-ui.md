# Native GTNH crafting and host controls (2026-09-15)

This change supersedes earlier ECO planner migration and report UI work.

- GTNH owns crafting calculation, task ordering, confirmation and submission.
  Removed ECO planner interception, component solvers, execution plans, phase
  runtime, planner options, report/graph screens and the report-only texture.
- Kept verified fastpath batching, input rollback, energy reservations, provider
  registration and ownership recovery. Resource identity and batch stack values
  now live in crafting/fastpath. Computation hosts retain their native AE2 CPU
  subclass and fastpath integration.
- Host side rails use the 1.21.1 original 23px segment textures. Host toolbar
  icons use the reference AE2 19.2.17 atlas bundled locally, separate from GTNH's
  terminal icon atlas. Server-side controls preserve snapshot synchronization:
  storage priority; crafting overclock/cooling; computation CPU selection;
  crafting/computation frequency.
- Added shared host help and construction entry pages. Construction uses the
  existing server-side ECOStructureBuilder with length/mirror settings, material
  and conflict checks, player inventory consumption and formation scan. Help is
  a local concise page, not the modern GuideME book. Construction checks report
  counts in chat, not the modern floating 3D material preview.
- No new MEGA partition backend or automatic cell-marking implementation is
  claimed. No in-world visual/click acceptance was run for this change.

Validation: full offline build, Checkstyle, Spotless and 69 retained tests pass.
The reobfuscated jar contains no crafting/planner, ECOExecutionRuntime,
craftinggraph or eco_craftingreport entries. git diff --check passes.
