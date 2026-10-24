package com.cloudflow.dto.request;

public final class InputPatterns {

    public static final String UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    public static final String SLUG = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    private InputPatterns() {}
}
