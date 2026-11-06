package team.projectpulse.system;

import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * What the message built by {@link EmailService} says and whom it goes to. Only the mail server is mocked, because the
 * tests that mock {@code EmailService} itself cannot see either: with the deactivation notice addressed to someone
 * else entirely, they all stayed green.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    JavaMailSender javaMailSender;

    @InjectMocks
    EmailService emailService;


    @Test
    void testAccountDeactivatedEmailGoesToTheStudentAndTellsHerWhoToContact() throws Exception {
        // Given
        ReflectionTestUtils.setField(this.emailService, "fromEmail", "no-reply@abc.edu");
        given(this.javaMailSender.createMimeMessage()).willReturn(new MimeMessage((Session) null));

        // When
        this.emailService.sendAccountDeactivatedEmail("j.smith@abc.edu", "John <Smith>");

        // Then
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(this.javaMailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();

        assertThat(message.getRecipients(Message.RecipientType.TO)).containsExactly(new InternetAddress("j.smith@abc.edu"));
        assertThat(message.getSubject()).isEqualTo("Your Project Pulse account has been deactivated");
        assertThat(htmlOf(message))
                .contains("John &lt;Smith&gt;") // A name is typed by people, so it is escaped
                .contains("Your Project Pulse account has been deactivated")
                .contains("please contact your instructor");
    }

    private static String htmlOf(Part part) throws Exception {
        if (part.getContent() instanceof Multipart multipart) {
            return htmlOf(multipart.getBodyPart(0));
        }
        return part.getContent().toString();
    }

}
