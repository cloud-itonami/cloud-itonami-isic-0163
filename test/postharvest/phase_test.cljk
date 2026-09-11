(ns postharvest.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [postharvest.phase :as phase]))

;; ──────────────────────── Phase Validity ──────────────────────

(deftest valid-phase-test
  (testing "intake is valid"
    (is (true? (phase/valid-phase? :intake))))

  (testing "sort-grade is valid"
    (is (true? (phase/valid-phase? :sort-grade))))

  (testing "archived is valid"
    (is (true? (phase/valid-phase? :archived))))

  (testing "invalid phase returns false"
    (is (false? (phase/valid-phase? :invalid)))))

;; ──────────────────────── Phase Transitions ──────────────────────

(deftest can-transition-test
  (testing "intake -> clean-trim is valid (forward progression)"
    (is (true? (phase/can-transition? :intake :clean-trim))))

  (testing "intake -> sort-grade is valid (skip clean-trim)"
    (is (true? (phase/can-transition? :intake :sort-grade))))

  (testing "clean-trim -> intake is invalid (backward)"
    (is (false? (phase/can-transition? :clean-trim :intake))))

  (testing "sort-grade -> archived is valid (forward to end)"
    (is (true? (phase/can-transition? :sort-grade :archived))))

  (testing "archived -> intake is invalid (backward from end)"
    (is (false? (phase/can-transition? :archived :intake))))

  (testing "same phase is invalid"
    (is (false? (phase/can-transition? :sort-grade :sort-grade))))

  (testing "invalid phases return false"
    (is (false? (phase/can-transition? :invalid :sort-grade)))
    (is (false? (phase/can-transition? :sort-grade :invalid)))))
