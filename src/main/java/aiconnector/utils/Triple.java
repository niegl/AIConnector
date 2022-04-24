package aiconnector.utils;

public class Triple<A,B,C> extends Tuple<A,B> {
    public final C c;

    public Triple(A a, B b, C c) {
        super(a,b);
        this.c = c;
    }

    public static <A,B,C> Triple<A,B,C> of(A a, B b, C c) {
        return new Triple<>(a,b,c);
    }
}
