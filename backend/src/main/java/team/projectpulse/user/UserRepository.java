package team.projectpulse.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<PeerEvaluationUser, Integer> {

    Optional<PeerEvaluationUser> findByUsername(String username);

    Optional<PeerEvaluationUser> findByEmail(String email);

    List<PeerEvaluationUser> findByEmailIn(Collection<String> emails);

    /**
     * Whether an account is active, empty if there is no such account. Runs on every authenticated request, so it
     * reads the one column: loading the entity would also fetch a student's course section, team, and course.
     */
    @Query("select u.enabled from PeerEvaluationUser u where u.id = :userId")
    Optional<Boolean> findEnabledById(@Param("userId") Integer userId);

}
