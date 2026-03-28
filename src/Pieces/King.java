package Pieces;

import main.Board;
import main.Scanner;

public class King extends Piece {
    Scanner scanner = new Scanner(board);
    public King (Board board, int col, int row, int color) {
        super(board);
        this.col = col;
        this.row = row;
        this.x = getX(col);
        this.y = getY(row);
        this.color = color;
        this.type = PieceType.KING;


        if (color == 0) {
            image = getImage("/resources/Chess_klt60.png");
        }
        else {
            image = getImage("/resources/Chess_kdt60.png");
        }

    }
    @Override
    public boolean isValidPieceMove(int newCol, int newRow) {
        int diffCol = Math.abs(newCol - this.col);
        int diffRow = Math.abs(newRow - this.row);

        if (diffCol == 2 && diffRow == 0) {
            if (newCol == 2 && scanner.canCastleQueenSide(this.color)) return true;
            else if (newCol == 6 && scanner.canCastleKingSide(this.color)) return true;
        }

        return (diffRow == 1 && diffCol == 1) || (diffRow == 0 && diffCol == 1)
                || (diffRow == 1 && diffCol == 0);
    }

}
