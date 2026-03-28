package Pieces;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

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

    public BufferedImage getImage(String imagePath) {
        BufferedImage image = null;
        java.io.InputStream is = getClass().getResourceAsStream(imagePath);
        if (is == null) {
            System.err.println("Resource not found: " + imagePath);
            image = new BufferedImage(Board.SQUARE_SIZE, Board.SQUARE_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.MAGENTA);
            g.fillRect(0, 0, Board.SQUARE_SIZE, Board.SQUARE_SIZE);
            g.setColor(Color.BLACK);
            g.drawString("?", Board.SQUARE_SIZE/2, Board.SQUARE_SIZE/2);
            g.dispose();
            return image;
        }

        try {
            image = ImageIO.read(is);
            if (image == null) {
                image = new BufferedImage(Board.SQUARE_SIZE, Board.SQUARE_SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = image.createGraphics();
                g.setColor(Color.MAGENTA);
                g.fillRect(0, 0, Board.SQUARE_SIZE, Board.SQUARE_SIZE);
                g.setColor(Color.BLACK);
                g.drawString("?", Board.SQUARE_SIZE/2, Board.SQUARE_SIZE/2);
                g.dispose();
            }
        }
        catch(IOException | IllegalArgumentException e) {
           e.printStackTrace();
           image = new BufferedImage(Board.SQUARE_SIZE, Board.SQUARE_SIZE, BufferedImage.TYPE_INT_ARGB);
        }
        return image;
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

}
