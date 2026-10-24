package com.cloudflow.service;

import com.cloudflow.exception.ConflictException;
import com.cloudflow.exception.ForbiddenException;
import com.cloudflow.exception.NotFoundException;
import com.cloudflow.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

@Service
public class ResourceState {

    public void requirePresent(Object resource) {
        if (resource == null) {
            throw new NotFoundException();
        }
    }

    public void requireAbsent(Object existing) {
        if (existing != null) {
            throw new ConflictException();
        }
    }

    public void requireAuthenticated(Object principal) {
        if (principal == null) {
            throw new UnauthorizedException();
        }
    }

    public void requireAllowed(boolean allowed) {
        if (!allowed) {
            throw new ForbiddenException();
        }
    }
}
