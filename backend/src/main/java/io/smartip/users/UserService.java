package io.smartip.users;

import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRepository;
import io.smartip.domain.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Page<UserEntity> findAll(UserFilters filters, Pageable pageable) {
        Specification<UserEntity> specification = Specification.where((root, query, builder) -> builder.conjunction());

        if (filters.role() != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("role"), filters.role()));
        }

        if (filters.query() != null && !filters.query().isBlank()) {
            String term = "%" + filters.query().trim().toLowerCase() + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("email")), term),
                    builder.like(builder.lower(root.get("fullName")), term)));
        }

        return userRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public List<UserEntity> findAll() {
        return userRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public UserEntity getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional
    public UserEntity createUser(CreateUserCommand command) {
        if (userRepository.existsByEmailIgnoreCase(command.email())) {
            throw new EmailAlreadyExistsException(command.email());
        }

        UserEntity entity = new UserEntity();
        entity.setEmail(command.email());
        entity.setFullName(command.fullName());
        entity.setRole(command.role());
        entity.setPasswordHash(passwordEncoder.encode(command.password()));
        return userRepository.save(entity);
    }

    @Transactional
    public UserEntity updateUser(Long id, UpdateUserCommand command) {
        UserEntity entity = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(command.email(), id)) {
            throw new EmailAlreadyExistsException(command.email());
        }

        entity.setEmail(command.email());
        entity.setFullName(command.fullName());
        entity.setRole(command.role());
        command.optionalPassword().ifPresent(password -> entity.setPasswordHash(passwordEncoder.encode(password)));
        return userRepository.save(entity);
    }

    @Transactional
    public void deleteUser(Long id) {
        UserEntity entity = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userRepository.delete(entity);
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        Optional<UserEntity> optional = userRepository.findByEmailIgnoreCase(email);
        UserEntity user = optional.orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public record CreateUserCommand(String email, String fullName, String password, UserRole role) {}

    public record UpdateUserCommand(String email, String fullName, UserRole role, String password) {
        public Optional<String> optionalPassword() {
            return Optional.ofNullable(password);
        }
    }

    public record UserFilters(String query, UserRole role) {}
}
