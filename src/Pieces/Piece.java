package Pieces;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import main.Board;

import javax.imageio.ImageIO;

public class Piece {
    public BufferedImage image;
    public int color;
    public int x, y;
    public int row, col;
    public PieceType type;
    public boolean isFirstMove = true;

    Board board;

    // Shared by GUI pieces, promotions and pondering copies. Treat as read-only.
    private static final ConcurrentMap<String, BufferedImage> IMAGES = new ConcurrentHashMap<>();

    public BufferedImage getImage(String imagePath) {
        return IMAGES.computeIfAbsent(imagePath, Piece::loadImage);
    }

    private static BufferedImage loadImage(String imagePath) {
        try (InputStream stream = Piece.class.getResourceAsStream(imagePath)) {
            if (stream == null) {
                System.err.println("Resource not found: " + imagePath);
            } else {
                BufferedImage loaded = ImageIO.read(stream);
                if (loaded != null) return loaded;
            }
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
        }

        // Cache the placeholder too, so missing resources are not retried on every copy.
        BufferedImage fallback = new BufferedImage(Board.SQUARE_SIZE, Board.SQUARE_SIZE,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = fallback.createGraphics();
        try {
            graphics.setColor(Color.MAGENTA);
            graphics.fillRect(0, 0, Board.SQUARE_SIZE, Board.SQUARE_SIZE);
            graphics.setColor(Color.BLACK);
            graphics.drawString("?", Board.SQUARE_SIZE / 2, Board.SQUARE_SIZE / 2);
        } finally {
            graphics.dispose();
        }
        return fallback;
    }

    public Piece(Board board) {
        this.board = board;
    }

    public int getX(int col) {
       return col * Board.SQUARE_SIZE;
    }

    public int getY(int row) {
        return row * Board.SQUARE_SIZE;
    }

    public void paint(Graphics graphics) {
       graphics.drawImage(image, x, y, null);
    }

    public boolean isValidPieceMove(int newCol, int newRow) {
        return true;
    }
    public boolean checkForCollision(int newCol, int newRow) {
        return false;
    }

    public Piece copy(Board newBoard) {
        Piece copy = switch (this.type) {
            case PAWN -> new Pawn(newBoard, col, row, color);
            case KNIGHT -> new Knight(newBoard, col, row, color);
            case BISHOP -> new Bishop(newBoard, col, row, color);
            case ROOK -> new Rook(newBoard, col, row, color);
            case QUEEN -> new Queen(newBoard, col, row, color);
            case KING -> new King(newBoard, col, row, color);
        };
        copy.isFirstMove = this.isFirstMove;
        return copy;
    }
}
