package aiconnector.utils.tuple;

import lombok.EqualsAndHashCode;
import lombok.Synchronized;

@EqualsAndHashCode
public class Tuple<A,B> {
    public final A a;
    public final B b;

    public Tuple(A a, B b) {
        this.a = a;
        this.b = b;
    }

    public static <A,B> Tuple<A,B> of(A a, B b) {
        return new Tuple<>(a,b);
    }

    public synchronized Tuple<A,B> clone(){
        return new Tuple<>(a,b);
    }
}
