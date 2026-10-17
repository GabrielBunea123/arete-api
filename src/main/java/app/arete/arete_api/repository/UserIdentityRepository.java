package app.arete.arete_api.repository;

import app.arete.arete_api.model.entity.UserIdentity;
import app.arete.arete_api.util.enums.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    @Query("""
            SELECT identity FROM UserIdentity identity
            JOIN FETCH identity.user
            WHERE identity.provider = :provider
              AND identity.providerSubject = :providerSubject
            """)
    Optional<UserIdentity> findByProviderAndProviderSubjectWithUser(
            @Param("provider") final AuthProvider provider,
            @Param("providerSubject") final String providerSubject);
}
