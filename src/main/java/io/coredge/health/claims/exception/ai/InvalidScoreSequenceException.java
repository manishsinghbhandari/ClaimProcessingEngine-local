package io.coredge.health.claims.exception.ai;


import io.coredge.health.claims.exception.base.BusinessException;
import io.coredge.health.claims.exception.models.ErrorCode;

public class InvalidScoreSequenceException extends BusinessException {

    private static final ErrorCode CODE = ErrorCode.INVALID_AI_SCORE_SEQUENCE_EXCEPTION;

    public InvalidScoreSequenceException(double currMin, double prevMax) {
        super(CODE, String.format(
                "Invalid range sequence: next range should start at %.2f (found %.2f).",
                prevMax + 1, currMin
        ));
    }

    public InvalidScoreSequenceException(String message) {
            super(CODE, message);
        }

    }

