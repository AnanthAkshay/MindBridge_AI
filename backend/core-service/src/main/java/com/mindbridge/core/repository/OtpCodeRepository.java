package com.mindbridge.core.repository;

import com.mindbridge.core.entity.OtpCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {
    Optional<OtpCode> findByEmailAndCode(String email, String code);
    void deleteByEmail(String email);
}
