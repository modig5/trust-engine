package engine;

public class AttackTables {

    // Fixed movement on kings and knights
    public static final int[] KNIGHT_OFFSETS = {-17, -15, -10, -6, 6, 10, 15, 17};
    public static final int[] KING_OFFSETS = {-9, -8, -7, -1, 1, 7, 8, 9};
    public static final int[] ROOK_DIRECTIONS = {8, -8, 1, -1};
    public static final int[] BISHOP_DIRECTIONS = {-9, -7, 7, 9}; // up-left, up-right, down-right, down-left
    public static final int[] QUEEN_DIRECTIONS = {8, -8, 1, -1, -9, -7, 7, 9};

    public static long[] whitePawnAttacks = new long[64];
    public static long[] blackPawnAttacks = new long[64];
    public static long[] bishopAttacks = new long[64];
    public static long[] rookAttacks = new long[64];
    public static long[] knightAttacks = new long[64];
    public static long[] kingAttacks = new long[64];
    public static long[] queenAttacks = new long[64];

    static {
        for (int i = 0; i < 64; i++) {
            whitePawnAttacks[i] = calculateWhitePawnAttacks(i);
            blackPawnAttacks[i] = calculateBlackPawnAttacks(i);
            bishopAttacks[i] = calculateSlidingAttacks(i, BISHOP_DIRECTIONS);
            rookAttacks[i] = calculateSlidingAttacks(i, ROOK_DIRECTIONS);
            knightAttacks[i] = calculateKnightAttacks(i);
            kingAttacks[i] = calculateKingAttacks(i);
            queenAttacks[i] = calculateSlidingAttacks(i, QUEEN_DIRECTIONS);
        }
    }

    public static long calculateSlidingAttacks(int index, int[] directions) {
        long attacks = 0L;

        for (int dir : directions) {
            int current = index;
            while (true) {
                int next = current + dir;
                if ((next < 0) || (next > 63)) break;
                if (!BitBoard.isValidDirection(current, next, dir)) break;
                attacks = BitBoard.setBit(attacks, next);
                current = next;
            }
        }
        return attacks;
    }

    public static long calculateKingAttacks(int index) {
        long attacks = 0L;

        int row = BitBoard.getRow(index);
        int col = BitBoard.getCol(index);

        for (int dr = -1; dr <= 1; dr++) { // DirectionRow
            for (int dc = -1; dc <= 1; dc++) { // DirectionCol
                if (dr == 0 && dc == 0) continue;
                int nr = row + dr;
                int nc = col + dc;
                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8)
                    attacks = BitBoard.setBit(attacks, BitBoard.SquareToIndex(nr,nc));
            }
        }
        return attacks;
    }

    public static long calculateKnightAttacks(int index) {
        long attacks = 0L;
        int row = BitBoard.getRow(index);
        int col = BitBoard.getCol(index);

        int[] dr = {-2,-2,-1,-1,1,1,2,2};
        int[] dc = {-1,1,-2,2,-2,2,-1,1};

        for (int i = 0; i < 8; i++) {
            int nr = row + dr[i];
            int nc = col + dc[i];
            int square = BitBoard.SquareToIndex(nr,nc);
            if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8)
                attacks = BitBoard.setBit(attacks, square);
        }
        return attacks;
    }

    public static long calculateWhitePawnAttacks(int index) {
        long attacks = 0L;
        int row = BitBoard.getRow(index);
        int col = BitBoard.getCol(index);

        if (row > 0) {
            if (col > 0)
                attacks = BitBoard.setBit(attacks, (row - 1) * 8 + (col - 1));
            if (col < 7)
                attacks = BitBoard.setBit(attacks, (row - 1) * 8 + (col + 1));
            }
        return attacks;
    }

    public static long calculateBlackPawnAttacks(int index) {
        long attacks = 0L;
        int row = BitBoard.getRow(index);
        int col = BitBoard.getCol(index);

        if (row < 7) {
            if (col > 0)
                attacks = BitBoard.setBit(attacks, (row + 1) * 8 + (col - 1));
            if (col < 7)
                attacks = BitBoard.setBit(attacks, (row + 1) * 8 + (col + 1));
        }
        return attacks;
    }


    // Unit tests
    public static void main(String[] args) {
        long bitboard = calculateSlidingAttacks(3, BISHOP_DIRECTIONS);
        System.out.println(BitBoard.bitboardSquares(bitboard));
        long bitboardRook = calculateSlidingAttacks(36, ROOK_DIRECTIONS);
        System.out.println(BitBoard.bitboardSquares(bitboardRook));
        long bitboardQueen = calculateSlidingAttacks(36, QUEEN_DIRECTIONS);
        System.out.println(BitBoard.bitboardSquares(bitboardQueen));
        long bitBoardKing = calculateKingAttacks(0);
        System.out.println(BitBoard.bitboardSquares(bitBoardKing));
        long bitBoardKnight = calculateKnightAttacks(35);
        System.out.println(BitBoard.bitboardSquares(bitBoardKnight));
        long bitBoardBlackPawn = calculateBlackPawnAttacks(10);
        System.out.println(BitBoard.bitboardSquares(bitBoardBlackPawn));

    }
}
