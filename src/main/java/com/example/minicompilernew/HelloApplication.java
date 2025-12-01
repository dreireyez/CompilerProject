package com.example.minicompilernew;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mini Compiler - JavaFX application
 * - Left: vertical button column (Open File, Lexical Analysis, Syntax Analysis, Semantic Analysis, Clear)
 * - Right/top: Result label + Result TextArea
 * - Right/bottom: Code TextArea
 *
 * Behavior follows the user's specification: only Open File enabled at start; enabling flows from lexical -> syntax -> semantic.
 * The analyses are mock implementations but demonstrate tokenization, grammar checks, and simple semantic checks.
 */
public class HelloApplication extends Application {

    private TextArea codeArea;
    private TextArea resultArea;

    private Button openBtn;
    private Button lexicalBtn;
    private Button syntaxBtn;
    private Button semanticBtn;
    private Button clearBtn;

    // State tracking
    private List<Token> lastTokens = new ArrayList<>();
    private boolean lexicalSucceeded = false;
    private boolean syntaxSucceeded = false;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Mini Compiler");

        // Left column - buttons
        openBtn = new Button("Open File");
        openBtn.setMaxWidth(Double.MAX_VALUE);
        lexicalBtn = new Button("Lexical Analysis");
        lexicalBtn.setMaxWidth(Double.MAX_VALUE);
        syntaxBtn = new Button("Syntax Analysis");
        syntaxBtn.setMaxWidth(Double.MAX_VALUE);
        semanticBtn = new Button("Semantic Analysis");
        semanticBtn.setMaxWidth(Double.MAX_VALUE);
        clearBtn = new Button("Clear");
        clearBtn.setMaxWidth(Double.MAX_VALUE);

        VBox leftButtons = new VBox(10, openBtn, lexicalBtn, syntaxBtn, semanticBtn, clearBtn);
        leftButtons.setPadding(new Insets(10));
        leftButtons.setPrefWidth(160);

        // Result area (right top)
        Label resultLabel = new Label("Result:");
        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(false);
        resultArea.setPrefRowCount(12);

        VBox resultBox = new VBox(6, resultLabel, resultArea);
        resultBox.setPadding(new Insets(10));
        resultBox.setVgrow(resultArea, Priority.ALWAYS);

        // Code area (right bottom)
        codeArea = new TextArea();
        codeArea.setWrapText(false);
        codeArea.setPromptText("Code will appear here after opening a file...");
        codeArea.setPrefRowCount(10);

        VBox codeBox = new VBox(6, new Label("Code:"), codeArea);
        codeBox.setPadding(new Insets(10));
        codeBox.setVgrow(codeArea, Priority.ALWAYS);

        // Right side layout: result on top, code on bottom
        VBox rightSide = new VBox(8, resultBox, codeBox);
        rightSide.setPadding(new Insets(0,10,10,0));
        rightSide.setVgrow(codeBox, Priority.ALWAYS);
        rightSide.setVgrow(resultBox, Priority.ALWAYS);

        // Top area: (we implement behavior; no visible explanatory text required)
        BorderPane root = new BorderPane();
        root.setLeft(leftButtons);
        root.setCenter(rightSide);

        // Initial button states
        setInitialButtonStates();

        // Button actions
        openBtn.setOnAction(e -> onOpenFile(primaryStage));
        lexicalBtn.setOnAction(e -> onLexicalAnalysis());
        syntaxBtn.setOnAction(e -> onSyntaxAnalysis());
        semanticBtn.setOnAction(e -> onSemanticAnalysis());
        clearBtn.setOnAction(e -> onClear());

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.show();

        // Optional: keyboard shortcuts
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+O"), () -> openBtn.fire());
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+L"), () -> lexicalBtn.fire());
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+S"), () -> syntaxBtn.fire());
    }

    private void setInitialButtonStates() {
        openBtn.setDisable(false);
        lexicalBtn.setDisable(true);
        syntaxBtn.setDisable(true);
        semanticBtn.setDisable(true);
        clearBtn.setDisable(false);
    }

    private void onOpenFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Source File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.java", "*.c", "*.cpp"),
                new FileChooser.ExtensionFilter("All Files", "*")
        );

        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                codeArea.setText(sb.toString());
                resultArea.appendText("Opened file: " + file.getName() + "\n");
                // Enable only lexical analysis
                lexicalBtn.setDisable(false);
                syntaxBtn.setDisable(true);
                semanticBtn.setDisable(true);
                lexicalSucceeded = false;
                syntaxSucceeded = false;
                lastTokens.clear();
            } catch (IOException ex) {
                showAlert("File Error", "Could not read file: " + ex.getMessage());
            }
        } else {
            resultArea.appendText("No file selected.\n");
        }
    }

    private void onLexicalAnalysis() {
        resultArea.appendText("--- Lexical Analysis ---\n");
        String code = codeArea.getText();
        if (code == null || code.trim().isEmpty()) {
            resultArea.appendText("Error: No code loaded. Please open a file first.\n");
            return;
        }

        try {
            lastTokens = lexicalAnalysis(code);
            if (lastTokens.isEmpty()) {
                resultArea.appendText("No tokens found (empty or whitespace-only file).\n");
                lexicalSucceeded = false;
            } else {
                resultArea.appendText("Tokens:\n");
                for (Token t : lastTokens) {
                    resultArea.appendText(t + "\n");
                }
                lexicalSucceeded = true;
                // Disable lexical, enable syntax
                lexicalBtn.setDisable(true);
                syntaxBtn.setDisable(false);
                semanticBtn.setDisable(true);
            }
        } catch (LexicalException le) {
            resultArea.appendText("Lexical Error: " + le.getMessage() + "\n");
            lexicalSucceeded = false;
        } catch (Exception ex) {
            resultArea.appendText("Unexpected lexical error: " + ex.getMessage() + "\n");
            lexicalSucceeded = false;
        }
    }

    private void onSyntaxAnalysis() {
        resultArea.appendText("--- Syntax Analysis ---\n");
        if (!lexicalSucceeded) {
            resultArea.appendText("Syntax Error: Lexical analysis not completed or failed.\n");
            return;
        }

        try {
            SyntaxResult sr = syntaxAnalysis(lastTokens);
            if (sr.ok) {
                resultArea.appendText("Syntax OK.\n");
                if (!sr.messages.isEmpty()) {
                    for (String m : sr.messages) resultArea.appendText(m + "\n");
                }
                syntaxSucceeded = true;
                syntaxBtn.setDisable(true);
                semanticBtn.setDisable(false);
            } else {
                resultArea.appendText("Syntax Errors:\n");
                for (String m : sr.messages) resultArea.appendText(m + "\n");
                syntaxSucceeded = false;
            }
        } catch (Exception ex) {
            resultArea.appendText("Unexpected syntax error: " + ex.getMessage() + "\n");
            syntaxSucceeded = false;
        }
    }

    private void onSemanticAnalysis() {
        resultArea.appendText("--- Semantic Analysis ---\n");
        if (!syntaxSucceeded) {
            resultArea.appendText("Semantic Error: Syntax analysis not completed or failed.\n");
            return;
        }

        try {
            List<String> semMsgs = semanticAnalysis(lastTokens);
            if (semMsgs.isEmpty()) {
                resultArea.appendText("Semantic OK. No issues found.\n");
            } else {
                resultArea.appendText("Semantic Warnings/Errors:\n");
                for (String m : semMsgs) resultArea.appendText(m + "\n");
            }
            semanticBtn.setDisable(true);
        } catch (Exception ex) {
            resultArea.appendText("Unexpected semantic error: " + ex.getMessage() + "\n");
        }
    }

    private void onClear() {
        codeArea.clear();
        resultArea.clear();
        lastTokens.clear();
        lexicalSucceeded = false;
        syntaxSucceeded = false;
        setInitialButtonStates();
    }

    // ---------------- Mock Analyses ----------------

    /**
     * Very simple tokenizer that recognizes identifiers, numbers, operators, punctuation, and keywords.
     */
    private List<Token> lexicalAnalysis(String code) throws LexicalException {
        List<Token> tokens = new ArrayList<>();

        // Simple token regex pieces
        String identifier = "[a-zA-Z_][a-zA-Z_0-9]*";
        String number = "\\d+(?:\\.\\d+)?";
        String operator = "==|!=|<=|>=|=|\\+|\\-|\\*|/|<|>";
        String punctuation = "[(){};,]";
        String stringLiteral = '"' + "(.*?)" + '"';

        // Combined pattern
        Pattern tokenPattern = Pattern.compile(String.format("(%s)|(%s)|(%s)|(%s)|(%s)", identifier, number, operator, punctuation, stringLiteral));

        Matcher matcher = tokenPattern.matcher(code);
        int index = 0;
        while (matcher.find()) {
            if (matcher.start() != index) {
                // Found some unrecognized characters between tokens
                String skipped = code.substring(index, matcher.start()).trim();
                if (!skipped.isEmpty()) {
                    // If the skipped text contains non-whitespace like @ or #, treat as lexical error
                    if (skipped.matches(".*[^\\s].*")) {
                        // allow comments: // ... or /* ... */ are ignored earlier? Not implemented - treat as error
                        throw new LexicalException("Unrecognized token starting at: '" + skipped + "'");
                    }
                }
            }

            String id = matcher.group(1);
            String num = matcher.group(2);
            String op = matcher.group(3);
            String punc = matcher.group(4);
            String strLit = matcher.group(5);

            if (id != null) {
                Token.Type ttype = Token.Type.IDENTIFIER;
                if (isKeyword(id)) ttype = Token.Type.KEYWORD;
                tokens.add(new Token(ttype, id));
            } else if (num != null) {
                tokens.add(new Token(Token.Type.NUMBER, num));
            } else if (op != null) {
                tokens.add(new Token(Token.Type.OPERATOR, op));
            } else if (punc != null) {
                tokens.add(new Token(Token.Type.PUNCTUATION, punc));
            } else if (strLit != null) {
                tokens.add(new Token(Token.Type.STRING, strLit));
            }

            index = matcher.end();
        }

        // check trailing unrecognized characters
        if (index < code.length()) {
            String trailing = code.substring(index).trim();
            if (!trailing.isEmpty()) {
                throw new LexicalException("Unrecognized trailing text: '" + trailing + "'");
            }
        }

        return tokens;
    }

    private boolean isKeyword(String s) {
        String[] kws = {"int", "float", "double", "if", "else", "while", "for", "return", "void", "String"};
        for (String k : kws) if (k.equals(s)) return true;
        return false;
    }

    /**
     * Very small syntax checker: checks statement termination with semicolons and simple assignment grammar.
     */
    private SyntaxResult syntaxAnalysis(List<Token> tokens) {
        List<String> messages = new ArrayList<>();
        boolean ok = true;

        // Quick example: check that semicolons end top-level statements
        // We'll scan for identifiers followed by '=' and ensure there is a terminating ';' later in the statement
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type == Token.Type.IDENTIFIER) {
                // Look ahead for '=' operator
                if (i + 1 < tokens.size() && tokens.get(i + 1).type == Token.Type.OPERATOR && "=".equals(tokens.get(i + 1).text)) {
                    // find semicolon after this assignment
                    boolean foundSemicolon = false;
                    for (int j = i + 2; j < tokens.size(); j++) {
                        if (tokens.get(j).type == Token.Type.PUNCTUATION && ";".equals(tokens.get(j).text)) {
                            foundSemicolon = true; break;
                        }
                        // if we encounter another identifier followed by '=', maybe the semicolon is missing
                    }
                    if (!foundSemicolon) {
                        messages.add("Missing semicolon at or after assignment to '" + t.text + "'.");
                        ok = false;
                    }
                }
            }
        }

        // Check parentheses balance as a very basic syntax rule
        int paren = 0, brace = 0;
        for (Token t : tokens) {
            if (t.type == Token.Type.PUNCTUATION) {
                if ("(".equals(t.text)) paren++;
                else if (")".equals(t.text)) paren--;
                else if ("{".equals(t.text)) brace++;
                else if ("}".equals(t.text)) brace--;
                if (paren < 0) { messages.add("Unmatched closing parenthesis."); ok = false; paren = 0; }
                if (brace < 0) { messages.add("Unmatched closing brace."); ok = false; brace = 0; }
            }
        }
        if (paren > 0) { messages.add("Unmatched opening parenthesis."); ok = false; }
        if (brace > 0) { messages.add("Unmatched opening brace."); ok = false; }

        return new SyntaxResult(ok, messages);
    }

    /**
     * Simple semantic checks: variable declaration before use and basic type checks for numeric literals.
     * We'll consider tokens and look for patterns like: <type> <id> = <number> ;
     */
    private List<String> semanticAnalysis(List<Token> tokens) {
        List<String> messages = new ArrayList<>();
        Map<String, String> symbolTable = new HashMap<>(); // var -> type

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            // Declaration: keyword (type) followed by identifier
            if (t.type == Token.Type.KEYWORD) {
                if (i + 1 < tokens.size() && tokens.get(i + 1).type == Token.Type.IDENTIFIER) {
                    String varName = tokens.get(i + 1).text;
                    String varType = t.text; // e.g., int, float
                    symbolTable.put(varName, varType);
                }
            }

            // Usage: identifier followed by '=' then something
            if (t.type == Token.Type.IDENTIFIER) {
                if (i + 1 < tokens.size() && tokens.get(i + 1).type == Token.Type.OPERATOR && "=".equals(tokens.get(i + 1).text)) {
                    String varName = t.text;
                    // find right-hand side token
                    if (i + 2 < tokens.size()) {
                        Token rhs = tokens.get(i + 2);
                        // check declared
                        if (!symbolTable.containsKey(varName)) {
                            messages.add("Variable '" + varName + "' assigned before declaration.");
                        } else {
                            String declaredType = symbolTable.get(varName);
                            // simple type mismatch: if declared int but rhs is STRING
                            if ("int".equals(declaredType) && rhs.type == Token.Type.STRING) {
                                messages.add("Type error: cannot assign string to int variable '" + varName + "'.");
                            }
                        }
                    }
                }
            }
        }

        // Simple check: flagged unused declarations
        for (String var : symbolTable.keySet()) {
            boolean used = false;
            for (Token tok : tokens) {
                if (tok.type == Token.Type.IDENTIFIER && tok.text.equals(var)) {
                    used = true; break;
                }
            }
            if (!used) messages.add("Warning: variable '" + var + "' declared but not used.");
        }

        return messages;
    }

    // ---------------- Helpers and small classes ----------------

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private static class Token {
        enum Type {IDENTIFIER, NUMBER, OPERATOR, PUNCTUATION, KEYWORD, STRING}
        final Type type;
        final String text;

        Token(Type type, String text) {
            this.type = type; this.text = text;
        }

        public String toString() {
            return String.format("[%s] %s", type, text);
        }
    }

    private static class LexicalException extends Exception {
        LexicalException(String msg) { super(msg); }
    }

    private static class SyntaxResult {
        final boolean ok;
        final List<String> messages;
        SyntaxResult(boolean ok, List<String> messages) { this.ok = ok; this.messages = messages; }
    }
}