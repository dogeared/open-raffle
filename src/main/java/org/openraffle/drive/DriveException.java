package org.openraffle.drive;

/** Google Drive did not do what was asked; the message is fit to show an organizer. */
public class DriveException extends Exception {

    public DriveException(String message) {
        super(message);
    }

    public DriveException(String message, Throwable cause) {
        super(message, cause);
    }
}
