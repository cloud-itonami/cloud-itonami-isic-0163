(ns postharvest.registry
  "Pure validation functions for post-harvest crop-processing parameters.
  These are called by the Governor to independently verify physical/
  operational constraints -- the advisor's confidence is NOT sufficient
  to override these checks.

  All functions here are pure arithmetic/boolean predicates with no
  host-clock or I/O calls, so this namespace stays trivially portable
  across Clojure/ClojureScript. Callers that need the current time (see
  `drying-equipment-calibration-overdue?`) obtain it themselves via a
  `:clj`/`:cljs` reader-conditional at the call site (see
  `postharvest.governor`).")

(defn moisture-out-of-target?
  "Independently verify that the batch's finished-lot moisture falls
  within tolerance of the crop-lot type's target moisture. Only
  meaningful for crop-lot types with a drying step (target/tolerance
  non-nil) -- callers must guard on that themselves; this function does
  no nil-checking of its own. Product outside its moisture window risks
  mold growth and spoilage in storage (too high) or brittleness/breakage
  during handling (too low)."
  [actual-percent target-percent tolerance-percent]
  (or (< actual-percent (- target-percent tolerance-percent))
      (> actual-percent (+ target-percent tolerance-percent))))

(defn defect-rate-exceeded?
  "Independently verify that the batch's physical/visual defect rate (%)
  does not exceed the crop-lot type's maximum tolerance. Defects
  (bruising, discoloration, insect damage) above tolerance indicate the
  sorting/grading line failed to remove out-of-grade product before it
  reaches the primary market."
  [actual-percent max-percent]
  (> actual-percent max-percent))

(defn foreign-matter-exceeded?
  "Independently verify that the batch's foreign-matter content (%) --
  non-crop debris such as stems, soil, or trash -- does not exceed the
  crop-lot type's maximum tolerance. Excess foreign matter indicates a
  cleaning-line fault."
  [actual-percent max-percent]
  (> actual-percent max-percent))

(defn pest-infestation-detected?
  "Independently verify a batch's pest-infestation-inspection result.
  Any detection is a genuine hazard to the batch and to co-stored/
  co-shipped lots -- this predicate simply coerces the raw fact to a
  boolean so the Governor's check functions stay uniform in shape with
  every other independently-verified physical constraint in this
  namespace."
  [actual-detected?]
  (boolean actual-detected?))

(defn pesticide-residue-exceeded?
  "Independently verify a batch's pesticide-residue laboratory test
  result. Any exceedance is a genuine food-safety hazard to the primary
  market this actor coordinates shipment toward."
  [actual-exceeded?]
  (boolean actual-exceeded?))

(defn drying-equipment-calibration-overdue?
  "Independently verify that the drying/grading line's moisture-meter and
  scale instrumentation was calibrated within the last 90 days.
  `last-calibration-epoch-ms` and `now-epoch-ms` are both epoch
  milliseconds -- callers obtain `now` via a `:clj`/`:cljs`
  reader-conditional, keeping this namespace free of any host-clock
  call. An out-of-calibration moisture meter silently invalidates the
  very test this actor's Governor relies on."
  [last-calibration-epoch-ms now-epoch-ms]
  (> (- now-epoch-ms last-calibration-epoch-ms)
     (* 90 24 60 60 1000)))

(defn weight-variance-excessive?
  "Independently verify that a batch's finished-package weight variance
  (drift from target, in grams) does not exceed the maximum tolerance.
  Excessive variance indicates the packaging scale is out of calibration
  or the processing yield was measured incorrectly."
  [actual-variance-grams max-variance-grams]
  (> actual-variance-grams max-variance-grams))

(defn cold-storage-temp-out-of-range?
  "Independently verify that the batch's cold-storage handling
  temperature falls within tolerance of the crop-lot type's target.
  Only meaningful for crop-lot types with a cold-chain requirement
  (target/tolerance non-nil) -- callers must guard on that themselves;
  this function does no nil-checking of its own. Product outside its
  cold-chain window risks accelerated spoilage (too warm) or chilling
  injury (too cold)."
  [actual-temp-c target-temp-c tolerance-c]
  (or (< actual-temp-c (- target-temp-c tolerance-c))
      (> actual-temp-c (+ target-temp-c tolerance-c))))

(defn sanitation-score-insufficient?
  "Independently verify that the facility's pre-processing sanitation/
  cross-contamination-control score meets the minimum required. Score is
  0-100, assessed by a third-party auditor against post-harvest
  crop-processing sanitation standards."
  [actual-score min-score-required]
  (< actual-score min-score-required))
