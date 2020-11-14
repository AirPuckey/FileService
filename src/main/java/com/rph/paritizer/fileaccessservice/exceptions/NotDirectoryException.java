package com.rph.paritizer.fileaccessservice.exceptions;

import java.io.IOException;

public class NotDirectoryException extends IOException {

    public NotDirectoryException() {
    }

    public NotDirectoryException(String message) {
        super(message);
    }

    public NotDirectoryException(Throwable cause) {
        super(cause);
    }

    public NotDirectoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
