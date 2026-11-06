package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.system.EmailService;
import team.projectpulse.system.StatusCode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student against the seed data. John Smith is student 4, in course section 2 and on Team1. Bill Gates
 * is assigned to course section 2 and owns no course. Bingyang Wei is assigned to it and owns its course. Tim Cook owns
 * another course and is not assigned to it.
 */
@DisplayName("Integration tests for deactivating a student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    EmailService emailService; // Real sends to the Mailpit container; the spy records them, and can make one fail

    @Autowired
    Clock clock;
    @Autowired
    MockMvc mockMvc;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    EntityManager entityManager;
    @Autowired
    StudentRepository studentRepository;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String instructorBillToken;
    Student john;


    @BeforeEach
    void setUp() throws Exception {
        this.instructorBillToken = login("b.gates@abc.edu");
        this.john = student("j.smith@abc.edu");
    }

    // Main success scenario

    @Test
    @DisplayName("An instructor assigned to her course section deactivates her, and her work and team are kept")
    void anAssignedInstructorDeactivatesHer() throws Exception {
        // Given
        Integer teamId = this.john.getTeam().getTeamId();
        long activities = count("select count(a) from Activity a where a.student = :student");
        long evaluationsWritten = count("select count(e) from PeerEvaluation e where e.evaluator = :student");
        long evaluationsReceived = count("select count(e) from PeerEvaluation e where e.evaluatee = :student");
        assertThat(activities).isPositive();
        assertThat(evaluationsWritten).isPositive();
        assertThat(evaluationsReceived).isPositive();

        // When and then
        deactivateJohn(this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.data.student.id").value(this.john.getId()))
                .andExpect(jsonPath("$.data.student.enabled").value(false))
                .andExpect(jsonPath("$.data.student.teamId").value(teamId))
                .andExpect(jsonPath("$.data.student.deactivatedAt").doesNotExist())
                .andExpect(jsonPath("$.data.noticeSent").value(true));

        reloadJohn();
        assertThat(this.john.isEnabled()).isFalse();
        assertThat(this.john.getDeactivatedAt()).isEqualTo(LocalDateTime.now(this.clock));
        assertThat(this.john.getTeam().getTeamId()).isEqualTo(teamId);
        assertThat(count("select count(a) from Activity a where a.student = :student")).isEqualTo(activities);
        assertThat(count("select count(e) from PeerEvaluation e where e.evaluator = :student")).isEqualTo(evaluationsWritten);
        assertThat(count("select count(e) from PeerEvaluation e where e.evaluatee = :student")).isEqualTo(evaluationsReceived);
        verify(this.emailService, times(1)).sendAccountDeactivatedEmail("j.smith@abc.edu", "John Smith");
    }

    @Test
    @DisplayName("The admin of her course deactivates her without being assigned to her course section")
    void aCourseAdminNotAssignedToHerCourseSectionDeactivatesHer() throws Exception {
        // Given: the seed has no such course admin
        Instructor courseAdmin = this.john.getSection().getCourse().getCourseAdmin();
        this.john.getSection().removeInstructor(courseAdmin);
        this.entityManager.flush();
        assertThat(this.john.getSection().getInstructors()).doesNotContain(courseAdmin);

        // When and then
        deactivateJohn(login(courseAdmin.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student.enabled").value(false));

        reloadJohn();
        assertThat(this.john.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Deactivation takes effect on her next request, from a session she already has open")
    void herOpenSessionEndsOnHerNextRequest() throws Exception {
        // Given
        String johnsToken = login("j.smith@abc.edu");

        // When
        deactivateJohn(this.instructorBillToken).andExpect(status().isOk());

        // Then
        this.mockMvc.perform(get(this.baseUrl + "/students/" + this.john.getId()).header(HttpHeaders.AUTHORIZATION, johnsToken))
                .andExpect(status().isUnauthorized());
        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized());
    }

    // 3a. The user is not permitted to deactivate this student, or the student does not exist

    @Test
    @DisplayName("3a: another course's admin is refused")
    void anotherCoursesAdminIsRefused() throws Exception {
        assertRefusedAndUnchanged(login("t.cook@abc.edu"), this.john.getId());
    }

    @Test
    @DisplayName("3a: an instructor not assigned to her course section is refused")
    void anUnassignedInstructorIsRefused() throws Exception {
        // Given
        Instructor bill = instructor("b.gates@abc.edu");
        this.john.getSection().removeInstructor(bill);
        this.entityManager.flush();

        assertRefusedAndUnchanged(this.instructorBillToken, this.john.getId());
    }

    @Test
    @DisplayName("3a: another student is refused")
    void anotherStudentIsRefused() throws Exception {
        assertRefusedAndUnchanged(login("e.hudson@abc.edu"), this.john.getId());
    }

    @Test
    @DisplayName("3a: she cannot deactivate herself")
    void sheCannotDeactivateHerself() throws Exception {
        assertRefusedAndUnchanged(login("j.smith@abc.edu"), this.john.getId());
    }

    @Test
    @DisplayName("3a: an unknown id is refused the same way, so it does not reveal which ids exist")
    void anUnknownIdIsRefused() throws Exception {
        assertRefusedAndUnchanged(this.instructorBillToken, 999999);
    }

    private void assertRefusedAndUnchanged(String token, Integer studentId) throws Exception {
        this.mockMvc.perform(post(this.baseUrl + "/students/" + studentId + "/deactivate").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        reloadJohn();
        assertThat(this.john.isEnabled()).isTrue();
        assertThat(this.john.getDeactivatedAt()).isNull();
        verify(this.emailService, never()).sendAccountDeactivatedEmail(anyString(), anyString());
    }

    // 6a. The student has already been deactivated

    @Test
    @DisplayName("6a: already deactivated is a conflict that keeps her date and does not email her")
    void alreadyDeactivatedIsAConflict() throws Exception {
        // Given
        LocalDateTime deactivatedAt = LocalDateTime.of(2023, 8, 1, 9, 0);
        this.john.deactivate(deactivatedAt);
        this.entityManager.flush();

        // When and then
        deactivateJohn(this.instructorBillToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.CONFLICT))
                .andExpect(jsonPath("$.message").value("This student is already deactivated."));

        reloadJohn();
        assertThat(this.john.getDeactivatedAt()).isEqualTo(deactivatedAt);
        verify(this.emailService, never()).sendAccountDeactivatedEmail(anyString(), anyString());
    }

    // 6b. The system cannot send the notification to the student

    @Test
    @DisplayName("6b: when the mail fails she stays deactivated and the response says so")
    void aMailFailureLeavesHerDeactivated() throws Exception {
        // Given
        doThrow(new MailSendException("The mail server is down")).when(this.emailService).sendAccountDeactivatedEmail(anyString(), anyString());

        // When and then
        deactivateJohn(this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.data.student.enabled").value(false))
                .andExpect(jsonPath("$.data.noticeSent").value(false));

        reloadJohn();
        assertThat(this.john.isEnabled()).isFalse();
        assertThat(this.john.getDeactivatedAt()).isNotNull();
    }

    private ResultActions deactivateJohn(String token) throws Exception {
        return this.mockMvc.perform(post(this.baseUrl + "/students/" + this.john.getId() + "/deactivate")
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private void reloadJohn() {
        this.entityManager.flush();
        this.entityManager.refresh(this.john);
    }

    private long count(String query) {
        return this.entityManager.createQuery(query, Long.class).setParameter("student", this.john).getSingleResult();
    }

    // The scoped finder

    @Test
    @DisplayName("The scoped finder returns her for an instructor assigned to her course section")
    void theScopedFinderAdmitsAnAssignedInstructor() {
        assertThat(this.studentRepository.findByIdAndManagedByUser(this.john.getId(), instructor("b.gates@abc.edu").getId())).contains(this.john);
    }

    @Test
    @DisplayName("The scoped finder returns one row for a course admin, however many instructors the course section has")
    void theScopedFinderAdmitsTheCourseAdminOfACourseSectionWithSeveralInstructors() {
        // Given
        Instructor courseAdmin = this.john.getSection().getCourse().getCourseAdmin();
        this.john.getSection().removeInstructor(courseAdmin); // Assigned to neither, and Bill remains
        this.entityManager.flush();
        assertThat(this.john.getSection().getInstructors()).isNotEmpty();

        // Then
        assertThat(this.studentRepository.findByIdAndManagedByUser(this.john.getId(), courseAdmin.getId())).contains(this.john);
    }

    @Test
    @DisplayName("The scoped finder returns nothing for another course's admin, for another student, or for herself")
    void theScopedFinderRefusesEveryoneElse() {
        assertThat(this.studentRepository.findByIdAndManagedByUser(this.john.getId(), instructor("t.cook@abc.edu").getId())).isEmpty();
        assertThat(this.studentRepository.findByIdAndManagedByUser(this.john.getId(), student("e.hudson@abc.edu").getId())).isEmpty();
        assertThat(this.studentRepository.findByIdAndManagedByUser(this.john.getId(), this.john.getId())).isEmpty();
    }

    // PUT /students/{studentId}: the edit form cannot deactivate her, and can still reactivate her

    @Test
    @DisplayName("PUT cannot disable a student, even from an instructor")
    void putCannotDisable() throws Exception {
        updateJohn(this.instructorBillToken, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        this.entityManager.flush();
        this.entityManager.refresh(this.john);
        assertThat(this.john.isEnabled()).isTrue();
        assertThat(this.john.getDeactivatedAt()).isNull();
    }

    @Test
    @DisplayName("PUT can enable a deactivated student, which clears her date and keeps her team")
    void putEnablesADeactivatedStudent() throws Exception {
        // Given
        Integer teamId = this.john.getTeam().getTeamId();
        this.john.deactivate(LocalDateTime.of(2023, 8, 8, 9, 0));
        this.entityManager.flush();

        // When and then
        updateJohn(this.instructorBillToken, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.teamId").value(teamId));

        this.entityManager.flush();
        this.entityManager.refresh(this.john);
        assertThat(this.john.isEnabled()).isTrue();
        assertThat(this.john.getDeactivatedAt()).isNull();
    }

    private ResultActions updateJohn(String token, boolean enabled) throws Exception {
        Map<String, Object> studentDto = new HashMap<>();
        studentDto.put("username", this.john.getUsername());
        studentDto.put("firstName", this.john.getFirstName());
        studentDto.put("lastName", this.john.getLastName());
        studentDto.put("email", this.john.getEmail());
        studentDto.put("enabled", enabled);
        return this.mockMvc.perform(put(this.baseUrl + "/students/" + this.john.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(this.jsonMapper.writeValueAsString(studentDto))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private Student student(String email) {
        return this.entityManager.createQuery("select s from Student s where s.email = :email", Student.class)
                .setParameter("email", email)
                .getSingleResult();
    }

    private Instructor instructor(String email) {
        return this.entityManager.createQuery("select i from Instructor i where i.email = :email", Instructor.class)
                .setParameter("email", email)
                .getSingleResult();
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        return "Bearer " + new JSONObject(mvcResult.getResponse().getContentAsString()).getJSONObject("data").getString("token");
    }

}
