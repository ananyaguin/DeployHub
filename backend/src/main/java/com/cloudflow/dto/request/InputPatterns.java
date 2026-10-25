package com.cloudflow.dto.request;

public final class InputPatterns {

    public static final String UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    public static final String SLUG = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    public static final String WORKER_NAME = "^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$";

    private InputPatterns() {}
}
