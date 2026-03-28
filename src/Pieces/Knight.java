package Pieces;

import main.Board;

public class Knight extends Piece {
    public Knight (Board board, int col, int row, int color) {
        super(board);
        this.col = col;
        this.row = row;
        this.x = getX(col);
        this.y = getY(row);
        this.color = color;
        this.type = PieceType.KNIGHT;


        if (color == 0) {
            image = getImage("/resources/Chess_nlt60.png");
        }
        else {
            image = getImage("/resources/Chess_ndt60.png");
        }
    }

    @Override
    public boolean isValidPieceMove(int newCol, int newRow) {
        int colDiff = Math.abs(newCol - this.col);
        int rowDiff = Math.abs(newRow - this.row);

        // L SHAPE
        return (colDiff == 2 && rowDiff == 1) || (colDiff == 1 && rowDiff == 2);
    }
}
