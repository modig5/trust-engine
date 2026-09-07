package tests;

import Pieces.Piece;
import Pieces.PieceType;
import engine.MoveGen;
import engine.Zobrist;
import main.Board;
import main.Move;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Headless regression checks for occupancy, incremental hashing and lazy FEN. */
public class BoardStateRegression {
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String CASTLING = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

    private static class CountingBoard extends Board {
        int serializations;
        CountingBoard(String fen) { super(fen); }
        @Override
        public String generateFEN(String ep, String rights) {
            serializations++;
            return super.generateFEN(ep, rights);
        }
    }

    private record Snapshot(String fen, long hash, Map<Long, Integer> repetitions,
                            boolean threefold, boolean fiftyMove) {
        Snapshot(Board board) {
            this(board.getFEN(), board.zobristHash, new HashMap<>(board.repetitionMap),
                    board.threefold, board.drawByHalfMoveClock);
        }
        void verify(Board board) {
            require(fen.equals(board.getFEN()), "FEN not restored");
            require(hash == board.zobristHash, "Hash not restored");
            require(repetitions.equals(board.repetitionMap), "Repetition counts not restored");
            require(threefold == board.threefold && fiftyMove == board.drawByHalfMoveClock,
                    "Draw state not restored");
            checkState(board);
        }
    }

    public static void main(String[] args) {
        checkPerft(START, 4, 197281);
        checkPerft("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 3, 97862);
        checkPerft("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 3, 2812);
        checkSpecialMoves();
        checkLazyFenAndCopy();
        checkRepetitionAndHistory();
        System.out.println("Board state regression checks passed");
    }

    private static void checkPerft(String fen, int depth, long expected) {
        CountingBoard board = new CountingBoard(fen);
        Snapshot before = new Snapshot(board);
        int serializations = board.serializations;
        long nodes = perft(board, new MoveGen(board), depth);
        require(board.serializations == serializations, "Perft serialized FEN");
        require(nodes == expected, "Perft depth " + depth + ": " + nodes + " != " + expected);
        before.verify(board);
        System.out.println("Perft depth " + depth + ": " + nodes + " passed");
    }

    private static long perft(Board board, MoveGen generator, int depth) {
        checkState(board);
        if (depth == 0) return 1;
        long hash = board.zobristHash;
        var moves = generator.getAllValidMoves();
        require(hash == board.zobristHash, "Legality checks changed hash");
        checkState(board);
        long nodes = 0;
        for (Move move : moves) {
            Move undo = board.makeMove(move, true);
            nodes += perft(board, generator, depth - 1);
            board.undoMove(undo);
            require(hash == board.zobristHash, "Undo changed parent hash");
            checkState(board);
        }
        return nodes;
    }

    private static void checkSpecialMoves() {
        for (String uci : new String[]{"e1g1", "e1c1", "a1a8", "h1h8"}) roundTrip(CASTLING, uci, null);
        for (String uci : new String[]{"e8g8", "e8c8"}) roundTrip(CASTLING.replace(" w ", " b "), uci, null);
        roundTrip("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2", "e5d6", null);
        roundTrip("4k3/8/8/8/3Pp3/8/8/4K3 b - d3 0 2", "e4d3", null);
        for (PieceType type : new PieceType[]{PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT}) {
            roundTrip("1r2k3/P7/8/8/8/8/8/4K3 w - - 0 1", "a7b8", type);
            roundTrip("4k3/P7/8/8/8/8/8/4K3 w - - 0 1", "a7a8", type);
            roundTrip("4k3/8/8/8/8/8/p7/1R2K3 b - - 0 1", "a2b1", type);
        }
        Board board = new Board(CASTLING);
        board.makeMove(move(board, "h1h2", null), true);
        require(board.getFEN().split(" ")[2].equals("Qkq"), "Wrong castling rights after rook move");
        Board pinned = new Board("4k3/8/8/r4pPK/8/8/8/8 w - f6 0 1");
        Snapshot before = new Snapshot(pinned);
        Move ep = new Move(pinned, pinned.getPiece(6, 3), 5, 2);
        require(!pinned.isValidMove(ep), "Pinned en passant was accepted");
        before.verify(pinned);
    }

    private static void roundTrip(String fen, String uci, PieceType promotion) {
        Board board = new Board(fen);
        Snapshot before = new Snapshot(board);
        Move undo = board.makeMove(move(board, uci, promotion), true);
        checkState(board);
        // Reparse serialized state as an independent check of special-move state.
        Board restored = new Board(board.getFEN());
        require(restored.zobristHash == board.zobristHash, "FEN/hash mismatch after " + uci);
        board.undoMove(undo);
        before.verify(board);
    }

    private static void checkLazyFenAndCopy() {
        CountingBoard board = new CountingBoard(START);
        int count = board.serializations;
        Move undo = board.makeMove(move(board, "e2e4", null), true);
        require(board.serializations == count, "Search move serialized FEN");
        Board copy = new Board(board);
        require(board.serializations == count, "Copy serialized source FEN");
        String expected = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        require(board.getFEN().equals(expected) && copy.getFEN().equals(expected), "Stale FEN after move/copy");
        board.getFEN();
        require(board.serializations == count + 1, "FEN was not cached");
        for (Piece piece : board.pieceList) require(copy.getPiece(piece.col, piece.row) != piece, "Copy shares pieces");
        copy.makeMove(move(copy, "e7e5", null), true);
        checkState(copy);
        require(board.getFEN().equals(expected), "Copy changed original board");
        board.undoMove(undo);
        require(board.getFEN().equals(START), "Undo did not restore cached FEN");
        require(board.serializations == count + 1, "Undo serialized FEN");
    }

    private static void checkRepetitionAndHistory() {
        Board board = new Board(START);
        board.isAIThinking = true; // Prevent GUI AI callbacks for actual moves.
        Snapshot before = new Snapshot(board);
        for (int i = 0; i < 2; i++) {
            for (String uci : new String[]{"g1f3", "g8f6", "f3g1", "f6g8"})
                board.makeMove(move(board, uci, null), false);
        }
        require(board.threefold && board.repetitionMap.get(board.zobristHash) == 3, "Threefold not detected");
        Snapshot repeated = new Snapshot(board);
        board.undoLastMove();
        require(!board.threefold, "Undo did not clear threefold");
        board.redoLastMove();
        repeated.verify(board);
        for (int i = 0; i < 8; i++) board.undoLastMove();
        before.verify(board);

        Board simulated = new Board(START);
        ArrayList<Move> undos = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            for (String uci : new String[]{"g1f3", "g8f6", "f3g1", "f6g8"})
                undos.add(simulated.makeMove(move(simulated, uci, null), true));
        }
        require(simulated.repetitionMap.get(simulated.zobristHash) == 3 && !simulated.threefold,
                "Search repetition tracking changed GUI draw state");
        for (int i = undos.size() - 1; i >= 0; i--) simulated.undoMove(undos.get(i));
        before.verify(simulated);

        Board clock = new Board("4k3/8/8/8/8/8/P7/4K2R w - - 99 1");
        Snapshot clockBefore = new Snapshot(clock);
        Move undo = clock.makeMove(move(clock, "h1h2", null), true);
        require(clock.halfMoveCounter == 100 && clock.drawByHalfMoveClock, "Halfmove clock not advanced");
        clock.undoMove(undo);
        clockBefore.verify(clock);
        undo = clock.makeMove(move(clock, "a2a3", null), true);
        require(clock.halfMoveCounter == 0 && !clock.drawByHalfMoveClock, "Pawn move did not reset clock");
        clock.undoMove(undo);
        clockBefore.verify(clock);
    }

    private static Move move(Board board, String uci, PieceType promotion) {
        int fromCol = uci.charAt(0) - 'a', fromRow = '8' - uci.charAt(1);
        int toCol = uci.charAt(2) - 'a', toRow = '8' - uci.charAt(3);
        for (Move move : new MoveGen(board).getAllValidMoves()) {
            if (move.col == fromCol && move.row == fromRow && move.newCol == toCol
                    && move.newRow == toRow && move.promotionPiece == promotion) return move;
        }
        throw new AssertionError("No legal move " + uci);
    }

    private static void checkState(Board board) {
        Piece[] expected = new Piece[64];
        for (Piece piece : board.pieceList) {
            int square = piece.row * 8 + piece.col;
            require(expected[square] == null, "Duplicate occupied square");
            expected[square] = piece;
        }
        for (int square = 0; square < 64; square++)
            require(board.getPiece(square % 8, square / 8) == expected[square], "Occupancy mismatch");
        require(board.zobristHash == Zobrist.computeHash(board), "Incremental hash mismatch");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
