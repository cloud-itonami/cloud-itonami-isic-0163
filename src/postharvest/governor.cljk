(ns postharvest.governor
  "Post-Harvest Operations Governor -- the independent compliance layer
  that earns the PostHarvestAdvisor the right to commit. The LLM has no
  notion of:
    - Whether a batch's finished moisture stayed within its safe target
      range (only meaningful for crop-lot types with a drying step)
    - Whether the batch's physical/visual defect rate exceeds the
      crop-lot type's maximum tolerance
    - Whether foreign-matter (non-crop debris) content exceeds the
      crop-lot type's maximum tolerance
    - Whether pest infestation was detected on the batch's own inspection
    - Whether pesticide-residue laboratory testing exceeded tolerance
    - Whether the drying/grading line's moisture-meter/scale calibration
      is current
    - Whether final package weight variance is acceptable
    - Whether cold-storage handling temperature stayed within its safe
      target range (only meaningful for crop-lot types that require
      refrigerated handling)
    - Whether facility sanitation/cross-contamination-control score is
      passed
    - Whether an open quality concern has been resolved

  This MUST be a separate system able to *reject* a proposal and fall back
  to HOLD.

  Unlike direct drying/grading/packing-equipment control (NEVER done by
  this actor -- dryer, scalper/cleaner, grader, and packing-line
  operation remain exclusive to facility staff), the Governor operates on
  batch metadata: provenance, processing parameters, sanitation records,
  and quality flags. This is facility-operations coordination, not
  process control.

  CRITICAL: Any proposal involving a quality concern (excess defects,
  pest infestation, pesticide-residue exceedance) ALWAYS escalates to
  human facility-operator sign-off. The LLM's confidence is never
  sufficient for food-safety or product-quality decisions.

  Hard violations (always HOLD, no override):
    1. No jurisdiction citation (jurisdiction unknown -> can't verify reqs)
    2. Evidence incomplete (missing required-evidence per jurisdiction)
    3. Moisture out of target range (only when the crop-lot type has a
       drying step)
    4. Defect rate exceeds the crop-lot type's maximum tolerance
    5. Foreign-matter content exceeds the crop-lot type's maximum
       tolerance
    6. Pest infestation detected (own-batch inspection)
    7. Pesticide-residue laboratory test exceeded tolerance
    8. Drying/grading-line moisture-meter/scale calibration overdue
    9. Weight variance excessive (packaging scale drift risk)
   10. Cold-storage temperature out of target range (only when the
       crop-lot type requires refrigerated handling)
   11. Facility sanitation/cross-contamination-control score insufficient
   12. Quality flag unresolved (open concern, escalate required)

  Soft gates (always escalate for human):
    - Low confidence
    - Real actuation (`:log-processing-batch`, `:coordinate-shipment`)
    - `:flag-quality-concern` (never auto-resolved by confidence alone)

  This design mirrors `seedops.governor` (ISIC 0164, seed processing for
  propagation) but specializes on post-harvest FOR-MARKET product-quality
  concerns -- moisture/defect/foreign-matter/cold-chain/pesticide-residue
  -- rather than seed VIABILITY (germination rate, varietal purity)."
  (:require [postharvest.facts :as facts]
            [postharvest.registry :as registry]
            [postharvest.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Logging a batch into processing records (`:log-processing-batch`) and
  coordinating shipment of processed crop (`:coordinate-shipment`) are
  the two real-world actuation events this actor performs. Both require
  facility operator sign-off."
  #{:log-processing-batch :coordinate-shipment})

(def always-escalate-ops
  "Operations that always require human sign-off, even when the Governor's
  hard checks are clean and confidence is high: the two high-stakes
  actuation events (`high-stakes`) plus `:flag-quality-concern` -- a
  quality concern (excess defects, pest infestation, pesticide-residue
  exceedance) is never auto-resolved by advisor confidence alone, it
  always needs a human look."
  (conj high-stakes :flag-quality-concern))

(def allowed-ops
  "Closed allowlist of proposal operations this actor may ever make. Any
  proposal for an operation outside this set -- most importantly direct
  drying/grading/packing-equipment control (dryer, scalper/cleaner,
  grader, packing-line operation) -- is a hard, permanent block: this
  actor coordinates facility operations, it does not operate equipment."
  #{:log-processing-batch :schedule-maintenance :flag-quality-concern :coordinate-shipment})

;; ────────────────────────── Checks ──────────────────────────

(defn- op-not-allowed-violations
  "HARD, permanent block: any proposal outside the closed operation
  allowlist (e.g. direct drying/grading/packing-equipment control) is
  refused unconditionally -- this actor has no authority to make such a
  proposal at all, let alone commit it."
  [{:keys [op]} _proposal]
  (when-not (contains? allowed-ops op)
    [{:rule :op-not-allowed
      :detail (str op " はこのactorの許可された提案種別 (log-processing-batch/"
                  "schedule-maintenance/flag-quality-concern/coordinate-shipment) "
                  "に含まれない -- 乾燥/選別/包装機制御はこのactorに無い")}]))

(defn- effect-not-propose-violations
  "HARD invariant: this actor's proposals are always `:effect :propose` --
  it never claims direct write/actuation authority for itself. A proposal
  asserting any other effect is refused unconditionally."
  [_request proposal]
  (when-let [effect (:effect proposal)]
    (when (not= effect :propose)
      [{:rule :effect-not-propose
        :detail (str "この actor の提案は :propose 以外の :effect を持てない (got " effect ")")}])))

(defn- shipment-batch-not-registered-violations
  "HARD invariant: a facility/batch record must be verified/registered in
  the store before `:coordinate-shipment` can be proposed against it --
  coordinating shipment of a batch this facility never checked in is out
  of scope for this actor."
  [{:keys [op subject]} st]
  (when (= op :coordinate-shipment)
    (when-not (store/production-batch st subject)
      [{:rule :batch-not-registered
        :detail (str subject " は施設に登録されたバッチ記録が無い -- 出荷調整提案は進められない")}])))

(defn- spec-basis-violations
  "A proposal with no jurisdiction citation is a HARD violation -- never
  invent a jurisdiction's post-harvest quality requirements."
  [{:keys [op]} proposal]
  (when (contains?
         #{:log-processing-batch :coordinate-shipment :flag-quality-concern}
         op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :jurisdiction) (nil? (:jurisdiction value))))
        [{:rule :no-spec-basis
          :detail "公式仕様の引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:log-processing-batch`, verify the batch's evidence checklist is
  complete per jurisdiction requirements."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when-not (and b
                     (facts/required-evidence-satisfied?
                      (:jurisdiction b)
                      (:evidence-checklist b)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(lot-intake-record/cleaning-grading-log/defect-inspection/pesticide-residue-test等)が充足していない状態での提案"}]))))

(defn- moisture-out-of-target-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the batch's
  finished moisture falls within tolerance via
  `registry/moisture-out-of-target?`. Only evaluated when the crop-lot
  type actually has a drying step (target/tolerance non-nil) -- fresh
  produce crop-lot types have nothing to check here, never a fabricated
  target."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/crop-lot-type-by-id (:crop-lot-type b)))]
      (when (and b p (:moisture-percent b) (:moisture-target-percent p)
                 (registry/moisture-out-of-target?
                  (:moisture-percent b)
                  (:moisture-target-percent p)
                  (:moisture-tolerance-percent p)))
        [{:rule :moisture-out-of-target
          :detail (str subject " の水分(" (:moisture-percent b)
                      "%)が目標範囲外 -- バッチ登録提案は進められない")}]))))

(defn- defect-rate-exceeded-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the batch's
  defect rate meets the crop-lot type's maximum tolerance via
  `registry/defect-rate-exceeded?`. Evaluated UNCONDITIONALLY -- this is
  the single most direct for-market quality hazard specific to post-
  harvest sorting/grading."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/crop-lot-type-by-id (:crop-lot-type b)))]
      (when (and b p (:defect-rate-percent b)
                 (registry/defect-rate-exceeded?
                  (:defect-rate-percent b)
                  (:defect-rate-max-percent p)))
        [{:rule :defect-rate-exceeded
          :detail (str subject " の欠陥率(" (:defect-rate-percent b)
                      "%)が製品規格(" (:defect-rate-max-percent p)
                      "%)を上回る -- バッチ登録提案は進められない")}]))))

(defn- foreign-matter-exceeded-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the batch's
  foreign-matter content falls within the crop-lot type's expected range
  via `registry/foreign-matter-exceeded?`."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/crop-lot-type-by-id (:crop-lot-type b)))]
      (when (and b p (:foreign-matter-percent b)
                 (registry/foreign-matter-exceeded?
                  (:foreign-matter-percent b)
                  (:foreign-matter-max-percent p)))
        [{:rule :foreign-matter-exceeded
          :detail (str subject " の異物混入率(" (:foreign-matter-percent b)
                      "%)が製品規格範囲外 -- バッチ登録提案は進められない")}]))))

(defn- pest-infestation-detected-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify the batch's own
  pest-infestation-inspection result via
  `registry/pest-infestation-detected?`. A detection on THIS batch's own
  inspection is a hard, food-safety hazard block -- distinct from
  `quality-flag-unresolved-violations` below, which covers a separately-
  raised, not-yet-resolved concern."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (registry/pest-infestation-detected? (:pest-infestation-detected? b)))
        [{:rule :pest-infestation-detected
          :detail (str subject " で害虫混入が検出された -- バッチ登録提案は進められない")}]))))

(defn- pesticide-residue-exceeded-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify the batch's own
  pesticide-residue laboratory test result via
  `registry/pesticide-residue-exceeded?`."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (registry/pesticide-residue-exceeded? (:pesticide-residue-exceeded? b)))
        [{:rule :pesticide-residue-exceeded
          :detail (str subject " で残留農薬基準超過が検出された -- バッチ登録提案は進められない")}]))))

(defn- now-epoch-ms
  "Current time in epoch milliseconds, portable across Clojure/
  ClojureScript. Isolated to this single call site so the rest of the
  namespace (and all of `postharvest.registry`) stays free of host-clock
  calls."
  []
  #?(:clj (System/currentTimeMillis)
     :cljs (js/Date.now)))

(defn- drying-equipment-calibration-overdue-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the drying/
  grading line's moisture-meter/scale calibration is current
  (recalibration required every 90 days)."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:drying-equipment-last-calibration-date b)
                 (registry/drying-equipment-calibration-overdue? (:drying-equipment-last-calibration-date b) (now-epoch-ms)))
        [{:rule :drying-equipment-calibration-overdue
          :detail (str subject " の乾燥/計量機器の校正が期限切れ -- バッチ登録提案は進められない")}]))))

(defn- weight-variance-excessive-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify the weight variance."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:weight-variance-grams b)
                 (registry/weight-variance-excessive? (:weight-variance-grams b) 50))
        [{:rule :weight-variance-excessive
          :detail (str subject " の重量分散(" (:weight-variance-grams b)
                      "g)が許容範囲(50g)を超過 -- バッチ登録提案は進められない")}]))))

(defn- cold-storage-temp-out-of-range-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the batch's
  cold-storage handling temperature falls within tolerance via
  `registry/cold-storage-temp-out-of-range?`. Only evaluated when the
  crop-lot type actually requires refrigerated handling (target/
  tolerance non-nil) -- dried-goods crop-lot types have nothing to check
  here, never a fabricated target."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)
          p (when b (facts/crop-lot-type-by-id (:crop-lot-type b)))]
      (when (and b p (:cold-storage-temp-c b) (:cold-storage-temp-target-c p)
                 (registry/cold-storage-temp-out-of-range?
                  (:cold-storage-temp-c b)
                  (:cold-storage-temp-target-c p)
                  (:cold-storage-temp-tolerance-c p)))
        [{:rule :cold-storage-temp-out-of-range
          :detail (str subject " のコールドチェーン温度(" (:cold-storage-temp-c b)
                      "℃)が目標範囲外 -- バッチ登録提案は進められない")}]))))

(defn- sanitation-score-insufficient-violations
  "For `:log-processing-batch`, INDEPENDENTLY verify that the facility's
  sanitation/cross-contamination-control score meets minimum
  requirements."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and b (:sanitation-score b)
                 (registry/sanitation-score-insufficient? (:sanitation-score b) 75))
        [{:rule :sanitation-score-insufficient
          :detail (str subject " の施設衛生/交差汚染防止スコア(" (:sanitation-score b)
                      ")が最低要件(75)を下回る -- バッチ登録提案は進められない")}]))))

(defn- quality-flag-unresolved-violations
  "An unresolved quality flag is a HARD, un-overridable hold. Quality
  concerns (suspected excess defects, pest infestation, pesticide-
  residue exceedance) raised during processing or testing MUST be
  resolved before the batch can be logged. Evaluated UNCONDITIONALLY at
  `:log-processing-batch`."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (let [b (store/production-batch st subject)]
      (when (and (true? (:quality-concern-raised? b))
                 (not (true? (:quality-concern-resolved? b))))
        [{:rule :quality-flag-unresolved
          :detail (str subject " は未解決の品質フラグがある -- バッチ登録提案は進められない")}]))))

(defn- already-processed-violations
  "For `:log-processing-batch`, refuse to process the SAME batch twice, off
  a dedicated `:processed?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :log-processing-batch)
    (when (store/batch-already-processed? st subject)
      [{:rule :already-processed
        :detail (str subject " は既に登録済み")}])))

(defn- already-shipment-finalized-violations
  "For `:coordinate-shipment`, refuse to finalize the SAME batch's shipment
  twice, off a dedicated `:shipment-finalized?` fact."
  [{:keys [op subject]} st]
  (when (= op :coordinate-shipment)
    (when (store/batch-shipment-finalized? st subject)
      [{:rule :already-shipment-finalized
        :detail (str subject " は既に出荷確定済み")}])))

(defn check
  "Censors a PostHarvestAdvisor proposal against the Governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}.

  Stakes (high-stakes actuation vs. always-escalate) are read off the
  REQUEST's `:op` -- not off the proposal -- since the operation being
  proposed (not the advisor's self-reported stake) is what determines
  whether a human must sign off."
  [request _context proposal st]
  (let [hard (into []
                   (concat (op-not-allowed-violations request proposal)
                           (effect-not-propose-violations request proposal)
                           (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (moisture-out-of-target-violations request st)
                           (defect-rate-exceeded-violations request st)
                           (foreign-matter-exceeded-violations request st)
                           (pest-infestation-detected-violations request st)
                           (pesticide-residue-exceeded-violations request st)
                           (drying-equipment-calibration-overdue-violations request st)
                           (weight-variance-excessive-violations request st)
                           (cold-storage-temp-out-of-range-violations request st)
                           (sanitation-score-insufficient-violations request st)
                           (quality-flag-unresolved-violations request st)
                           (already-processed-violations request st)
                           (already-shipment-finalized-violations request st)
                           (shipment-batch-not-registered-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        actuation? (boolean (high-stakes (:op request)))
        escalate-op? (boolean (always-escalate-ops (:op request)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not escalate-op?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? escalate-op?))
     :high-stakes? actuation?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
