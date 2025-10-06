package io.smartip.users;

public class InvalidUserRoleException extends RuntimeException {

    public InvalidUserRoleException(String role) {
        super("Invalid user role: " + role);
    }
}
