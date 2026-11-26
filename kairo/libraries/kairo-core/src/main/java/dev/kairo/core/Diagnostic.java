package dev.kairo.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Diagnostic {
    public enum Severity {
        INFO, WARNING, ERROR
    }

    private final Severity severity;
    private final String code;
    private final String message;
    private final SourceLocation location;

    public Diagnostic(Severity severity, String code, String message, SourceLocation location) {
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.location = location == null ? SourceLocation.UNKNOWN : location;
    }

    public static Diagnostic error(String code, String message, SourceLocation location) {
        return new Diagnostic(Severity.ERROR, code, message, location);
    }

    public static Diagnostic warning(String code, String message, SourceLocation location) {
        return new Diagnostic(Severity.WARNING, code, message, location);
    }

    public Severity severity() { return severity; }
    public String code() { return code; }
    public String message() { return message; }
    public SourceLocation location() { return location; }

    @Override
    public String toString() {
        return "[" + severity + "] " + code + " (" + location + "): " + message;
    }

    public static final class Bag {
        private final List<Diagnostic> items = new ArrayList<>();

        public void add(Diagnostic diagnostic) {
            items.add(diagnostic);
        }

        public void error(String code, String message, SourceLocation location) {
            items.add(Diagnostic.error(code, message, location));
        }

        public void warning(String code, String message, SourceLocation location) {
            items.add(Diagnostic.warning(code, message, location));
        }

        public boolean hasErrors() {
            return items.stream().anyMatch(d -> d.severity() == Severity.ERROR);
        }

        public List<Diagnostic> all() {
            return Collections.unmodifiableList(items);
        }
    }
}
