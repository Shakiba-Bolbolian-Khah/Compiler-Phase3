package toorla.types.singleType;

import toorla.ast.declaration.classDecs.ClassDeclaration;

public class UserDefinedType extends SingleType {
    private ClassDeclaration typeClass;

    public UserDefinedType(ClassDeclaration cd) {
        typeClass = cd;
    }

    public ClassDeclaration getClassDeclaration() {
        return typeClass;
    }

    public void setClassDeclaration(ClassDeclaration typeClass) {
        this.typeClass = typeClass;
    }

    public String getClassName() {return typeClass.getName().getName();}

    @Override
    public String toString() {
        return "(UserDefined," + typeClass.getName().getName() + ")";
    }

    public String typeName() { return "UserDefined"; }
}
