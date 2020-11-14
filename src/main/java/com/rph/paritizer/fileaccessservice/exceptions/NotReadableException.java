package com.rph.paritizer.fileaccessservice.exceptions;

import java.io.IOException;

public class NotReadableException extends IOException {

    public NotReadableException() {
    }

    public NotReadableException(String message) {
        super(message);
    }

    public NotReadableException(Throwable cause) {
        super(cause);
    }

    public NotReadableException(String message, Throwable cause) {
        super(message, cause);
    }
}
