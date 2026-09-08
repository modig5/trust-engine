# trust-engine

Small Java chess engine + GUI for experimentation.

## Dependencies

- **JDK 21** (override with `make JAVA_RELEASE=17` for older JDKs)
- `make`
- No third-party libraries — pure Java standard library + Swing for the GUI

## Build & run

```sh
make          # build runnable jar at build/trust-engine.jar
make run      # compile and run from class files
make run-jar  # run the built jar
make perft    # run perft move-generation tests
make profile  # record search CPU samples, allocations, and garbage collection
make clean    # remove build output
```

## Profiling search

Run `make profile` with JDK 21 to profile iterative search in opening, middlegame,
and endgame positions. It uses Java Flight Recorder (JFR), included in the JDK;
no additional dependencies are needed.

```sh
make profile
make profile PROFILE_DEPTH=7 PROFILE_RUNS=3 PROFILE_WARMUPS=3
```

Each invocation creates a new `build/profiles/search-*` directory containing
`summary.txt` and a `.jfr` recording for each position/run. The summary includes
search elapsed time, best move, sampled CPU hotspots, allocation sample counts
by class, total bytes allocated by the search thread (when the JVM supports the
counter), and garbage collection pause time. Open recordings in a JFR viewer for
full call stacks, or inspect one with the JDK command:

```sh
jfr summary build/profiles/search-<id>/middlegame-1.jfr
```

Warmup runs happen before recording, at up to depth 5. Each measured search starts
with a fresh transposition table and iteratively searches through the requested
depth. Board construction and table allocation happen before recording; opening
book lookup, GUI rendering, and pondering are excluded. The runner checks that
each returned move is legal and that search restored the position.

CPU percentages describe sampled top-of-stack locations in search, not exact
function timings or call counts. Allocation sample shares are not byte shares or
exact object counts; the separate thread counter measures total allocated bytes,
not retained heap size. Raw JFR allocation weights can span recording boundaries,
so the summary deliberately does not use them for per-class byte totals.
GC pauses cover the recording and may include collection of setup/warmup garbage.
Short runs can capture few samples; increase depth if needed. Compare repeated
runs with the same JDK, heap settings, and positions, then confirm speedups with
unprofiled benchmarks. The existing perft benchmark measures move generation
separately from search. Generated profiles are under the ignored `build/` directory.

## Opening book

The engine loads a Polyglot-format `.bin` book from `src/resources/Opening.bin`
at startup.

## Project layout

```
src/
  main/       Board, Move, FEN helpers, GUI entry point
  engine/     AI search, evaluation, opening book
  Pieces/     Piece types and move generation
  tests/      Perft tests
  resources/  Sprites, opening books
```

## Controls

- Click-and-drag to move pieces
- Left/right arrow keys to undo/redo moves
