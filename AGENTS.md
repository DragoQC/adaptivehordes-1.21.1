# Adaptive Hordes Development Instructions

These instructions apply to the entire repository. Read this file and
`docs/PROJECT_MEMORY.md` before implementing any change.

## Mandatory Context Check

Before implementation:

1. Run `git status --short` and preserve all unrelated user changes.
2. Read `docs/PROJECT_MEMORY.md` and the files relevant to the requested behavior.
3. Determine the actual project stack and exact installed versions from the
   repository. Inspect, as applicable, `build.gradle`, `settings.gradle`,
   `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties`, mod manifests,
   dependency manifests, and lockfiles.
4. Explicitly identify the Java version, Minecraft version, mod loader and loader
   version, build plugin version, mappings version, and Gradle version before using
   version-sensitive APIs.
5. Inspect existing shared components, services, models, configuration classes,
   registration code, networking code, and helpers before creating a new pattern.

Never treat a version written in documentation or remembered from an earlier task
as authoritative. The current repository manifests and resolved dependencies are
the source of truth.

## Research and API Authority

Use this order when an implementation depends on Minecraft or loader behavior:

1. The repository's resolved dependency source and Javadocs for the installed version.
2. Official documentation matching the installed Minecraft and loader version.
3. Reputable open-source mods, such as Mekanism or Powah, when a relevant
   architectural comparison is useful.

Reference mods are examples, not authorities. Prefer examples using a compatible
Minecraft version and loader, account for licensing, and do not copy code blindly.
If exact-version behavior cannot be verified, state what was verified locally and
what remains uncertain before implementing risky behavior.

## Java and Change Discipline

- Do not use Java local-variable type inference (`var`). Declare the exact type for
  every local variable. If Java makes an explicit type genuinely impossible,
  document the reason in the implementation handoff.
- Before completing Java work, search the entire repository for Java `var` usage;
  the repository should remain compliant, not only the files touched by the task.
- Make the smallest correct change and follow existing project patterns.
- Do not introduce dependencies, abstractions, endpoints, fields, configuration
  options, or refactors that are not required by the request.
- Do not rename existing identifiers unless the request or correctness requires it.
- Keep common/server code free of client-only class references and verify that
  dedicated-server class loading remains safe.
- Avoid reflection, mixins, access transformers, and invasive patches when the
  installed NeoForge or Minecraft API provides a supported hook. If no supported
  hook exists, explain the tradeoff before using an invasive approach.

## Minecraft and NeoForge Practices

- Use lifecycle events, event buses, registries, payload APIs, capabilities, data
  attachments, and other loader facilities according to the installed version,
  not examples written for another release.
- Keep gameplay server-authoritative. Treat clients and network payloads as
  untrusted, validate incoming data, and perform work on the correct game thread.
- Keep physical-side and logical-side behavior separate. A client feature must not
  make the mod fail on a dedicated server.
- Prefer registries, tags, codecs, data files, and stable resource locations over
  hardcoded lists or assumptions about another mod's internals.
- Preserve save, world, network, and configuration compatibility unless a breaking
  change is explicitly approved. Validate and clamp external configuration values.
- The JSON configuration layout is intentional. Do not silently delete, reset, or
  destructively rewrite user configuration. Ask before changing its schema in a
  way that may require migration or invalidate existing files.
- Use translation keys for new player-facing text. Keep messages and HUD feedback
  clear, actionable, accessible, and free of unnecessary spam.
- Keep event and tick work bounded. Avoid repeated global scans, avoidable
  allocations, forced chunk loads, and unbounded entity work on hot paths.
- Respect vanilla behavior, server rules, dimensions, difficulty, and other mods'
  ownership of shared state. Prefer additive and interoperable behavior.
- Test gameplay-affecting changes from the relevant client, integrated-server, and
  dedicated-server perspectives when their behavior differs.

## Player Experience Gate

Evaluate requested gameplay and UI changes for fairness, clarity, accessibility,
performance, configurability, multiplayer impact, and modpack compatibility.

If a request is likely to cause material player harm or a meaningfully worse
experience, stop before implementing that part. Explain the concrete concern, who
it affects, and the practical alternatives, then ask the user to choose. Minor
concerns may be noted while proceeding when they do not materially change the
requested outcome.

## Verification and Handoff

- Run the narrowest relevant checks, then the project's standard compile and
  resource-processing tasks for source changes.
- Add or update tests when the repository has an applicable test pattern. For
  behavior that cannot be automated, describe focused in-game verification steps.
- Do not run data generation, destructive reset commands, migrations, or
  infrastructure/deployment changes unless explicitly requested.
- Report what changed, why the approach matches the installed versions, which
  checks ran, and any checks that could not run.

## Project Memory Updates

`docs/PROJECT_MEMORY.md` contains durable product and design decisions. Update it
only when the user explicitly asks to remember, add, change, or remove project
memory. Ordinary implementation decisions must not silently become permanent
instructions.
