package io.smartip.users;

class UserNotFoundException extends RuntimeException {

    UserNotFoundException(Long id) {
        super("User not found: " + id);
    }
}
