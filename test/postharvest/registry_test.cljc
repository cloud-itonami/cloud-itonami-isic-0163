(ns postharvest.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [postharvest.registry :as registry]))

;; ──────────────────────── Moisture Target ──────────────────────

(deftest moisture-out-of-target-test
  (testing "moisture at target with no tolerance returns false"
    (is (false? (registry/moisture-out-of-target? 3.0 3.0 0.5))))

  (testing "moisture within tolerance range returns false"
    (is (false? (registry/moisture-out-of-target? 2.7 3.0 0.5))))

  (testing "moisture below tolerance returns true (violation)"
    (is (true? (registry/moisture-out-of-target? 2.0 3.0 0.5))))

  (testing "moisture above tolerance returns true (violation)"
    (is (true? (registry/moisture-out-of-target? 3.6 3.0 0.5)))))

;; ──────────────────────── Defect Rate ──────────────────────

(deftest defect-rate-exceeded-test
  (testing "rate below maximum returns false (no violation)"
    (is (false? (registry/defect-rate-exceeded? 3 5))))

  (testing "rate at maximum returns false"
    (is (false? (registry/defect-rate-exceeded? 5 5))))

  (testing "rate above maximum returns true (violation)"
    (is (true? (registry/defect-rate-exceeded? 6 5)))))

;; ──────────────────────── Foreign Matter ──────────────────────

(deftest foreign-matter-exceeded-test
  (testing "content within tolerance returns false (no violation)"
    (is (false? (registry/foreign-matter-exceeded? 0.3 0.5))))

  (testing "content at tolerance returns false"
    (is (false? (registry/foreign-matter-exceeded? 0.5 0.5))))

  (testing "content exceeding tolerance returns true (violation)"
    (is (true? (registry/foreign-matter-exceeded? 0.8 0.5)))))

;; ──────────────────────── Pest Infestation ──────────────────────

(deftest pest-infestation-detected-test
  (testing "no detection returns false"
    (is (false? (registry/pest-infestation-detected? false)))
    (is (false? (registry/pest-infestation-detected? nil))))

  (testing "detection returns true"
    (is (true? (registry/pest-infestation-detected? true)))))

;; ──────────────────────── Pesticide Residue ──────────────────────

(deftest pesticide-residue-exceeded-test
  (testing "no exceedance returns false"
    (is (false? (registry/pesticide-residue-exceeded? false)))
    (is (false? (registry/pesticide-residue-exceeded? nil))))

  (testing "exceedance returns true"
    (is (true? (registry/pesticide-residue-exceeded? true)))))

;; ──────────────────────── Drying-Equipment Calibration ──────────────────────

(deftest drying-equipment-calibration-overdue-test
  (testing "recent calibration returns false (no violation)"
    ;; Assume calibrated 10 days ago
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          ten-days-ago (- now (* 10 24 60 60 1000))]
      (is (false? (registry/drying-equipment-calibration-overdue? ten-days-ago now)))))

  (testing "overdue calibration returns true (violation)"
    (let [now #?(:clj (System/currentTimeMillis) :cljs (.now js/Date))
          hundred-days-ago (- now (* 100 24 60 60 1000))]
      (is (true? (registry/drying-equipment-calibration-overdue? hundred-days-ago now))))))

;; ──────────────────────── Weight Variance ──────────────────────

(deftest weight-variance-excessive-test
  (testing "variance within tolerance returns false (no violation)"
    (is (false? (registry/weight-variance-excessive? 45 50))))

  (testing "variance at tolerance returns false"
    (is (false? (registry/weight-variance-excessive? 50 50))))

  (testing "variance exceeding tolerance returns true (violation)"
    (is (true? (registry/weight-variance-excessive? 51 50)))))

;; ──────────────────────── Cold-Storage Temperature ──────────────────────

(deftest cold-storage-temp-out-of-range-test
  (testing "temperature at target with no tolerance returns false"
    (is (false? (registry/cold-storage-temp-out-of-range? 8.0 8.0 1.0))))

  (testing "temperature within tolerance range returns false"
    (is (false? (registry/cold-storage-temp-out-of-range? 8.5 8.0 1.0))))

  (testing "temperature below tolerance returns true (violation)"
    (is (true? (registry/cold-storage-temp-out-of-range? 6.5 8.0 1.0))))

  (testing "temperature above tolerance returns true (violation)"
    (is (true? (registry/cold-storage-temp-out-of-range? 9.5 8.0 1.0)))))

;; ──────────────────────── Sanitation Score ──────────────────────

(deftest sanitation-score-insufficient-test
  (testing "score at minimum returns false (no violation)"
    (is (false? (registry/sanitation-score-insufficient? 75 75))))

  (testing "score above minimum returns false"
    (is (false? (registry/sanitation-score-insufficient? 85 75))))

  (testing "score below minimum returns true (violation)"
    (is (true? (registry/sanitation-score-insufficient? 74 75)))))
