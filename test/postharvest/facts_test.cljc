(ns postharvest.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [postharvest.facts :as facts]))

;; ──────────────────────── Crop-Lot Type Lookups ──────────────────────

(deftest crop-lot-type-by-id-test
  (testing "black tea crop-lot type exists"
    (let [p (facts/crop-lot-type-by-id :tea/black-leaf)]
      (is (some? p))
      (is (= (:id p) :tea/black-leaf))
      (is (= (:moisture-target-percent p) 3.0))
      (is (= (:defect-rate-max-percent p) 5.0))))

  (testing "citrus produce crop-lot type exists and has no moisture spec"
    (let [p (facts/crop-lot-type-by-id :produce/citrus)]
      (is (some? p))
      (is (nil? (:moisture-target-percent p)))
      (is (= (:cold-storage-temp-target-c p) 8.0))))

  (testing "nonexistent crop-lot type returns nil"
    (is (nil? (facts/crop-lot-type-by-id :maize/nonexistent)))))

;; ──────────────────────── Jurisdiction Lookups ──────────────────────

(deftest jurisdiction-by-id-test
  (testing "JP MHLW jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :jp/mhlw)]
      (is (some? j))
      (is (contains? (set (:required-evidence j)) :pesticide-residue-test))))

  (testing "US FDA jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :us/fda)]
      (is (some? j))
      (is (contains? (set (:required-evidence j)) :pest-inspection))))

  (testing "EU GFL jurisdiction exists"
    (let [j (facts/jurisdiction-by-id :eu/gfl)]
      (is (some? j))
      (is (contains? (set (:required-evidence j)) :foreign-matter-test))))

  (testing "nonexistent jurisdiction returns nil"
    (is (nil? (facts/jurisdiction-by-id :xx/unknown)))))

;; ──────────────────────── Post-Harvest Safety Predicates ──────────

(deftest moisture-in-range-test
  (testing "moisture within tolerance passes"
    (let [p (facts/crop-lot-type-by-id :tea/black-leaf)]
      (is (true? (facts/moisture-in-range? 3.0 p)))))

  (testing "moisture at lower tolerance boundary passes"
    (let [p (facts/crop-lot-type-by-id :tea/black-leaf)]
      (is (true? (facts/moisture-in-range? 2.5 p)))))

  (testing "moisture below range fails"
    (let [p (facts/crop-lot-type-by-id :tea/black-leaf)]
      (is (false? (facts/moisture-in-range? 2.0 p)))))

  (testing "moisture above range fails"
    (let [p (facts/crop-lot-type-by-id :tea/black-leaf)]
      (is (false? (facts/moisture-in-range? 4.0 p)))))

  (testing "crop-lot type with no moisture spec (fresh produce) is never in range"
    (let [p (facts/crop-lot-type-by-id :produce/citrus)]
      (is (false? (facts/moisture-in-range? 3.0 p))))))

(deftest defect-rate-in-range-test
  (testing "defect rate at or below maximum passes"
    (let [p (facts/crop-lot-type-by-id :tea/black-leaf)]
      (is (true? (facts/defect-rate-in-range? 5.0 p)))
      (is (true? (facts/defect-rate-in-range? 2.0 p)))))

  (testing "defect rate above maximum fails"
    (let [p (facts/crop-lot-type-by-id :tea/black-leaf)]
      (is (false? (facts/defect-rate-in-range? 6.0 p))))))

(deftest foreign-matter-in-range-test
  (testing "foreign matter at or below maximum passes"
    (let [p (facts/crop-lot-type-by-id :tobacco/flue-cured)]
      (is (true? (facts/foreign-matter-in-range? 1.0 p)))
      (is (true? (facts/foreign-matter-in-range? 0.5 p)))))

  (testing "foreign matter above maximum fails"
    (let [p (facts/crop-lot-type-by-id :tobacco/flue-cured)]
      (is (false? (facts/foreign-matter-in-range? 1.5 p))))))

(deftest cold-storage-temp-in-range-test
  (testing "temperature within tolerance passes"
    (let [p (facts/crop-lot-type-by-id :produce/leafy-greens)]
      (is (true? (facts/cold-storage-temp-in-range? 2.0 p)))))

  (testing "temperature above tolerance fails"
    (let [p (facts/crop-lot-type-by-id :produce/leafy-greens)]
      (is (false? (facts/cold-storage-temp-in-range? 4.0 p)))))

  (testing "crop-lot type with no cold-chain requirement (dried goods) is never in range"
    (let [p (facts/crop-lot-type-by-id :tea/black-leaf)]
      (is (false? (facts/cold-storage-temp-in-range? 2.0 p))))))

;; ──────────────────────── Evidence Completeness ──────────────────────

(deftest required-evidence-satisfied-test
  (testing "complete evidence checklist passes"
    (let [j (facts/jurisdiction-by-id :jp/mhlw)
          evidence [:lot-intake-record :cleaning-grading-log :defect-inspection
                    :foreign-matter-test :pest-inspection :pesticide-residue-test :weight-check]]
      (is (true? (facts/required-evidence-satisfied? j evidence)))))

  (testing "incomplete evidence fails"
    (let [j (facts/jurisdiction-by-id :jp/mhlw)
          evidence [:lot-intake-record :cleaning-grading-log]]
      (is (false? (facts/required-evidence-satisfied? j evidence)))))

  (testing "raw jurisdiction id call convention also works"
    (let [evidence [:lot-intake-record :cleaning-grading-log :defect-inspection
                    :foreign-matter-test :pest-inspection :pesticide-residue-test :weight-check]]
      (is (true? (facts/required-evidence-satisfied? :us/fda evidence)))))

  (testing "unknown jurisdiction never satisfies"
    (is (false? (facts/required-evidence-satisfied? :xx/unknown [])))))
