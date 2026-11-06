package team.projectpulse.student.dto;

/**
 * @param student    the student, now deactivated
 * @param noticeSent whether she could be notified (UC-STU-deactivate-student extension 6b)
 */
public record StudentDeactivationDto(StudentDto student, boolean noticeSent) {
}
