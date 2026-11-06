package team.projectpulse.student;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import team.projectpulse.system.EmailService;

/**
 * Tells a student her account has been deactivated (UC-STU-deactivate-student step 6), and reports whether the mail
 * server accepted the message.
 *
 * <p>Deliberately not transactional, and called only after the deactivation has committed: a mail failure must not
 * undo it (extension 6b), and a database transaction should not stay open across SMTP.
 */
@Component
public class StudentDeactivationNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudentDeactivationNotifier.class);

    private final EmailService emailService;


    public StudentDeactivationNotifier(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * @return whether the notice was sent; a failure is logged, not thrown, because she stays deactivated either way
     */
    public boolean notify(Student student) {
        try {
            this.emailService.sendAccountDeactivatedEmail(student.getEmail(), student.getFirstName() + " " + student.getLastName());
            return true;
        } catch (RuntimeException e) {
            LOGGER.error("Could not send the deactivation notice to student {}", student.getId(), e);
            return false;
        }
    }

}
