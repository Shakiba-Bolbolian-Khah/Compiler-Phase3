package toorla.types;

public class AnonymousType extends Type {
    @Override
    public String toString() {
        return "(Anonymous)";
    }
    public String typeName() { return "Anonymous"; }
}
