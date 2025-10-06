package io.smartip.users;

class EmailAlreadyExistsException extends RuntimeException {

    EmailAlreadyExistsException(String email) {
        super("Email already in use: " + email);
    }
}
