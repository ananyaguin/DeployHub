package com.cloudflow.dto.response;

public record OperationsResponse(
        long queued,
        long running,
        long succeeded,
        long failed,
        long retried,
        long rollbackSucceeded,
        long rollbackFailed,
        long schedulingBlocked,
        long workersOnline,
        long workersOffline,
        boolean capacityDeclared) {}
