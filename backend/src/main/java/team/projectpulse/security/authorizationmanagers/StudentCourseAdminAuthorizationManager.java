package team.projectpulse.security.authorizationmanagers;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import team.projectpulse.student.StudentSecurityService;

import java.util.function.Supplier;

/**
 * Admits only the course admin of the course a student belongs to, whether or not she is assigned to the student's
 * course section. Combined with {@link StudentInstructorAuthorizationManager} on the routes BR-student-lifecycle opens
 * to both.
 *
 * <p>Reads {@code studentId} and delegates to {@link StudentSecurityService#isCurrentUserCourseAdminOfStudent}.
 */
@Component
public class StudentCourseAdminAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final StudentSecurityService studentSecurityService;


    public StudentCourseAdminAuthorizationManager(StudentSecurityService studentSecurityService) {
        this.studentSecurityService = studentSecurityService;
    }

    @Override
    public @Nullable AuthorizationResult authorize(Supplier<? extends @Nullable Authentication> authentication, RequestAuthorizationContext context) {
        Integer studentId = PathVariables.readId(context, "studentId");
        if (studentId == null) {
            return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(
                this.studentSecurityService.isCurrentUserCourseAdminOfStudent(studentId));
    }

}
