# Rill/Wheel - Event generation using aggregates and commands

![Logo](logo.png)


> It may be hard for an egg to turn into a bird: it would be a jolly
> sight harder for it to learn to fly while remaining an egg.

-- C.S. Lewis

[![Build Status](https://travis-ci.org/rill-event-sourcing/wheel.svg?branch=master)](https://travis-ci.org/rill-event-sourcing/wheel)

## Usage

See [the manual](https://rill-event-sourcing.github.io/wheel/index.html)

## Changelog

### v0.1.23
  - remove work-in-progress broken files from release

### v0.1.22
  - events don't include their aggregate type any more
  - introduce `map->{name}` constructors

### v0.1.21
  - upgrade to clojure-1.9.0-alpha16 with clojure.spec.alpha
    namespaces

### v0.1.20
  - ensure repositories print as short names instead of potentially
    giant map

### V0.1.19
  - deserialize `:rill.message/stream-id` in messages when using
    `rill.wheel/wrap-stream-properties`.
  - remove debug output from `rill.wheel.wrap-upcasts`
  - added tests for integration with `rill.event-store.mysql`

### V0.1.18
  - include `:rill.message/stream-id` and `:rill.message/number` in
    `ok` result.

### v0.1.17
  - added upcasts wrapper. added presentation to docs

### v0.1.16
  - serialize props for storing aggregate ids in durable stores

### v0.1.15
  - small documentation improvements
  - use clojure.spec for parsing `defevent` and `defcommand` macro
    arguments
  - use provided prepost maps in `->event-name` constructors

### v0.1.14
  - clojure.spec specs for macros and functions
  - clojure.spec enabled for tests

### v0.1.13
  - allow access to backing `event-store` from repository
  - added triggers. See `rill.wheel.trigger`
  - added `rill.wheel.wrap-new-events-callback` event store wrapper
  - small documentation typos fixed
  - added `->{event-name}` constructor function
  - added `->{command-name}` constructor function
  - deprecated `{command-name}-command` function

### v0.1.12
  - added `:rill.wheel/properties` metadata to events and commands
  - added markdown reporting

### v0.1.11
  - (breaking) Renamed `rill.wheel.aggregate` to `rill.wheel`
  - (breaking) `defevent` now takes aggregate type as a required
    argument
  - `defevent` checks for collisions of event properties with
    aggregate identifier
  - Added more documentation
  - Added `reason` function to get reason for a `rejection`

### v0.1.10
  Modified command setup, allowing commands to be supplied as
  messages.
  
  - (breaking) merged `rill.wheel.command` into `rill.wheel.aggregate`
    namespace
  - (breaking) `rill.wheel.command` takes aggregate type as additional
    argument.
  - (breaking) first argument to commands now *must* be an aggregate.
  - (breaking) `ok` result's aggregate now reflects the comitted
    (updated) aggregate.

### v0.1.9
  - Improve & expand documentation

### v0.1.8
  - Allow inlining of event and command definitions in defaggregate
  - Make `:rill.wheel.command/events` metadata optional.
  - (breaking) defcommand does not automatically commit results, use
    `rill.wheel.command/commit!`

### v0.1.7
  - Documentation improvements
  - (breaking/fix) preconditions on `defevent` go on the event
    constructor.
  - (breaking) split off aggregate retrieval code generated by
    `defaggregate` into new `get-$name` function.

### v0.1.6
  - (breaking) aggregate ids now include the aggregate type
  - body of `defevent` is now optional
  - (breaking) `defevent` creates additional `{name}-event` function

### v0.1.5
  Some breaking API updates.
  - defaggregate builds the aggregate constructor automatically
  - aggregate id is a map of identifying properties
  - repository protocol does `update` instead of
    `fetch`.

### v0.1.4
  - Finished `rill.wheel.wrap-stream-properties`.

### v0.1.3
  - Fixed `rill.wheel.testing/sub?` checking seqs with lists.

### v0.1.2

  - Added `rill.wheel.check` namespace for checking model consistency.
  - Enforcing use of `:rill.wheel.command/events` key on command
    definitions.

### v0.1.1

Initial release

## License

Copyright © 2016 Joost Diepenmaat, Zeekat Software Ontwikkeling

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
