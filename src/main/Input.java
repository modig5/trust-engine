package main;

import static main.Board.*;

import Pieces.Piece;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class Input extends MouseAdapter implements KeyListener {

    private static final int DRAG_THRESHOLD = 5;

    private final Board board;

    private int pressX, pressY;
    private boolean dragging;
    private boolean suppressRelease;
    private Piece pressedPiece;

    public Input(Board board) {
        this.board = board;
    }

    // ---- helpers -----------------------------------------------------------

    private boolean inputLocked() {
        return board.threefold
            || board.isAIThinking
            || board.colorToMove != board.humanColor
            || board.scanner.scanCheckMate(board.colorToMove)
            || board.scanner.insufficientMaterial();
    }

    private int toCol(int pixelX) {
        int col = clamp(pixelX / SQUARE_SIZE, 0, MAX_COLS - 1);
        return board.isBoardFlipped ? (MAX_COLS - 1 - col) : col;
    }

    private int toRow(int pixelY) {
        int row = clamp(pixelY / SQUARE_SIZE, 0, MAX_ROWS - 1);
        return board.isBoardFlipped ? (MAX_ROWS - 1 - row) : row;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void snapToGrid(Piece piece) {
        board.positionPieceOnBoard(piece);
    }

    private boolean isFriendly(Piece piece) {
        return piece != null && piece.color == board.colorToMove;
    }

    private void select(Piece piece) {
        board.selectedPiece = piece;
        suppressRelease = true;
        board.repaint();
    }

    private void deselect() {
        if (board.selectedPiece != null) {
            snapToGrid(board.selectedPiece);
            board.selectedPiece = null;
        }
        board.repaint();
    }

    // ---- mouse events ------------------------------------------------------

    @Override
    public void mousePressed(MouseEvent e) {
        pressX = e.getX();
        pressY = e.getY();
        dragging = false;
        suppressRelease = false;

        if (inputLocked()) return;

        int col = toCol(e.getX());
        int row = toRow(e.getY());
        pressedPiece = board.getPiece(col, row);

        // Nothing selected yet -- pick up a friendly piece.
        if (board.selectedPiece == null) {
            if (isFriendly(pressedPiece)) {
                select(pressedPiece);
            }
            return;
        }

        // Already have a selection -- switch to a different friendly piece.
        if (isFriendly(pressedPiece)) {
            select(pressedPiece);
            return;
        }

        // Clicked an empty/enemy square: keep selection, release will try the move.
        board.repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (inputLocked()) return;
        if (board.selectedPiece == null) return;

        // Suppress the release that pairs with the initial selection click,
        // unless the user dragged (in which case treat it as a drag-and-drop).
        if (suppressRelease && !dragging) {
            suppressRelease = false;
            return;
        }
        suppressRelease = false;

        int col = toCol(e.getX());
        int row = toRow(e.getY());

        // Clicking/releasing on the same square as the selected piece -- no-op.
        if (!dragging && col == board.selectedPiece.col && row == board.selectedPiece.row) {
            return;
        }

        // Attempt the move.
        Move move = new Move(board, board.selectedPiece, col, row);
        if (board.isValidMove(move)) {
            board.makeMove(move, false);
        } else if (dragging) {
            snapToGrid(board.selectedPiece);
        } else {
            deselect();
        }

        dragging = false;
        pressedPiece = null;
        board.repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (inputLocked()) return;
        if (board.selectedPiece == null) return;

        // Only drag the piece that was pressed on, not a previously-selected one.
        if (pressedPiece != board.selectedPiece) return;

        int dx = Math.abs(e.getX() - pressX);
        int dy = Math.abs(e.getY() - pressY);
        if (dx + dy > DRAG_THRESHOLD) {
            dragging = true;
        }

        board.selectedPiece.x = e.getX() - SQUARE_SIZE / 2;
        board.selectedPiece.y = e.getY() - SQUARE_SIZE / 2;
        board.repaint();
    }

    // Get keyboard input for undo/redo move history. (Left-arrow, right-arrow)
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_LEFT:
                board.undoLastMove();
                break;
            case KeyEvent.VK_RIGHT:
                board.redoLastMove();
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        
    }

    @Override
    public void keyTyped(KeyEvent e) {
        
    }
    
}
