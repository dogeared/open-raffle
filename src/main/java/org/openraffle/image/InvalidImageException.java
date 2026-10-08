package org.openraffle.image;

/** An upload was refused; the message is fit to show the organizer. */
public class InvalidImageException extends Exception {

    public InvalidImageException(String message) {
        super(message);
    }
}
