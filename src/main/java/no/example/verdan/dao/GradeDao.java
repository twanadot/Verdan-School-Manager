package no.example.verdan.dao;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import no.example.verdan.model.Grade;
import no.example.verdan.util.HibernateUtil;

public class GradeDao extends BaseDao<Grade> {
    public GradeDao(){ super(Grade.class); }

    public enum GradeScale { NUMERIC_1_TO_6, LETTER_A_TO_F }

    public Double parseGradeToDouble(String val){
        if (val == null) return null;
        String s = val.trim().toUpperCase().replace(',', '.');
        try {
            double v = Double.parseDouble(s);
            if (v >= 1.0 && v <= 6.0) return v;
            return null;
        } catch (NumberFormatException ignore){}
        switch (s) {
            case "A": return 6.0;
            case "B": return 5.0;
            case "C": return 4.0;
            case "D": return 3.0;
            case "E": return 2.0;
            case "F": return 1.0;
            default: return null;
        }
    }

    private String toLetter(double numeric){
        if (numeric >= 5.5) return "A";
        if (numeric >= 4.5) return "B";
        if (numeric >= 3.5) return "C";
        if (numeric >= 2.5) return "D";
        if (numeric >= 1.5) return "E";
        return "F";
    }

    public String formatAverage(double avg, GradeScale scale){
        if (scale == GradeScale.LETTER_A_TO_F) return toLetter(avg);
        return new DecimalFormat("#0.00").format(avg);
    }

    /** Find all grades for a given student username. */
    public List<Grade> findByStudentUsername(String username, int institutionId){
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "select g from Grade g join fetch g.student s " +
                "where s.username = :u and g.institution.id = :instId " +
                "order by g.dateGiven desc, g.id desc",
                Grade.class)
            .setParameter("u", username)
            .setParameter("instId", institutionId)
            .getResultList();
        } finally { em.close(); }
    }

    /** Find all grades registered by a given teacher (teacherUsername). */
    public List<Grade> findByTeacherUsername(String username, int institutionId){
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "select g from Grade g " +
                "where lower(g.teacherUsername) = lower(:u) and g.institution.id = :instId " +
                "order by g.dateGiven desc, g.id desc",
                Grade.class)
            .setParameter("u", username)
            .setParameter("instId", institutionId)
            .getResultList();
        } finally { em.close(); }
    }

    /**
     * 🔹 NEW: All grades registered by this teacher, but only for students
     * who are STILL registered in that subject (subject_assignments).
     *
     * Used in the teacher view so that students removed from the subject in Admin
     * no longer appear in the teacher's list.
     */
    public List<Grade> findForTeacherWithActiveAssignments(String teacherUsername, int institutionId) {
        if (teacherUsername == null || teacherUsername.isBlank()) {
            return List.of();
        }

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                    "select distinct g " +
                    "from Grade g join fetch g.student join fetch g.institution, SubjectAssignment sa " +
                    "where lower(g.teacherUsername) = lower(:t) " +
                    "  and lower(g.student.username) = lower(sa.username) " +
                    "  and upper(sa.role) = 'STUDENT' " +
                    "  and lower(g.subject) = lower(sa.subject) " +
                    "  and g.institution.id = :instId",
                    Grade.class)
                .setParameter("t", teacherUsername)
                .setParameter("instId", institutionId)
                .getResultList();
        } finally {
            em.close();
        }
    }

    /** All grades (simple, for reports). */
    public List<Grade> findAllGrades(int institutionId){
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "select g from Grade g join fetch g.student join fetch g.institution " +
                "where g.institution.id = :instId " +
                "order by g.subject asc, g.dateGiven desc, g.id desc",
                Grade.class)
            .setParameter("instId", institutionId)
            .getResultList();
        } finally { em.close(); }
    }

    public double avgForStudent(String username, int institutionId){
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<Grade> list = em.createQuery(
                "select g from Grade g join g.student s where s.username = :u and g.institution.id = :instId",
                Grade.class)
            .setParameter("u", username)
            .setParameter("instId", institutionId)
            .getResultList();
            List<Double> nums = new ArrayList<>();
            for (Grade g : list){
                Double v = parseGradeToDouble(g.getValue());
                if (v != null) nums.add(v);
            }
            return nums.stream().mapToDouble(d->d).average().orElse(0.0);
        } finally { em.close(); }
    }

    public double avgForSubject(String subject, int institutionId){
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<Grade> list = em.createQuery(
                "select g from Grade g where lower(g.subject) = lower(:s) and g.institution.id = :instId",
                Grade.class)
            .setParameter("s", subject)
            .setParameter("instId", institutionId)
            .getResultList();
            List<Double> nums = new ArrayList<>();
            for (Grade g : list){
                Double v = parseGradeToDouble(g.getValue());
                if (v != null) nums.add(v);
            }
            return nums.stream().mapToDouble(d->d).average().orElse(0.0);
        } finally { em.close(); }
    }

    /** Returns [subject, avg(double)] for all subjects. */
    public List<Object[]> avgGradePerSubject(int institutionId){
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<Grade> all = em.createQuery("select g from Grade g where g.institution.id = :instId", Grade.class)
                    .setParameter("instId", institutionId)
                    .getResultList();
            Map<String, List<Double>> buckets = new LinkedHashMap<>();
            for (Grade g : all){
                String subj = g.getSubject() != null ? g.getSubject() : "(empty)";
                Double v = parseGradeToDouble(g.getValue());
                if (v != null){
                    buckets.computeIfAbsent(subj, k -> new ArrayList<>()).add(v);
                }
            }
            List<Object[]> out = new ArrayList<>();
            for (Map.Entry<String,List<Double>> e : buckets.entrySet()){
                double avg = e.getValue().stream().mapToDouble(d->d).average().orElse(0.0);
                out.add(new Object[]{ e.getKey(), avg });
            }
            return out;
        } finally { em.close(); }
    }

    // 🔹 Check if student already has a grade in this subject
    public boolean hasGradeForStudentInSubject(String username, String subjectCode, int institutionId) {
        if (username == null || username.isBlank()) return false;
        if (subjectCode == null || subjectCode.isBlank()) return false;

        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            Long count = em.createQuery(
                    "select count(g) from Grade g " +
                    "join g.student s " +
                    "where lower(s.username) = lower(:u) " +
                    "  and lower(g.subject) = lower(:sub) " +
                    "  and g.institution.id = :instId",
                    Long.class)
                .setParameter("u", username)
                .setParameter("sub", subjectCode)
                .setParameter("instId", institutionId)
                .getSingleResult();
            return count != null && count > 0;
        } finally {
            em.close();
        }
    }

    /**
     * Find ALL grades for a student across ALL institutions.
     * Used for the student's full education history.
     */
    public List<Grade> findAllByStudentUsername(String username) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "select g from Grade g join fetch g.student s join fetch g.institution " +
                "where s.username = :u order by g.institution.id asc, g.dateGiven desc",
                Grade.class)
            .setParameter("u", username)
            .getResultList();
        } finally { em.close(); }
    }

    /**
     * Find grades for a student at institutions of a specific level (e.g. UNGDOMSSKOLE, VGS).
     * Used for admission GPA calculation from the correct source level.
     */
    public List<Grade> findByStudentAndInstitutionLevel(String username, String level) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            return em.createQuery(
                "select g from Grade g join fetch g.student s join fetch g.institution i " +
                "where s.username = :u and i.level = :lvl " +
                "order by g.dateGiven desc",
                Grade.class)
            .setParameter("u", username)
            .setParameter("lvl", level)
            .getResultList();
        } finally { em.close(); }
    }

    /** Find a specific grade for a student in a subject at an institution. */
    public Grade findByStudentAndSubject(String username, String subjectCode, int institutionId) {
        EntityManager em = HibernateUtil.emf().createEntityManager();
        try {
            List<Grade> results = em.createQuery(
                "select g from Grade g join g.student s " +
                "where lower(s.username) = lower(:u) " +
                "  and lower(g.subject) = lower(:sub) " +
                "  and g.institution.id = :instId",
                Grade.class)
            .setParameter("u", username)
            .setParameter("sub", subjectCode)
            .setParameter("instId", institutionId)
            .getResultList();
            return results.isEmpty() ? null : results.get(0);
        } finally { em.close(); }
    }
}