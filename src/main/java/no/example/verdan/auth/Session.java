package no.example.verdan.auth;

import no.example.verdan.model.User;

public class Session {
  private static User current;
  public static void setCurrent(User u){ current = u; }
  public static User getCurrent(){ return current; }
  public static boolean isTeacher(){ return current != null && "TEACHER".equalsIgnoreCase(current.getRole()); }
  public static boolean isStudent(){ return current != null && "STUDENT".equalsIgnoreCase(current.getRole()); }
  public static boolean isSuperAdmin(){ return current != null && "SUPER_ADMIN".equalsIgnoreCase(current.getRole()); }
  public static boolean isInstitutionAdmin(){ return current != null && "INSTITUTION_ADMIN".equalsIgnoreCase(current.getRole()); }
  public static boolean isAnyAdmin(){ return isSuperAdmin() || isInstitutionAdmin(); }
  public static Integer getInstitutionId(){ return (current != null && current.getInstitution() != null) ? current.getInstitution().getId() : null; }
  public static String getInstitutionName(){ return (current != null && current.getInstitution() != null) ? current.getInstitution().getName() : null; }
}
