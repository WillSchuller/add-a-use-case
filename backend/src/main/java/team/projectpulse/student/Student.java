package team.projectpulse.student;

import team.projectpulse.section.Section;
import team.projectpulse.team.Team;
import team.projectpulse.user.PeerEvaluationUser;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Student extends PeerEvaluationUser {

    @ManyToOne(optional = false)
    private Section section;
    @ManyToOne
    private Team team;
    private LocalDateTime deactivatedAt; // Null while active, and for a student deactivated before the date was recorded


    public Student() {
    }

    public Student(String username, String firstName, String lastName, String email, String password, boolean enabled, String roles) {
        super(username, firstName, lastName, email, password, enabled, roles);
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public LocalDateTime getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(LocalDateTime deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }

    /**
     * Revokes her access and records when. Her team and her submitted work are untouched (BR-student-lifecycle).
     */
    public void deactivate(LocalDateTime at) {
        setEnabled(false);
        this.deactivatedAt = at;
    }

    /**
     * Restores her access and clears the date, so the course section's peer evaluation report lists her again.
     */
    public void reactivate() {
        setEnabled(true);
        this.deactivatedAt = null;
    }

}
