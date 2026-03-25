package main;

import engine.AI;
import Pieces.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Board extends JPanel {
    public static final int SQUARE_SIZE = 60;
    public static final int MAX_ROWS = 8;
    public static final int MAX_COLS = 8;

    public String FEN = "";

    public static ArrayList<Piece> pieceList = new ArrayList<>();
    public Piece selectedPiece;
    public int colorToMove = 0;
    public Scanner scanner = new Scanner(this);

    // initialize to -1 to indicate no move has been made yet
    public int lastSquareMoveFrom = -1; 
    public int lastSquareMoveTo = -1;

    AI ai = new AI(this);

    public boolean isAIThinking = false;

    // Map for threefold repetition: FEN string -> count of occurrences
    public Map<String, Integer> repetitionMap = new HashMap<>();
    public boolean threefold = false;

    public Board() {
        this("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    public Board(String fullFEN) {
        String[] parts = fullFEN.split(" ");

        // Parse piece placement (field 1)
        addPieces(parts[0]);

        // Parse active color (field 2)
        if (parts.length > 1) {
            colorToMove = parts[1].equals("b") ? 1 : 0;
        }

        // Parse castling rights (field 3) and update piece isFirstMove flags
        String castlingRights = parts.length > 2 ? parts[2] : "-";
        applyCastlingRights(castlingRights);
        applyPawnFirstMoveFlags();

        // Parse en passant target (field 4)
        String enPassantTarget = parts.length > 3 ? parts[3] : "-";
        applyEnPassantTarget(enPassantTarget);

        // Store the full FEN via generateFEN
        FEN = generateFEN(enPassantTarget, castlingRights);

        // Add first position to repetition map
        repetitionMap.put(FEN, 1);

        Input input = new Input(this);
        this.addMouseListener(input);
        this.addMouseMotionListener(input);
    }

    public Piece getPiece(int col, int row) {
       for (Piece piece : pieceList) {
           if (piece.col == col && piece.row == row) {
               return piece;
           }
       } return null;
    }

    public void capture(Move move) {
        pieceList.remove(move.capture);
    }

    public void promotion(Move move, boolean simulate) {
        pieceList.remove(move.piece);

        // If promotionPiece is specified in the move, use it
        if (move.promotionPiece != null) {
            Piece promotedPiece = createPromotedPieceByName(move.promotionPiece, move.newCol, move.newRow, move.piece.color);
            pieceList.add(promotedPiece);
        }
        // Otherwise just promote to queen
        else if (simulate || isAIThinking) {
            Piece promotedPiece = new Queen(this, move.newCol, move.newRow, move.piece.color);
            pieceList.add(promotedPiece);
        }
        else {
            String[] options = {"Queen", "Rook", "Bishop", "Knight"};
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "Choose promotion piece:",
                    "Pawn Promotion",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            Piece promotedPiece = createPromotedPiece(choice, move.newCol, move.newRow, move.piece.color);
            pieceList.add(promotedPiece);
        }
    }

    // For when choosing a piece (interactive)
    public Piece createPromotedPiece(int choice, int col, int row, int color) {
        return switch (choice) {
            case 1 -> new Rook(this, col, row, color);
            case 2 -> new Bishop(this, col, row, color);
            case 3 -> new Knight(this, col, row, color);
            default -> new Queen(this, col, row, color);
        };
    }

    // For when engine decides
    public Piece createPromotedPieceByName(String pieceName, int col, int row, int color) {
        return switch (pieceName) {
            case "Rook" -> new Rook(this, col, row, color);
            case "Bishop" -> new Bishop(this, col, row, color);
            case "Knight" -> new Knight(this, col, row, color);
            default -> new Queen(this, col, row, color);
        };
    }

    public void castling(Move move) {
        if (move.newCol == 2) {
            Piece rook = getPiece(0, move.row);
            if (rook != null) {
                rook.col = 3;
                rook.row = move.row;
                rook.x   = 3 * SQUARE_SIZE;
                rook.y   = move.row * SQUARE_SIZE;
                rook.isFirstMove = false;
            }
        } else {
            Piece rook = getPiece(7, move.row);
            if (rook != null) {
                rook.col = 5;
                rook.row = move.row;
                rook.x   = 5 * SQUARE_SIZE;
                rook.y   = move.row * SQUARE_SIZE;
                rook.isFirstMove = false;
            }
        }
    }

    public void enPassant(Move move) {
        int squareDiff;
        if (move.piece.color == 0)
            squareDiff = 1;
        else
            squareDiff = -1;

        Piece pawn = getPiece(move.newCol, move.newRow + squareDiff);
        move.capture = pawn;
        pieceList.remove(pawn);
    }

    public void firstMove(Piece piece) {
        piece.isFirstMove = false;
    }

    public Move makeMove(Move move, boolean simulate) {
        if (threefold)
            return null;

        Move undoInfo = undoInfoForMove(move);

        if (!simulate) {
            lastSquareMoveFrom = move.col + move.row * MAX_COLS;
            lastSquareMoveTo = move.newCol + move.newRow * MAX_COLS;
        }

        handleFirstMove(move.piece);
        executeMove(move);
        handleSpecialMoves(move, simulate);
        capture(move);

        selectedPiece = null;

        colorToMove = colorToMove ^ 1;

        updateFEN(move, simulate);

        //if (!isAIThinking && !simulate)
        //    aiMove();


        return undoInfo;
    }

    public Move undoInfoForMove(Move move) {
        Move undoInfo = new Move(this, move.piece, move.piece.col, move.piece.row);

        if (isEnPassant(move)) {
            int squareDiff = move.piece.color == 0 ? 1 : -1;
            Piece capturedPawn = getPiece(move.newCol, move.newRow + squareDiff);
            if (capturedPawn != null) {
                undoInfo.capture = capturedPawn;
                undoInfo.wasEnPassant = true;
            }
        }
        else {
            undoInfo.capture = getPiece(move.newCol, move.newRow);
        }

        undoInfo.oldColorToMove = colorToMove;
        undoInfo.newRow = move.newRow;
        undoInfo.newCol = move.newCol;

        undoInfo.firstMove = move.piece.isFirstMove;
        undoInfo.enPassantEnabled = scanner.enPassantEnable;
        undoInfo.enPassantCol = scanner.enPassantCol;
        undoInfo.enPassantRow = scanner.enPassantRow;
        undoInfo.previousFEN = FEN;
        undoInfo.previousThreefold = threefold;

        if (isPawnPromotion(move)) {
            undoInfo.wasPromotion = true;
        }

        if (move.piece.name.equals("King") && Math.abs(move.col - move.newCol) == 2) {
            undoInfo.wasCastling = true;
            // Save rook's isFirstMove state for castling undo
            if (move.newCol == 2) {
                // Queenside castling - rook at a-file
                Piece rook = getPiece(0, move.row);
                if (rook != null) {
                    undoInfo.rookFirstMove = rook.isFirstMove;
                }
            } else {
                // Kingside castling - rook at h-file
                Piece rook = getPiece(7, move.row);
                if (rook != null) {
                    undoInfo.rookFirstMove = rook.isFirstMove;
                }
            }
        }

        return undoInfo;
    }

    public void undoMove(Move undoInfo) {
        // Roll back repetition count for the position produced by this move
        String fenToDecrement = undoInfo.resultingFEN != null ? undoInfo.resultingFEN : FEN;
        int count = repetitionMap.getOrDefault(fenToDecrement, 0);
        // If count is 1 we can decrement, otherwise remove the entry entirely (it was never repeated before)
        if (count > 1) {
            repetitionMap.put(fenToDecrement, count - 1);
        } else {
            repetitionMap.remove(fenToDecrement);
        }

        if (undoInfo.wasPromotion)
            undoPromotion(undoInfo);

        if (undoInfo.wasEnPassant)
            undoEnPassant(undoInfo);

        if (undoInfo.wasCastling)
            undoCastling(undoInfo);

        // Restore piece position
        undoInfo.piece.col = undoInfo.col;
        undoInfo.piece.row = undoInfo.row;
        undoInfo.piece.x = undoInfo.col * SQUARE_SIZE;
        undoInfo.piece.y = undoInfo.row * SQUARE_SIZE;

        undoInfo.piece.isFirstMove = undoInfo.firstMove;
        scanner.enPassantEnable = undoInfo.enPassantEnabled;
        scanner.enPassantCol = undoInfo.enPassantCol;
        scanner.enPassantRow = undoInfo.enPassantRow;

        // Restore captured piece if any (skip for en passant - already handled above)
        if (undoInfo.capture != null && !undoInfo.wasEnPassant) {
            pieceList.add(undoInfo.capture);
        }

        // Restore color to move
        colorToMove = undoInfo.oldColorToMove;
        FEN = undoInfo.previousFEN;
        threefold = undoInfo.previousThreefold;
    }

    public void undoPromotion(Move move) {
        // remove promoted piece and add back pawn
        Piece prom = getPiece(move.newCol, move.newRow);
        if (prom != null) {
            pieceList.remove(prom);
            pieceList.add(move.piece);
        }
    }

    public void undoEnPassant(Move move) {
        // Restore captured pawn
        pieceList.add(move.capture);
    }

    public void undoCastling(Move move) {
        // Move back rook to corresponding side
        if (move.newCol == 2) {
            // Queenside castling - move rook back to a-file
            Piece rook = getPiece(3, move.row);
            if (rook != null) {
                rook.col = 0;
                rook.row = move.row;
                rook.x = rook.col * SQUARE_SIZE;
                rook.y = rook.row * SQUARE_SIZE;
                rook.isFirstMove = move.rookFirstMove;
            }
        }
        else {
            // Kingside castling - move rook back to h-file
            Piece rook = getPiece(5, move.row);
            if (rook != null) {
                rook.col = 7;
                rook.row = move.row;
                rook.x = rook.col * SQUARE_SIZE;
                rook.y = rook.row * SQUARE_SIZE;
                rook.isFirstMove = move.rookFirstMove;
            }
        }
    }

    public void aiMove() {
        if (colorToMove == 1 && !(scanner.scanCheckMate(1))) {
            isAIThinking = true;

            // Timer to make it feel more natural and allow UI to update before move is made
            Timer timer = new Timer(50, e -> {
                ai.makeAIMove();
                isAIThinking = false;
                repaint();
            });
            timer.setRepeats(false);
            timer.start();
        }
    }

    public void handleFirstMove(Piece piece) {
        if (piece.name.equals("Pawn") || piece.name.equals("King") || piece.name.equals("Rook"))
            firstMove(piece);
    }

    public void handleSpecialMoves(Move move, boolean simulate) {
        // Castling
        if (move.piece.name.equals("King") && Math.abs(move.col - move.newCol) == 2) {
            castling(move);
            scanner.enPassantEnable = false;
            return;
        }

        if (isEnPassant(move)) {
            enPassant(move);
            scanner.enPassantEnable = false;
        }

        // Setup en passant
        else if (move.piece.name.equals("Pawn") && Math.abs(move.row - move.newRow) == 2)
            scanner.enPassantPossible(move);
        else
            scanner.enPassantEnable = false;


        if (isPawnPromotion(move)) {
            promotion(move, simulate);
        }
    }

    public boolean isEnPassant(Move move) {
        if (!scanner.enPassantEnable) return false;
        if (!move.piece.name.equals("Pawn")) return false;
        if (Math.abs(move.col - move.newCol) != 1) return false;
        if (move.newCol != scanner.enPassantCol) return false;

        // White pawn on rank 3 capturing to rank 2, or black pawn on rank 4 capturing to rank 5
        if (move.piece.color == 0) {
            return move.row == 3 && move.newRow == 2;
        } else {
            return move.row == 4 && move.newRow == 5;
        }
    }

    public boolean isPawnPromotion(Move move) {
        return move.piece.name.equals("Pawn") &&
                (move.newRow == 0 || move.newRow == 7);
    }

    public void executeMove(Move move) {
        move.piece.col = move.newCol;
        move.piece.row = move.newRow;
        move.piece.x = move.newCol * SQUARE_SIZE;
        move.piece.y = move.newRow * SQUARE_SIZE;
    }

    public boolean checkTeam(Piece p1, Piece p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        else {
            return (p1.color == p2.color);
        }
    }

    public boolean isValidMove(Move move) {
        if (threefold) return false;
        return scanner.isValidMove(move);
    }

    public int countPieces(int color, String name) {
        int count = 0;
        for (Piece piece : pieceList) {
            if (piece.name.equals(name) && piece.color == color)
                count++;
        }
        return count;
    }

    // load position from FEN string
    public void addPieces(String FEN) {
        pieceList.clear();
        int row = 0;
        int col = 0;
        int color;
        for (int i = 0; i < FEN.length(); i++) {
            char ch = FEN.charAt(i);
            if (ch == '/') {
                row++;
                col = 0;
            }
            else if (Character.isDigit(ch)) {
                col += Character.getNumericValue(ch);
            }
            else {
                if (Character.isUpperCase(ch)) {
                    color = 0;
                }
                else color = 1;
                char piece = Character.toLowerCase(ch);

                switch (piece) {
                    case 'r':
                        pieceList.add(new Rook(this,col,row,color));
                        break;
                    case 'n':
                        pieceList.add(new Knight(this,col,row,color));
                        break;
                    case 'b':
                        pieceList.add(new Bishop(this,col,row,color));
                        break;
                    case 'q':
                        pieceList.add(new Queen(this,col,row,color));
                        break;
                    case 'k':
                        pieceList.add(new King(this,col,row,color));
                        break;
                    case 'p':
                        pieceList.add(new Pawn(this,col,row,color));
                        break;
                }
                col++;
            }
        }
    }

    // Apply castling rights from FEN by setting piece isFirstMove flags
    private void applyCastlingRights(String castling) {
        BoardFenHelper.applyCastlingRights(this, castling);
    }

    // If pawns are on a different row set firstMove to false
    private void applyPawnFirstMoveFlags() {
        BoardFenHelper.applyPawnFirstMoveFlags(this);
    }

    // Apply en passant target square from FEN by configuring the scanner
    private void applyEnPassantTarget(String enPassantTarget) {
        BoardFenHelper.applyEnPassantTarget(this, enPassantTarget);
    }

    // FEN helper to compute castling rights
    private String computeCastlingString() {
        return BoardFenHelper.computeCastlingString(this);
    }

    private String computeEnPassantTarget(int color) {
        return BoardFenHelper.computeEnPassantTarget(this, color);
    }

    public String generateFEN(String enPassantTarget, String castlingRights) {
        return BoardFenHelper.generateFEN(this, enPassantTarget, castlingRights);
    }

    public void updateFEN(Move move, boolean simulate) {
        // Compute castling rights and en passant target square based on the move
        String castlingRights = computeCastlingString();
        String enPassantTarget = computeEnPassantTarget(move.piece.color);
        FEN = generateFEN(enPassantTarget, castlingRights);
        move.resultingFEN = FEN;

        int count = repetitionMap.getOrDefault(FEN, 0) + 1;
        repetitionMap.put(FEN, count);
        if (!simulate && count >= 3) {
            threefold = true;
        }
    }

    public void updateRepetitionMap(String FEN) {
        BoardFenHelper.updateRepetitionMap(this, FEN);
    }

    @Override
    public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        for (int i = 0; i < MAX_ROWS; i++) {
            for (int j = 0; j < MAX_COLS; j++) {
                if ((i + j) % 2 == 0) {
                    graphics.setColor(Color.WHITE);
                } else {
                    graphics.setColor(new Color(192, 164, 132));
                }
                graphics.fillRect(j * SQUARE_SIZE, i * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
            }
        }

        // Highlight last played move (from and to squares)
        if (lastSquareMoveFrom >= 0) {
            int fromCol = lastSquareMoveFrom % MAX_COLS;
            int fromRow = lastSquareMoveFrom / MAX_COLS;
            graphics.setColor(new Color(246, 246, 105, 180));
            graphics.fillRect(fromCol * SQUARE_SIZE, fromRow * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
        }

        if (lastSquareMoveTo >= 0) {
            int toCol = lastSquareMoveTo % MAX_COLS;
            int toRow = lastSquareMoveTo / MAX_COLS;
            graphics.setColor(new Color(246, 246, 105, 180));
            graphics.fillRect(toCol * SQUARE_SIZE, toRow * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
        }

        // Highlight selected piece and valid moves
        if (selectedPiece != null) {
            // Selected piece's square
            graphics.setColor(new Color(246, 246, 105, 180));
            graphics.fillRect(selectedPiece.col * SQUARE_SIZE, selectedPiece.row * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);

            // Valid destination squares
            for (int i = 0; i < MAX_ROWS; i++) {
                for (int j = 0; j < MAX_COLS; j++) {
                    if (isValidMove(new Move(this, selectedPiece, j, i))) {
                        graphics.setColor(new Color(66, 127, 46, 166));
                        graphics.fillRect(j * SQUARE_SIZE, i * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
                    }
                }
            }
        }

        for (Piece piece : pieceList) {
            piece.paint(graphics);
        }

        if (scanner.scanCheckMate(colorToMove)) {
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font("Arial", Font.BOLD, 50));
            graphics.drawString(((colorToMove == 0) ? "Black" : "White" ) + " wins!", 100, MAX_ROWS * SQUARE_SIZE / 2);
        }

        if (threefold) {
            graphics.setColor(Color.BLACK);
            graphics.setFont(new Font("Arial", Font.BOLD, 50));
            graphics.drawString("Draw by repetition", 20, MAX_ROWS * SQUARE_SIZE / 2);
        }

    }

    public void display() {
        JFrame frame = new JFrame("Chess Board");
        frame.add(this);
        frame.setLocation(600, 200);
        frame.setSize(MAX_COLS * SQUARE_SIZE + 16, MAX_ROWS * SQUARE_SIZE + 39);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
