package team.projectpulse.student;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import team.projectpulse.system.EmailService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StudentDeactivationNotifierTest {

    @Mock
    EmailService emailService;

    @InjectMocks
    StudentDeactivationNotifier notifier;

    private final Student john = new Student("john", "John", "Smith", "j.smith@abc.edu", "123456", false, "student");


    @Test
    void testNotifySendsOneEmailToHerAddressAndReturnsTrue() {
        // When
        boolean noticeSent = this.notifier.notify(this.john);

        // Then
        assertThat(noticeSent).isTrue();
        verify(this.emailService, times(1)).sendAccountDeactivatedEmail("j.smith@abc.edu", "John Smith");
    }

    @Test
    void testNotifyReturnsFalseWhenTheMailCannotBeSent() {
        // Given
        willThrow(new MailSendException("The mail server is down")).given(this.emailService).sendAccountDeactivatedEmail(anyString(), anyString());

        // When
        boolean noticeSent = this.notifier.notify(this.john);

        // Then
        assertThat(noticeSent).isFalse();
    }

}
