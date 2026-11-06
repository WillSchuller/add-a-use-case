package team.projectpulse.system.exception;

public class StudentAlreadyDeactivatedException extends RuntimeException {

    public StudentAlreadyDeactivatedException() {
        super("This student is already deactivated.");
    }

}
