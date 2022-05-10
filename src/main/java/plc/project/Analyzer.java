package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * See the specification for information about what the different visit
 * methods should do.
 */
public final class Analyzer implements Ast.Visitor<Void> {

    public Scope scope;
    private Ast.Method method;

    public Analyzer(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", "System.out.println", Arrays.asList(Environment.Type.ANY), Environment.Type.NIL, args -> Environment.NIL);
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Void visit(Ast.Source ast) {
        ast.getFields().forEach(this::visit);
        ast.getMethods().forEach(this::visit);

        List<Environment.PlcObject> stuff = new ArrayList<>();
        stuff.add(Environment.create(BigInteger.ZERO));

        try {
            requireAssignable(scope.lookupFunction("main", 0).invoke(stuff).getType(), Environment.Type.INTEGER);
        }
        catch (Exception e) {
            throw new RuntimeException("The function main/0 is not defined in this scope.");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {

        //Defines a variable in the current scope according to the following, also setting it in the Ast (Ast.Field#setVariable).
        //The variable's name and jvmName are both the name of the field.
        //The variable's type is the type registered in the Environment with the same name as the one in the AST.
        //The variable's value is Environment.NIL (since it is not used by the analyzer)
        //The value of the field, if present, must be visited before the variable is defined (otherwise, the field would be used before it was initialized).
        //Additionally, throws a RuntimeException if:
        //The value, if present, is not assignable to the field.
        //For a value to be assignable, it's type must be a subtype of the field's type as defined above.
        //Returns null.
        Environment.Type type = null;
        if(ast.getValue().isPresent()) {
            visit(ast.getValue().get());
        }
        if(ast.getTypeName() == "Boolean") {
            type = Environment.Type.BOOLEAN;
        }
        else if(ast.getTypeName() == "Decimal") {
            type = Environment.Type.DECIMAL;
        }
        else if(ast.getTypeName() == "Integer") {
            type = Environment.Type.INTEGER;
        }
        else if(ast.getTypeName() == "Character") {
            type = Environment.Type.CHARACTER;
        }
        else if(ast.getTypeName() == "String") {
            type = Environment.Type.STRING;
        }
        else if(ast.getTypeName() == "Nil") {
            type = Environment.Type.NIL;
        }
        else if(ast.getTypeName() == "Any") {
            type = Environment.Type.ANY;
        }
        else if(ast.getTypeName() == "Comparable") {
            type = Environment.Type.COMPARABLE;
        }
        else {
            throw new RuntimeException("invalid type name");
        }
        scope.defineVariable(ast.getName(), ast.getName(), type, Environment.NIL);
        ast.setVariable(scope.lookupVariable(ast.getName()));

        requireAssignable(type,ast.getValue().get().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        //Defines a function in the current scope according to the following, also setting it in the Ast (Ast.Method#setFunction).
        //The function's name and jvmName are both the name of the method.
        //The function's parameter types and return type are retrieved from the environment using the corresponding names in the method.
        //Examine the grammar and identify that providing a return type in the method declaration is option.
        // Therefore, if the return type is not provided and thus, not present in the AST, the return type will be Nil.
        //The function's function (such naming much wow) is args -> Environment.NIL, which always returns nil (since it is not used by the analyzer).
        Scope temp = scope;

        List<Environment.Type> paramTypes = new ArrayList<>();
        for(int i = 0; i < ast.getParameterTypeNames().size(); i++) {
            paramTypes.add(Environment.getType(ast.getParameterTypeNames().get(i)));
        }
        if(ast.getReturnTypeName().isPresent()) {
            Environment.Type returnType = Environment.getType(ast.getReturnTypeName().get());
            ast.setFunction(scope.defineFunction(ast.getName(), ast.getName(), paramTypes, returnType, args -> Environment.NIL));
        }
        else {
            ast.setFunction(scope.defineFunction(ast.getName(), ast.getName(), paramTypes, Environment.Type.NIL, args -> Environment.NIL));
        }

        //Then, visits all of the method's statements inside of a new scope containing variables for each parameter.
        // Unlike fields, this is done after the function is defined to allow for recursive functions.
        //Additionally, you will need to somehow coordinate with Ast.Stmt.Return so the expected return type is known (hint: save in a variable).

        scope = new Scope(temp);

        ast.getStatements().forEach(this::visit);

        if(ast.getStatements().isEmpty()) {

        }
        else{
            if(ast.getStatements().get(0) instanceof Ast.Stmt.Return) {
                Ast.Stmt.Return ret = (Ast.Stmt.Return) ast.getStatements().get(0);
            }
        }


        scope = temp;
        //Returns null.
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        //Validates the expression statement.
        // Throws a RuntimeException if:
        //The expression is not an Ast.Expr.Function (since this is the only type of expression that can cause a side effect).
        //Returns null.
        visit(ast.getExpression());

        if(ast.getExpression() instanceof Ast.Expr.Function) {

        }
        else {
            throw new RuntimeException("Invalid expression type");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        //this is the one from dobbins
        // 'LET' identifier (':' identifier)? ('=' expression)? ';

        //Defines a variable in the current scope according to the following:
        //The variable's name and jvmName are both the name in the AST.
        //The variable's type is the type registered in the Environment with the same name as the one in the AST, if present, or else the type of the value.
        //If neither are present this is an error.
        //The variable's value is Environment.NIL (since it is not used by the analyzer).
        //The value of the field, if present, must be visited before the variable is defined (otherwise, the field would be used before it was initialized and also because it's type may be needed to determine the type of the variable).
        //Additionally, throws a RuntimeException if:
        //The value, if present, is not assignable to the variable (see Ast.Field for info).
        //Returns null.

        if(!ast.getTypeName().isPresent() && !ast.getValue().isPresent()) {
            throw new RuntimeException("Need type or value");
        }
        Environment.Type type = null;

        if(ast.getTypeName().isPresent()) {
            type = Environment.getType(ast.getTypeName().get());
        }

        if(ast.getValue().isPresent()) {

            visit(ast.getValue().get()); //visit does not evaluate

            if(type == null) {
                type = ast.getValue().get().getType();
            }

            requireAssignable(type, ast.getValue().get().getType());
        }

        ast.setVariable(scope.defineVariable(ast.getName(), ast.getName(), type, Environment.NIL));

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        //Validates an assignment statement. Throws a RuntimeException if:
        //The receiver is not an access expression (since any other type is not assignable).
        //The value is not assignable to the receiver (see Ast.Field for info).
        //Returns null.
        if(ast.getReceiver() instanceof Ast.Expr.Access)
        {
            // Things are good //
            visit(ast.getReceiver());

            visit(ast.getValue());

            requireAssignable(ast.getReceiver().getType(), ast.getValue().getType());
        }
        else
        {
            throw new RuntimeException("Invalid Types");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        //Validates an if statement. Throws a RuntimeException if:
        //The condition is not of type Boolean.
        //The thenStatements list is empty.
        //After handling the condition, visit the then and else statements inside of a new scope for each one.
        //Returns null.

        visit(ast.getCondition());

        Scope temp = scope;
        requireAssignable(ast.getCondition().getType(), Environment.Type.BOOLEAN);
        if(ast.getThenStatements().isEmpty()) {
            throw new RuntimeException("thenStatements is empty");
        }

        Ast.Expr.Literal truth = new Ast.Expr.Literal(true);
        truth.setType(Environment.Type.BOOLEAN);

        if(ast.getCondition().equals(truth)) {
            scope = new Scope(temp);
            ast.getThenStatements().forEach(this::visit);
        }
        else {
            scope = new Scope(temp);
            ast.getElseStatements().forEach(this::visit);
        }

        scope = temp;

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        Scope temp = scope;
        //Validates a for statement. Throws a RuntimeException if:
        visit(ast.getValue());
        if(!(ast.getValue().getType() == Environment.Type.INTEGER_ITERABLE)) {
            throw new RuntimeException("Invalid type");
        }
        if(ast.getStatements().isEmpty()) {
            throw new RuntimeException("empty statements list");
        }

        scope = new Scope(temp);
        scope.defineVariable(ast.getName(), ast.getName(), Environment.Type.INTEGER, Environment.NIL);
        ast.getStatements().forEach(this::visit);

        scope = temp;

        //The value is not of type IntegerIterable.
        //The statements list is empty.
        //Then, visits all of the for loop's statements in a new scope. This scope should have a variable defined as follows:
        //The variable's name and jvmName are both the name in the AST.
        //The variable's type is Integer.
        //The variable's value is Environment.NIL (since it is not used by the analyzer).
        //Returns null.
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        //Validates a while statement. Throws a RuntimeException if:
        //The value is not of type Boolean.
        //Then, visits all of the while loop's statements in a new scope.
        //Returns null.
        Scope temp = scope;
        visit(ast.getCondition());

        if(ast.getCondition().getType() != Environment.Type.BOOLEAN) {
            throw new RuntimeException("Invalid type");
        }
        else {
            scope = new Scope(temp);
            ast.getStatements().forEach(this::visit);
        }

        scope = temp;

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        //Validates a return statement. Throws a RuntimeException if:
        //The value is not assignable to the return type of the method it in.
        //As hinted in Ast.Method, you will need to coordinate between these methods to accomplish this.
        //Note: This method will only be called as part of visiting a method, since otherwise there would not be a return type to consider.
        //Returns null.
        visit(ast.getValue());
        return null;
        //throw new RuntimeException("invalid");
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        //Validates and sets type of the literal as described below.
        // You will need to make use of instanceof to figure out what type the literal value is (remember to distinguish between the type in our language and the type of the Java object!).
        //Nil, Boolean, Character, String: No additional behavior.
        //Integer: Throws a RuntimeException if the value is out of range of a Java int (32-bit signed int). There are methods in BigInteger that can help with this, but make sure to throw a RuntimeException!
        //Decimal: Throws a RuntimeException if the value is out of range of a Java double value (64-bit signed float). This is a bit trickier than the previous one, but the method you should use here is BigDecimal#doubleValue(). Check the Javadocs to see what happens if the value does not fit into a double and go from there.
        //Returns null.
        if(ast.getLiteral() instanceof BigInteger) {
            BigInteger astVal = (BigInteger) ast.getLiteral();
            if(astVal.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) == 1 || astVal.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) == -1) {
                throw new RuntimeException("Invalid integer");
            }
            ast.setType(Environment.Type.INTEGER);
        }
        else if(ast.getLiteral() instanceof BigDecimal) {
            BigDecimal astVal= (BigDecimal) ast.getLiteral();
            if(astVal.compareTo(BigDecimal.valueOf(Double.MAX_VALUE)) == 1 || astVal.compareTo(BigDecimal.valueOf(Double.MIN_VALUE)) == -1) {
                throw new RuntimeException("Invalid decimal");
            }
            ast.setType(Environment.Type.DECIMAL);
        }
        else if(ast.getLiteral() instanceof Boolean) {
            ast.setType(Environment.Type.BOOLEAN);
        }
        else if(ast.getLiteral() instanceof String) {
            ast.setType(Environment.Type.STRING);
        }
        else if(ast.getLiteral() instanceof Character) {
            ast.setType(Environment.Type.CHARACTER);
        }
        else {
            ast.setType(Environment.Type.NIL);
        }

        return null;
    }


    @Override
    public Void visit(Ast.Expr.Group ast) {
        if(!(ast.getExpression() instanceof Ast.Expr.Binary))
        {
            throw new RuntimeException("Not a Binary Expression!");
        }

        ast.setType(ast.getExpression().getType());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {

        visit(ast.getLeft());
        visit(ast.getRight());

        // AND / OR //
        if(ast.getOperator().equals("AND") || ast.getOperator().equals("OR"))
        {
            ast.setType(Environment.Type.BOOLEAN);

            if(ast.getLeft().getType() != Environment.Type.BOOLEAN || ast.getRight().getType() != Environment.Type.BOOLEAN)
            {
                throw new RuntimeException("Mismatched Types!");
            }
        }
        // < / <= / > / >= / == / != //
        if(ast.getOperator().equals("<") || ast.getOperator().equals("<=") || ast.getOperator().equals(">") || ast.getOperator().equals(">=") || ast.getOperator().equals("==") || ast.getOperator().equals("!="))
        {
            ast.setType(Environment.Type.BOOLEAN);

            if(!(ast.getLeft() instanceof Comparable || ast.getRight() instanceof Comparable))
            {
                throw new RuntimeException("Incorrect Types!");
            }

            if(ast.getLeft().getType() != ast.getRight().getType())
            {
                throw new RuntimeException("Mismatched Types!");
            }
        }
        // + //
        if(ast.getOperator().equals("+"))
        {
            System.out.println(ast.getLeft().getType());
            if(ast.getLeft().getType() == Environment.Type.STRING || ast.getRight().getType() == Environment.Type.STRING)
            {
                ast.setType(Environment.Type.STRING);
            }
            else if(ast.getLeft().getType() == Environment.Type.INTEGER && ast.getRight().getType() == Environment.Type.INTEGER)
            {
                ast.setType(Environment.Type.INTEGER);
            }
            else if(ast.getLeft().getType() == Environment.Type.DECIMAL && ast.getRight().getType() == Environment.Type.DECIMAL)
            {
                ast.setType(Environment.Type.DECIMAL);
            }
            else
            {
                // FAILURE //
                throw new RuntimeException("Mismatched Types!");
            }

        }
        // - / * / / //
        if(ast.getOperator().equals("-") || ast.getOperator().equals("*") || ast.getOperator().equals("/"))
        {
            ast.setType(Environment.Type.INTEGER);

            if(ast.getLeft().getType() == Environment.Type.INTEGER && ast.getRight().getType() == Environment.Type.INTEGER)
            {
                ast.setType(Environment.Type.INTEGER);
            }
            else if(ast.getLeft().getType() == Environment.Type.DECIMAL && ast.getRight().getType() == Environment.Type.DECIMAL)
            {
                ast.setType(Environment.Type.DECIMAL);
            }
            else
            {
                // FAILURE //
                throw new RuntimeException("Mismatched Types!");
            }

        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        //Validates an access expression
        // sets the variable of the expression (Ast.Expr.Access#setVariable)
        // which internally sets the type of the expression to be the type of the variable.
        // The variable is a field of the receiver if present, otherwise it is a variable in the current scope.
        //Returns null.

        if(ast.getReceiver().isPresent())
        {
            //object.field, scope = {object: ObjectType {field: Integer}
            //ast.getType() == Integer
            visit(ast.getReceiver().get());
            Environment.Type type = ast.getReceiver().get().getType().getScope().lookupVariable(ast.getName()).getType();
            scope.defineVariable(ast.getName(), ast.getName(), type, Environment.NIL);
            Environment.Variable var = scope.lookupVariable(ast.getName());
            ast.setVariable(var);
        }
        else
        {
            ast.setVariable(scope.lookupVariable(ast.getName()));
        }

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        //Validates a function expression and
        // sets the function of the expression (Ast.Expr.Function#setFunction),
        // which internally sets the type of the expression to be the return type of the function.
        // The function is a method of the receiver if present, otherwise it is a function in the current scope.
        // Additionally, checks that the provided arguments are assignable to the corresponding parameter types of the function.
        //
        //IMPORTANT: The first parameter of a method (retrieved from the receiver) is the object it is being called on (like self in Python).
        // Therefore, the first argument is at index 1 in the parameters, not 0, only for methods.
        //This is a bit of a weird quirk, but every design decision has tradeoffs.
        //Returns null.
        for(Ast.Expr stmt : ast.getArguments())
        {
            visit(stmt);
        }

        if(ast.getReceiver().isPresent()) {
            visit(ast.getReceiver().get());
            Environment.Function func = ast.getReceiver().get().getType().getMethod(ast.getName(), ast.getArguments().size());
            scope.defineFunction(func.getName(),func.getJvmName(),func.getParameterTypes(), func.getReturnType(), args -> Environment.NIL);
            ast.setFunction(func);

            for(int i = 0; i < ast.getArguments().size(); i++) {
                requireAssignable(ast.getArguments().get(i).getType(), scope.lookupFunction(func.getName(), ast.getArguments().size()).getParameterTypes().get(i));
            }
        }
        else {
            ast.setFunction(scope.lookupFunction(ast.getName(), ast.getArguments().size()));
        }


        return null;
    }

    public static void requireAssignable(Environment.Type target, Environment.Type type) {
        //When the two types are the same, the assignment can be performed.
        //When the target type is Any, anything from our language can be assigned to it.  Any in our language is similar to the Object class in Java.
        //When the target type is Comparable, it can be assigned any of our defined Comparable types:  Integer, Decimal, Character, and String. You will not need to support any other Comparable types.
        //In all other cases, a mismatched assignments will fail throwing a RuntimeException.

        //if target doesn't match type
        if(target != type) {
            //if target isn't any
            if(target != Environment.Type.ANY) {
                //if target is comparable
                if(target == Environment.Type.COMPARABLE) {
                    //if type is not comparable
                    if(type != Environment.Type.INTEGER && type != Environment.Type.CHARACTER && type != Environment.Type.DECIMAL && type != Environment.Type.STRING) {
                        throw new RuntimeException("incompatible comparison type");
                    }
                    //else is fine
                }
                else {
                    throw new RuntimeException("incompatible type");
                }
            }
            //else if fine
        }
        //else is fine
    }

}