package dev.kairo.core;

public record SourceLocation(String file, int line, int column) {
    public static final SourceLocation UNKNOWN = new SourceLocation("<unknown>", -1, -1);

    @Override
    public String toString() {
        return line > 0 ? file + ":" + line + ":" + column : file;
    }
}
