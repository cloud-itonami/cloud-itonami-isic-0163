(ns postharvest.phase
  "Phase machine: the states a post-harvest crop-processing batch transits
  through.

  State machine:
    :intake -> :clean-trim -> :sort-grade -> :dry-or-store -> :pack -> :audit -> :archived

  `:intake` is crop-lot receiving; `:clean-trim` is cleaning/trimming
  (removing foreign matter, damaged material); `:sort-grade` is
  sorting/grading by size, density, or visual quality; `:dry-or-store` is
  either drying (for crop-lot types with a drying step, e.g. tea/
  tobacco) or cold-storage handling (for crop-lot types requiring
  refrigeration); `:pack` is finished-lot packing for the primary
  market; `:audit` is compliance audit; `:archived` is the terminal
  state.

  Each transition can accept a proposal and yield an audit fact.")

(def all-phases
  "All valid phases in the post-harvest crop-processing workflow."
  [:intake :clean-trim :sort-grade :dry-or-store :pack :audit :archived])

(def phase-sequence
  "Ordered phases representing normal batch progression."
  [:intake :clean-trim :sort-grade :dry-or-store :pack :audit :archived])

(defn valid-phase?
  "Check if a phase is valid."
  [phase]
  (contains? (set all-phases) phase))

(defn- index-of
  "Portable (Clojure/ClojureScript) index lookup -- `.indexOf` is a
  JVM-only `java.util.List` method that ClojureScript's PersistentVector
  does not implement, so it is avoided here even though `phase-sequence`
  is a plain vector. Returns -1 when `x` is not found, matching
  `java.util.List/indexOf`'s contract."
  [coll x]
  (or (first (keep-indexed (fn [i v] (when (= v x) i)) coll)) -1))

(defn can-transition?
  "Check if a transition from one phase to another is valid
  (must be forward-only in the sequence, no backtracking). Always returns a
  boolean (never nil), including when either phase is invalid."
  [from-phase to-phase]
  (boolean
   (and (valid-phase? from-phase) (valid-phase? to-phase)
        (let [from-idx (index-of phase-sequence from-phase)
              to-idx (index-of phase-sequence to-phase)]
          (and (>= from-idx 0) (>= to-idx 0) (< from-idx to-idx))))))
