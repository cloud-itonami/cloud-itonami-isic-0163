(ns postharvest.facts
  "Reference facts for post-harvest crop-processing facilities: crop-lot
  type quality windows (finished-product moisture / defect rate / foreign
  matter / cold-chain temperature), jurisdiction evidence-checklist
  requirements. This namespace contains pure lookup functions for
  post-harvest-quality compliance checks -- the Governor calls these to
  independently validate proposals; the advisor's confidence is never
  sufficient on its own.

  A post-harvest crop-processing facility (ISIC Rev.5 0163) prepares
  agricultural products for the PRIMARY MARKET -- cleaning, trimming,
  sorting, grading, disinfecting, drying, cooling/cold-storage handling,
  and packing -- for crops NOT already covered by a more specific ISIC
  class. This is what distinguishes 0163 from grain-mill post-harvest
  processing (ISIC 1061, which transforms the crop into flour/milled
  product) and seed processing for propagation (ISIC 0164, whose quality
  bar is seed VIABILITY for planting, not product quality for market).
  The quality bar here is FOR-MARKET / FOR-CONSUMPTION product quality:
  moisture (relevant only to crop-lot types with a drying step, e.g. tea,
  tobacco -- absent, never fabricated, for fresh produce), physical
  defect rate, foreign-matter/trash content, and cold-chain temperature
  (relevant only to crop-lot types that require refrigerated handling --
  absent for dried goods). Pest infestation and pesticide-residue lab
  flags are genuine food-safety hazards this actor surfaces but never
  resolves on its own."
  (:require [clojure.set :as set]))

(def crop-lot-types
  "Valid post-harvest crop-lot categories and their safe processing/
  packing windows. `moisture-target-percent`/`moisture-tolerance-percent`
  are nil for crop-lot types with no drying step (fresh produce) -- the
  Governor's moisture check is skipped entirely for those types rather
  than fabricating a target. `defect-rate-max-percent` is the maximum
  tolerated fraction of visually/physically defective units (bruising,
  discoloration, insect damage) after sorting/grading.
  `foreign-matter-max-percent` is the maximum tolerated fraction of
  non-crop debris (stems, soil, trash) remaining after cleaning.
  `cold-storage-temp-target-c`/`cold-storage-temp-tolerance-c` are nil
  for crop-lot types with no cold-chain requirement (dried goods) --
  the Governor's cold-chain check is skipped entirely for those types."
  {:tea/black-leaf
   {:id :tea/black-leaf
    :name "紅茶(リーフ)"
    :moisture-target-percent 3.0
    :moisture-tolerance-percent 0.5
    :defect-rate-max-percent 5.0
    :foreign-matter-max-percent 0.5
    :cold-storage-temp-target-c nil
    :cold-storage-temp-tolerance-c nil}

   :tobacco/flue-cured
   {:id :tobacco/flue-cured
    :name "フルーキュア葉タバコ"
    :moisture-target-percent 13.0
    :moisture-tolerance-percent 1.0
    :defect-rate-max-percent 8.0
    :foreign-matter-max-percent 1.0
    :cold-storage-temp-target-c nil
    :cold-storage-temp-tolerance-c nil}

   :produce/citrus
   {:id :produce/citrus
    :name "柑橘類(生鮮)"
    :moisture-target-percent nil
    :moisture-tolerance-percent nil
    :defect-rate-max-percent 3.0
    :foreign-matter-max-percent 0.5
    :cold-storage-temp-target-c 8.0
    :cold-storage-temp-tolerance-c 1.0}

   :produce/leafy-greens
   {:id :produce/leafy-greens
    :name "葉物野菜(生鮮)"
    :moisture-target-percent nil
    :moisture-tolerance-percent nil
    :defect-rate-max-percent 2.0
    :foreign-matter-max-percent 0.3
    :cold-storage-temp-target-c 2.0
    :cold-storage-temp-tolerance-c 0.5}})

(defn crop-lot-type-by-id [id]
  (get crop-lot-types id))

(def jurisdictions
  "Post-harvest crop-processing jurisdictions and their evidence-
  checklist requirements."
  {:jp/mhlw
   {:id :jp/mhlw
    :name "日本 (食品衛生法・厚生労働省)"
    :required-evidence
    [:lot-intake-record
     :cleaning-grading-log
     :defect-inspection
     :foreign-matter-test
     :pest-inspection
     :pesticide-residue-test
     :weight-check]}

   :us/fda
   {:id :us/fda
    :name "United States (FSMA Produce Safety Rule / FDA)"
    :required-evidence
    [:lot-intake-record
     :cleaning-grading-log
     :defect-inspection
     :foreign-matter-test
     :pest-inspection
     :pesticide-residue-test
     :weight-check]}

   :eu/gfl
   {:id :eu/gfl
    :name "European Union (General Food Law Regulation (EC) 178/2002)"
    :required-evidence
    [:lot-intake-record
     :cleaning-grading-log
     :defect-inspection
     :foreign-matter-test
     :pest-inspection
     :pesticide-residue-test
     :weight-check]}})

(defn jurisdiction-by-id [id]
  (get jurisdictions id))

(defn required-evidence-satisfied?
  "Verify that every item in the jurisdiction's `:required-evidence` list
  is present in `evidence`. `jurisdiction` may be a resolved jurisdiction
  map (as returned by `jurisdiction-by-id`) or a raw jurisdiction id --
  both call conventions are in use (tests pass a resolved map; the
  Governor passes the raw id straight off batch metadata)."
  [jurisdiction evidence]
  (let [j (if (map? jurisdiction) jurisdiction (jurisdiction-by-id jurisdiction))]
    (if-not j
      false
      (set/subset? (set (:required-evidence j)) (set evidence)))))

(defn moisture-in-range?
  "Positive-sense convenience predicate: does `percent` fall within
  `crop-lot`'s moisture tolerance window (inclusive) around its target?
  Returns false when the crop-lot type has no moisture spec at all (a
  drying step doesn't apply, e.g. fresh produce) -- there is nothing to
  be 'in range' of."
  [percent crop-lot]
  (boolean
   (and (some? crop-lot)
        (some? (:moisture-target-percent crop-lot))
        (let [target (:moisture-target-percent crop-lot)
              tol (:moisture-tolerance-percent crop-lot)]
          (and (>= percent (- target tol))
               (<= percent (+ target tol)))))))

(defn defect-rate-in-range?
  "Positive-sense convenience predicate: does `percent` stay at or below
  `crop-lot`'s maximum tolerated defect rate?"
  [percent crop-lot]
  (boolean
   (and (some? crop-lot)
        (<= percent (:defect-rate-max-percent crop-lot)))))

(defn foreign-matter-in-range?
  "Positive-sense convenience predicate: does `percent` stay at or below
  `crop-lot`'s maximum tolerated foreign-matter content?"
  [percent crop-lot]
  (boolean
   (and (some? crop-lot)
        (<= percent (:foreign-matter-max-percent crop-lot)))))

(defn cold-storage-temp-in-range?
  "Positive-sense convenience predicate: does `temp-c` fall within
  `crop-lot`'s cold-chain tolerance window (inclusive) around its
  target? Returns false when the crop-lot type has no cold-chain
  requirement at all (a dried good, e.g. tea/tobacco) -- there is
  nothing to be 'in range' of."
  [temp-c crop-lot]
  (boolean
   (and (some? crop-lot)
        (some? (:cold-storage-temp-target-c crop-lot))
        (let [target (:cold-storage-temp-target-c crop-lot)
              tol (:cold-storage-temp-tolerance-c crop-lot)]
          (and (>= temp-c (- target tol))
               (<= temp-c (+ target tol)))))))
