package plc.project;

import java.util.*;
import java.util.concurrent.Callable;

/**
 * The lexer works through three main functions:
 *
 *  - {@link #lex()}, which repeatedly calls lexToken() and skips whitespace
 *  - {@link #lexToken()}, which lexes the next token
 *  - {@link CharStream}, which manages the state of the lexer and literals
 *
 * If the lexer fails to parse something (such as an unterminated string) you
 * should throw a {@link ParseException} with an index at the character which is
 * invalid or missing.
 *
 * The {@link #peek(String...)} and {@link #match(String...)} functions are
 * helpers you need to use, they will make the implementation a lot easier.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    /**
     * Repeatedly lexes the input using {@link #lexToken()}, also skipping over
     * whitespace where appropriate.
     */
    public List<Token> lex() {
        //create list to return
        List<Token> Tokens  = new ArrayList<Token>();

        while(chars.has(0)) {
            //if match escape character
            if(peek("[\b\n\r\t ]")) {
                chars.advance();
                chars.skip();
            }
            else {
                //otherwise
                //add a token (call lexToken)
                Tokens.add(lexToken());
            }
        }

        return Tokens;
    }

    /**
     * This method determines the type of the next token, delegating to the
     * appropriate lex method. As such, it is best for this method to not change
     * the state of the char stream (thus, use peek not match).
     *
     * The next character should start a valid token since whitespace is handled
     * by {@link #lex()}
     */
    public Token lexToken() {
        //peeks the first characters of new token to find which method to invoke
        //should have lots of if else statements

        if(peek("[A-Za-z_]")) {
            return lexIdentifier();
        }
        else if(peek("[+-[0-9]]")) {
            if(peek("[-+]")) {
                chars.advance();
            }
            return lexNumber();
        }
        else if(peek("\'")) {
            chars.advance();
            return lexCharacter();
        }
        else if(peek("[\"]")) {
            chars.advance();
            return lexString();
        }
        else if(peek("[\b\n\r\t]")) {
            return null;
        }
        else if(peek(".")) {
            return  lexOperator();
        }
        else {
            throw new ParseException("Parse exception", chars.index);
        }
    }

    public Token lexIdentifier() {
        while(match("[A-Za-z0-9_-]"))
        {
            //match looks at the first char
            //if peek is true
            //it advances to the next character (index++, length++)
        }
        return chars.emit(Token.Type.IDENTIFIER);
    }

    public Token lexNumber() {
        if(peek("[0-9]")) {
            boolean decimal = false;
            while(true)
            {
                if(!decimal && match("[0-9]", "\\." , "[0-9]"))
                {
                    decimal = true;
                }
                else if(decimal && match("\\."))
                {
                    // Stop reading //
                    return chars.emit(Token.Type.DECIMAL);
                    // return decimal //
                }
                if(!match("[0-9]"))
                {
                    break;
                }
            }

            if(decimal)
            {
                return chars.emit(Token.Type.DECIMAL);
            }

            return chars.emit(Token.Type.INTEGER);

        }
        else {
            if(chars.length > 0) {
                chars.length--;
                chars.index--;

                return lexOperator();
            }
        }
        return chars.emit(Token.Type.INTEGER);
    }

    public Token lexCharacter() {
        //while it isn't another single quote
        if(match("[\\\\]", "[bnrt'\"\\\\]", "[\']") || match(".", "[\']"))
        {
            // return statement
            return chars.emit(Token.Type.CHARACTER);
        }
        else
        {
            if(peek(".")) {
                chars.index++;
            }
            throw new ParseException("Invalid at index: \r", chars.index);
        }
    }

    public Token lexString() {
        while(chars.has(0) && !match("\"")) {
            if(peek("[\\\\]"))
            {
                chars.advance();
                if(!peek("[bnrt'\"\\\\]"))
                {
                    throw new ParseException("Invalid escape", chars.index);
                }
            }
            chars.advance();
        }
        if(chars.get(-1) != '\"') {
            throw new ParseException("Error", chars.index);
        }
        return chars.emit(Token.Type.STRING);
    }

    public void lexEscape() {
        System.out.println("Lex escape");
    }

    public Token lexOperator() {
        if(match("[<>!=]", "=")) {
            return chars.emit(Token.Type.OPERATOR);
        }
        else {
            if(match(".")) {
                if(chars.get(-1) == ' ') {
                    throw new ParseException("Invalid", chars.index);
                }
                return chars.emit(Token.Type.OPERATOR);
            }
            throw new ParseException("Invalid", chars.index);
        }
    }

    /**
     * Returns true if the next sequence of characters match the given patterns,
     * which should be a regex. For example, {@code peek("a", "b", "c")} would
     * return true if the next characters are {@code 'a', 'b', 'c'}.
     */
    //examines sequence of paramaters (types) to determine if they match the next set
    //of elements (beginning from current index) in stream
    public boolean peek(String... patterns) {
        for(int i = 0; i < patterns.length; i++) {
            if(!chars.has(i) || !String.valueOf(chars.get(i)).matches(patterns[i])) {
                return false;
            }
        }
        return  true;
    }

    /**
     * Returns true in the same way as {@link #peek(String...)}, but also
     * advances the character stream past all matched characters if peek returns
     * true. Hint - it's easiest to have this method simply call peek.
     */
    public boolean match(String... patterns) {
        boolean peek = peek(patterns);

        if(peek) {
            for (int i = 0; i < patterns.length; i++) {
                chars.advance();
            }
        }
        return  peek;
    }

    /**
     * A helper class maintaining the input string, current index of the char
     * stream, and the current length of the token being matched.
     *
     * You should rely on peek/match for state management in nearly all cases.
     * The only field you need to access is {@link #index} for any {@link
     * ParseException} which is thrown.
     */
    public static final class CharStream {
        //input = source string
        private final String input;
        //index = position within source (input)
        private int index = 0;
        //length = size of current token
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }

        //checks if input has *offset amount* of characters remaining
        public boolean has(int offset) {
            return index + offset < input.length();
        }

        //returns character at offset position
        public char get(int offset) {
            return input.charAt(index + offset);
        }

        //moves to next character position in input
        public void advance() {
            index++;
            length++;
        }

        //resets size of current token to zero;
        public void skip() {
            length = 0;
        }

        //instantiates current token
        public Token emit(Token.Type type) {
            int start = index - length;
            //Let x = 0
            //index 3
            //length 3
            //start 0

            skip();
            //set length to 0

            return new Token(type, input.substring(start, index), start);
            //token: identifier, Let, 0
        }

    }

}