package toorla.visitor;

import toorla.ast.Program;
import toorla.ast.declaration.classDecs.ClassDeclaration;
import toorla.ast.declaration.classDecs.EntryClassDeclaration;
import toorla.ast.declaration.classDecs.classMembersDecs.ClassMemberDeclaration;
import toorla.ast.declaration.classDecs.classMembersDecs.FieldDeclaration;
import toorla.ast.declaration.classDecs.classMembersDecs.MethodDeclaration;
import toorla.ast.declaration.localVarDecs.ParameterDeclaration;
import toorla.ast.expression.*;
import toorla.ast.expression.binaryExpression.*;
import toorla.ast.expression.unaryExpression.Neg;
import toorla.ast.expression.unaryExpression.Not;
import toorla.ast.expression.value.BoolValue;
import toorla.ast.expression.value.IntValue;
import toorla.ast.expression.value.StringValue;
import toorla.ast.statement.*;
import toorla.ast.statement.localVarStats.LocalVarDef;
import toorla.ast.statement.localVarStats.LocalVarsDefinitions;
import toorla.ast.statement.returnStatement.Return;
import toorla.symbolTable.SymbolTable;
import toorla.symbolTable.exceptions.ItemAlreadyExistsException;
import toorla.symbolTable.exceptions.ItemNotFoundException;
import toorla.symbolTable.symbolTableItem.ClassSymbolTableItem;
import toorla.symbolTable.symbolTableItem.MethodSymbolTableItem;
import toorla.symbolTable.symbolTableItem.SymbolTableItem;
import toorla.symbolTable.symbolTableItem.varItems.FieldSymbolTableItem;
import toorla.symbolTable.symbolTableItem.varItems.LocalVariableSymbolTableItem;
import toorla.symbolTable.symbolTableItem.varItems.VarSymbolTableItem;
import toorla.types.Type;
import toorla.types.arrayType.ArrayType;
import toorla.types.singleType.*;
import toorla.utilities.graph.Graph;

import java.util.ArrayList;
import java.util.List;

import static toorla.ast.declaration.classDecs.classMembersDecs.AccessModifier.ACCESS_MODIFIER_PRIVATE;

public class TypeChecker implements Visitor<Type> {

    private Graph<String> inheritenceGraph;

    private boolean isLvalue;
    private boolean isInWhile;
    private boolean hasError;

    private String currentClassName;

    public TypeChecker(Graph<String> inheritenceGraph_){
        isLvalue = false;
        isInWhile = false;
        hasError = false;
        inheritenceGraph = inheritenceGraph_;
    }

    public boolean HasError() {
        return hasError;
    }

    public boolean isSubType(Type child, Type parent){

        if(child.toString().equals("(UndefinedType)") || parent.toString().equals("(UndefinedType")) {
            return true;
        }
        if(child.toString().equals(parent.toString()))
            return true;
        else {
            if (child.toString().startsWith("(UserDefinedType") && parent.toString().startsWith("(UserDefinedType"))
                return inheritenceGraph.isParent(((UserDefinedType) child).getClassName(), ((UserDefinedType) parent).getClassName());
            else
                return false;
        }
    }

    @Override
    public Type visit(PrintLine printStat) {
        Type printType = printStat.getArg().accept(this);
        if(!(printType.toString().equals("(UndefinedType)") || printType.toString().equals("(IntType)")
                || printType.toString().equals("(StringType)") || printType.toString().equals("(ArrayType,(IntType))"))) {
            System.out.println("Error:Line:" + printStat.line + ":Type of parameter of print built-in function must be integer , string or array of integer;");
            hasError = true;
        }

        return null;
    }

    @Override
    public Type visit(Assign assignStat) {
        isLvalue = false;
        Type lhs = assignStat.getLvalue().accept(this);

        if(!isLvalue && !lhs.toString().equals("(UndefinedType)")) {
            System.out.println("Error:Line:" + assignStat.line + ":Left hand side expression is not assignable;");
            hasError = true;
        }

        Type rhs = assignStat.getRvalue().accept(this);

        if(rhs.toString().startsWith("(UserDefinedType") && !inheritenceGraph.doesGraphContainNode(((UserDefinedType) rhs).getClassName())){
            System.out.println("Error:Line:" + assignStat.line+":There is no class with name " + ((UserDefinedType) rhs).getClassName() +";");
            rhs = new UndefinedType();
            hasError = true;
        }
        if(lhs.toString().startsWith("(UserDefinedType") && !inheritenceGraph.doesGraphContainNode(((UserDefinedType) lhs).getClassName())){
            System.out.println("Error:Line:" + assignStat.line+":There is no class with name " + ((UserDefinedType) lhs).getClassName() +";");
            lhs = new UndefinedType();
            hasError = true;
        }

        if (!isSubType(rhs,lhs)) {
            System.out.println("Error:Line:" + assignStat.line + ":Type " + rhs.toString() + " can not be assigned to type " + lhs.toString() + ";");
            hasError = true;
        }


        isLvalue = false;
        return null;
    }

    @Override
    public Type visit(Block block) {
        SymbolTable.push(new SymbolTable(SymbolTable.top()));

        List<Statement> stmts = block.body;

        for(int i = 0; i < stmts.size(); i++)
            stmts.get(i).accept(this);

        SymbolTable.pop();

        return null;
    }

    @Override
    public Type visit(Conditional conditional) {
        Type condType = conditional.getCondition().accept(this);
        if(!condType.toString().equals("(BoolType)") && !condType.toString().equals("(UndefinedType)")) {
            System.out.println("Error:Line:" + conditional.getCondition().line + ":Condition type must be bool in Conditional statements;");
            hasError = true;
        }

        SymbolTable.push(new SymbolTable(SymbolTable.top()));
        conditional.getThenStatement().accept(this);
        SymbolTable.pop();

        SymbolTable.push(new SymbolTable(SymbolTable.top()));
        conditional.getElseStatement().accept(this);
        SymbolTable.pop();

        return null;
    }

    @Override
    public Type visit(While whileStat) {

        Type condType = whileStat.expr.accept(this);
        if(!condType.toString().equals("(BoolType)") && !condType.toString().equals("(UndefinedType)")) {
            System.out.println("Error:Line:" + whileStat.expr.line + ":Condition type must be bool in Loop statements;");
            hasError = true;
        }

        SymbolTable.push(new SymbolTable(SymbolTable.top()));
        isInWhile = true;
        whileStat.body.accept(this);
        isInWhile = false;
        SymbolTable.pop();

        return null;
    }

    @Override
    public Type visit(Return returnStat) {

        Type retType = returnStat.getReturnedExpr().accept(this);
        Type methodRetType = null;

        try {
            LocalVariableSymbolTableItem item = (LocalVariableSymbolTableItem) SymbolTable.top().get("var_#ret");
            methodRetType = item.getVarType();
        }
        catch (ItemNotFoundException e){
        }

        if(retType.toString().startsWith("(UserDefinedType") && !inheritenceGraph.doesGraphContainNode(((UserDefinedType) retType).getClassName())){
            System.out.println("Error:Line:" + returnStat.line+":There is no class with name " + ((UserDefinedType) retType).getClassName() +";");
            retType = new UndefinedType();
            hasError = true;
        }

        if(!isSubType(retType,methodRetType)) {
            System.out.println("Error:Line:" + returnStat.line + ":Expression returned by this method must be " + methodRetType.toString() + ";");
            hasError = true;
        }

        return null;
    }

    @Override
    public Type visit(Break breakStat) {

        if(!isInWhile) {
            System.out.println("Error:Line:" + breakStat.line + ":Invalid use of Break, Break must be used as loop statement;");
            hasError = true;
        }

        return null;
    }

    @Override
    public Type visit(Continue continueStat) {

        if(!isInWhile) {
            System.out.println("Error:Line:" + continueStat.line + ":Invalid use of Continue, Continue must be used as loop statement;");
            hasError = true;
        }

        return null;
    }

    @Override
    public Type visit(Skip skip) {
        return null;
    }

    @Override
    public Type visit(LocalVarDef localVarDef) {
        Type rhsType = localVarDef.getInitialValue().accept(this);

        try {
            LocalVariableSymbolTableItem item = new LocalVariableSymbolTableItem(localVarDef.getLocalVarName().getName(),-1);
            item.setVarType(rhsType);
            SymbolTable.top().put(item);
        }
        catch (ItemAlreadyExistsException e){
        }

        return null;
    }

    @Override
    public Type visit(IncStatement incStatement) {
        isLvalue = false;
        Type hsType = incStatement.getOperand().accept(this);

        if(!isLvalue && !hsType.toString().equals("(UndefinedType)")) {
            System.out.println("Error:Line:" + incStatement.line + ":Operand of Inc must be a valid lvalue;");
            hasError = true;
        }
        if(!hsType.toString().equals("(IntType)") && !hsType.toString().equals("(UndefinedType)")) {
            System.out.println("Error:Line:" + incStatement.line + ":Unsupported operand types for " + incStatement.toString() + ";");
            hasError = true;
        }

        isLvalue = false;
        return null;
    }

    @Override
    public Type visit(DecStatement decStatement) {
        isLvalue = false;
        Type hsType = decStatement.getOperand().accept(this);
        if(!isLvalue && !hsType.toString().equals("(UndefinedType)")) {
            System.out.println("Error:Line:" + decStatement.line + ":Operand of Dec must be a valid lvalue;");
            hasError = true;
        }
        if(!hsType.toString().equals("(IntType)") && !hsType.toString().equals("(UndefinedType)")) {
            System.out.println("Error:Line:" + decStatement.line + ":Unsupported operand types for " + decStatement.toString() + ";");
            hasError = true;
        }

        isLvalue = false;
        return null;
    }

    @Override
    public Type visit(Plus plusExpr) {
        Type lhsType = plusExpr.getLhs().accept(this);
        Type rhsType = plusExpr.getRhs().accept(this);

        if((!lhsType.toString().equals("(IntType)") && !lhsType.toString().equals("(UndefinedType)")) ||
                (!rhsType.toString().equals("(IntType)") && !rhsType.toString().equals("(UndefinedType)"))) {
            System.out.println("Error:Line:" + plusExpr.line + ":Unsupported operand types for " + plusExpr.toString() + ";");
            isLvalue = false;
            hasError = true;
            return new UndefinedType();
        }

        isLvalue = false;

        return new IntType();
    }

    @Override
    public Type visit(Minus minusExpr) {
        Type lhsType = minusExpr.getLhs().accept(this);
        Type rhsType = minusExpr.getRhs().accept(this);

        if((!lhsType.toString().equals("(IntType)") && !lhsType.toString().equals("(UndefinedType)")) ||
                (!rhsType.toString().equals("(IntType)") && !rhsType.toString().equals("(UndefinedType)"))) {
            System.out.println("Error:Line:" + minusExpr.line + ":Unsupported operand types for " + minusExpr.toString() + ";");
            isLvalue = false;
            hasError = true;
            return new UndefinedType();
        }
        isLvalue = false;

        return new IntType();
    }

    @Override
    public Type visit(Times timesExpr) {
        Type lhsType = timesExpr.getLhs().accept(this);
        Type rhsType = timesExpr.getRhs().accept(this);

        if((!lhsType.toString().equals("(IntType)") && !lhsType.toString().equals("(UndefinedType)")) ||
                (!rhsType.toString().equals("(IntType)") && !rhsType.toString().equals("(UndefinedType)"))) {
            System.out.println("Error:Line:" + timesExpr.line + ":Unsupported operand types for " + timesExpr.toString() + ";");
            isLvalue = false;
            hasError = true;
            return new UndefinedType();
        }

        isLvalue = false;
        return new IntType();
    }

    @Override
    public Type visit(Division divExpr) {
        Type lhsType = divExpr.getLhs().accept(this);
        Type rhsType = divExpr.getRhs().accept(this);

        if((!lhsType.toString().equals("(IntType)") && !lhsType.toString().equals("(UndefinedType)")) ||
                (!rhsType.toString().equals("(IntType)") && !rhsType.toString().equals("(UndefinedType)"))) {
            System.out.println("Error:Line:" + divExpr.line + ":Unsupported operand types for " + divExpr.toString() + ";");
            isLvalue = false;
            hasError = true;
            return new UndefinedType();
        }

        isLvalue = false;
        return new IntType();
    }

    @Override
    public Type visit(Modulo moduloExpr) {
        Type lhsType = moduloExpr.getLhs().accept(this);
        Type rhsType = moduloExpr.getRhs().accept(this);

        if((!lhsType.toString().equals("(IntType)") && !lhsType.toString().equals("(UndefinedType)")) ||
                (!rhsType.toString().equals("(IntType)") && !rhsType.toString().equals("(UndefinedType)"))) {
            System.out.println("Error:Line:" + moduloExpr.line + ":Unsupported operand types for " + moduloExpr.toString() + ";");
            isLvalue = false;
            hasError = true;
            return new UndefinedType();
        }

        isLvalue = false;
        return new IntType();
    }

    @Override
    public Type visit(Equals equalsExpr) {
        Type lhsType = equalsExpr.getLhs().accept(this);
        Type rhsType = equalsExpr.getRhs().accept(this);

        if(lhsType.toString().equals(rhsType.toString()) || lhsType.toString().equals("(UndefinedType)") || rhsType.toString().equals("(UndefinedType)")) {
            isLvalue = false;
            return new BoolType();
        }

        System.out.println("Error:Line:" + equalsExpr.line + ":Unsupported operand types for " + equalsExpr.toString() + ";");
        isLvalue = false;
        hasError = true;
        return new UndefinedType();
    }

    @Override
    public Type visit(GreaterThan gtExpr) {
        Type lhsType = gtExpr.getLhs().accept(this);
        Type rhsType = gtExpr.getRhs().accept(this);

        if((!lhsType.toString().equals("(IntType)") && !lhsType.toString().equals("(UndefinedType)")) ||
                (!rhsType.toString().equals("(IntType)") && !rhsType.toString().equals("(UndefinedType)"))) {
            System.out.println("Error:Line:" + gtExpr.line + ":Unsupported operand types for " + gtExpr.toString() + ";");
            isLvalue = false;
            hasError = true;
            return new UndefinedType();
        }

        isLvalue = false;
        return new BoolType();
    }

    @Override
    public Type visit(LessThan lessThanExpr) {
        Type lhsType = lessThanExpr.getLhs().accept(this);
        Type rhsType = lessThanExpr.getRhs().accept(this);

        if((!lhsType.toString().equals("(IntType)") && !lhsType.toString().equals("(UndefinedType)")) ||
                (!rhsType.toString().equals("(IntType)") && !rhsType.toString().equals("(UndefinedType)"))) {
            System.out.println("Error:Line:" + lessThanExpr.line + ":Unsupported operand types for " + lessThanExpr.toString() + ";");
            isLvalue = false;
            hasError = true;
            return new UndefinedType();
        }

        isLvalue = false;
        return new BoolType();
    }

    @Override
    public Type visit(And andExpr) {
        Type lhsType = andExpr.getLhs().accept(this);
        Type rhsType = andExpr.getRhs().accept(this);

        if((!lhsType.toString().equals("(BoolType)") && !lhsType.toString().equals("(UndefinedType)")) ||
                (!rhsType.toString().equals("(BoolType)") && !rhsType.toString().equals("(UndefinedType)"))) {
            System.out.println("Error:Line:" + andExpr.line + ":Unsupported operand types for " + andExpr.toString() + ";");
            isLvalue = false;
            hasError = true;
            return new UndefinedType();
        }

        isLvalue = false;
        return new BoolType();
    }

    @Override
    public Type visit(Or orExpr) {
        Type lhsType = orExpr.getLhs().accept(this);
        Type rhsType = orExpr.getRhs().accept(this);

        if((!lhsType.toString().equals("(BoolType)") && !lhsType.toString().equals("(UndefinedType)")) ||
                (!rhsType.toString().equals("(BoolType)") && !rhsType.toString().equals("(UndefinedType)"))) {
            System.out.println("Error:Line:" + orExpr.line + ":Unsupported operand types for " + orExpr.toString() + ";");
            isLvalue = false;
            hasError = true;
            return new UndefinedType();
        }

        isLvalue = false;
        return new BoolType();
    }

    @Override
    public Type visit(Neg negExpr) {
        Type hsType = negExpr.getExpr().accept(this);

        if(hsType.toString().equals("(IntType)") || hsType.toString().equals("(UndefinedType)")) {
            isLvalue = false;
            return new IntType();
        }

        System.out.println("Error:Line:" + negExpr.line + ":Unsupported operand types for " + negExpr.toString() + ";");
        isLvalue = false;
        hasError = true;
        return new UndefinedType();
    }

    @Override
    public Type visit(Not notExpr) {
        Type hsType = notExpr.getExpr().accept(this);

        if(hsType.toString().equals("(BoolType)") || hsType.toString().equals("(UndefinedType)")) {
            isLvalue = false;
            return new BoolType();
        }

        System.out.println("Error:Line:" + notExpr.line + ":Unsupported operand types for " + notExpr.toString() + ";");
        isLvalue = false;
        hasError = true;
        return new UndefinedType();
    }

    @Override
    public Type visit(MethodCall methodCall) {
        boolean isSelf = false;
        List<Type> argTypes = new ArrayList<>();
        Type retType = null;

        if(methodCall.getInstance().toString().equals("(Self)"))
            isSelf = true;


        Type instanceType = methodCall.getInstance().accept(this);

        if(!instanceType.toString().startsWith("(UserDefined") && !isSelf && !instanceType.toString().equals("(UndefinedType)")) {
            System.out.println("Error:Line:" + methodCall.line + ":Unsupported operand types for " + methodCall.toString() + ";");
            hasError = true;
            isLvalue = false;
            return new UndefinedType();
        }

        if(!isSelf && instanceType.toString().equals("(UndefinedType)")) {
            isLvalue = false;
            return new UndefinedType();
        }

        if(instanceType.toString().startsWith("(UserDefined")){
            String className = ((UserDefinedType)instanceType).getClassName();

            try{
                ClassSymbolTableItem item = (ClassSymbolTableItem) SymbolTable.root.get("class_" + className);
                SymbolTable.push(item.getSymbolTable());
            }
            catch (ItemNotFoundException e){
            }
        }


        try {
            MethodSymbolTableItem methodItem = (MethodSymbolTableItem) SymbolTable.top().get("method_" + methodCall.getMethodName().getName());

            List<Type> orgArgTypes = methodItem.getArgumentsTypes();

            if(instanceType.toString().startsWith("(UserDefined"))
                SymbolTable.pop();

            for(Expression exp:methodCall.getArgs())
                argTypes.add(exp.accept(this));

            if(argTypes.size() != orgArgTypes.size()){
                if(instanceType.toString().startsWith("(UserDefined")) {
                    System.out.println("Error:Line:" + methodCall.line + ":There is no method with name " + methodCall.getMethodName().getName() +
                            " with such parameters in class " + ((UserDefinedType) instanceType).getClassName() +";");
                    hasError = true;
                }
                else{
                    System.out.println("Error:Line:" + methodCall.line + ":There is no method with name " + methodCall.getMethodName().getName() +
                            " with such parameters in class " + currentClassName +";");
                    hasError = true;
                }
                isLvalue = false;
                return new UndefinedType();
            }

            for(int i = 0; i < argTypes.size(); i++){
                if(argTypes.get(i).toString().startsWith("(UserDefinedType") &&!inheritenceGraph.doesGraphContainNode(((UserDefinedType) argTypes.get(i)).getClassName())) {
                    System.out.println("Error:Line:" + methodCall.line+":There is no class with name " + ((UserDefinedType) argTypes.get(i)).getClassName() + ";");
                    isLvalue = false;
                    hasError = true;
                    return new UndefinedType();
                }

                if(!isSubType(argTypes.get(i),orgArgTypes.get(i))){
                    if(instanceType.toString().startsWith("(UserDefined")) {
                        System.out.println("Error:Line:" + methodCall.line + ":There is no method with name " + methodCall.getMethodName().getName() +
                                " with such parameters in class " + ((UserDefinedType) instanceType).getClassName() +";");
                        hasError = true;
                    }
                    else{
                        System.out.println("Error:Line:" + methodCall.line + ":There is no method with name " + methodCall.getMethodName().getName() +
                                " with such parameters in class " + currentClassName +";");
                        hasError = true;
                    }
                    isLvalue = false;
                    return new UndefinedType();
                }
            }

            if(!isSelf && methodItem.getAccessModifier().equals(ACCESS_MODIFIER_PRIVATE)){
                System.out.println("Error:Line:" + methodCall.line + ":Illegal access to Method " + methodCall.getMethodName().getName() + "of an object of Class" + ((UserDefinedType) instanceType).getClassName() + ";");
                isLvalue = false;
                hasError = true;
                return new UndefinedType();
            }

            isLvalue = false;
            return methodItem.getReturnType();

        }
        catch (ItemNotFoundException e){
            if(instanceType.toString().startsWith("(UserDefined")) {
                System.out.println("Error:Line:" + methodCall.line + ":There is no method with name " + methodCall.getMethodName().getName() +
                        " with such parameters in class " + ((UserDefinedType) instanceType).getClassName() +";");
                hasError = true;
            }
            else{
                System.out.println("Error:Line:" + methodCall.line + ":There is no method with name " + methodCall.getMethodName().getName() +
                        " with such parameters in class " + currentClassName +";");
                hasError = true;
            }
            isLvalue = false;
            return new UndefinedType();
        }
    }

    @Override
    public Type visit(Identifier identifier) {
        isLvalue = true;
        try {
            VarSymbolTableItem item = (VarSymbolTableItem) SymbolTable.top().get("var_" + identifier.getName());
            return item.getVarType();
        }
        catch (ItemNotFoundException e){
            System.out.println("Error:Line:" + identifier.line + ":Variable " + identifier.getName() + " is not declared yet in this Scope;");
            hasError = true;
            return new UndefinedType();
        }
    }

    @Override
    public Type visit(Self self) {
        isLvalue = false;
        return new UserDefinedType(new ClassDeclaration(new Identifier(currentClassName)));
    }

    @Override
    public Type visit(IntValue intValue) {
        isLvalue = false;
        return new IntType();
    }

    @Override
    public Type visit(NewArray newArray) {
        SingleType arrayType = newArray.getType();

        Type indexType = newArray.getLength().accept(this);
        if(!indexType.toString().equals("(IntType)") && !indexType.toString().equals("(UndefinedType)")) {
            hasError = true;
            System.out.println("Error:Line:" + newArray.line + ":Size of an array must be of type integer;");
        }
        if( arrayType.toString().startsWith("(UserDefined")) {
            try {
                SymbolTable.root.get("class_" + arrayType.getClass().getName());
            } catch (ItemNotFoundException e) {
                System.out.println("Error:Line:" + newArray.line + ":There is no class with name "+ ((UserDefinedType) arrayType).getClassName() +";");//TODO
                hasError = true;
                isLvalue = false;
                return new UndefinedType();
            }
        }

        isLvalue = false;
        return new ArrayType(arrayType);
    }

    @Override
    public Type visit(BoolValue booleanValue) {
        isLvalue = false;
        return new BoolType();
    }

    @Override
    public Type visit(StringValue stringValue) {
        isLvalue = false;
        return new StringType();
    }

    @Override
    public Type visit(NewClassInstance newClassInstance) {

        try {
            SymbolTableItem item = SymbolTable.root.get("class_" + newClassInstance.getClassName().getName());
            isLvalue = false;
            return new UserDefinedType(new ClassDeclaration(new Identifier(item.getName())));
        }
        catch (ItemNotFoundException e){
            System.out.println("Error:Line:" + newClassInstance.line + ":There is no class with name " + newClassInstance.getClassName().getName() +";");
            isLvalue = false;
            hasError = true;
            return new UndefinedType();
        }

    }

    @Override
    public Type visit(FieldCall fieldCall) {
        isLvalue = true;
        boolean isSelf = false;

        if(fieldCall.getInstance().toString().equals("(Self)"))
            isSelf = true;


        Type instanceType = fieldCall.getInstance().accept(this);

        if(instanceType.toString().startsWith("(ArrayType") && fieldCall.getField().getName().equals("length")){
            System.out.println("Error:Line:" + fieldCall.line + ":Unsupported operand types for " + fieldCall.toString() + ";");//TODO
            hasError = true;
            return new IntType();
        }

        if(!instanceType.toString().startsWith("(UserDefined") && !isSelf && !instanceType.toString().equals("(UndefinedType)")) {
            return new UndefinedType();
        }

        if(!isSelf && instanceType.toString().equals("(UndefinedType)"))
            return new UndefinedType();

        if(instanceType.toString().startsWith("(UserDefined")){
            String className = ((UserDefinedType)instanceType).getClassName();

            try{
                ClassSymbolTableItem item = (ClassSymbolTableItem) SymbolTable.root.get("class_" + className);
                SymbolTable.push(item.getSymbolTable());
            }
            catch (ItemNotFoundException e){
            }
        }


        try {
            FieldSymbolTableItem fieldItem = (FieldSymbolTableItem) SymbolTable.top().get("var_" + fieldCall.getField().getName());

            if(instanceType.toString().startsWith("(UserDefined"))
                SymbolTable.pop();

            if(!isSelf && fieldItem.getAccessModifier().equals(ACCESS_MODIFIER_PRIVATE)){
                System.out.println("Error:Line:" + fieldCall.line + ":Illegal access to Field " + fieldCall.getField().getName() +
                        " of an object of Class " + ((UserDefinedType) instanceType).getClassName() +";");
                hasError = true;
                return new UndefinedType();
            }


            return fieldItem.getVarType();

        }
        catch (ItemNotFoundException e){
            if(instanceType.toString().startsWith("(UserDefined")) {
                System.out.println("Error:Line:" + fieldCall.line + ":There is no Field with name " + fieldCall.getField().getName() +
                        " with in class " + ((UserDefinedType) instanceType).getClassName() + ";");
                hasError = true;
            }
            else {
                System.out.println("Error:Line:" + fieldCall.line + ":There is no Field with name " + fieldCall.getField().getName() +
                        " with in class " + currentClassName + ";");
                hasError = true;
            }
            return new UndefinedType();
        }

    }

    @Override
    public Type visit(ArrayCall arrayCall) {
        isLvalue = true;

        Type indexType = arrayCall.getIndex().accept(this);
        Type arrayType = arrayCall.getInstance().accept(this);

        if(!indexType.toString().equals("(IntType)") && !indexType.toString().equals("(UndefinedType)")) {
            System.out.println("Error:Line:" + arrayCall.line + ":Index of an array must be of type integer;");
            hasError = true;
        }

        if(!arrayType.toString().startsWith("(ArrayType,") && !arrayType.toString().equals("(UndefinedType)")){
            System.out.println("Error:Line:" + arrayCall.line + ":Unsupported operand types for " + arrayCall.toString() +";");
            hasError = true;
            return new UndefinedType();
        }

        if(arrayType.toString().equals("(UndefinedType)"))
            return new UndefinedType();

        return ((ArrayType)arrayType).getSingleType();
    }

    @Override
    public Type visit(NotEquals notEquals) {
        Type lhsType = notEquals.getLhs().accept(this);
        Type rhsType = notEquals.getRhs().accept(this);

        if(lhsType.toString().equals(rhsType.toString()) || lhsType.toString().equals("(UndefinedType)") || rhsType.toString().equals("(UndefinedType)")) {
            isLvalue = false;
            return new BoolType();
        }

        System.out.println("Error:Line:" + notEquals.line + ":Unsupported operand types for " + notEquals.toString() +";");
        hasError = true;
        isLvalue = false;
        return new UndefinedType();
    }

    @Override
    public Type visit(ClassDeclaration classDeclaration) {

        String className = classDeclaration.getName().getName();
        String parentName = classDeclaration.getParentName().getName();

        try {
            SymbolTable.push(((ClassSymbolTableItem) SymbolTable.root.get("class_" + className)).getSymbolTable());
            currentClassName = className;
        }
        catch (ItemNotFoundException e){
        }

        if(inheritenceGraph.isParent(className,className)) {
            System.out.println("Error:Line:" + classDeclaration.getName().line + ":There is inheritence loop for class with name " + className + ";");
            hasError = true;
        }

        if(parentName != null && !inheritenceGraph.doesGraphContainNode(parentName)) {
            System.out.println("Error:Line:" + classDeclaration.getName().line + ":There is no parent class with name " + parentName + ";");
            hasError = true;
        }

        for(ClassMemberDeclaration mem:classDeclaration.getClassMembers()){
            mem.accept(this);
        }

        SymbolTable.pop();
        return null;
    }

    @Override
    public Type visit(EntryClassDeclaration entryClassDeclaration) {
        String className = entryClassDeclaration.getName().getName();
        String parentName = entryClassDeclaration.getParentName().getName();

        if(inheritenceGraph.isParent(className,className)) {
            System.out.println("Error:Line:" + entryClassDeclaration.getName().line + ":There is inheritence loop for class with name " + className + ";");
            hasError = true;
        }

        if(parentName != null && !inheritenceGraph.doesGraphContainNode(parentName)) {
            System.out.println("Error:Line:" + entryClassDeclaration.getName().line + ":There is no parent class with name " + parentName + ";");
            hasError = true;
        }

        try {
            ClassSymbolTableItem item = (ClassSymbolTableItem) SymbolTable.root.get("class_" + className);
            SymbolTable.push(item.getSymbolTable());
            currentClassName = className;

            try {
                MethodSymbolTableItem mainMethod = (MethodSymbolTableItem) SymbolTable.top().get("method_main");
                if(mainMethod.getAccessModifier().equals(ACCESS_MODIFIER_PRIVATE) || (!mainMethod.getReturnType().toString().equals("(IntType)")) || (mainMethod.getArgumentsTypes().size() != 0)) {
                    System.out.println("Error:Line:" + entryClassDeclaration.getName().line + ":Main method definition is not mathced to the proper definition;");
                    hasError = true;
                }
            }
            catch (ItemNotFoundException e){
                System.out.println("Error:Line:" + entryClassDeclaration.getName().line + ":There is no method main in entry class;");
                hasError = true;
            }
        }
        catch (ItemNotFoundException e){
        }

        for(ClassMemberDeclaration mem:entryClassDeclaration.getClassMembers()){
            mem.accept(this);
        }

        SymbolTable.pop();
        return null;
    }

    @Override
    public Type visit(FieldDeclaration fieldDeclaration) {
        return null;
    }

    @Override
    public Type visit(ParameterDeclaration parameterDeclaration) {
        try {
            LocalVariableSymbolTableItem arg = new LocalVariableSymbolTableItem(parameterDeclaration.getIdentifier().getName(),-1);
            if(parameterDeclaration.getType().toString().startsWith("(UserDefined")){
                try {
                    SymbolTable.root.get("class_" + ((UserDefinedType) parameterDeclaration.getType()).getClassName());
                }
                catch (ItemNotFoundException e){
                    System.out.println("Error:Line:"+parameterDeclaration.getIdentifier().line +":There is no class with name " + ((UserDefinedType) parameterDeclaration.getType()).getClassName() + ";");
                    parameterDeclaration.setType(new UndefinedType());
                    hasError = true;
                }
            }
            arg.setVarType(parameterDeclaration.getType());
            SymbolTable.top().put(arg);
        }
        catch (ItemAlreadyExistsException ee){
        }
        return null;
    }

    @Override
    public Type visit(MethodDeclaration methodDeclaration) {
        SymbolTable.push(new SymbolTable(SymbolTable.top()));

        for(ParameterDeclaration arg:methodDeclaration.getArgs())
            arg.accept(this);

        if(methodDeclaration.getReturnType().toString().startsWith("(UserDefined")) {
            try {
                SymbolTable.root.get("class_" + ((UserDefinedType) methodDeclaration.getReturnType()).getClassName());
            }
            catch (ItemNotFoundException e){
                System.out.println("Error:Line:" + methodDeclaration.getName().line + ":There is no class with name " + ((UserDefinedType) methodDeclaration.getReturnType()).getClassName() + ";");
                methodDeclaration.setReturnType(new UndefinedType());
                hasError = true;
            }
        }

        try{
            LocalVariableSymbolTableItem item = new LocalVariableSymbolTableItem("#ret",-1);
            item.setVarType(methodDeclaration.getReturnType());
            SymbolTable.top().put(item);
        }
        catch (ItemAlreadyExistsException e){
        }

        for(Statement stmt:methodDeclaration.getBody())
            stmt.accept(this);

        SymbolTable.pop();

        return null;
    }

    @Override
    public Type visit(LocalVarsDefinitions localVarsDefinitions) {
        for(LocalVarDef var:localVarsDefinitions.getVarDefinitions())
            var.accept(this);
        return null;
    }

    @Override
    public Type visit(Program program) {

        for(ClassDeclaration classs:program.getClasses())
            classs.accept(this);

        return null;
    }
}
