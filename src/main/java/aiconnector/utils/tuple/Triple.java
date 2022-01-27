package aiconnector.utils.tuple;

public class Triple<A,B,C> extends Tuple<A,B> {
    public final C c;

    public Triple(A a, B b, C c) {
        super(a,b);
        this.c = c;
    }
}
