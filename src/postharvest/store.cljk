(ns postharvest.store
  "Store abstraction for post-harvest crop-processing batches. Current
  implementation operates on plain data (`{:batches {batch-id batch-map}
  :facts [...]}`); production should migrate this seam to Datomic/
  kotoba-server (the same seam point all cloud-itonami actors use) while
  keeping the same pure-function surface.

  A processing batch is the minimal unit of work: one clean/sort/grade/
  (dry-or-chill)/pack run of a crop lot, tracked from intake through
  cleaning, grading, drying-or-cold-storage handling, packing, and
  shipment. Representative batch keys:
    - :crop-lot-type keyword crop-lot id (see `postharvest.facts/crop-lot-types`)
    - :jurisdiction keyword jurisdiction id (see `postharvest.facts/jurisdictions`)
    - :moisture-percent finished-lot moisture actual (nil when the
      crop-lot type has no drying step)
    - :defect-rate-percent / :foreign-matter-percent finished-lot actuals
    - :pest-infestation-detected? true if pest inspection flagged
      infestation
    - :pesticide-residue-exceeded? true if laboratory residue testing
      exceeded tolerance
    - :sanitation-score 0-100 facility hygiene/cross-contamination-control score
    - :drying-equipment-last-calibration-date epoch-ms of last moisture-
      meter/scale calibration
    - :weight-variance-grams finished-package weight drift from target
    - :cold-storage-temp-c finished-lot cold-chain handling temperature
      actual (nil when the crop-lot type has no cold-chain requirement)
    - :evidence-checklist evidence items present for the batch
    - :quality-concern-raised? / :quality-concern-resolved? open quality flag
    - :processed? true once a `:log-processing-batch` proposal commits
    - :shipment-finalized? true once a `:coordinate-shipment` proposal commits

  The ledger (`:facts`) is a separate append-only vector of audit facts,
  kept alongside `:batches` in the same store value.")

(defn production-batch
  "Retrieve a batch by id, or nil if it does not exist / is not yet
  registered."
  [st batch-id]
  (get-in st [:batches batch-id]))

(defn batch-already-processed?
  "True only if the batch exists and has already been marked processed."
  [st batch-id]
  (true? (:processed? (production-batch st batch-id))))

(defn batch-shipment-finalized?
  "True only if the batch exists and its shipment has already been
  finalized."
  [st batch-id]
  (true? (:shipment-finalized? (production-batch st batch-id))))

(defn log-batch
  "Register/update `batch-data` under `batch-id` and mark it processed
  (one-way flag). Used once a `:log-processing-batch` proposal commits."
  [st batch-id batch-data]
  (assoc-in st [:batches batch-id] (assoc batch-data :processed? true)))

(defn finalize-shipment
  "Mark an existing batch's shipment as finalized (one-way flag). Used once
  a `:coordinate-shipment` proposal commits."
  [st batch-id]
  (assoc-in st [:batches batch-id :shipment-finalized?] true))

(defn audit-trail
  "Return the append-only audit ledger (empty vector if none yet)."
  [st]
  (get st :facts []))

(defn append-fact
  "Append `fact` to the store's audit ledger."
  [st fact]
  (update st :facts (fnil conj []) fact))
