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
make clean    # remove build output
```

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
