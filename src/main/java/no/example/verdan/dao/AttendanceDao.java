package no.example.verdan.dao;

import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;
import no.example.verdan.model.Attendance;
import no.example.verdan.util.HibernateUtil;

public class AttendanceDao extends BaseDao<Attendance> {

    // Adjust this if the school has a different number of school days per year
    private static final int SCHOOL_DAYS_PER_YEAR = 190;

    public AttendanceDao() {
        super(Attendance.class);
    }

    /**
     * Retrieve all attendance records for a given student (username), sorted by newest first.
     */
    public List<Attendance> findByStudentUsername(String username, int institutionId) {
        if (username == null || username.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select a from Attendance a " +
                    " join a.student s " +
                    " where lower(s.username) = lower(:u) " +
                    "   and a.institution.id = :instId " +
                    " order by a.dateOf desc",
                    Attendance.class)
                .setParameter("u", username)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Retrieve all attendance records with a valid student.
     * Use this instead of BaseDao.findAll() to avoid potential issues with deleted users.
     */
    public List<Attendance> findAllWithStudent(int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select a from Attendance a " +
                    " join a.student s " +
                    " where a.institution.id = :instId " +
                    " order by a.dateOf desc",
                    Attendance.class)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * Retrieve all attendance records relevant for a specific teacher.
     * logic: Only shows students registered in subjects where this teacher is assigned,
     * and where the absence is recorded for that specific subject.
     */
    public List<Attendance> findForTeacher(String teacherUsername, int institutionId) {
        if (teacherUsername == null || teacherUsername.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct a " +
                    "from Attendance a " +
                    " join a.student s " +
                    " join SubjectAssignment sas on lower(sas.username) = lower(s.username) " +
                    " join SubjectAssignment tat on tat.subject = sas.subject " +
                    "where upper(sas.role) = 'STUDENT' " +
                    "  and a.subjectCode = sas.subject " +
                    "  and upper(tat.role) = 'TEACHER' " +
                    "  and lower(tat.username) = lower(:t) " +
                    "  and a.institution.id = :instId " +
                    "order by a.dateOf desc",
                    Attendance.class)
                .setParameter("t", teacherUsername)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /**
     * 🔹 Find a single attendance record for (student, date, subjectCode) – case-insensitive.
     * Used to prevent duplicates on the same day in the same subject.
     */
    public Attendance findByStudentDateSubject(String username, LocalDate date, String subjectCode, int institutionId) {
        if (username == null || username.isBlank()) return null;
        if (date == null) return null;
        if (subjectCode == null || subjectCode.isBlank()) return null;

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<Attendance> list = em.createQuery(
                    "select a from Attendance a " +
                    " join a.student s " +
                    "where lower(s.username) = lower(:u) " +
                    "  and a.dateOf = :d " +
                    "  and lower(a.subjectCode) = lower(:sub) " +
                    "  and a.institution.id = :instId",
                    Attendance.class)
                .setParameter("u", username)
                .setParameter("d", date)
                .setParameter("sub", subjectCode)
                .setParameter("instId", institutionId)
                .setMaxResults(1)
                .getResultList();

            return list.isEmpty() ? null : list.get(0);
        } finally {
            em.close();
        }
    }

    /**
     * Calculate average absence rate (0..1) for ONE subject (subjectCode),
     * based on the entire class, over a school year.
     *
     * Logic:
     * - absentCount = number of attendance entries in this subject that are NOT "present"
     * - totalPossible = number of students in subject * SCHOOL_DAYS_PER_YEAR
     * - rate = absentCount / totalPossible
     */
    public double absenceRateForSubject(String subjectCode, int institutionId) {
        if (subjectCode == null || subjectCode.isBlank()) return 0.0;

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            Long studentCount = em.createQuery(
                    "select count(distinct s.username) " +
                    "from SubjectAssignment s " +
                    "where upper(s.role) = 'STUDENT' " +
                    "  and s.subject = :sub",
                    Long.class)
                .setParameter("sub", subjectCode)
                .getSingleResult();

            if (studentCount == null || studentCount == 0L) {
                return 0.0;
            }

            Long absentCount = em.createQuery(
                    "select count(a) " +
                    "from Attendance a " +
                    "where a.subjectCode = :sub " +
                    "  and lower(a.status) <> 'present' " +
                    "  and a.institution.id = :instId",
                    Long.class)
                .setParameter("sub", subjectCode)
                .setParameter("instId", institutionId)
                .getSingleResult();

            long abs = (absentCount == null ? 0L : absentCount);

            long totalPossible = studentCount * SCHOOL_DAYS_PER_YEAR;
            if (totalPossible == 0L) return 0.0;

            return abs * 1.0 / totalPossible;

        } finally {
            em.close();
        }
    }

    /**
     * Calculate absence rate (0..1) for ONE student in total (all subjects).
     *
     * Logic:
     * - absentDayCount = number of *days* where the student has at least one non-"present" entry
     * - rate = absentDayCount / SCHOOL_DAYS_PER_YEAR
     */
    public double absenceRateForStudent(String username, int institutionId) {
        if (username == null || username.isBlank()) return 0.0;

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            Long absentDayCount = em.createQuery(
                    "select count(distinct a.dateOf) " +
                    "from Attendance a " +
                    " join a.student s " +
                    "where lower(s.username) = lower(:u) " +
                    "  and lower(a.status) <> 'present' " +
                    "  and a.institution.id = :instId",
                    Long.class)
                .setParameter("u", username)
                .setParameter("instId", institutionId)
                .getSingleResult();

            long absDays = (absentDayCount == null ? 0L : absentDayCount);

            if (SCHOOL_DAYS_PER_YEAR <= 0) return 0.0;

            return absDays * 1.0 / SCHOOL_DAYS_PER_YEAR;

        } finally {
            em.close();
        }
    }

    /**
     * Calculate absence rate (0..1) for ONE student in ONE specific subject.
     *
     * Logic:
     * - abs = number of absence entries (status != "present") in this subject for this student
     * - rate = abs / SCHOOL_DAYS_PER_YEAR
     */
    public double absenceRateForStudentInSubject(String username, String subjectCode, int institutionId) {
        if (username == null || username.isBlank()) return 0.0;
        if (subjectCode == null || subjectCode.isBlank()) return 0.0;

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            Long absentCount = em.createQuery(
                    "select count(a) " +
                    "from Attendance a " +
                    " join a.student s " +
                    "where lower(s.username) = lower(:u) " +
                    "  and a.subjectCode = :sub " +
                    "  and lower(a.status) <> 'present' " +
                    "  and a.institution.id = :instId",
                    Long.class)
                .setParameter("u", username)
                .setParameter("sub", subjectCode)
                .setParameter("instId", institutionId)
                .getSingleResult();

            long abs = (absentCount == null ? 0L : absentCount);

            if (SCHOOL_DAYS_PER_YEAR <= 0) return 0.0;

            return abs * 1.0 / SCHOOL_DAYS_PER_YEAR;

        } finally {
            em.close();
        }
    }

    /**
     * Retrieve all distinct subject codes that have at least one attendance record.
     */
    public List<String> distinctSubjectsWithAttendance(int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct a.subjectCode " +
                    "from Attendance a " +
                    "where a.subjectCode is not null " +
                    "  and a.institution.id = :instId",
                    String.class)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }
}