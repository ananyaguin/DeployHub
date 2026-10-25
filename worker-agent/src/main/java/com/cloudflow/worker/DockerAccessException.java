package com.cloudflow.worker;

class DockerAccessException extends RuntimeException {

    DockerAccessException() {
        super("Docker is unavailable");
    }
}
