package aiconnector.collide;

public class Vec4d {
    public Vec4d(int x1, int y1, int x2, int y2) {
        d4[0] = x1;
        d4[1] = y1;
        d4[2] = x2;
        d4[3] = y2;
    }

    int at(int _Pos) {
        return d4[_Pos];
    }

    private final int[] d4 = new int[4];
}
