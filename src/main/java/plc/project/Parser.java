package plc.project;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The parser takes the sequence of tokens emitted by the lexer and turns that
 * into a structured representation of the program, called the Abstract Syntax
 * Tree (AST).
 *
 * The parser has a similar architecture to the lexer, just with {@link Token}s
 * instead of characters. As before, {@link #peek(Object...)} and {@link
 * #match(Object...)} are helpers to make the implementation easier.
 *
 * This type of parser is called <em>recursive descent</em>. Each rule in our
 * grammar will have it's own function, and reference to other rules correspond
 * to calling that functions.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    /**
     * Parses the {@code source} rule.
     */
    public Ast.Source parseSource() throws ParseException {
        // field* method*
        List<Ast.Field> fields = new ArrayList<>();
        List<Ast.Method> methods = new ArrayList<>();

        // if token == LET, call parse field & add to fields
        //else if start with def, call parse method and add to methods

        while(match("LET")) {
            fields.add(parseField());
        }

        while(match("DEF")) {
            methods.add(parseMethod());
        }

        return new Ast.Source(fields, methods);
    }

    /**
     * Parses the {@code field} rule. This method should only be called if the
     * next tokens start a field, aka {@code LET}.
     */
    public Ast.Field parseField() throws ParseException {
        // 'LET' identifier ('=' expression)? ';'
        if(peek("LET")) {
            tokens.advance();
        }
        String name;
        String type = null;
        Optional<Ast.Expr> value = Optional.empty();

        name = tokens.get(0).getLiteral();
        tokens.advance();

        if(match(":")) {
            type = tokens.get(0).getLiteral();
            tokens.advance();
        }
        else {
            throw new ParseException("Invalid field", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }

        if(match("=")) {
            if(!peek(";")) {
                value = Optional.of(parseExpression());
                tokens.advance();
            }
        }

        if(!match(";")) {
            //throw new ParseException("Expected semicolon.", tokens.get(-1).getIndex()+1);
        }
        return new Ast.Field(name,type, value);


    }

    /**
     * Parses the {@code method} rule. This method should only be called if the
     * next tokens start a method, aka {@code DEF}.
     */
    public Ast.Method parseMethod() throws ParseException {
        //'DEF' identifier '(' (identifier (',' identifier)*)? ')' 'DO' statement* 'END'
        if(peek("DEF")) {
            tokens.advance();
        }
        String name = tokens.get(0).getLiteral();
        Optional<String> type = Optional.of("Any");
        tokens.advance();
        List<String> parameters = new ArrayList<>();
        List<String> pTypeName = new ArrayList<>();
        List<Ast.Stmt> statements = new ArrayList<>();

        if(match("(")) {
            if(!peek(")")) {
                parameters.add(tokens.get(0).getLiteral());
                tokens.advance();
                if(match(":")) {
                    pTypeName.add(tokens.get(0).getLiteral());
                    tokens.advance();
                }
                else{
                    throw new ParseException("Invalid method",tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
                while(match(",")) {
                    parameters.add(tokens.get(0).getLiteral());
                    if(match(":")) {
                        pTypeName.add(tokens.get(0).getLiteral());
                        tokens.advance();
                    }
                    else{
                        throw new ParseException("Invalid method",tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    }
                }
            }
            if(!match(")")) {

                throw new ParseException("Expected closing parenthesis.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
            if(match(":")) {
                type = Optional.of(tokens.get(0).getLiteral());
                tokens.advance();
            }
            if(!match("DO")) {
                throw new ParseException("Invalid method.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }

            while(!match("END")) {
                statements.add(parseStatement());
            }
            if(tokens.has(0)) {
                throw new ParseException("Invalid method.",  tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
        }


        return new Ast.Method(name, parameters, pTypeName, type, statements);

    }

    /**
     * Parses the {@code statement} rule and delegates to the necessary method.
     * If the next tokens do not start a declaration, if, while, or return
     * statement, then it is an expression/assignment statement.
     */
    public Ast.Stmt parseStatement() throws ParseException {
        // LET' identifier ('=' expression)? ';' |
        //'IF' expression 'DO' statement* ('ELSE' statement*)? 'END' |
        //'FOR' identifier 'IN' expression 'DO' statement* 'END' |
        //  'WHILE' expression 'DO' statement* 'END' |
        // 'RETURN' expression ';' |
        //  expression ('=' expression)? ';'
        Ast.Stmt stmt = null;
        Ast.Expr stmtExpr = null;
        Ast.Expr stmtExpr2 = null;
        boolean expr = false;

        if(match("LET")) {
            stmt = parseDeclarationStatement();
        }
        else if(match("IF")) {
            stmt = parseIfStatement();
        }
        else if(match("FOR")) {
            stmt = parseForStatement();
        }
        else if(match("WHILE")) {
            stmt = parseWhileStatement();
        }
        else if(match("RETURN")) {
            stmt = parseReturnStatement();
        }
        else {
            stmtExpr = parseExpression();
            if(match("=")) {
                stmtExpr2 = parseExpression();

                if(!match(";")) {
                    throw new ParseException("Missing semicolon.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
                else {
                    return new Ast.Stmt.Assignment(stmtExpr,stmtExpr2);
                }
            }
            else {
                if(!match(";")) {
                    throw new ParseException("Missing semicolon.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }
            }
            expr = true;
        }

        if(expr) {
            return new Ast.Stmt.Expression(stmtExpr);
        }
        else{
            return stmt;
        }
    }

    /**
     * Parses a declaration statement from the {@code statement} rule. This
     * method should only be called if the next tokens start a declaration
     * statement, aka {@code LET}.
     */
    public Ast.Stmt.Declaration parseDeclarationStatement() throws ParseException {
        //'LET' identifier ('=' expression)? ';'
        String name = tokens.get(0).getLiteral();
        Optional<String> typeName = Optional.empty();
        Optional<Ast.Expr> value = Optional.empty();
        tokens.advance();

        if(match(":")) {
            typeName = Optional.of(tokens.get(0).getLiteral());
            tokens.advance();
        }

        if(match("=")) {
            value = Optional.of(parseExpression());
        }

        if(!match(";")) {
            throw new ParseException("Missing semicolon.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
        else {
            return new Ast.Stmt.Declaration(name, typeName, value);
        }
    }

    /**
     * Parses an if statement from the {@code statement} rule. This method
     * should only be called if the next tokens start an if statement, aka
     * {@code IF}.
     */
    public Ast.Stmt.If parseIfStatement() throws ParseException {
        // 'IF' expression 'DO' statement* ('ELSE' statement*)? 'END'
        Ast.Expr condition = parseExpression();
        List<Ast.Stmt> thenStatements = new ArrayList<>();
        List<Ast.Stmt> elseStatements = new ArrayList<>();

        if(!match("DO")) {
            throw new ParseException("Invalid IF statement.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
        else {
            while(!match("ELSE") && !match("END")) {
                if(peek(";")) {
                    tokens.advance();
                }
                else {
                    thenStatements.add(parseStatement());
                }
            }
            tokens.index--;

            if(match("ELSE")) {
                while(!match("END")) {
                    if(peek(";")) {
                        tokens.advance();
                    }
                    else {
                        elseStatements.add(parseStatement());
                    }
                }
                tokens.index--;
            }

            if(!match("END")) {
                throw new ParseException("Invalid IF statement.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }

            return new Ast.Stmt.If(condition, thenStatements, elseStatements);

        }
    }

    /**
     * Parses a for statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a for statement, aka
     * {@code FOR}.
     */
    public Ast.Stmt.For parseForStatement() throws ParseException {
        //'FOR' identifier 'IN' expression 'DO' statement* 'END' |
        String name = tokens.get(0).getLiteral();
        String checkInt = name;
        checkInt = checkInt.substring(0,1);

        //if(checkInt.contains("[0-9]")){}

        if(checkInt.equals("1") || checkInt.equals("2") || checkInt.equals("3") || checkInt.equals("4") || checkInt.equals("5") || checkInt.equals("6") || checkInt.equals("7") || checkInt.equals("8") || checkInt.equals("9") ||  checkInt.equals("0")){
            throw new ParseException("Invalid name.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
        tokens.advance();
        if(!match("IN")) {
            throw new ParseException("Invalid FOR statement.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
        Ast.Expr value = parseExpression();
        List<Ast.Stmt> statements = new ArrayList<>();
        if(!match("DO")) {
            throw new ParseException("Invalid FOR statement.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }

        while(!match("END")) {
            if(peek(";")) {
                tokens.advance();
            }
            else {
                statements.add(parseStatement());
            }
        }

        return new Ast.Stmt.For(name, value, statements);


    }

    /**
     * Parses a while statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a while statement, aka
     * {@code WHILE}.
     */
    public Ast.Stmt.While parseWhileStatement() throws ParseException {
        //'WHILE' expression 'DO' statement* 'END' |
        Ast.Expr condition = parseExpression();
        List<Ast.Stmt> statements = new ArrayList<>();

        if(!match("DO")) {
            throw new ParseException("Invalid WHILE statement.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }

        while(!match("END")) {
            if(peek(";")) {
                tokens.advance();
            }
            else {
                statements.add(parseStatement());
            }
        }

        return new Ast.Stmt.While(condition, statements);
    }

    /**
     * Parses a return statement from the {@code statement} rule. This method
     * should only be called if the next tokens start a return statement, aka
     * {@code RETURN}.
     */
    public Ast.Stmt.Return parseReturnStatement() throws ParseException {
        //'RETURN' expression ';'
        Ast.Expr value = parseExpression();

        if(!match(";")) {
            throw new ParseException("Invalid RETURN statement.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }

        return new Ast.Stmt.Return(value);
    }

    /**
     * Parses the {@code expression} rule.
     */
    public Ast.Expr parseExpression() throws ParseException {
        return parseLogicalExpression();
    }

    /**
     * Parses the {@code logical-expression} rule.
     */
    public Ast.Expr parseLogicalExpression() throws ParseException {
        String operator = "";
        Ast.Expr right;
        Ast.Expr expression = parseEqualityExpression();

        while(match("OR") || match("AND"))
        {
            operator = tokens.get(-1).getLiteral();

            right = parseEqualityExpression();

            expression = new Ast.Expr.Binary(operator, expression, right);

        }

        return expression;
    }

    /**
     * Parses the {@code equality-expression} rule.
     */
    public Ast.Expr parseEqualityExpression() throws ParseException {
        String operator = "";
        Ast.Expr right;
        Ast.Expr expression = parseAdditiveExpression();

        while(match("<") || match("<=") || match(">") || match(">=") || match("==") || match("!="))
        {
            operator = tokens.get(-1).getLiteral();

            right = parseAdditiveExpression();

            expression = new Ast.Expr.Binary(operator, expression, right);

        }

        return expression;
    }

    /**
     * Parses the {@code additive-expression} rule.
     */
    public Ast.Expr parseAdditiveExpression() throws ParseException {
        String operator = "";
        Ast.Expr right;
        Ast.Expr expression = parseMultiplicativeExpression();

        while(match("+") || match("-"))
        {
            operator = tokens.get(-1).getLiteral();

            right = parseMultiplicativeExpression();

            expression = new Ast.Expr.Binary(operator, expression, right);

        }

        return expression;
    }

    /**
     * Parses the {@code multiplicative-expression} rule.
     */
    public Ast.Expr parseMultiplicativeExpression() throws ParseException {

        String operator = "";
        Ast.Expr right;
        Ast.Expr expression = parseSecondaryExpression();

        while(match("*") || match("/"))
        {
            operator = tokens.get(-1).getLiteral();

            right = parseSecondaryExpression();

            expression = new Ast.Expr.Binary(operator, expression, right);

        }

        return expression;

    }

    /**
     * Parses the {@code secondary-expression} rule.
     */
    public Ast.Expr parseSecondaryExpression() throws ParseException {

        List<Ast.Expr> arguments = new ArrayList<>();

        String name = "";
        boolean var = false;
        boolean func = false;

        Ast.Expr expr = parsePrimaryExpression();
        Ast.Expr expr1 = null;

        if (match(".")) {

            var = true;

            if (match(Token.Type.IDENTIFIER)) {
                //get -1 since stream already advanced
                name = tokens.get(-1).getLiteral();

                String checkInt = name;
                checkInt = checkInt.substring(0,1);

                //if(checkInt.contains("[0-9]")){}

                if(checkInt.equals("1") || checkInt.equals("2") || checkInt.equals("3") || checkInt.equals("4") || checkInt.equals("5") || checkInt.equals("6") || checkInt.equals("7") || checkInt.equals("8") || checkInt.equals("9") ||  checkInt.equals("0")){
                    throw new ParseException("Invalid name.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                }

                // Access only when there are no parenthesis //

                if (match("(")) {

                    func = true;
                    //expr = parseExpression();
                    if(!peek(")")) {
                        if(tokens.has(0))  {
                            expr1 = parseExpression();
                            arguments.add(expr1);

                            while(match(",")) {
                                expr1 = parseExpression();
                                arguments.add(expr1);
                            }
                        }
                        else {
                            throw new ParseException("Expected closing parenthesis.", tokens.get(-1).getIndex());
                        }
                    }

                    if (!match(")")) {
                        throw new ParseException("Expected closing parenthesis.", tokens.get(-1).getIndex());
                        //add actual index
                    }
                }
                else {
                    Ast.Expr.Access exp = new Ast.Expr.Access(Optional.empty(), name);
                    arguments.add(exp);
                }
            }
            else {
                throw new ParseException("Invalid method", tokens.get(-1).getIndex());
            }

        }

        // Three cases
        // if just expr, return //
        if(!var && !func)
        {
            return expr;
        }

        // if just dot, return access
        if(var && !func)
        {
            return new Ast.Expr.Access(Optional.of(expr), name);
        }

        // if with parenthesis, return function
        if(func)
        {
            return new Ast.Expr.Function(Optional.of(expr), name, arguments);
        }

        throw new ParseException("Invalid secondary expression", tokens.get(-1).getIndex());
    }

    /**
     * Parses the {@code primary-expression} rule. This is the top-level rule
     * for expressions and includes literal values, grouping, variables, and
     * functions. It may be helpful to break these up into other methods but is
     * not strictly necessary.
     */
    public Ast.Expr parsePrimaryExpression() throws ParseException {
        if (match("TRUE")) {
            return new Ast.Expr.Literal(true);
        }
        else if(match("NIL")) {
            return new Ast.Expr.Literal(null);
        }
        else if(match("FALSE")) {
            return new Ast.Expr.Literal(false);
        }
        else if(match(Token.Type.INTEGER)) {

            BigInteger integer = new BigInteger(tokens.get(-1).getLiteral());
            return new Ast.Expr.Literal(integer);
        }
        else if(match(Token.Type.DECIMAL)) {
            BigDecimal dec = new BigDecimal(tokens.get(-1).getLiteral());
            return new Ast.Expr.Literal(dec);
        }
        else if(match(Token.Type.CHARACTER)) {
            //get rid of first '
            String chars = tokens.get(-1).getLiteral().substring(1);
            //get rid of second '
            chars = chars.substring(0,chars.length() - 1);
            chars = chars.replace("\\n","\n");
            chars = chars.replace("\\r","\r");
            chars = chars.replace("\\t","\t");
            chars = chars.replace("\\b","\b");
            chars = chars.replace("\\\\","\\");
            chars = chars.replace("\\\'","\'");
            chars = chars.replace("\\\"","\"");
            Character char1 = chars.charAt(0);

            return new Ast.Expr.Literal(char1);
        }
        else if(match(Token.Type.STRING)) {
            //get rid of first "
            String str = tokens.get(-1).getLiteral().substring(1);
            //get rid of second "
            str = str.substring(0,str.length() - 1);
            str = str.replace("\\n","\n");
            str = str.replace("\\r","\r");
            str = str.replace("\\t","\t");
            str = str.replace("\\b","\b");
            str = str.replace("\\\\","\\");
            str = str.replace("\\\'","\'");
            str = str.replace("\\\"","\"");

            return new Ast.Expr.Literal(str);
        }
        else if (match(Token.Type.IDENTIFIER)) {
            List<Ast.Expr> arguments = new ArrayList<>();

            String checkInt = tokens.get(-1).getLiteral();
            checkInt = checkInt.substring(0,1);

            //if(checkInt.contains("[0-9]")){}

            if(checkInt.equals("1") || checkInt.equals("2") || checkInt.equals("3") || checkInt.equals("4") || checkInt.equals("5") || checkInt.equals("6") || checkInt.equals("7") || checkInt.equals("8") || checkInt.equals("9") ||  checkInt.equals("0")){
                throw new ParseException("Invalid name.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }

            String name = "";
            Ast.Expr expr = null;
            //get -1 since stream already advanced
            name = tokens.get(-1).getLiteral();
            boolean func = false;

            if (match("(")) {
                func = true;
                if(!peek(")")) {
                    expr = parseExpression();
                    arguments.add(expr);

                    // Looking trailing commas
                    while(match(",")) {
                        expr = parseExpression();
                        arguments.add(expr);
                    }
                }

                if (!match(")")) {
                    throw new ParseException("Expected closing parenthesis.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
                    //add actual index
                }
            }
            if (func == true) {
                return new Ast.Expr.Function(Optional.empty(), name, arguments);
            }

            return new Ast.Expr.Access(Optional.empty(), name);
        }
        else if(match("(")) {
            Ast.Expr expr = parseExpression();
            if (!match(")")) {
                throw new ParseException("Expected closing parenthesis.", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
            }
            return new Ast.Expr.Group(expr);
        }
        else {
            throw new ParseException("Invalid primary expression", tokens.has(0) ? tokens.get(0).getIndex() : tokens.get(-1).getIndex() + tokens.get(-1).getLiteral().length());
        }
    }

    /**
     * As in the lexer, returns {@code true} if the current sequence of tokens
     * matches the given patterns. Unlike the lexer, the pattern is not a regex;
     * instead it is either a {@link Token.Type}, which matches if the token's
     * type is the same, or a {@link String}, which matches if the token's
     * literal is the same.
     *
     * In other words, {@code Token(IDENTIFIER, "literal")} is matched by both
     * {@code peek(Token.Type.IDENTIFIER)} and {@code peek("literal")}.
     */
    private boolean peek(Object... patterns) {
        for(int i = 0; i < patterns.length; i++) {
            if(!tokens.has(i)) {
                return false;
            }
            else if(patterns[i] instanceof Token.Type){
                if(patterns[i] != tokens.get(i).getType()) {
                    return false;
                }
            }
            else if(patterns[i] instanceof String) {
                if(!patterns[i].equals(tokens.get(i).getLiteral())) {
                    return false;
                }
            }
            else {
                throw new AssertionError("Invalid pattern object: " + patterns[i].getClass());
            }
        }
        return true;
    }

    /**
     * As in the lexer, returns {@code true} if {@link #peek(Object...)} is true
     * and advances the token stream.
     */
    private boolean match(Object... patterns) {
        boolean peek = peek(patterns);

        if(peek) {
            for(int i = 0; i < patterns.length; i++) {
                tokens.advance();
            }
        }
        return peek;
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at index + offset.
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Gets the token at index + offset.
         */
        public Token get(int offset) {
            return tokens.get(index + offset);
        }

        /**
         * Advances to the next token, incrementing the index.
         */
        public void advance() {
            index++;
        }

    }

}
