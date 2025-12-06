package com.dragoqc.adaptivehordes.constants;

public class ColorConstants {
	// ANSI Color codes for console output
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    
    // Styles
    public static final String BOLD = "\u001B[1m";
    public static final String UNDERLINE = "\u001B[4m";
    
    // Private constructor to prevent instantiation
    private ColorConstants() {
        throw new UnsupportedOperationException("This is a constants class");
    }
}
