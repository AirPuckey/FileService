package com.rph.paritizer.fileaccessservice.exceptions;

import java.io.IOException;

public class NotFileException extends IOException {

    public NotFileException() {
    }

    public NotFileException(String message) {
        super(message);
    }

    public NotFileException(Throwable cause) {
        super(cause);
    }

    public NotFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
