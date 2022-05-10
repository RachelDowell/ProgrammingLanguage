package plc.project;

import java.io.PrintWriter;
import java.math.BigInteger;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        //Generates a source. T
        // his includes a definition for the Main class that contains our code
        // as well as the public static void main(String[] args) method used as the entry point for Java.
        //The order of generation you will follow is:
        //the class header,
        //the source's fields,
        //Java's main method,
        //the source's methods, and
        //finally the closing brace for the class.
        //Pay close attention to spacing and indentation;
        //fields are grouped together while methods are separated by an empty line
        //(hint: use newline(0) for empty lines, giving it an explicit indent of 0).
        //The Java main method you generate will be:
        //
        //public static void main(String[] args) {
        //    System.exit(new Main().main());
        //}
        //Take note that:
        //
        //new Main() creates an instance of our Main class.
        //.main() calls our language's main method (having a different signature since it does not take arguments).
        //System.exit is used to specify the exit code of a Java program, unlike C/++ which does so automatically. [This is not critical for understanding the assignment, but is another 'flare' to draw attention to important concepts that you will almost certainly make use in the future.]
        //Note, our grammar does not require that the source node include the method main() that you are calling. However, one of the methods within a source node will always be main. [As a practice exercise, you can expand the grammar and other elements of our solution to require the presence of main.]
        //Returns null.

        print("public class Main {");
        newline(0);
        ++indent;
        for(int i = 0; i < ast.getFields().size(); i++) {
            if(i == 0) {
                newline(indent);
            }
            print(ast.getFields().get(i));
            if(i != ast.getFields().size() - 1) {
                newline(indent);
            }
            else {
                newline(0);
            }
        }
        newline(indent);
        print("public static void main(String[] args) {");
        newline(++indent);
        print("System.exit(new Main().main());");
        newline(--indent);
        print("}");

        for(int i = 0; i < ast.getMethods().size(); i++) {
            if(i == 0) {
                newline(--indent);
            }
            print(ast.getMethods().get(i));
            if(i != ast.getMethods().size() - 1) {
                newline(0);
            }
        }

        newline(--indent);
        newline(0);
        print("}");

        //create a "class Main {"

        //declare fields

        //declare "public static void main(String[] args) {
        //              System.exit(new Main().main());
        //          }"
        //declare each method
        return null;

    }

    @Override
    public Void visit(Ast.Field ast) {
        //Generates a field expression
        // The expression should consist of the type name and the variable name stored in the AST separated by a single space character. \
        // If a value is present,
        // then an equal sign character with surrounding single spaces is generated followed by the variable value (expression).
        // A semicolon should be generated at the end.
        //Returns null.

        print(ast.getVariable().getType().getJvmName(),
                " ",
                ast.getVariable().getJvmName());

        if(ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");

        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        //Generates a method expression.
        // The method should begin with the method's JVM type name followed by the method name, both of which are found in the AST.
        // Then the method should generate a comma-separated list of the method parameters surrounded by parenthesis.
        // Each parameter will consist of a JVM type name and the parameter name.
        // A single space will be placed after the list comma and before the next parameter type.
        // No space will be placed after the opening parenthesis and before the closing parenthesis.
        //Following a single space, the opening brace should be generated on the same line.
        // If the statements is empty the closing brace should also be on the same line,
        // otherwise each statement is generated on a new line with increased indentation followed by a closing brace on a new line with the original indentation.
        //Returns null.
        newline(++indent);
        print(ast.getFunction().getReturnType().getJvmName(), " ", ast.getFunction().getName(), "(");
        for(int i = 0; i < ast.getParameters().size(); i++) {
            if(i != (ast.getParameters().size() - 1)) {
                print(ast.getParameterTypeNames().get(i), " ", ast.getParameters().get(i), ", ");
            }
            else {
                print(ast.getParameterTypeNames().get(i), " ", ast.getParameters().get(i));
            }
        }
        print(")");

        print(" {");
        if(ast.getStatements().isEmpty()) {
            // Cassandra added this //
           // newline(indent);
            print("}");
        }
        else{
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
            print("}");
            --indent;
        }
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Expression ast) {
        //Generates an expression. It should consist of the generated expression found in the AST followed by a semicolon.
        //Though the Analyzer requires the contained expression be a function expression, your generator should still work with other expression types.
        //Returns null.
        print(ast.getExpression(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Declaration ast) {
        //Generates a declaration expression.
        // The expression should consist of the type name and the variable name stored in the AST separated by a single space.
        // If a value is present,
        // then an equal sign with surrounding single spaces is generated followed by the generated variable value.
        // A semicolon should be generated at the end.
        //Returns null.

        print(ast.getVariable().getType().getJvmName(),
                " ",
                ast.getVariable().getJvmName());

        if(ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Assignment ast) {
        //Generates a variable assignment expression.
        // The name should be the receiver of the variable stored in the AST
        // the value should be the generated value of the variable
        // An equal sign character with surrounding single spaces should be generated between the name and value
        // A semicolon should be generated at the end.
        //Returns null.
        // Cassandra
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.If ast) {
        //Generates an If expression.
        // The expression should consist of the if keyword followed by a single space and the generated condition with the surrounding parenthesis.
        // The opening brace should be generated on the same line.
        // After a single space following the condition's closing parenthesis,
        // the opening brace should be generated followed by a newline with an increase in the indentation
        // and the generation of all the statements each ending with a newline.
        // Following this should be a decrease in the indentation and the corresponding closing brace.
        //If there is an else block,
        // then generate the else keyword on the same line with the same block formatting.
        // There is no concept of else-if in our grammar, so nested if statements will still appear nested.
        // If there is not an else block, then the entire else section is left out of the generated code.
        //Returns null.
        print("if (", ast.getCondition(), ") ", "{");
        newline(++indent);

        for(int i = 0; i < ast.getThenStatements().size(); i++) {
            if(i != 0) {
                newline(indent);
            }
            print(ast.getThenStatements().get(i));
        }
        newline(--indent);
        print("}");

        if(!ast.getElseStatements().isEmpty()) {
            print(" else {");
            newline(++indent);
            for(int i = 0; i < ast.getElseStatements().size(); i++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getElseStatements().get(i));
            }
            newline(--indent);
            print("}");
        }

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.For ast) {
        //Generates a for loop expression.
        // The expression should consist of the for keyword.
        // It is followed by a single space with the following in parenthesis
        //The variable type, int
        //The variable name found in the AST
        //A colon with surrounding spaces
        //The generated value expression
        //The opening brace should be generated on the same line after a single space and followed by a newline with an increase in the indentation
        // and the generation of all the statements ending with a newline.
        // Following this should be a decrease in the indentation and a closing brace.
        //Returns null.
        print("for (", "int ", ast.getName(), " : ", ast.getValue(), ")"," {");
        newline(++indent);
        for(int i = 0; i < ast.getStatements().size(); i++) {
            if(i != 0) {
                newline(indent);
            }
            print(ast.getStatements().get(i));
        }
        newline(--indent);
        print("}");

        return null;
    }

    @Override
    public Void visit(Ast.Stmt.While ast) {
        //Generates a while loop expression
        //The expression will consist of the while keyword followed by a single space and then the generated condition expression surrounded by parenthesis.
        //Following a single space, the opening brace should be generated on the same line.
        // If the statements are empty,
        // the closing brace should also be on the same line
        // otherwise each statement is generated on a new line with increased indentation followed by a closing brace on a new line with the original indentation.
        //Returns null.
        print("while (", ast.getCondition(), ") {");

        if(!ast.getStatements().isEmpty()) {
            newline(++indent);
            for(int i = 0; i < ast.getStatements().size(); i++) {
                if(i != 0) {
                    newline(indent);
                }
                print(ast.getStatements().get(i));
            }
            newline(--indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Stmt.Return ast) {
        //Generates a return expression.
        // The expression will consist of the return keyword followed by a single space and the corresponding generated expression value.
        // A semicolon should be generated at the end.
        //Returns null.
        print("return ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Literal ast) {
        //Generates a literal expression.
        // The expression should generate the value of the literal found in the AST.
        //For characters and strings, remember that you will need to include the surrounding quotes.
        // You do not, however, have to worry about converting escape characters back to their escape sequence (though a full language would absolutely need to).
        //Note: The BigDecimal class represents numbers with a specific precision, and therefore you need to pay close attention to the precision it has when writing test cases.
        // It is recommended to use the BigDecimal(String) constructor for this reason so you know what the precision is.
        //Returns null.
        if(ast.getLiteral() instanceof String) {
            print("\"", ast.getLiteral(), "\"");
        }
        else if(ast.getLiteral() instanceof Character) {
            print("\'", ast.getLiteral(), "\'");
        }
        else {
            print(ast.getLiteral());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Group ast) {
        //Generates a group expression. The expression used should be a generated expression surrounded by parentheses.
        //Though the Analyzer requires the contained expression to be a binary expression, your generator should still work with other expression types.
        //Returns null.
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expr.Binary ast) {
        //Generates a binary expression.
        // It should first generate the AST's left expression,
        // then generate the corresponding JVM binary operator,
        // and lastly generate the right expression.
        // the binary operator should be generated with a single space on each side.
        //Returns null.
        print(ast.getLeft(), " ");
        if(ast.getOperator().equals("AND")) {
            print("&&");
        }
        else if(ast.getOperator().equals("OR)")) {
            print("||");
        }
        else{
            print(ast.getOperator());
        }
        print(" ", ast.getRight());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Access ast) {
        //Generates an access expression.
        // The name used should be the jvmName of the variable stored in the AST.
        // If a receiver is present, it should be generated first followed by a period.
        //Returns null.
        if(ast.getReceiver().isPresent()) {
            print(ast.getReceiver().get(), ".");
        }

        print(ast.getVariable().getJvmName());

        return null;
    }

    @Override
    public Void visit(Ast.Expr.Function ast) {
        //Generates a function expression.
        // The name used should be the jvmName of the function stored in the AST.
        // It should be followed by a comma-separated list of the generated argument expressions surrounded by parenthesis.
        // If a receiver is present, it should be generated first followed by a period.
        //Returns null.
        if(ast.getReceiver().isPresent()) {
            print(ast.getReceiver().get(), ".");
        }
        print(ast.getFunction().getJvmName(), "(");
        for(int i = 0; i < ast.getArguments().size(); i++) {
            if(i != ast.getArguments().size() - 1) {
                print(ast.getArguments().get(i), ", ");
            }
            else {
                print(ast.getArguments().get(i));
            }
        }
        print(")");
        return null;
    }

}
