package com.cloudflow.dto.request;

public final class InputPatterns {

    public static final String UUID =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    public static final String SLUG = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    public static final String WORKER_NAME = "^[A-Za-z0-9][A-Za-z0-9._-]{0,119}$";

    public static final String IMAGE =
            "^[a-z0-9]+(?:[._-][a-z0-9]+)*(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)*(?::[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}|@sha256:[a-f0-9]{64})$";

    public static final String CONTAINER_ID = "^[a-f0-9]{12,64}$";

    private InputPatterns() {}
}
