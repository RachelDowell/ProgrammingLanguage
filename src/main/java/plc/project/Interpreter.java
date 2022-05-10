package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

public class Interpreter implements Ast.Visitor<Environment.PlcObject> {

    private Scope scope = new Scope(null);

    public Interpreter(Scope parent) {
        scope = new Scope(parent);
        scope.defineFunction("print", 1, args -> {
            return Environment.NIL;
        });
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Environment.PlcObject visit(Ast.Source ast) {
        //Evaluates fields followed by methods.
        // Returns the result of calling the main/0 function (named main with arity 0).
        // If this function does not exist an error should be thrown.

        ast.getFields().forEach(this::visit);
        ast.getMethods().forEach(this::visit);

        List<Environment.PlcObject> stuff = new ArrayList<>();
        stuff.add(Environment.create(BigInteger.ZERO));

        try {
            return scope.lookupFunction("main", 0).invoke(stuff);
        }
        catch (Exception e) {
            throw new RuntimeException("The function main/0 is not defined in this scope.");
        }

        /*List<Ast.Method> methods = ast.getMethods();

        for(Ast.Method method : methods)
        {
            if(method.getName().contains("main"))
            {
                for(Ast.Stmt var : method.getStatements())
                {
                    if(var instanceof Ast.Stmt.Return)
                    {
                        // Fix this
                        //rachel
                        //if and return are fine
                        //src access and function
                        //return visit var.getvalue
                        return new Environment.PlcObject(scope, ((Ast.Expr.Literal) ((Ast.Stmt.Return) var).getValue()).getLiteral());
                        //return new Environment.PlcObject(scope, visit(var).getValue());
                    }

                }

            }
        }
        return Environment.NIL;
         */

    }

    @Override
    public Environment.PlcObject visit(Ast.Field ast) {

        if(ast.getValue().isPresent())
        {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        }
        else
        {
            scope.defineVariable(ast.getName(), Environment.NIL);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Method ast) {

        // keep track of scope //
        Scope temp = scope;

        scope.defineFunction(ast.getName(), ast.getParameters().size(), args -> {
            // Sets the scope to be a new child of the scope where the function was defined //
            // Keeps track of scope to be restored //
            Scope temp2 = scope;

            // Declaring the scope
            scope = new Scope(temp);

            for(int i = 0; i < args.size(); i++) {
                //changed here
                if(ast.getParameters().isEmpty()) {
                    scope.defineVariable(ast.getName(), args.get(i));
                }
                else {
                    scope.defineVariable(ast.getParameters().get(i), args.get(i));
                }
            }

            try
            {
                ast.getStatements().forEach(this::visit);

                return Environment.NIL;
            }
            catch(Return r)
            {
                return r.value;
            }
            finally
            {
                scope = temp2;
            }

        });

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Expression ast) {
        visit(ast.getExpression());

        Ast.Expr expr = ast.getExpression();

        if(expr instanceof Ast.Expr.Function)
        {
            if(((Ast.Expr.Function) expr).getName().contains("print"))
            {
                for(Ast.Expr message: ((Ast.Expr.Function) expr).getArguments())
                {
                    // This System should be here, do not delete ever //

                    System.out.println(visit(message).getValue());
                }
            }
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Declaration ast) {
        if(ast.getValue().isPresent())
        {
            scope.defineVariable(ast.getName(), visit(ast.getValue().get()));
        }
        else
        {
            scope.defineVariable(ast.getName(), Environment.NIL);
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Assignment ast) {

        if(!(ast.getReceiver() instanceof Ast.Expr.Access))
        {
            throw new AssertionError("Wrong type!");
        }

        Ast.Expr.Access reciever = (Ast.Expr.Access) ast.getReceiver();

        if(reciever.getReceiver().isPresent())
        {
            visit(reciever.getReceiver().get()).setField(reciever.getName(), visit(ast.getValue()));
        }
        else
        {
            scope.lookupVariable(reciever.getName()).setValue(visit(ast.getValue()));
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.If ast) {

        if(visit(ast.getCondition()).getValue() instanceof Boolean)
        {
            try
            {
                scope = new Scope(scope);

                if(visit(ast.getCondition()).getValue().equals(true))
                {
                    for(Ast.Stmt stmt : ast.getThenStatements())
                    {
                        visit(stmt);
                    }
                }
                if(visit(ast.getCondition()).getValue().equals(false))
                {
                    for(Ast.Stmt stmt : ast.getElseStatements())
                    {
                        visit(stmt);
                    }
                }
            }
            finally {
                scope = scope.getParent();
            }

        }

        return Environment.NIL;
    }


    @Override
    public Environment.PlcObject visit(Ast.Stmt.For ast) {

        for(Object iter : requireType(Iterable.class, visit(ast.getValue())))
        {

            try
            {
                scope = new Scope(scope);

                // Define Variable //
                scope.defineVariable(ast.getName(), (Environment.PlcObject) iter);

                ast.getStatements().forEach(this::visit);

            }
            finally {
                scope = scope.getParent();
            }
        }

        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.While ast) {
        while(requireType(Boolean.class, visit(ast.getCondition())))
        {
            try
            {
                scope = new Scope(scope);
                for(Ast.Stmt stmt : ast.getStatements())
                {
                    visit(stmt);
                }
            }
            finally {
                scope = scope.getParent();
            }
        }
        return Environment.NIL;
    }

    @Override
    public Environment.PlcObject visit(Ast.Stmt.Return ast) {

        //  This needs to be tested // TODO
        throw new Return(visit(ast.getValue()));
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Literal ast) {

        // If null, return a NIL object //
        if(ast.getLiteral() == null)
        {
            return Environment.NIL;
        }

        // Creates new object with value from ast //
        return Environment.create(ast.getLiteral());
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Group ast){

        BigInteger value = BigInteger.ZERO;

        if(ast.getExpression() instanceof Ast.Expr.Literal)
        {
            return Environment.create(((Ast.Expr.Literal) ast.getExpression()).getLiteral());
        }
        else if (ast.getExpression() instanceof Ast.Expr.Binary)
        {
            Ast.Expr.Binary expr = (Ast.Expr.Binary) ast.getExpression();

            if(expr.getOperator().equals("+"))
            {
                value = value.add((BigInteger) ((Ast.Expr.Literal) expr.getLeft()).getLiteral());
                value = value.add((BigInteger) ((Ast.Expr.Literal) expr.getRight()).getLiteral());
            }

            return Environment.create(value);
        }

        return Environment.NIL;

    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Binary ast) {

        // Holds Operator //
        String oper = ast.getOperator();

        Environment.PlcObject left = visit(ast.getLeft());

        //cannot define right because it keeps changing
        Ast.Expr right = ast.getRight();

        // AND / OR //
        if(oper.equals("AND"))
        {
            return Environment.create(requireType(Boolean.class, left) && requireType(Boolean.class, visit(ast.getRight())));
        }
        if(oper.equals("OR"))
        {
            return Environment.create(requireType(Boolean.class, left) || requireType(Boolean.class, visit(ast.getRight())));
        }

        // < / <= / > / >= //
        if(oper.equals("<"))
        {
            BigInteger leftInt = (BigInteger) left.getValue();
            BigInteger rightInt = (BigInteger) visit(right).getValue();

            if(rightInt.compareTo(leftInt) == 1)
            {
                return Environment.create(true);
            }

            return Environment.create(false);
        }
        if(oper.equals(">"))
        {
            BigInteger leftInt = (BigInteger) left.getValue();
            BigInteger rightInt = (BigInteger) visit(right).getValue();

            if(rightInt.compareTo(leftInt) == -1)
            {
                return Environment.create(true);
            }

            return Environment.create(false);
        }
        if(oper.equals("<="))
        {
            BigInteger leftInt = (BigInteger) left.getValue();
            BigInteger rightInt = (BigInteger) visit(right).getValue();

            if(rightInt.compareTo(leftInt) == 1 || rightInt.compareTo(leftInt) == 0)
            {
                return Environment.create(true);
            }

            return Environment.create(false);
        }
        if(oper.equals(">="))
        {
            BigInteger leftInt = (BigInteger) left.getValue();
            BigInteger rightInt = (BigInteger) visit(right).getValue();

            if(rightInt.compareTo(leftInt) == -1 || rightInt.compareTo(leftInt) == 0)
            {
                return Environment.create(true);
            }

            return Environment.create(false);
        }

        // == / != //
        if(oper.equals("=="))
        {
            if(left.equals(right))
            {
                return Environment.create(true);
            }

            return Environment.create(false);
        }
        if(oper.equals("!="))
        {
            if(!left.equals(right))
            {
                return Environment.create(true);
            }

            return Environment.create(false);
        }

        // + //
        if(oper.equals("+"))
        {
            if(left.getValue() instanceof String && visit(right).getValue() instanceof String)
            {
                return Environment.create(((String) left.getValue()) + ((String) visit(right).getValue()));
            }
            if(left.getValue() instanceof BigInteger && visit(right).getValue() instanceof BigInteger)
            {
                return Environment.create(((BigInteger) left.getValue()).add((BigInteger) visit(right).getValue()));
            }
            if(left.getValue() instanceof BigDecimal && visit(right).getValue() instanceof BigDecimal)
            {
                return Environment.create(((BigDecimal) left.getValue()).add((BigDecimal) visit(right).getValue()));
            }

            // They don't match! //
            throw new AssertionError("Operator types do not match.");
        }

        // - / * //
        if(oper.equals("-"))
        {
            if(left.getValue() instanceof BigInteger && visit(right).getValue() instanceof BigInteger)
            {
                return Environment.create(((BigInteger) left.getValue()).subtract((BigInteger) visit(right).getValue()));
            }
            if(left.getValue() instanceof BigDecimal && visit(right).getValue() instanceof BigDecimal)
            {
                return Environment.create(((BigDecimal) left.getValue()).subtract((BigDecimal) visit(right).getValue()));
            }

            // They don't match! //
            throw new AssertionError("Operator types do not match.");

        }
        if(oper.equals("*"))
        {
            if(left.getValue() instanceof BigInteger && visit(right).getValue() instanceof BigInteger)
            {
                return Environment.create(((BigInteger) left.getValue()).multiply((BigInteger) visit(right).getValue()));
            }
            if(left.getValue() instanceof BigDecimal && visit(right).getValue() instanceof BigDecimal)
            {
                return Environment.create(((BigDecimal) left.getValue()).multiply((BigDecimal) visit(right).getValue()));
            }

            // They don't match! //
            throw new AssertionError("Operator types do not match.");
        }

        // / //
        if(oper.equals("/"))
        {
            if((left.getValue() instanceof BigInteger && ((Ast.Expr.Literal) right).getLiteral() instanceof BigInteger))
            {
                // no no //
                if(((BigInteger) ((Ast.Expr.Literal) right).getLiteral()).equals(BigInteger.ZERO))
                {
                    throw new AssertionError("Cannot divide by zero.");
                }

                return Environment.create(((BigInteger) left.getValue()).divide((BigInteger) ((Ast.Expr.Literal) right).getLiteral()));
            }
            if(left.getValue() instanceof BigDecimal && ((Ast.Expr.Literal) right).getLiteral() instanceof BigDecimal)
            {
                // no no //
                if(((BigDecimal) ((Ast.Expr.Literal) right).getLiteral()).equals(BigDecimal.ZERO))
                {
                    throw new AssertionError("Cannot divide by zero.");
                }

                BigDecimal leftDeci = (BigDecimal) left.getValue();
                BigDecimal rightDeci = (BigDecimal) ((Ast.Expr.Literal) right).getLiteral();
                RoundingMode rounder = RoundingMode.HALF_EVEN;

                return Environment.create(leftDeci.divide(rightDeci, rounder));
            }

            // They don't match! //
            throw new AssertionError("Operator types do not match.");
        }

        // Just in case //
        return Environment.NIL;

    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Access ast) {

        // TODO
        // If there is a receiver, evaluate //
        if(ast.getReceiver().isPresent())
        {
            scope.defineVariable(ast.getName(), visit(ast.getReceiver().get()).getField(ast.getName()).getValue());
        }

        return (scope.lookupVariable(ast.getName())).getValue();
    }

    @Override
    public Environment.PlcObject visit(Ast.Expr.Function ast) {
        //If the expression has a receiver, evaluate it and return the result of calling the appropriate method,

        List<Environment.PlcObject> stuff = new ArrayList<>();
        for(int i = 0; i < ast.getArguments().size(); i++) {
            stuff.add(visit(ast.getArguments().get(i)));
        }

        // TODO
        // If there is a receiver, evaluate //
        if(ast.getReceiver().isPresent())
        {
            Environment.PlcObject test = visit(ast.getReceiver().get());
            return test.callMethod(ast.getName(), stuff);
        }

        if(ast.getArguments().isEmpty())
        {
            stuff.add(Environment.create(ast.getName()));
        }

        return scope.lookupFunction(ast.getName(), ast.getArguments().size()).invoke(stuff);

        /*// This needs to be better TODO //
        if(ast.getArguments().isEmpty())
        {
            return Environment.create(ast.getName());
        }


        return Environment.NIL;

         */
    }

    /**
     * Helper function to ensure an object is of the appropriate type.
     */
    private static <T> T requireType(Class<T> type, Environment.PlcObject object) {
        if (type.isInstance(object.getValue())) {
            return type.cast(object.getValue());
        } else {
            throw new RuntimeException("Expected type " + type.getName() + ", received " + object.getValue().getClass().getName() + ".");
        }
    }

    /**
     * Exception class for returning values.
     */
    private static class Return extends RuntimeException {

        private final Environment.PlcObject value;

        private Return(Environment.PlcObject value) {
            this.value = value;
        }

    }

}
