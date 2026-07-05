# Adaptive Hordes Project Memory

Read this file before implementation together with the root `AGENTS.md`.

This file records durable product and design decisions, not transient task details
or dependency versions. Repository manifests and resolved dependencies always
determine the current Java, Minecraft, loader, mappings, and build-tool versions.

## Product Purpose

Adaptive Hordes is a progression-based horde mod. It evaluates player equipment
and progression, then uses that information to select and scale hostile waves for
the affected player.

The goal is meaningful pressure that responds to progression without making
success feel arbitrary or punishing players through hidden, unavoidable rules.

## Configuration Direction

- The editable JSON files under the mod's configuration directory are intentional.
- Server owners should retain direct control over pacing, scaling, mobs, drops,
  ignored players, and weapon overrides.
- Existing configuration files should remain recoverable and compatible whenever
  practical. Invalid data should be handled clearly rather than silently discarded.
- Generated configuration help is part of the user experience and should stay
  aligned with configurable behavior.

## Player-First Principles

- Difficulty should be challenging but explainable, fair, and proportional to the
  player's progression.
- Important wave state and consequences should be communicated clearly without
  flooding chat or obstructing normal gameplay.
- Features should work sensibly in multiplayer and avoid penalizing one player for
  another player's progression without an explicit design reason.
- Server and client performance, dedicated-server safety, accessibility,
  configurability, and modpack compatibility are product concerns, not afterthoughts.
- When a proposed feature would materially worsen player experience, surface the
  concern and alternatives before implementation.

## Memory Policy

- Change this file only when the user explicitly requests a project-memory update.
- Record durable intentions and decisions, including enough rationale to apply them
  consistently later.
- Do not store current dependency versions here; they can become stale.
- A current explicit user request overrides older memory. Update this file only if
  the user also asks for that newer decision to be remembered.
