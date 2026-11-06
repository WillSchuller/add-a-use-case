-- UC-STU-deactivate-student: the date a student's account was deactivated (POST-1). Cleared on reactivation.
-- No backfill: a student disabled before this column existed has no date, which BR-deactivated-student-report-weeks
-- handles. Only Student maps this column of the single table, so it stays null for an instructor.
ALTER TABLE `peer_evaluation_user` ADD COLUMN `deactivated_at` datetime(6) DEFAULT NULL;
