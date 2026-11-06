package team.projectpulse.student;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudentRepository extends JpaRepository<Student, Integer>, JpaSpecificationExecutor<Student> {

    // Find all students in a section
    List<Student> findBySectionSectionId(Integer sectionId);

    List<Student> findByTeamTeamId(Integer teamId);

    /**
     * The student, if the user is an instructor assigned to her course section or the admin of its course
     * (BR-student-lifecycle, BR-section-scoped-access). The scope is in the query rather than compared after a
     * primary-key load, per Authorization point 2. {@code exists}, not a join, so that a course section with several
     * instructors still yields one row for its course admin.
     */
    @Query("""
            select student from Student student
            where student.id = :studentId
              and (student.section.course.courseAdmin.id = :userId
                   or exists (select 1 from Section section join section.instructors instructor
                              where section = student.section and instructor.id = :userId))
            """)
    Optional<Student> findByIdAndManagedByUser(@Param("studentId") Integer studentId, @Param("userId") Integer userId);
}
