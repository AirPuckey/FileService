package com.rph.paritizer.fileaccessservice.exceptions;

import java.io.IOException;

public class DiskNotFoundException extends IOException {

    public DiskNotFoundException() {
    }

    public DiskNotFoundException(String message) {
        super(message);
    }

    public DiskNotFoundException(Throwable cause) {
        super(cause);
    }

    public DiskNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
