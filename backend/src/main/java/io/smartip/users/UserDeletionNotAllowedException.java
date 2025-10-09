package io.smartip.users;

class UserDeletionNotAllowedException extends RuntimeException {
    UserDeletionNotAllowedException(String message) {
        super(message);
    }
}
