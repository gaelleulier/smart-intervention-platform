package io.smartip.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface UserRepository extends JpaRepository<UserEntity, Long>, JpaSpecificationExecutor<UserEntity> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    List<UserEntity> findAllByOrderByIdAsc();

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    List<UserEntity> findByRoleOrderByIdAsc(UserRole role);

    long countByRole(UserRole role);
}
