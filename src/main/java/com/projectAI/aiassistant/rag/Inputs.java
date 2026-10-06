package com.projectAI.aiassistant.rag;

public final class Inputs {

    private Inputs() {}

    public static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maximum) {
            throw new IllegalArgumentException(field + " exceeds " + maximum + " characters");
        }
        return value.strip();
    }
}
