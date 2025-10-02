package io.smartip.users;

import io.smartip.domain.UserEntity;
import io.smartip.domain.UserRepository;
import io.smartip.domain.UserRole;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
        return userRepository.save(entity);
    }

    @Transactional
    public void deleteUser(Long id) {
        UserEntity entity = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        userRepository.delete(entity);
    }

    public record CreateUserCommand(String email, String fullName, UserRole role) {}

    public record UpdateUserCommand(String email, String fullName, UserRole role) {}

    public record UserFilters(String query, UserRole role) {}
}
