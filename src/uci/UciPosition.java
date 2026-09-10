package uci;

import engine.MoveGen;
import main.Board;
import main.Move;

import java.util.Arrays;

/** Parse into a new board so invalid input cannot partly replace the current position. */
final class UciPosition {
    static Board parse(String[] tokens) {
        if (tokens.length < 2) throw new IllegalArgumentException("position requires startpos or fen");
        Board board;
        int next;
        if (tokens[1].equals("startpos")) {
            board = new Board();
            next = 2;
        } else if (tokens[1].equals("fen")) {
            if (tokens.length < 8) throw new IllegalArgumentException("FEN requires six fields");
            String[] fen = Arrays.copyOfRange(tokens, 2, 8);
            validateFen(fen);
            board = new Board(String.join(" ", fen));
            next = 8;
        } else throw new IllegalArgumentException("Expected startpos or fen");
        if (next < tokens.length) {
            if (!tokens[next++].equals("moves")) throw new IllegalArgumentException("Expected moves");
            while (next < tokens.length) board.makeMove(parseMove(board, tokens[next++]), true);
        }
        return board;
    }

    private static Move parseMove(Board board, String text) {
        if (!text.matches("[a-h][1-8][a-h][1-8][qrbn]?"))
            throw new IllegalArgumentException("Invalid move: " + text);
        for (Move move : new MoveGen(board).getAllValidMoves()) {
            if (format(move).equals(text)) return move;
        }
        throw new IllegalArgumentException("Illegal move: " + text);
    }

    static String format(Move move) {
        if (move == null) return "0000";
        String text = "" + (char) ('a' + move.col) + (8 - move.row)
                + (char) ('a' + move.newCol) + (8 - move.newRow);
        if (move.promotionPiece != null) text += switch (move.promotionPiece) {
            case QUEEN -> "q";
            case ROOK -> "r";
            case BISHOP -> "b";
            case KNIGHT -> "n";
            default -> throw new IllegalArgumentException("Invalid promotion");
        };
        return text;
    }

    private static void validateFen(String[] fen) {
        String[] ranks = fen[0].split("/", -1);
        if (ranks.length != 8) throw new IllegalArgumentException("FEN requires eight ranks");
        int whiteKings = 0, blackKings = 0;
        for (int row = 0; row < 8; row++) {
            int squares = 0;
            for (char c : ranks[row].toCharArray()) {
                if (c >= '1' && c <= '8') squares += c - '0';
                else if ("prnbqkPRNBQK".indexOf(c) >= 0) {
                    squares++;
                    if (c == 'K') whiteKings++;
                    if (c == 'k') blackKings++;
                    if ((row == 0 || row == 7) && (c == 'p' || c == 'P'))
                        throw new IllegalArgumentException("Unpromoted pawn on final rank");
                } else throw new IllegalArgumentException("Unknown FEN piece");
            }
            if (squares != 8) throw new IllegalArgumentException("FEN rank must contain eight squares");
        }
        if (whiteKings != 1 || blackKings != 1) throw new IllegalArgumentException("FEN requires one king per side");
        if (!fen[1].matches("[wb]") || !fen[2].matches("-|K?Q?k?q?") || fen[2].isEmpty()
                || !fen[3].matches("-|[a-h][36]")) throw new IllegalArgumentException("Invalid FEN state");
        if (Integer.parseInt(fen[4]) < 0 || Integer.parseInt(fen[5]) < 1)
            throw new IllegalArgumentException("Invalid FEN move counters");
    }
}
