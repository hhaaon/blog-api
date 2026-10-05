package com.hao.blog.user.persistence;

import com.hao.blog.user.application.DuplicateEmailException;
import com.hao.blog.user.domain.User;
import com.hao.blog.user.domain.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Optional;

@Repository
public class JpaUserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    public JpaUserRepositoryAdapter(
            UserJpaRepository userJpaRepository
    ) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public User save(User user) {
        try {
            UserEntity saved = userJpaRepository.save(toEntity(user));
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateEmail(ex)) {
                throw new DuplicateEmailException();
            }

            throw ex;
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email)
                .map(entity -> new User(
                        entity.id(),
                        entity.email(),
                        entity.passwordHash(),
                        entity.role()
                ));
    }

    private UserEntity toEntity(User user) {
        return new UserEntity(
                user.email(),
                user.passwordHash(),
                user.role()
        );
    }

    private User toDomain(UserEntity userEntity) {
        return new User(
                userEntity.id(),
                userEntity.email(),
                userEntity.passwordHash(),
                userEntity.role()
        );
    }

    private boolean isDuplicateEmail(DataIntegrityViolationException ex) {
        Throwable cause = ex;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return "users_email_unique".equals(
                        constraintViolation.getConstraintName()
                );
            }

            cause = cause.getCause();
        }

        return false;
    }
}
