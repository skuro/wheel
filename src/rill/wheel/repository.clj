(ns rill.wheel.repository
  "The protocol for implementing repositories."
  (:refer-clojure :exclude [update]))

(defprotocol Repository
  (commit! [repo aggregate]
    "Commit changes to `aggregate` by storing its
    `:rill.wheel.aggregate/new-events`.  Returns `true` on success or
    when there are no new events. `nil` otherwise.

    Application writers should use `rill.wheel.command/commit!`
    instead.")
  (update [repo aggregate]
    "Update an aggregate by applying any new comitted events, as
    determined by `:rill.wheel.aggregate/version`.

    Application writers should call the `get-{aggregate-name}`
    functions generated by `rill.wheel.aggregate/defaggregate`
    instead."))