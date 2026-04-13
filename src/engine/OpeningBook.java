package engine;

import Pieces.Piece;
import Pieces.PieceType;
import main.Board;
import main.BoardFenHelper;
import main.Move;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static engine.PolyglotRandom.Random64;


public class OpeningBook {

    public static class BookEntry {
        public final int polyMove;
        public final int weight;

        public BookEntry(int polyMove, int weight) {
            this.polyMove = polyMove;
            this.weight = weight;
        }
    }
    
    public HashMap<Long, List<BookEntry>> dictionary = new HashMap<>();

    private final Board board;

    public OpeningBook(String path, Board board) {
        load(path);
        this.board = board;
    }

    // Load entries from path (.bin)
    private void load(String path) {
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(path)))) {
                while (true) {
                    long key;
                    try {
                        key = in.readLong();
                    }
                    catch (java.io.EOFException eof) {
                        break;
                    }
                    int move   = in.readUnsignedShort();
                    int weight = in.readUnsignedShort();
                    in.readInt();

                    dictionary.computeIfAbsent(key, k -> new ArrayList<>()).add(new BookEntry(move, weight));
                }
            }
            catch (IOException e) {
                throw new RuntimeException("Failed to load opening book: " + path, e);
            }
        System.out.println("Opening book loaded: " + dictionary.size() + " positions");
      }

    public Move decodeMove(int polyMove) {
        int toFile   =  polyMove        & 0x7;
        int toRank   = (polyMove >>> 3) & 0x7;
        int fromFile = (polyMove >>> 6) & 0x7;
        int fromRank = (polyMove >>> 9) & 0x7;
        int promo    = (polyMove >>> 12) & 0x7;

        int fromCol = fromFile;
        int fromRow = 7 - fromRank;
        int toCol   = toFile;
        int toRow   = 7 - toRank;

        Piece piece = board.getPiece(fromCol, fromRow);
        if (piece == null) return null;

        // Polyglot encodes castling as king-takes-own-rook (e1h1, e1a1, e8h8, e8a8).
        // So translate to into acutal coordinates
        if (piece.type == PieceType.KING && fromCol == 4) {
            if (fromRow == 7 && toRow == 7 && (toCol == 7 || toCol == 0)) {
                toCol = (toCol == 7) ? 6 : 2;
            } else if (fromRow == 0 && toRow == 0 && (toCol == 7 || toCol == 0)) {
                toCol = (toCol == 7) ? 6 : 2;
            }
        }

        PieceType promotionPiece = switch (promo) {
            case 1 -> PieceType.KNIGHT;
            case 2 -> PieceType.BISHOP;
            case 3 -> PieceType.ROOK;
            case 4 -> PieceType.QUEEN;
            default -> null;
        };

        return new Move(board, piece, toCol, toRow, promotionPiece);
    }

    // Randomize a move, weighted by frequency
    // Each move has weight (how often it's played by masters)
    public BookEntry pickMove() {
        List<BookEntry> entries = dictionary.get(polyGlotToHash());
        if (entries == null || entries.isEmpty()) return null;

        // Roll a random number [0,totalWeight) 
        int totalWeight = 0;
        for (BookEntry e : entries) totalWeight += e.weight;
        int r = (int) (Math.random() * totalWeight);

        // Selects first entry that exceeds the cumulated sum
        int acc = 0;
        for (BookEntry e : entries) {
            acc += e.weight;
            if (r < acc) return e;
        }
        return entries.get(0);
    }

    // 12 * 28 for random pieces    
    // 768 random castle
    // 772 random enPassant
    // 780 randomTurns
    public long polyGlotToHash() {
        long key = 0;

        for (Piece piece : board.pieceList) {
            int polyPiece = polyGlotPieceIndex(piece.type, piece.color);
            int polySquare = 8 * (7 - piece.row) + piece.col; // row 0 is rank 8
            key ^= Random64[64 * polyPiece + polySquare];
        }

        // Castling rights begin at index 768
        String castling = BoardFenHelper.computeCastlingString(board);
        if (castling.indexOf('K') >= 0) key ^= Random64[768 + 0];
        if (castling.indexOf('Q') >= 0) key ^= Random64[768 + 1];
        if (castling.indexOf('k') >= 0) key ^= Random64[768 + 2];
        if (castling.indexOf('q') >= 0) key ^= Random64[768 + 3];

        // En passant rights begin at index 772
        if (board.scanner.enPassantEnable) {
            int epFile = board.scanner.enPassantCol;
            // Black captures on row 4, white captures on row 3
            int capturingRow = (board.colorToMove == 0) ? 4 : 3;
            // Check if there's a pawn in position (adjacent cols) to capture en passant
            boolean canCaptureEP = false;
            for (int dc = -1; dc <= 1; dc += 2) {
                int adjacentCol = epFile + dc;
                if (adjacentCol >= 0 && adjacentCol < 8) {
                    Piece adjacentPiece = board.getPiece(adjacentCol, capturingRow);
                    if (adjacentPiece != null && adjacentPiece.type == PieceType.PAWN && adjacentPiece.color == board.colorToMove) {
                        canCaptureEP = true;
                        break;
                    }
                }
            }
            if (canCaptureEP) {
                key ^= Random64[772 + epFile];
            }
        }

        if (board.colorToMove == 0) {
            key ^= Random64[780]; // White to move
        }
        
        return key;
    }

    public int polyGlotPieceIndex(PieceType type, int color) {
        // Polyglot order: bp=0, wp=1, bn=2, wn=3, bb=4, wb=5, br=6, wr=7, bq=8, wq=9, bk=10, wk=11
        int typeIdx = switch (type) {
            case PAWN   -> 0;
            case KNIGHT -> 1;
            case BISHOP -> 2;
            case ROOK   -> 3;
            case QUEEN  -> 4;
            case KING   -> 5;
        };
        return 2 * typeIdx + (1 - color);  // white=+1, black=+0
    }
}
