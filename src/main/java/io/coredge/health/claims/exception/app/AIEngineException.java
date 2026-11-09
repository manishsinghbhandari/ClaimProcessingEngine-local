package io.coredge.health.claims.exception.app;

public class AIEngineException extends RuntimeException {
    public AIEngineException(String message , Throwable cause) {
        super(message , cause);
    }
    public AIEngineException(String message){
        super(message);
    }
}
