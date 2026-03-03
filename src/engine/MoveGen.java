package engine;

import Pieces.Piece;
import main.Board;
import main.Move;

import java.util.ArrayList;

import static main.Board.pieceList;

public class MoveGen {
    private final Board board;

    public MoveGen(Board board) {
        this.board = board;
    }

    public ArrayList<Move> getAllValidMoves() {
        ArrayList<Move> validMoves = new ArrayList<>();
        ArrayList<Piece> piecesCopy = new ArrayList<>(pieceList);

        for (Piece piece : piecesCopy) {
            if (piece == null || piece.color != board.colorToMove)
                continue;
            generateMovesForPiece(piece, validMoves);

        }
        return validMoves;
    }

    public void generateMovesForPiece(Piece piece, ArrayList<Move> validMoves) {
        int square = BitBoard.SquareToIndex(piece.row, piece.col);

        switch (piece.name) {
            case "Pawn" -> generatePawnMoves(piece, square, validMoves);
            case "Knight" -> generateKnightMoves(piece, square, validMoves);
            case "King" -> generateKingMoves(piece, square, validMoves);
            case "Bishop" -> generateSlidingMoves(piece, AttackTables.BISHOP_DIRECTIONS, validMoves);
            case "Rook" -> generateSlidingMoves(piece, AttackTables.ROOK_DIRECTIONS, validMoves);
            case "Queen" -> generateSlidingMoves(piece, AttackTables.QUEEN_DIRECTIONS, validMoves);
        }
    }

    public void generatePawnMoves(Piece piece, int index, ArrayList<Move> validMoves) {
        long attacks = (piece.color == 0) ? AttackTables.calculateWhitePawnAttacks(index)
                                            : AttackTables.calculateBlackPawnAttacks(index);
        generateMovesFromBitBoard(piece, attacks, validMoves, true);

        // Forward push
        generatePawnPushes(piece, validMoves);
    }

    public void generatePawnPushes(Piece piece, ArrayList<Move> validMoves) {
        int forward = (piece.color == 0) ? -1 : 1;
        int nr = piece.row + forward;

        if (nr >= 0 && nr < 8 && board.getPiece(piece.col, nr) == null) {
            addMoveIfValid(piece, piece.col, nr, validMoves);

            if (piece.isFirstMove) {
                int nr2 = nr + forward;
                if (nr2 >= 0 && nr2 < 8 && board.getPiece(piece.col, nr2) == null)
                    addMoveIfValid(piece, piece.col, nr2, validMoves);
            }
        }
    }

    public void generateKnightMoves(Piece piece, int index, ArrayList<Move> validMoves) {
        long attacks = AttackTables.calculateKnightAttacks(index);
        generateMovesFromBitBoard(piece, attacks, validMoves, false);
    }

    public void generateKingMoves(Piece piece, int index, ArrayList<Move> validMoves) {
        long attacks = AttackTables.calculateKingAttacks(index);
        generateMovesFromBitBoard(piece, attacks, validMoves, false);
    }

    public void generateMovesFromBitBoard(Piece piece, long attacks, ArrayList<Move> validMoves, boolean capture) {
        long mask = attacks;
        while (mask != 0) {
            int index = Long.numberOfTrailingZeros(mask);
            mask &= (mask - 1);
            int nr = index / 8;
            int nc = index % 8;
            Piece target = board.getPiece(nc, nr);

            if (capture) {
                if (target != null && target.color != piece.color)
                    addMoveIfValid(piece, nc, nr, validMoves);
                else if (board.scanner.enPassantEnable && nc == board.scanner.enPassantCol)
                    addMoveIfValid(piece, nc, nr , validMoves);
            } else {
                // For knights/kings - empty squares OR captures
                if (target == null || target.color != piece.color)
                    addMoveIfValid(piece, nc, nr, validMoves);
            }
        }
    }

    public void generateSlidingMoves(Piece piece, int[] directions, ArrayList<Move> validMoves) {
        int index = BitBoard.SquareToIndex(piece.row, piece.col);

        for (int dir : directions) {
            int prev = index;
            int current = index + dir;
            while (current >= 0 && current < 64) {
                if (!BitBoard.isValidDirection(prev, current, dir))
                    break;

                int nr = current / 8;
                int nc = current % 8;
                Piece target = board.getPiece(nc, nr);

                if (target == null)
                    addMoveIfValid(piece, nc, nr, validMoves);
                else {
                    // Break after finding blocking piece, only add if capture
                    if (target.color != piece.color)
                        addMoveIfValid(piece, nc, nr, validMoves);
                    break;
                }
                prev = current;
                current += dir;
            }
        }
    }

    private void addMoveIfValid(Piece piece, int col, int row, ArrayList<Move> validMoves) {
        Move move = new Move(board, piece, col, row);

        if (board.scanner.isValidMove(move)) {
            validMoves.add(move);
        }
    }

    private String debugWhyMoveInvalid(Move move) {
        // Check each validation step from Scanner.isValidMove() in order:

        // 1. Check team collision
        if (board.checkTeam(move.piece, move.capture)) {
            return "Cannot capture own piece";
        }

        // 2. Check if it's the right player's turn
        if (!(move.piece.color == board.colorToMove)) {
            return "Not this piece's turn to move (colorToMove=" + board.colorToMove +
                    ", piece.color=" + move.piece.color + ")";
        }

        // 3. Check if the piece can legally move to that square
        if (!(move.piece.isValidPieceMove(move.newCol, move.newRow))) {
            return "Piece cannot move to that square (violates piece movement rules)";
        }

        // 4. Check for collision/blocking
        if (move.piece.checkForCollision(move.newCol, move.newRow)) {
            return "Path is blocked by another piece";
        }

        // 5. Check if move would leave king in check
        if (board.scanner.wouldBeInCheck(move)) {
            return "Move would leave own king in check";
        }

        return "Unknown reason (shouldn't reach here)";
    }
}
