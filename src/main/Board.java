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

    public String CLASSIC = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR";
    public String FENTEST = "4k3/8/8/8/5p2/8/4N3/K7";
    public String FENPROMOTION = "4k3/8/8/8/5p2/8/8/K7";
    public String endGame =  "R7/8/8/8/8/2K5/3p2r1/4k3";
    public String repetition = "8/8/8/8/8/2N5/4K3/3k4";

    public String FEN = "";

    public static ArrayList<Piece> pieceList = new ArrayList<>();
    public Piece selectedPiece;
    public int colorToMove = 0;
    public Scanner scanner = new Scanner(this);
    public int lastSquareMoveFrom;
    public int lastSquareMoveTo;

    AI ai = new AI(this);

    public boolean isAIThinking = false;

    // Map for threefold repetition: FEN string -> count of occurrences
    public Map<String, Integer> repetitionMap = new HashMap<>();
    public boolean threefold = false;


    public Board() {
        addPieces(CLASSIC);

        // initialize full FEN
        String initCastlingRights = computeCastlingString();
        String initEnPassant = "-";

        FEN = generateFEN(initEnPassant, initCastlingRights);

        // add first position to repetition map
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

        // simple popup choice
        if (simulate || isAIThinking) {
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


    public Piece createPromotedPiece(int choice, int col, int row, int color) {
        return switch (choice) {
            case 1 -> new Rook(this, col, row, color);
            case 2 -> new Bishop(this, col, row, color);
            case 3 -> new Knight(this, col, row, color);
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

        handleFirstMove(move.piece);
        executeMove(move);
        handleSpecialMoves(move, simulate);
        capture(move);

        selectedPiece = null;

        colorToMove = colorToMove ^ 1;

        if (!isAIThinking && !simulate)
            aiMove();

        updateFEN(move, simulate);


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

        if (isPawnPromotion(move)) {
            undoInfo.wasPromotion = true;
        }

        if (move.piece.name.equals("King") && Math.abs(move.col - move.newCol) == 2)
            undoInfo.wasCastling = true;

        return undoInfo;
    }

    public void undoMove(Move undoInfo) {
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

        // Restore captured piece if any
        if (undoInfo.capture != null) {
            pieceList.add(undoInfo.capture);
        }

        // Restore color to move
        colorToMove = undoInfo.oldColorToMove;
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
            Piece rook = getPiece(3,move.row);
            if (rook != null) {
                rook.col = 0;
                rook.x = rook.col * SQUARE_SIZE;
                rook.y = rook.row * SQUARE_SIZE;
                rook.isFirstMove = true;
            }
        }
        else {
            Piece rook = getPiece(5,move.row);
            if (rook != null) {
                rook.col = 7;
                rook.x = rook.col * SQUARE_SIZE;
                rook.y = rook.row * SQUARE_SIZE;
                rook.isFirstMove = true;
            }
        }

    }


    public void aiMove() {
        if (colorToMove == 1 && !(scanner.scanCheckMate(1))) {
            isAIThinking = true;

            Timer timer = new Timer(500, e -> {
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
            return;
        }

        if (isEnPassant(move)) {
            enPassant(move);
            scanner.enPassantEnable = false;
        }

        // Setup en passant
        else if (move.piece.name.equals("Pawn") && Math.abs(move.piece.row - move.newRow) == 2)
            scanner.enPassantPossible(move);
        else
            scanner.enPassantEnable = false;


        if (isPawnPromotion(move)) {
            promotion(move, simulate);
        }
    }


    public boolean isEnPassant(Move move) {
        return (move.piece.name.equals("Pawn") && move.newCol ==
                scanner.enPassantCol && Math.abs(move.piece.col - move.newCol) == 1) && scanner.enPassantEnable;
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

    // FEN helper to compute castling rights
    private String computeCastlingString() {

        StringBuilder sb = new StringBuilder();

        // White king castling rights
        Piece whiteKing = getPiece(4, 7);
        if (whiteKing != null && "King".equals(whiteKing.name) && whiteKing.isFirstMove) {
            Piece whiteRookA = getPiece(0, 7);
            if (whiteRookA != null && "Rook".equals(whiteRookA.name) && whiteRookA.isFirstMove) 
                sb.append('K');
            Piece whiteRookH = getPiece(7, 7);
            if (whiteRookH != null && "Rook".equals(whiteRookH.name) && whiteRookH.isFirstMove) 
                sb.append('Q');
        }

        Piece blackKing = getPiece(4, 0);
        if (blackKing != null && "King".equals(blackKing.name) && blackKing.isFirstMove) {
            Piece blackRookA = getPiece(0, 0);
            if (blackRookA != null && "Rook".equals(blackRookA.name) && blackRookA.isFirstMove)
                sb.append('k');
            Piece blackRookH = getPiece(7, 0);
            if (blackRookH != null && "Rook".equals(blackRookH.name) && blackRookH.isFirstMove) {
                sb.append('q');
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }


    private String computeEnPassantTarget(int color) {
        if (!scanner.enPassantEnable)
            return "-";
        int EnPassantRow = (color == 0) ? 5 : 2;
        char file = (char) ('a' + scanner.enPassantCol);
        int rank = 8 - EnPassantRow;
        return "" + file + rank;
    }


    public String generateFEN(String enPassantTarget, String castlingRights) {
        StringBuilder fen = new StringBuilder();
        for (int row = 0; row < MAX_ROWS; row++) {
            int emptyCount = 0;
            for (int col = 0; col < MAX_COLS; col++) {
                Piece piece = getPiece(col, row);
                if (piece == null) {
                    emptyCount++;
                } else {
                    if (emptyCount > 0) {
                        fen.append(emptyCount);
                        emptyCount = 0;
                    }
                    char pieceChar = switch (piece.name) {
                        case "Rook" -> 'r';
                        case "Knight" -> 'n';
                        case "Bishop" -> 'b';
                        case "Queen" -> 'q';
                        case "King" -> 'k';
                        case "Pawn" -> 'p';
                        default -> '?';
                    };
                    fen.append(piece.color == 0 ? Character.toUpperCase(pieceChar) : pieceChar);
                }
            }
            if (emptyCount > 0) {
                fen.append(emptyCount);
            }
            if (row < MAX_ROWS - 1) {
                fen.append('/');
            }
        }
        fen.append(" ").append(colorToMove == 0 ? "w" : "b");

        // Castling rights and en passant target square
        fen.append (" ").append(castlingRights == null ? "-" : castlingRights);
        fen.append(" ").append(enPassantTarget == null ? "-" : enPassantTarget);
        fen.append(" 0 1"); // Placeholder for castling and en passant
        return fen.toString();
    }

    public void updateFEN(Move move, boolean simulate) {
        if (simulate)
            return;

        // Compute castling rights and en passant target square based on the move
        String castlingRights = computeCastlingString();
        String enPassantTarget = computeEnPassantTarget(move.piece.color);
        FEN = generateFEN(enPassantTarget, castlingRights);
        updateRepetitionMap(FEN);
    }

    public void updateRepetitionMap(String FEN) {
        int count = repetitionMap.getOrDefault(FEN, 0) + 1;
        repetitionMap.put(FEN, count);
        if (count >= 3) {
            threefold = true;
        }
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
