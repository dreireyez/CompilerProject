package com.example.minicompilernew;

import javafx.application.Application;
import javafx.geometry.Insets;
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
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mini Compiler - JavaFX application (Fixed)
 *
 * Bug fixed: token regex used inner capturing groups which shifted matcher.group indices.
 * The string literal pattern now uses a non-capturing group so group numbers align correctly.
 *
 * Features:
 * - Left vertical buttons: Open File, Lexical Analysis, Syntax Analysis, Semantic Analysis, Clear
 * - Right side: Result (top) and Code (bottom)
 * - FileChooser with filters (.txt, .java, .mc)
 * - Lexical output per-line: <TYPE, "lexeme"> tokens printed on same line as source
 * - Syntax and Semantic analysis implementations (mock but functional)
 * - Buttons enabled/disabled according to analysis flow
 * - Allows typing/pasting code directly into the Code area (when no file opened)
 * - Graceful fallback if style.css is missing
 */
public class HelloApplication extends Application {

    private TextArea codeArea;
    private TextArea resultArea;

    private Button openBtn;
    private Button lexicalBtn;
    private Button syntaxBtn;
    private Button semanticBtn;
    private Button clearBtn;

    private final List<Token> tokenStream = new ArrayList<>();
    private boolean lexicalSucceeded = false;
    private boolean syntaxSucceeded = false;
    private boolean fileOpened = false; // track whether user opened a file vs typed directly

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Mini Compiler");

        // Left column buttons
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

        VBox leftButtons = new VBox(12, openBtn, lexicalBtn, syntaxBtn, semanticBtn, clearBtn);
        leftButtons.setPadding(new Insets(12));
        leftButtons.setPrefWidth(170);

        // Result area
        Label resultLabel = new Label("Result:");
        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setWrapText(false);
        resultArea.setPrefRowCount(12);

        VBox resultBox = new VBox(6, resultLabel, resultArea);
        resultBox.setPadding(new Insets(10));
        resultBox.setVgrow(resultArea, Priority.ALWAYS);

        // Code area
        Label codeLabel = new Label("Code:");
        codeArea = new TextArea();
        codeArea.setWrapText(false);
        codeArea.setPromptText("Paste or type source code here, or open a file...");
        codeArea.setPrefRowCount(10);

        VBox codeBox = new VBox(6, codeLabel, codeArea);
        codeBox.setPadding(new Insets(10));
        codeBox.setVgrow(codeArea, Priority.ALWAYS);

        VBox rightSide = new VBox(10, resultBox, codeBox);
        rightSide.setPadding(new Insets(0, 10, 10, 0));
        rightSide.setVgrow(resultBox, Priority.ALWAYS);
        rightSide.setVgrow(codeBox, Priority.ALWAYS);

        BorderPane root = new BorderPane();
        root.setLeft(leftButtons);
        root.setCenter(rightSide);

        Label topInfo = new Label("Open a source file (accepted: .txt, .java, .mc), or paste/type code into the Code area. Run analyses in order.");
        topInfo.setPadding(new Insets(8));
        root.setTop(topInfo);
        BorderPane.setAlignment(topInfo, javafx.geometry.Pos.CENTER_LEFT);

        // Initial button states
        resetButtonStates();

        // Actions
        openBtn.setOnAction(e -> openFile(primaryStage));
        lexicalBtn.setOnAction(e -> doLexicalAnalysis());
        syntaxBtn.setOnAction(e -> doSyntaxAnalysis());
        semanticBtn.setOnAction(e -> doSemanticAnalysis());
        clearBtn.setOnAction(e -> onClear());

        // Enable lexical when user types/pastes code directly (and no file opened)
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            boolean nonEmpty = newText != null && !newText.trim().isEmpty();
            if (!fileOpened) {
                // allow lexical if there's code typed
                lexicalBtn.setDisable(!nonEmpty);
            }
            // If user modified code after lexical/syntax, require re-analysis
            if (lexicalSucceeded || syntaxSucceeded) {
                lexicalSucceeded = false;
                syntaxSucceeded = false;
                syntaxBtn.setDisable(true);
                semanticBtn.setDisable(true);
                lexicalBtn.setDisable(!nonEmpty);
            }
        });

        Scene scene = new Scene(root, 960, 640);

        // keyboard accelerators
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+O"), () -> openBtn.fire());
        scene.getAccelerators().put(KeyCombination.keyCombination("Ctrl+L"), () -> lexicalBtn.fire());

        // Try loading stylesheet; fallback to inline styles if missing
        URL cssUrl = getClass().getResource("/style.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            // fallback minimal inline styling
            root.setStyle("-fx-font-family: 'Segoe UI'; -fx-background-color: #f4f5f7;");
            codeArea.setStyle("-fx-control-inner-background: white; -fx-background-radius: 6; -fx-border-radius: 6;");
            resultArea.setStyle("-fx-control-inner-background: white; -fx-background-radius: 6; -fx-border-radius: 6;");
            String btnStyle = "-fx-background-color: linear-gradient(#63a4ff, #4285f4); -fx-text-fill: white; -fx-background-radius: 6;";
            openBtn.setStyle(btnStyle);
            lexicalBtn.setStyle(btnStyle);
            syntaxBtn.setStyle(btnStyle);
            semanticBtn.setStyle(btnStyle);
            clearBtn.setStyle("-fx-background-color: #e0e0e0; -fx-text-fill: black; -fx-background-radius: 6;");
            topInfo.setStyle("-fx-padding: 6 6 6 12; -fx-text-fill: #333333;");
        }

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void resetButtonStates() {
        openBtn.setDisable(false);
        lexicalBtn.setDisable(true);
        syntaxBtn.setDisable(true);
        semanticBtn.setDisable(true);
        clearBtn.setDisable(false);
        lexicalSucceeded = false;
        syntaxSucceeded = false;
        tokenStream.clear();
        fileOpened = false;
    }

    private void openFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Source Code File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Source Files (*.txt, *.java, *.mc)", "*.txt", "*.java", "*.mc"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                codeArea.setText(sb.toString());
                resultArea.appendText("Opened file: " + file.getName() + "\n");
                // enable lexical (file opened)
                fileOpened = true;
                lexicalBtn.setDisable(false);
                syntaxBtn.setDisable(true);
                semanticBtn.setDisable(true);
                lexicalSucceeded = false;
                syntaxSucceeded = false;
                tokenStream.clear();
            } catch (IOException ex) {
                showAlert("File Error", "Could not read file: " + ex.getMessage());
            }
        } else {
            resultArea.appendText("No file selected.\n");
        }
    }

    // ---------------- LEXICAL ----------------
    private void doLexicalAnalysis() {
        resultArea.appendText("--- Lexical Analysis ---\n");
        tokenStream.clear();
        lexicalSucceeded = false;

        String fullCode = codeArea.getText();
        if (fullCode == null || fullCode.trim().isEmpty()) {
            resultArea.appendText("Error: No code to analyze. Paste or open a file.\n");
            return;
        }

        String[] lines = fullCode.split("\\r?\\n", -1);
        boolean hadError = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNo = i + 1;
            try {
                List<Token> toks = Lexer.tokenizeLine(line, lineNo);
                StringBuilder sb = new StringBuilder();
                for (Token t : toks) {
                    sb.append(formatTokenForOutput(t)).append(" ");
                    tokenStream.add(t);
                }
                resultArea.appendText(sb.toString().trim() + "\n");
            } catch (LexicalException le) {
                hadError = true;
                resultArea.appendText("Lexical Error (line " + lineNo + "): " + le.getMessage() + "\n");
            }
        }

        if (!hadError) {
            lexicalSucceeded = true;
            resultArea.appendText("Lexical Analysis completed successfully.\n");
            lexicalBtn.setDisable(true);
            syntaxBtn.setDisable(false);
            semanticBtn.setDisable(true);
        } else {
            resultArea.appendText("Lexical Analysis failed. Fix errors and retry.\n");
            syntaxBtn.setDisable(true);
            semanticBtn.setDisable(true);
        }
    }

    private String formatTokenForOutput(Token t) {
        String safe = t.lexeme.replace("\"", "\\\"");
        return "<" + t.type + ", \"" + safe + "\">";
    }

    // ---------------- SYNTAX ----------------
    private void doSyntaxAnalysis() {
        resultArea.appendText("--- Syntax Analysis ---\n");
        if (!lexicalSucceeded) {
            resultArea.appendText("Syntax Error: Lexical analysis not completed or failed.\n");
            return;
        }

        syntaxSucceeded = false;
        List<String> messages = new ArrayList<>();

        // Check semicolons for assignments on same line
        for (int i = 0; i < tokenStream.size(); i++) {
            Token t = tokenStream.get(i);
            if ("ASSIGNMENT".equals(t.type)) {
                int line = t.line;
                boolean foundSemi = false;
                for (int j = i + 1; j < tokenStream.size(); j++) {
                    Token look = tokenStream.get(j);
                    if (look.line != line) break;
                    if ("PUNCTUATION".equals(look.type) && ";".equals(look.lexeme)) { foundSemi = true; break; }
                }
                if (!foundSemi) messages.add("Missing semicolon at or after assignment on line " + line + ".");
            }
        }

        // Balance parentheses and braces
        int paren = 0, brace = 0;
        for (Token t : tokenStream) {
            if ("PUNCTUATION".equals(t.type)) {
                if ("(".equals(t.lexeme)) paren++;
                else if (")".equals(t.lexeme)) paren--;
                else if ("{".equals(t.lexeme)) brace++;
                else if ("}".equals(t.lexeme)) brace--;
                if (paren < 0) { messages.add("Unmatched closing parenthesis at line " + t.line + "."); paren = 0; }
                if (brace < 0) { messages.add("Unmatched closing brace at line " + t.line + "."); brace = 0; }
            }
        }
        if (paren > 0) messages.add("Unmatched opening parenthesis.");
        if (brace > 0) messages.add("Unmatched opening brace.");

        if (messages.isEmpty()) {
            syntaxSucceeded = true;
            resultArea.appendText("Syntax OK.\n");
            syntaxBtn.setDisable(true);
            semanticBtn.setDisable(false);
        } else {
            resultArea.appendText("Syntax Errors:\n");
            for (String m : messages) resultArea.appendText(m + "\n");
            resultArea.appendText("Fix syntax errors and try again.\n");
            syntaxBtn.setDisable(false);
            semanticBtn.setDisable(true);
        }
    }

    // ---------------- SEMANTIC ----------------
    private void doSemanticAnalysis() {
        resultArea.appendText("--- Semantic Analysis ---\n");
        if (!syntaxSucceeded) {
            resultArea.appendText("Semantic Error: Syntax analysis not completed or failed.\n");
            return;
        }

        List<String> messages = new ArrayList<>();
        Map<String, String> symbolTable = new HashMap<>();
        Map<String, Integer> declLine = new HashMap<>();
        Set<String> used = new HashSet<>();

        // Build symbol table from declarations
        for (int i = 0; i < tokenStream.size(); i++) {
            Token t = tokenStream.get(i);
            if ("KEYWORD".equals(t.type)) {
                if (i + 1 < tokenStream.size()) {
                    Token next = tokenStream.get(i + 1);
                    if ("IDENTIFIER".equals(next.type)) {
                        symbolTable.put(next.lexeme, t.lexeme);
                        declLine.put(next.lexeme, next.line);
                    }
                }
            }
        }

        // Check assignments and usages
        for (int i = 0; i < tokenStream.size(); i++) {
            Token t = tokenStream.get(i);
            if ("IDENTIFIER".equals(t.type)) {
                // assignment pattern
                if (i + 1 < tokenStream.size() && "ASSIGNMENT".equals(tokenStream.get(i + 1).type)) {
                    String var = t.lexeme;
                    used.add(var);
                    if (!symbolTable.containsKey(var)) {
                        messages.add("Variable '" + var + "' assigned before declaration (line " + t.line + ").");
                    } else {
                        if (i + 2 < tokenStream.size()) {
                            Token rhs = tokenStream.get(i + 2);
                            String declared = symbolTable.get(var);
                            if ("STRING".equals(rhs.type) && "int".equals(declared)) {
                                messages.add("Type error: cannot assign string to int variable '" + var + "' (line " + rhs.line + ").");
                            }
                        }
                    }
                } else {
                    if (symbolTable.containsKey(t.lexeme)) used.add(t.lexeme);
                }
            }
        }

        // Unused declarations
        for (String v : symbolTable.keySet()) {
            if (!used.contains(v)) messages.add("Warning: variable '" + v + "' declared at line " + declLine.getOrDefault(v, -1) + " but not used.");
        }

        if (messages.isEmpty()) {
            resultArea.appendText("Semantic OK. No issues found.\n");
        } else {
            resultArea.appendText("Semantic Warnings/Errors:\n");
            for (String m : messages) resultArea.appendText(m + "\n");
        }

        semanticBtn.setDisable(true);
    }

    private void onClear() {
        codeArea.clear();
        resultArea.clear();
        tokenStream.clear();
        lexicalSucceeded = false;
        syntaxSucceeded = false;
        fileOpened = false;
        resetButtonStates();
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setTitle(title);
        a.setHeaderText(null);
        a.showAndWait();
    }

    // ---------------- Token & Lexer ----------------
    private static class Token {
        final String type;
        final String lexeme;
        final int line;

        Token(String type, String lexeme, int line) {
            this.type = type;
            this.lexeme = lexeme;
            this.line = line;
        }
    }

    private static class LexicalException extends Exception {
        LexicalException(String m) { super(m); }
    }

    private static class Lexer {
        private static final String identifier = "[a-zA-Z_][a-zA-Z0-9_]*";
        private static final String number = "\\d+(?:\\.\\d+)?";
        // Use non-capturing group inside the string literal to avoid shifting groups
        private static final String stringLiteral = "\"(?:.*?)\"";
        private static final String operator = "==|!=|<=|>=|=|\\+|\\-|\\*|/|<|>";
        private static final String punctuation = "[(){};,]";

        private static final Pattern TOKEN_PATTERN = Pattern.compile(
                String.format("(%s)|(%s)|(%s)|(%s)|(%s)", stringLiteral, identifier, number, operator, punctuation));

        private static final Set<String> keywords = new HashSet<>(Arrays.asList(
                "int", "float", "double", "if", "else", "while", "for", "return", "void", "String"
        ));

        static List<Token> tokenizeLine(String line, int lineNumber) throws LexicalException {
            List<Token> tokens = new ArrayList<>();
            Matcher matcher = TOKEN_PATTERN.matcher(line);
            int index = 0;
            while (matcher.find()) {
                if (matcher.start() != index) {
                    String skipped = line.substring(index, matcher.start());
                    if (skipped.matches(".*\\S.*")) {
                        throw new LexicalException("Unrecognized token near: '" + skipped.trim() + "'");
                    }
                }

                String strLit = matcher.group(1); // now entire string literal (non-capturing inner group prevents shift)
                String id = matcher.group(2);
                String num = matcher.group(3);
                String op = matcher.group(4);
                String punc = matcher.group(5);

                if (strLit != null) {
                    tokens.add(new Token("STRING", strLit, lineNumber));
                } else if (id != null) {
                    if (keywords.contains(id)) tokens.add(new Token("KEYWORD", id, lineNumber));
                    else tokens.add(new Token("IDENTIFIER", id, lineNumber));
                } else if (num != null) {
                    tokens.add(new Token("NUMBER", num, lineNumber));
                } else if (op != null) {
                    if ("=".equals(op)) tokens.add(new Token("ASSIGNMENT", op, lineNumber));
                    else tokens.add(new Token(mapOperatorToType(op), op, lineNumber));
                } else if (punc != null) {
                    tokens.add(new Token("PUNCTUATION", punc, lineNumber));
                }

                index = matcher.end();
            }

            if (index < line.length()) {
                String trailing = line.substring(index);
                if (trailing.matches(".*\\S.*")) {
                    throw new LexicalException("Unrecognized trailing text: '" + trailing.trim() + "'");
                }
            }
            return tokens;
        }

        private static String mapOperatorToType(String op) {
            switch (op) {
                case "+": return "OPERATOR";
                case "-": return "OPERATOR";
                case "*": return "OPERATOR";
                case "/": return "OPERATOR";
                case "==": return "OPERATOR";
                case "!=": return "OPERATOR";
                case "<": return "OPERATOR";
                case ">": return "OPERATOR";
                case "<=": return "OPERATOR";
                case ">=": return "OPERATOR";
                default: return "OPERATOR";
            }
        }
    }
}
