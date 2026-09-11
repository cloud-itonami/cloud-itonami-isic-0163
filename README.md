# cloud-itonami-isic-0163: Post-Harvest Crop Activities Coordination Actor

**ISIC Rev. 5 0163** — Post-Harvest Crop Activities

A distributed actor for autonomous, compliant coordination of post-harvest crop-processing facility operations: crop-lot intake → cleaning/trimming → sorting/grading → drying (for crop-lot types with a drying step) or cold-storage handling (for crop-lot types requiring refrigeration) → packing → shipment logistics for the primary market. Sealed LLM advisor; independent Governor enforcement; append-only audit ledger. **Not equipment control.** Dryer/cleaner/grader/packing-line operation remains exclusive to licensed post-harvest facility staff.

## Scope

This actor coordinates **facility-operations workflow** for post-harvest crop activities preparing agricultural products for the primary market — for crops NOT covered by a more specific ISIC class:
- Processing batch logging (crop-lot intake, cleaning/grading parameters, evidence checklist)
- Equipment maintenance scheduling (dryers, cleaners/scalpers, graders, packing lines)
- Quality concern escalation (excess defect rate, pest infestation, pesticide-residue exceedance)
- Processed-crop shipment coordination

**Out of scope:**
- Direct drying/cleaning/grading/packing-line equipment control (facility staff exclusive)
- Grain-mill post-harvest processing that transforms the crop into another product, e.g. flour (ISIC 1061)
- Seed processing for propagation, whose quality bar is seed viability, not product quality (ISIC 0164)
- Regulatory interpretation (proposals cite jurisdiction specifications; the Governor enforces only published requirements)

## Design

### Governor (Independent Compliance Layer)

The Governor is the separation-of-powers enforcement. It never trusts the advisor's confidence for anything quality- or compliance-relevant, and it always wins over the advisor.

- **Hard HOLD** (un-overridable):
  - Operation outside the closed allowlist (`:op-not-allowed`) — includes any proposal that would touch drying/grading/packing-equipment control
  - Proposal asserting an `:effect` other than `:propose` (`:effect-not-propose`)
  - No jurisdiction citation (`:no-spec-basis`) — can't verify requirements without one
  - Evidence checklist incomplete, or the batch record isn't registered (`:evidence-incomplete`)
  - Finished-lot moisture outside the crop-lot type's target range, when the crop-lot type has a drying step (`:moisture-out-of-target`)
  - Defect rate exceeds the crop-lot type's maximum tolerance (`:defect-rate-exceeded`)
  - Foreign-matter content exceeds the crop-lot type's maximum tolerance (`:foreign-matter-exceeded`)
  - Pest infestation detected on the batch's own inspection (`:pest-infestation-detected`)
  - Pesticide-residue laboratory test exceeded tolerance (`:pesticide-residue-exceeded`)
  - Drying/grading-line moisture-meter/scale calibration overdue (`:drying-equipment-calibration-overdue`)
  - Finished-package weight variance excessive (`:weight-variance-excessive`)
  - Cold-storage handling temperature out of the crop-lot type's target range, when the crop-lot type requires refrigeration (`:cold-storage-temp-out-of-range`)
  - Facility sanitation/cross-contamination-control score insufficient (`:sanitation-score-insufficient`)
  - Unresolved quality flag (`:quality-flag-unresolved`)
  - Batch already processed / shipment already finalized (double-commit guards)
  - `:coordinate-shipment` against a batch that was never registered (`:batch-not-registered`)
- **Escalate** (human sign-off always required):
  - `:log-processing-batch` / `:coordinate-shipment` — real actuation events, always require facility-operator sign-off even when the Governor is otherwise clean
  - `:flag-quality-concern` — a quality concern (excess defects, pest infestation, pesticide-residue exceedance) is never auto-resolved by advisor confidence alone
  - Low advisor confidence (below `governor/confidence-floor`, 0.6)
- **Commit** (advisor proposal approved; Governor clean; not a mandatory-escalation op):
  - Routine, low-stakes proposals only — in this actor's current allowlist that is effectively `:schedule-maintenance` when clean

### Operations (Proposals)

Closed allowlist — the advisor may **only** ever propose these four operation types, all `:effect :propose`:

- **`:log-processing-batch`** — Log crop-lot intake → cleaning → grading → drying/cold-storage → packing batch into processing records (always requires human sign-off)
- **`:schedule-maintenance`** — Propose equipment maintenance for dryers/cleaners/graders/packing lines (routine, low risk)
- **`:flag-quality-concern`** — Surface a quality concern (e.g. excess defect rate, pest infestation, pesticide-residue exceedance); always escalates
- **`:coordinate-shipment`** — Finalize shipment of processed crop for the primary market (always requires human sign-off)

Any proposal for an operation outside this allowlist — most importantly anything that would amount to direct drying/cleaning/grading/packing-line control — is refused unconditionally by the Governor (`:op-not-allowed`), regardless of advisor confidence.

## Testing

```bash
# Run full test suite
kbb -M:test

# Check code quality
kbb -M:lint

# Run demo simulation
kbb -M:run
```

## Standalone Use

This repo is **forkable outside the workspace**. If cloning standalone (not in the kotoba-lang monorepo), override `:local/root` paths in `deps.edn`:

```clojure
{:deps {io.github.kotoba-lang/langchain {:git/url "https://github.com/kotoba-lang/langchain" :git/tag "v0.1.0"}
        io.github.kotoba-lang/langgraph {:git/url "https://github.com/kotoba-lang/langgraph" :git/tag "v0.1.0"}}}
```

## License

AGPL-3.0-or-later. Forking/contribution welcome; see `CONTRIBUTING.md`.

## Security

Report security issues to the issue tracker or private disclosure; see `SECURITY.md`.

---

Part of **cloud-itonami**: autonomous actor fleet for regulated industries. See [github.com/cloud-itonami](https://github.com/cloud-itonami).
