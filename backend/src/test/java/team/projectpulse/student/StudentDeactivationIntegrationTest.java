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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.system.StatusCode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
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

    private org.springframework.test.web.servlet.ResultActions updateJohn(String token, boolean enabled) throws Exception {
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
