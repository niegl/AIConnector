package aiconnector.utils.tuple;

public class Quadra<A,B,C,D> extends Triple<A,B,C> {
    public final D d;

    public Quadra(A a, B b, C c, D d) {
        super(a, b, c);
        this.d = d;
    }
}
