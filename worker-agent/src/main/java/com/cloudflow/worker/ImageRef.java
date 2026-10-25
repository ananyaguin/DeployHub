package com.cloudflow.worker;

/**
 * Same rule as the backend image pattern. A tag or a sha256 digest is required.
 */
public final class ImageRef {

    public static final String PATTERN =
            "^[a-z0-9]+(?:[._-][a-z0-9]+)*(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)*(?::[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}|@sha256:[a-f0-9]{64})$";

    private ImageRef() {}

    public static boolean valid(String image) {
        return image != null && image.length() <= 255 && image.matches(PATTERN);
    }
}
