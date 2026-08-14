(ns postharvest.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: it previously had no
  demo page and no generator. This namespace drives the REAL actor stack
  --- `postharvest.operation/run-operation` -> `postharvest.governor/check`
  -> `postharvest.store` --- and renders whatever that stack actually
  produced. Nothing on the page is hand-typed domain data.

  ## Where the scenario data comes from (read this before editing)

  This repo ships NO seed data: `postharvest.sim/-main` is a stub that
  prints `PostHarvest simulation: not yet implemented.` (verified by
  running `clojure -M:dev:run` before this file was written), and
  `postharvest.store` has no `seed-db`. So the console seeds its own
  scenario, and every value in it is traceable to something already in
  the repo:

    - crop-lot types, their names, and every quality window
      (moisture target/tolerance, defect-rate max, foreign-matter max,
      cold-storage target/tolerance) come from `postharvest.facts/crop-lot-types`
    - jurisdictions, their names and their `:required-evidence` lists come
      from `postharvest.facts/jurisdictions`
    - the CLEAN batch shape (which keys a batch carries, and the clean
      actuals 2.0/0.2 for dried goods and 1.0/0.1 for cold-chain lots,
      weight-variance 20, sanitation 85, calibration ten-days-ago) is
      field-for-field the `clean-batch` / `clean-cold-chain-batch`
      fixtures in `test/postharvest/governor_test.cljc`
    - the failing actuals are DERIVED from the crop-lot type's own window
      (e.g. `out-of-spec-defect` = the type's max + 1.0), never typed, so
      they cannot drift away from `facts` --- the two exceptions are the
      sanitation floor (75) and the weight-variance ceiling (50), which
      are literals at their `postharvest.governor` call sites and are
      re-stated here with that provenance
    - the batch-id convention (`batch-NNN`, plus the unregistered
      `batch-999`) is the one the repo's own tests use
    - the citation attached to each proposal is the jurisdiction's own
      `:name` from `facts`, not an invented spec number

  ## Determinism

  Byte-identical across reruns: no timestamps in the page, no randomness,
  every map iterated through an explicit sort. The scenario does read the
  host clock --- it must, because
  `governor/drying-equipment-calibration-overdue-violations` compares a
  batch's calibration date against `now` --- but the clock value never
  reaches the page: calibration dates are set to now-10d and now-100d
  (the fixtures' `ten-days-ago` / `hundred-days-ago`), which land on the
  same side of the 90-day boundary on every run, and the page renders
  only the derived in-window/overdue verdict.

  ## Both directions, and a build-time invariant

  The scenario reaches every disposition this actor can produce: an
  auto-commit, four human escalations that are approved and committed,
  and 17 HARD (un-overridable) governor holds --- one for each hard rule
  the Governor implements. `-main` counts the HARD hold rows it actually
  emitted into the document and throws if that count is zero, so the
  HARD-hold requirement is a build-time invariant rather than a
  convention.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [jp-go-dds.skin :as skin]
            [postharvest.facts :as facts]
            [postharvest.governor :as governor]
            [postharvest.operation :as operation]
            [postharvest.registry :as registry]
            [postharvest.store :as store]))

;; ─────────────────────────── scenario seed ───────────────────────────

(def ^:private operator
  "The context `operation/run-operation` threads through to the Governor.
  `:hold-fact-fn` is the actor's own `governor/hold-fact`, so every hold
  row on the page is a fact the ACTOR minted, not one this console wrote."
  {:actor-id "postharvest-op-1" :hold-fact-fn governor/hold-fact})

(def ^:private approvers
  "Distinct human sign-off identity per op. They differ on purpose: it is
  what lets the console MEASURE which approval survives onto a store
  record and which one only ever exists in the ledger (see
  `approver-retention-rows`)."
  {:log-processing-batch "op-batch-1"
   :coordinate-shipment  "op-ship-1"
   :schedule-maintenance "op-maint-1"
   :flag-quality-concern "op-qa-1"})

;; Thresholds that live as literals at their `postharvest.governor` call
;; sites rather than in `postharvest.facts`; re-stated here only so the
;; spec columns can be labelled. The pass/fail VERDICTS on the page never
;; come from these --- they come from the ledger the Governor produced.
(def ^:private sanitation-min-score 75)
(def ^:private weight-variance-max-grams 50)

(def ^:private day-ms (* 24 60 60 1000))
(def ^:private now-ms (System/currentTimeMillis))
(def ^:private calibration-current-ms (- now-ms (* 10 day-ms)))
(def ^:private calibration-overdue-ms (- now-ms (* 100 day-ms)))

(defn- out-of-spec-moisture [p]
  (- (:moisture-target-percent p) (* 2 (:moisture-tolerance-percent p))))

(defn- out-of-spec-defect [p] (+ (:defect-rate-max-percent p) 1.0))

(defn- out-of-spec-foreign [p] (+ (:foreign-matter-max-percent p) 1.0))

(defn- out-of-spec-cold [p]
  (+ (:cold-storage-temp-target-c p) (* 2 (:cold-storage-temp-tolerance-c p))))

(defn- clean-batch
  "A batch that passes every Governor hard check, in the exact shape of
  the `clean-batch` / `clean-cold-chain-batch` fixtures in
  `test/postharvest/governor_test.cljc`. Spec-relative actuals are read
  off the crop-lot type itself, so a dried good gets a moisture actual
  and no cold-chain actual, and a fresh lot the reverse --- never a
  fabricated target for a type that has none."
  [crop-lot-id jurisdiction-id]
  (let [p (facts/crop-lot-type-by-id crop-lot-id)
        cold? (some? (:cold-storage-temp-target-c p))]
    {:crop-lot-type crop-lot-id
     :jurisdiction jurisdiction-id
     :moisture-percent (:moisture-target-percent p)
     :cold-storage-temp-c (:cold-storage-temp-target-c p)
     :defect-rate-percent (if cold? 1.0 2.0)
     :foreign-matter-percent (if cold? 0.1 0.2)
     :pest-infestation-detected? false
     :pesticide-residue-exceeded? false
     :drying-equipment-last-calibration-date calibration-current-ms
     :weight-variance-grams 20
     :sanitation-score 85
     :evidence-checklist (vec (:required-evidence
                               (facts/jurisdiction-by-id jurisdiction-id)))}))

(def ^:private demo-batches
  "The facility's batch registry for this run. `:override` receives the
  batch's own crop-lot-type map from `facts` and returns the single
  out-of-spec actual that batch exists to demonstrate."
  [{:id "batch-001" :crop-lot :tea/black-leaf :jurisdiction :jp/mhlw
    :intent "clean dried-goods lot — approved and committed end to end"}
   {:id "batch-002" :crop-lot :produce/leafy-greens :jurisdiction :jp/mhlw
    :intent "clean cold-chain lot — routine maintenance auto-commits"}
   {:id "batch-003" :crop-lot :tea/black-leaf :jurisdiction :jp/mhlw
    :intent "finished moisture below the drying window"
    :override (fn [p] {:moisture-percent (out-of-spec-moisture p)})}
   {:id "batch-004" :crop-lot :produce/citrus :jurisdiction :us/fda
    :intent "defect rate above the crop-lot maximum"
    :override (fn [p] {:defect-rate-percent (out-of-spec-defect p)})}
   {:id "batch-005" :crop-lot :tea/black-leaf :jurisdiction :eu/gfl
    :intent "foreign matter above the crop-lot maximum"
    :override (fn [p] {:foreign-matter-percent (out-of-spec-foreign p)})}
   {:id "batch-006" :crop-lot :tea/black-leaf :jurisdiction :jp/mhlw
    :intent "pest infestation on the batch's own inspection"
    :override (fn [_] {:pest-infestation-detected? true})}
   {:id "batch-007" :crop-lot :tobacco/flue-cured :jurisdiction :us/fda
    :intent "pesticide-residue laboratory exceedance"
    :override (fn [_] {:pesticide-residue-exceeded? true})}
   {:id "batch-008" :crop-lot :tea/black-leaf :jurisdiction :jp/mhlw
    :intent "moisture-meter / scale calibration past 90 days"
    :override (fn [_] {:drying-equipment-last-calibration-date calibration-overdue-ms})}
   {:id "batch-009" :crop-lot :tobacco/flue-cured :jurisdiction :eu/gfl
    :intent "finished-package weight variance beyond tolerance"
    :override (fn [_] {:weight-variance-grams 75})}
   {:id "batch-010" :crop-lot :produce/citrus :jurisdiction :jp/mhlw
    :intent "cold-chain handling temperature above the window"
    :override (fn [p] {:cold-storage-temp-c (out-of-spec-cold p)})}
   {:id "batch-011" :crop-lot :produce/leafy-greens :jurisdiction :us/fda
    :intent "facility sanitation score below the floor"
    :override (fn [_] {:sanitation-score 60})}
   {:id "batch-012" :crop-lot :tea/black-leaf :jurisdiction :jp/mhlw
    :intent "quality concern raised and still unresolved"
    :override (fn [_] {:quality-concern-raised? true :quality-concern-resolved? false})}
   {:id "batch-013" :crop-lot :produce/citrus :jurisdiction :eu/gfl
    :intent "evidence checklist missing the residue test"
    :override (fn [_] {:evidence-checklist
                       (vec (remove #{:pesticide-residue-test}
                                    (:required-evidence
                                     (facts/jurisdiction-by-id :eu/gfl))))})}
   {:id "batch-014" :crop-lot :tobacco/flue-cured :jurisdiction :jp/mhlw
    :intent "clean lot, but the proposal carries no jurisdiction citation"}])

(defn- seed-store []
  {:batches (into {}
                  (map (fn [{:keys [id crop-lot jurisdiction override]}]
                         (let [p (facts/crop-lot-type-by-id crop-lot)]
                           [id (merge (clean-batch crop-lot jurisdiction)
                                      (when override (override p)))]))
                       demo-batches))
   :facts []})

(def ^:private scenario
  "Every step is a real proposal put through the real stack. The comment
  on each line is the disposition the Governor is expected to reach; the
  page renders whatever it ACTUALLY reached."
  [{:op :log-processing-batch :subject "batch-001"}                    ; escalate -> approved -> committed
   {:op :coordinate-shipment  :subject "batch-001"}                    ; escalate -> approved -> shipped
   {:op :log-processing-batch :subject "batch-001"}                    ; HARD already-processed
   {:op :coordinate-shipment  :subject "batch-001"}                    ; HARD already-shipment-finalized
   {:op :schedule-maintenance :subject "batch-002"}                    ; auto-commit (only self-clearing op)
   {:op :schedule-maintenance :subject "batch-002" :confidence 0.4}    ; escalate (below confidence floor)
   {:op :control-drying-line  :subject "batch-002"}                    ; HARD op-not-allowed
   {:op :schedule-maintenance :subject "batch-002" :effect :commit}    ; HARD effect-not-propose
   {:op :log-processing-batch :subject "batch-003"}                    ; HARD moisture-out-of-target
   {:op :log-processing-batch :subject "batch-004"}                    ; HARD defect-rate-exceeded
   {:op :log-processing-batch :subject "batch-005"}                    ; HARD foreign-matter-exceeded
   {:op :log-processing-batch :subject "batch-006"}                    ; HARD pest-infestation-detected
   {:op :log-processing-batch :subject "batch-007"}                    ; HARD pesticide-residue-exceeded
   {:op :log-processing-batch :subject "batch-008"}                    ; HARD calibration-overdue
   {:op :log-processing-batch :subject "batch-009"}                    ; HARD weight-variance-excessive
   {:op :log-processing-batch :subject "batch-010"}                    ; HARD cold-storage-temp-out-of-range
   {:op :log-processing-batch :subject "batch-011"}                    ; HARD sanitation-score-insufficient
   {:op :log-processing-batch :subject "batch-012"}                    ; HARD quality-flag-unresolved
   {:op :flag-quality-concern :subject "batch-012"}                    ; escalate (always, never auto)
   {:op :log-processing-batch :subject "batch-013"}                    ; HARD evidence-incomplete
   {:op :log-processing-batch :subject "batch-014" :no-cites? true}    ; HARD no-spec-basis
   {:op :coordinate-shipment  :subject "batch-999"}])                  ; HARD batch-not-registered

;; ─────────────────────────── driving the stack ───────────────────────

(defn- proposal-for
  "Build the advisor proposal. The citation is the jurisdiction's own
  `:name` out of `postharvest.facts` --- the console never invents a
  specification identifier."
  [st {:keys [subject confidence effect no-cites?]}]
  (let [jid (or (:jurisdiction (store/production-batch st subject)) :jp/mhlw)]
    {:cites (if no-cites? [] [{:spec (:name (facts/jurisdiction-by-id jid)) :jurisdiction jid}])
     :value {:jurisdiction (when-not no-cites? jid)}
     :effect (or effect :propose)
     :confidence (or confidence 0.9)}))

(defn- step!
  "Run one proposal through `operation/run-operation` and fold the result
  into the store. Facts the actor minted are appended verbatim; anything
  this console adds carries `:source :console-harness` so the page can
  tell the two apart."
  [st {:keys [op subject] :as step}]
  (let [request  {:op op :subject subject}
        proposal (proposal-for st step)
        result   (operation/run-operation request operator proposal st governor/check)
        verdict  (:verdict result)
        hard?    (boolean (seq (:violations verdict)))
        st'      (reduce store/append-fact st (:facts result))
        actor-n  (count (:facts result))]
    (cond
      ;; Governor cleared it outright: no human is consulted at all.
      (:ok? result)
      (store/append-fact st' {:t :auto-commit :op op :subject subject
                              :source :console-harness :disposition :auto-commit
                              :actor-facts actor-n :store-mutated? false :store-fn nil})

      ;; HARD hold. Un-overridable: the console does NOT offer it to a
      ;; human, and no store mutation follows.
      hard? st'

      ;; Escalation. A human signs off, then the console applies the
      ;; store effect through whichever `postharvest.store` function
      ;; exists for this op --- for two of the four ops, none does.
      :else
      (let [approver (get approvers op)
            before   (store/production-batch st' subject)
            [st'' store-fn]
            (case op
              :log-processing-batch
              [(store/log-batch st' subject (assoc before :approved-by approver))
               "store/log-batch"]
              :coordinate-shipment
              [(store/finalize-shipment st' subject) "store/finalize-shipment"]
              [st' nil])
            after (store/production-batch st'' subject)]
        (store/append-fact st''
                           {:t :operator-approval :op op :subject subject
                            :source :console-harness :disposition :approved
                            :approved-by approver :actor-facts actor-n
                            :store-mutated? (not= before after) :store-fn store-fn})))))

(defn run-demo!
  "Runs the whole scenario against a freshly seeded store and returns the
  resulting store value. Every field the renderer reads below is real
  Governor / store output."
  []
  (reduce step! (seed-store) scenario))

;; ───────────────────────────── rendering ─────────────────────────────

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- muted [s] (str "<span class=\"muted\">" s "</span>"))
(defn- ok [s] (str "<span class=\"ok\">" s "</span>"))
(defn- warn [s] (str "<span class=\"warn\">" s "</span>"))
(defn- crit [s] (str "<span class=\"critical\">" s "</span>"))
(defn- code [v] (str "<code>" (esc v) "</code>"))

(defn- tr [attrs cells]
  (str "        <tr" attrs ">"
       (str/join (map #(str "<td>" % "</td>") cells))
       "</tr>"))

(defn- hard-hold? [f]
  (and (= :governor-hold (:t f)) (boolean (seq (:basis f)))))

(defn- soft-hold? [f]
  (and (= :governor-hold (:t f)) (empty? (:basis f))))

(defn- rule-list [f]
  (str/join ", " (map name (:basis f))))

(defn- last-fact-for [ledger subject]
  (last (filter #(= subject (:subject %)) ledger)))

(defn- status-cell [ledger subject]
  (let [f (last-fact-for ledger subject)]
    (cond
      (nil? f) (muted "この run では未使用")
      (hard-hold? f) (crit (str "HARD 保留 · " (esc (rule-list f))))
      (soft-hold? f) (warn "人間承認待ち")
      (= :operator-approval (:t f)) (ok (str "承認・コミット済 · " (esc (:approved-by f))))
      (= :auto-commit (:t f)) (ok "自動コミット")
      :else (muted (esc (name (:t f)))))))

(defn- lifecycle-cell [b]
  (cond
    (:shipment-finalized? b) (ok "登録済 → 出荷確定")
    (:processed? b) (warn "登録済（未出荷）")
    :else (muted "未登録")))

;; ── section 1: batch registry ──

(defn- registry-rows [st ledger]
  (for [{:keys [id crop-lot jurisdiction intent]} (sort-by :id demo-batches)
        :let [b (store/production-batch st id)]]
    (tr "" [(code id)
            (esc (:name (facts/crop-lot-type-by-id crop-lot)))
            (esc (:name (facts/jurisdiction-by-id jurisdiction)))
            (lifecycle-cell b)
            (status-cell ledger id)
            (muted (esc intent))])))

;; ── section 2: measured actuals vs the crop-lot window ──

(defn- verdict-cell [actual in-range?]
  (cond
    (nil? actual) (muted "—")
    in-range? (ok (esc actual))
    :else (crit (esc actual))))

(defn- spec-window [target tol]
  (if (nil? target) (muted "規格なし")
      (esc (str (- target tol) " – " (+ target tol)))))

(defn- quality-rows [st]
  (for [{:keys [id crop-lot]} (sort-by :id demo-batches)
        :let [b (store/production-batch st id)
              p (facts/crop-lot-type-by-id crop-lot)
              m (:moisture-percent b)
              c (:cold-storage-temp-c b)]]
    (tr "" [(code id)
            (esc (:name p))
            (verdict-cell m (facts/moisture-in-range? (or m 0) p))
            (spec-window (:moisture-target-percent p) (:moisture-tolerance-percent p))
            (verdict-cell (:defect-rate-percent b)
                          (facts/defect-rate-in-range? (:defect-rate-percent b) p))
            (esc (str "≤ " (:defect-rate-max-percent p)))
            (verdict-cell (:foreign-matter-percent b)
                          (facts/foreign-matter-in-range? (:foreign-matter-percent b) p))
            (esc (str "≤ " (:foreign-matter-max-percent p)))
            (verdict-cell c (facts/cold-storage-temp-in-range? (or c 0) p))
            (spec-window (:cold-storage-temp-target-c p) (:cold-storage-temp-tolerance-c p))
            (verdict-cell (:sanitation-score b)
                          (not (registry/sanitation-score-insufficient?
                                (:sanitation-score b) sanitation-min-score)))
            (verdict-cell (:weight-variance-grams b)
                          (not (registry/weight-variance-excessive?
                                (:weight-variance-grams b) weight-variance-max-grams)))
            (if (registry/drying-equipment-calibration-overdue?
                 (:drying-equipment-last-calibration-date b) now-ms)
              (crit "期限切れ")
              (ok "期限内"))
            (if (and (:quality-concern-raised? b) (not (:quality-concern-resolved? b)))
              (crit "未解決")
              (ok "なし"))])))

;; ── section 3/4: the reference tables the Governor checks against ──

(defn- crop-lot-rows []
  (for [[id p] (sort-by key facts/crop-lot-types)]
    (tr "" [(code id)
            (esc (:name p))
            (if (:moisture-target-percent p)
              (esc (str (:moisture-target-percent p) " ± " (:moisture-tolerance-percent p)))
              (muted "乾燥工程なし"))
            (esc (str "≤ " (:defect-rate-max-percent p)))
            (esc (str "≤ " (:foreign-matter-max-percent p)))
            (if (:cold-storage-temp-target-c p)
              (esc (str (:cold-storage-temp-target-c p) " ± " (:cold-storage-temp-tolerance-c p)))
              (muted "冷蔵要件なし"))])))

(defn- jurisdiction-rows []
  (for [[id j] (sort-by key facts/jurisdictions)]
    (tr "" [(code id)
            (esc (:name j))
            (str/join " " (map #(code (name %)) (:required-evidence j)))
            (esc (count (:required-evidence j)))])))

;; ── section 5: the gate, derived from the Governor's own public vars ──

(defn- gate-rows []
  (for [op (sort-by name governor/allowed-ops)]
    (tr "" [(code op)
            (cond
              (contains? governor/high-stakes op)
              (warn "実アクチュエーション · 常に人間承認（信頼度に関わらず）")
              (contains? governor/always-escalate-ops op)
              (warn "常にエスカレーション · 信頼度だけでは自動解決しない")
              :else
              (ok (str "hard 違反なし かつ 信頼度 ≥ " governor/confidence-floor " で自動コミット")))
            (if (contains? governor/high-stakes op) (esc "高") (esc "低"))])))

;; ── section 6: which hard rules this run actually exercised ──

(defn- hard-rule-rows [ledger]
  (let [hard (filter hard-hold? ledger)
        by-rule (group-by #(first (:basis %)) hard)]
    (for [[rule fs] (sort-by (comp name first) by-rule)]
      (tr "" [(code rule)
              (esc (count fs))
              (str/join " " (map #(code (:subject %)) (sort-by :subject fs)))
              (esc (-> fs first :violations first :detail))]))))

;; ── section 7: the ledger itself ──

(defn- ledger-rows [ledger]
  (map-indexed
   (fn [i f]
     (tr (if (hard-hold? f) " data-hard-hold=\"1\"" "")
         [(esc (inc i))
          (cond (hard-hold? f) (crit "governor-hold（HARD）")
                (soft-hold? f) (warn "governor-hold（エスカレーション）")
                :else (ok (esc (name (:t f)))))
          (if (= :console-harness (:source f))
            (muted "console harness")
            (esc "actor · governor/hold-fact"))
          (code (name (:op f)))
          (code (:subject f))
          (esc (name (or (:disposition f) :n-a)))
          (if (seq (:basis f)) (esc (rule-list f)) (muted "—"))]))
   ledger))

;; ── section 8: measured approver retention ──

(defn- approver-retained?
  "Does the approver identity recorded in the ledger appear ANYWHERE in
  the store record for that subject? Scans values rather than a fixed key
  so that the page self-corrects the day the store starts retaining an
  approver under any name."
  [st fact]
  (let [rec (store/production-batch st (:subject fact))
        approver (:approved-by fact)]
    (boolean (and rec approver (some #(= approver %) (vals rec))))))

(defn- approval-facts [ledger]
  (filter #(= :operator-approval (:t %)) ledger))

(defn- approver-rows [st ledger]
  (for [f (approval-facts ledger)]
    (let [mutated? (:store-mutated? f)
          retained? (approver-retained? st f)]
      (tr "" [(code (name (:op f)))
              (code (:subject f))
              (code (:approved-by f))
              (if (:store-fn f) (code (:store-fn f)) (muted "該当する store 関数なし"))
              (cond
                (not mutated?) (muted "ストア記録の変化なし")
                retained? (ok "保持されている")
                :else (crit "台帳にのみ存在 · 記録には残っていない"))]))))

(defn- approver-gap?
  "True when at least one human sign-off mutated a store record but its
  approver identity did not survive onto that record. The disclosure
  paragraph below is emitted only when this MEASURES true."
  [st ledger]
  (boolean (some #(and (:store-mutated? %) (not (approver-retained? st %)))
                 (approval-facts ledger))))

(defn- actor-commit-fact-gap?
  "True when every disposition the actor cleared (auto-commit or
  escalation-then-approval) produced zero actor-minted audit facts on the
  commit side. Measured off `:actor-facts`, which records what
  `operation/run-operation` actually returned."
  [ledger]
  (let [cleared (filter #(contains? #{:auto-commit :operator-approval} (:t %)) ledger)]
    (and (seq cleared)
         (zero? (reduce + 0 (map #(if (= :auto-commit (:t %)) (:actor-facts %) 0) cleared))))))

;; ── stylesheet ──

(def ^:private fallback-tokens
  "Only reached if the jp-go-dds CSS resource is missing (a standalone
  fork with the dep stubbed out). Keeps the skin's own variable NAMES so
  the layout still resolves; not a second source of truth for tokens."
  (str ":root{--font-family-sans:system-ui,sans-serif;--font-family-mono:ui-monospace,monospace;"
       "--color-neutral-white:#fff;--color-neutral-solid-gray-50:#f2f2f2;"
       "--color-neutral-solid-gray-200:#ccc;--color-neutral-solid-gray-300:#b3b3b3;"
       "--color-neutral-solid-gray-600:#666;--color-neutral-solid-gray-800:#333;"
       "--color-neutral-solid-gray-900:#1a1a1a;--color-key-900:#0017c1;"
       "--color-primitive-blue-50:#e8f1fe;--color-primitive-blue-200:#c5d7fb;"
       "--color-semantic-success-2:#197a4b;--color-semantic-warning-yellow-2:#7a4d00;"
       "--color-semantic-error-1:#ec0000;}"))

(defn- style-css
  "jp-go-dds for styling: the design system's own `:root` token block plus
  its hand-written `skin-css`. The vendored COMPONENT css (another ~63 KB
  of `.dads-*` rules this page uses none of) is deliberately not inlined
  --- the page is judged on what it shows, not on its byte size."
  []
  (let [res (io/resource "jp_go_dds/dds.css")
        tokens (when res
                 (let [css (slurp res)
                       s (str/index-of css ":root {")
                       e (when s (str/index-of css "\n}" s))]
                   (when e (subs css s (+ e 2)))))]
    (str (or tokens fallback-tokens) "\n" skin/skin-css "\n")))

(defn- section [title lead headers rows]
  (str "  <section class=\"card\">\n"
       "    <h2>" title "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" % "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"
       "  </section>\n"))

(defn render
  "Renders the console from a store `st` that has already been through
  `run-demo!`."
  [st]
  (let [ledger (vec (store/audit-trail st))
        hard-n (count (filter hard-hold? ledger))
        soft-n (count (filter soft-hold? ledger))
        appr-n (count (approval-facts ledger))
        auto-n (count (filter #(= :auto-commit (:t %)) ledger))]
    (str
     "<!DOCTYPE html>\n"
     "<html lang=\"ja\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-0163 · post-harvest crop activities — Operator Console</title>"
     "<style>\n" (style-css) "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>収穫後作物処理 (ISIC 0163) — オペレータコンソール</h1>\n"
     "</header>\n"
     "<p><span class=\"badge\">read-only sample</span> "
     "<span class=\"badge\">governor-gated</span> "
     "<span class=\"badge\">バッチ登録・出荷確定は常に人間承認</span></p>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>この run の要約</h2>\n"
     "    <p>このページは <code>postharvest.render-html</code> がビルド時に生成した。"
     "表示されている判定はすべて <code>postharvest.operation/run-operation</code> → "
     "<code>postharvest.governor/check</code> → <code>postharvest.store</code> の実行結果で、"
     "手書きの結果は 1 件も無い。作物ロット規格・法域の必要証跡・引用する仕様名は "
     "<code>postharvest.facts</code> から、バッチ記録の形と clean 実測値は "
     "<code>test/postharvest/governor_test.cljc</code> の fixture から取っている"
     "（<code>postharvest.sim</code> は未実装のスタブで、この repo には seed データが無い）。</p>\n"
     "    <ul>\n"
     "      <li><span class=\"critical\">HARD 保留 " hard-n " 件</span>"
     " — 人間には提示されない。承認による上書きは構造的に不可能。</li>\n"
     "      <li><span class=\"warn\">エスカレーション " soft-n " 件</span>"
     " — ガバナは clean だが人間の署名が要る。うち " appr-n " 件をこの run で承認した。</li>\n"
     "      <li><span class=\"ok\">自動コミット " auto-n " 件</span>"
     " — このアクタで自動確定しうる唯一の経路（<code>:schedule-maintenance</code>）。</li>\n"
     "      <li>台帳 " (count ledger) " 件（HARD 保留とエスカレーションは"
     "アクタの <code>governor/hold-fact</code> が発行、承認・自動コミットは"
     "コンソールが <code>store/append-fact</code> で追記）。</li>\n"
     "    </ul>\n"
     "  </section>\n"

     (section "バッチ登録簿"
              (str "施設が抱えるロット。「最終状態」列は台帳の最後のファクトから導出している。")
              ["バッチ" "作物ロット種別" "法域" "処理状態" "最終状態" "この run での役割"]
              (registry-rows st ledger))

     (section "品質実測 vs 作物ロット規格"
              (str "実測値は <code>postharvest.facts</code> の陽性述語"
                   "（<code>moisture-in-range?</code> 等）で判定した。"
                   "規格を持たない項目（乾燥工程の無い生鮮、冷蔵要件の無い乾物）は"
                   "目標値を捏造せず「規格なし」と表示する。"
                   "衛生スコア下限 " sanitation-min-score " と重量分散上限 "
                   weight-variance-max-grams "g は "
                   "<code>postharvest.governor</code> の呼び出し側リテラル。")
              ["バッチ" "作物ロット" "水分%" "水分規格" "欠陥率%" "上限" "異物%" "上限"
               "低温℃" "冷蔵規格" "衛生" "重量分散g" "校正" "品質フラグ"]
              (quality-rows st))

     (section "作物ロット規格（postharvest.facts/crop-lot-types）"
              "ガバナが独立に突き合わせる参照表。このページはここから読むだけで、値を持たない。"
              ["ID" "名称" "水分目標" "欠陥率上限" "異物上限" "冷蔵温度目標"]
              (crop-lot-rows))

     (section "法域と必要証跡（postharvest.facts/jurisdictions）"
              (str "<code>:log-processing-batch</code> は、この一覧が"
                   "バッチの <code>:evidence-checklist</code> に全て含まれない限り HARD 保留になる。")
              ["ID" "名称" "必要証跡" "件数"]
              (jurisdiction-rows))

     (section "ガバナ・ゲート（postharvest.governor の公開 var から導出）"
              (str "許可された提案種別は <code>governor/allowed-ops</code> の "
                   (count governor/allowed-ops) " 件のみ。この閉じた集合の外にある提案"
                   "（乾燥・選別・包装機の直接制御など）は、信頼度に関わらず無条件に拒否される。"
                   "信頼度の下限は <code>governor/confidence-floor</code> = "
                   governor/confidence-floor "。")
              ["提案種別" "ゲート" "ステーク"]
              (gate-rows))

     (section "HARD 保留の内訳（この run の台帳から集計）"
              (str "上書き不可の規則ごとの発火件数。件数と説明文はガバナが返した"
                   " <code>:violations</code> そのもので、このページが書いた文言ではない。")
              ["規則" "件数" "対象バッチ" "ガバナの説明"]
              (hard-rule-rows ledger))

     (section "監査台帳（この run）"
              (str "追記のみ。<code>governor/hold-fact</code> が発行したファクトと、"
                   "承認・自動コミットをコンソールが記録したファクトを「発行元」列で区別する。")
              ["#" "ファクト" "発行元" "提案種別" "対象" "処置" "根拠規則"]
              (ledger-rows ledger))

     (section "承認者の保持（実測）"
              (str "人間が署名した後、その承認者 ID がストア記録に残るかを実際に測った。"
                   "測り方は「台帳のファクトに記録された承認者 ID が、そのバッチ記録の"
                   "いずれかの値として現れるか」の走査で、特定のキー名を仮定していない"
                   "——ストアが将来どんなキー名で承認者を持っても、この表は追従する。")
              ["提案種別" "対象" "承認者" "コンソールが呼んだストア関数" "実測結果"]
              (approver-rows st ledger))

     (when (approver-gap? st ledger)
       (str "  <section class=\"card\">\n"
            "    <h3>実測から導かれる欠陥（1）: 承認者を残せない出荷確定</h3>\n"
            "    <p>上の表で <span class=\"critical\">台帳にのみ存在</span> と出た行は、"
            "人間の署名がストア記録を変えたにもかかわらず、その承認者 ID が記録側に"
            "1 つも残っていないことを意味する。<code>store/log-batch</code> は渡された"
            "記録をそのまま保持するので承認者は残るが、<code>store/finalize-shipment</code> は"
            "バッチ ID しか受け取らず、承認者を書き込む場所そのものが無い。"
            "つまり出荷確定については「誰が承認したか」を記録から復元できない。</p>\n"
            "    <p class=\"muted\">この節は上の実測が欠落を示したときにだけ描画される。"
            "ストアが承認者を保持するようになれば、次回の生成でこの節は消える。"
            "「承認者が記録されていない」ことと「誰も承認していない」ことは"
            "区別して表示している——後者は台帳に <code>operator-approval</code> ファクトが"
            "そもそも現れない。</p>\n"
            "  </section>\n"))

     (when (actor-commit-fact-gap? ledger)
       (str "  <section class=\"card\">\n"
            "    <h3>実測から導かれる欠陥（2）: 承認側の監査ファクトが出ない</h3>\n"
            "    <p><code>operation/run-operation</code> は <code>:ok?</code> が真の経路で "
            "<code>{:ok? true :facts []}</code> を返す——つまりガバナが自動で通した提案は"
            "監査ファクトを 1 件も残さない（この run の実測でも 0 件だった）。"
            "追記専用台帳が保留だけを記録し、確定を記録しないことになる。"
            "このページの「自動コミット」「承認・コミット済」の行は、"
            "そのためコンソールが <code>store/append-fact</code> で補って書いたもので、"
            "「発行元」列で <span class=\"muted\">console harness</span> と明示している。</p>\n"
            "    <p class=\"muted\">この節も実測が 0 件のときにだけ描画される。"
            "<code>run-operation</code> が確定ファクトを返すようになれば消える。</p>\n"
            "  </section>\n"))

     "</main>\n"
     "<footer>\n"
     "  <p class=\"muted\">生成: <code>clojure -M:dev:render-html</code> "
     "(<code>postharvest.render-html</code>)。同じ入力なら毎回バイト一致する"
     "（時刻・乱数をページに出さない）。スタイルは jp-go-dds の token + skin。</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        st (run-demo!)
        ledger (vec (store/audit-trail st))
        html (render st)
        ;; Count the HARD holds actually EMITTED into the document, not
        ;; merely the ones in the ledger, and refuse to write a console
        ;; that fails to demonstrate an un-overridable block.
        rendered-hard (count (re-seq #"data-hard-hold=\"1\"" html))
        ledger-hard (count (filter hard-hold? ledger))]
    (when (zero? rendered-hard)
      (throw (ex-info "operator console rendered 0 HARD governor holds — refusing to write it"
                      {:rendered-hard rendered-hard
                       :ledger-hard ledger-hard
                       :ledger-facts (count ledger)})))
    (when (not= rendered-hard ledger-hard)
      (throw (ex-info "HARD holds in the ledger and in the rendered document disagree"
                      {:rendered-hard rendered-hard :ledger-hard ledger-hard})))
    (io/make-parents out)
    (spit out html)
    (println "wrote" out
             (str "(" (count ledger) " ledger facts, "
                  rendered-hard " HARD holds, "
                  (count (filter soft-hold? ledger)) " escalations, "
                  (count (approval-facts ledger)) " approvals)"))))
