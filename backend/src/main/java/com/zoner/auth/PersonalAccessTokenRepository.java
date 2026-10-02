package com.zoner.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {

    @Query("SELECT pat FROM PersonalAccessToken pat JOIN FETCH pat.user WHERE pat.tokenHash = :tokenHash")
    Optional<PersonalAccessToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    Optional<PersonalAccessToken> findByTokenHash(String tokenHash);

    List<PersonalAccessToken> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PersonalAccessToken> findByIdAndUserId(Long id, Long userId);
}
