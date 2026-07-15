# Contributing to cloud-itonami-isic-0163

Thank you for your interest in contributing to the Post-Harvest Crop Activities
Operations actor.

## Scope

This repository is a specialization of the cloud-itonami architecture for ISIC
0163 (post-harvest crop activities). Contributions should:

1. Extend or correct the **Governor rules** (post-harvest quality/food-safety constraints)
2. Add **crop-lot types** or **jurisdictional requirements** to the facts registry
3. Improve **test coverage** for post-harvest-processing-specific scenarios
4. Clarify **documentation** and ADRs

## Prohibited Changes

Do **not**:

- Add direct drying/cleaning/grading/packing-line equipment control (dryer/scalper/grader/packing-line operation remains exclusive to facility staff)
- Modify the Governor to allow LLM confidence to override quality/food-safety hard holds
- Add JVM-only code (all source must be `.cljc` / portable)
- Change the AGPL-3.0-or-later license

## Process

1. Open an issue describing your proposed change
2. Link to the relevant ADR in the `kotoba-lang/industry` registry repository (or the `com-junkawasaki/root` superproject's `90-docs/adr/`)
3. Submit a pull request against `main`
4. Ensure all tests pass: `clojure -M:test`
5. Run linter: `clojure -M:lint`

## Code Style

- Use `.cljc` for all source (no `.clj` or `.cljs` only)
- Follow Clojure conventions (kebab-case, docstrings on public fns)
- Governor rules must be pure, side-effect-free predicates
- Test all new facts and registry entries

## Questions?

File an issue or reach out to the maintainers.
