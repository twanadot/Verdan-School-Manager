package no.example.verdan.service;

import no.example.verdan.dao.ProgramDao;
import no.example.verdan.dao.ProgramMemberDao;
import no.example.verdan.dao.UserDao;
import no.example.verdan.model.Institution;
import no.example.verdan.model.Program;
import no.example.verdan.model.ProgramMember;
import no.example.verdan.model.Subject;
import no.example.verdan.model.User;
import no.example.verdan.auth.AuthService;
import no.example.verdan.security.InputValidator;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;

/**
 * Service for batch-importing students from CSV or Excel files.
 * Handles user creation, program assignment, and subject enrollment.
 */
public class BatchImportService {

    private static final Logger LOG = LoggerFactory.getLogger(BatchImportService.class);
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserDao userDao;
    private final ProgramDao programDao;
    private final ProgramMemberDao memberDao;
    private final AuthService authService;

    public BatchImportService() {
        this.userDao = new UserDao();
        this.programDao = new ProgramDao();
        this.memberDao = new ProgramMemberDao();
        this.authService = new AuthService();
    }

    /** Generate a random 12-character password that meets security requirements. */
    private String generatePassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghjkmnpqrstuvwxyz";
        String digits = "23456789";
        String special = "!@#$%&*";
        String all = upper + lower + digits + special;

        // Guarantee at least one of each required type
        StringBuilder sb = new StringBuilder(12);
        sb.append(upper.charAt(RANDOM.nextInt(upper.length())));
        sb.append(lower.charAt(RANDOM.nextInt(lower.length())));
        sb.append(digits.charAt(RANDOM.nextInt(digits.length())));
        sb.append(special.charAt(RANDOM.nextInt(special.length())));

        // Fill remaining with random chars
        for (int i = 4; i < 12; i++) {
            sb.append(all.charAt(RANDOM.nextInt(all.length())));
        }

        // Shuffle to avoid predictable positions
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    /** Generate a unique username from first + last name. */
    private String generateUsername(String firstName, String lastName) {
        String base = (firstName.trim() + "." + lastName.trim())
                .toLowerCase()
                .replaceAll("[^a-z0-9.]", "")
                .replaceAll("\\s+", "");
        if (base.isBlank()) base = "elev";

        String candidate = base;
        int counter = 1;
        while (!userDao.findByUsername(candidate).isEmpty()) {
            candidate = base + counter;
            counter++;
        }
        return candidate;
    }

    /**
     * Import students from a CSV or Excel file.
     *
     * Uses an atomic "all-or-nothing" approach:
     *   Phase 1: Validate ALL rows first without touching the database.
     *   Phase 2: Only if zero validation errors, create all users.
     *
     * This prevents partial imports where some students are created
     * and others are skipped, which would be hard to clean up.
     */
    public ImportResult importStudents(byte[] fileData, String fileName, int institutionId) {
        List<StudentRow> rows;
        String ext = fileName.toLowerCase();

        try {
            if (ext.endsWith(".xlsx") || ext.endsWith(".xls")) {
                rows = parseExcel(fileData);
            } else if (ext.endsWith(".csv")) {
                rows = parseCsv(fileData);
            } else {
                return new ImportResult(0, 0, 0, List.of("Ugyldig filformat. Bruk .csv eller .xlsx"));
            }
        } catch (Exception e) {
            LOG.error("Failed to parse import file: {}", e.getMessage());
            return new ImportResult(0, 0, 0, List.of("Kunne ikke lese filen. Sjekk at filen er gyldig CSV eller Excel."));
        }

        if (rows.isEmpty()) {
            return new ImportResult(0, 0, 0, List.of("Filen inneholder ingen data"));
        }

        // Load all programs for the institution
        List<Program> programs = programDao.findByInstitution(institutionId);
        Map<String, Program> programMap = new HashMap<>();
        for (Program p : programs) {
            programMap.put(p.getName().toLowerCase().trim(), p);
        }

        // ═══════════════════════════════════════════════════════════
        // PHASE 1: Validate ALL rows — do NOT touch the database yet
        // ═══════════════════════════════════════════════════════════

        List<String> errors = new ArrayList<>();
        List<ValidatedRow> validatedRows = new ArrayList<>();
        Set<String> emailsInBatch = new HashSet<>(); // detect duplicates within the file

        for (int i = 0; i < rows.size(); i++) {
            StudentRow row = rows.get(i);
            int rowNum = i + 2; // +2 because row 1 is header

            // Validate required fields
            if (row.firstName == null || row.firstName.isBlank()) {
                errors.add("Rad " + rowNum + ": Fornavn mangler");
                continue;
            }
            if (row.lastName == null || row.lastName.isBlank()) {
                errors.add("Rad " + rowNum + ": Etternavn mangler");
                continue;
            }
            if (row.email == null || row.email.isBlank()) {
                errors.add("Rad " + rowNum + ": E-post mangler");
                continue;
            }
            if (row.phone == null || row.phone.isBlank()) {
                errors.add("Rad " + rowNum + ": Telefon mangler");
                continue;
            }

            // Check for duplicate email within the file itself
            String emailLower = row.email.trim().toLowerCase();
            if (!emailsInBatch.add(emailLower)) {
                errors.add("Rad " + rowNum + ": E-post " + row.email + " er duplikat i filen");
                continue;
            }

            // Check for duplicate email in the database
            if (!userDao.findByEmail(row.email.trim()).isEmpty()) {
                errors.add("Rad " + rowNum + ": E-post " + row.email + " finnes allerede i systemet");
                continue;
            }

            // Validate gender
            String gender = null;
            if (row.gender != null && !row.gender.isBlank()) {
                String g = row.gender.trim().toUpperCase();
                if (g.startsWith("M") || g.equals("GUTT") || g.equals("MANN")) {
                    gender = "MALE";
                } else if (g.startsWith("F") || g.startsWith("K") || g.equals("JENTE") || g.equals("KVINNE")) {
                    gender = "FEMALE";
                } else {
                    errors.add("Rad " + rowNum + ": Ugyldig kjønn '" + row.gender + "'. Bruk M/F, Gutt/Jente, Mann/Kvinne");
                    continue;
                }
            }

            // Parse birthDate
            LocalDate birthDate = null;
            if (row.birthDate != null && !row.birthDate.isBlank()) {
                try {
                    birthDate = LocalDate.parse(row.birthDate.trim());
                } catch (Exception e) {
                    // Try dd.MM.yyyy format
                    try {
                        String[] dp = row.birthDate.trim().split("[./\\-]");
                        if (dp.length == 3) {
                            birthDate = LocalDate.of(Integer.parseInt(dp[2]), Integer.parseInt(dp[1]), Integer.parseInt(dp[0]));
                        } else {
                            errors.add("Rad " + rowNum + ": Ugyldig fødselsdato '" + row.birthDate + "'. Bruk dd.MM.yyyy eller yyyy-MM-dd");
                            continue;
                        }
                    } catch (Exception e2) {
                        errors.add("Rad " + rowNum + ": Ugyldig fødselsdato '" + row.birthDate + "'. Bruk dd.MM.yyyy eller yyyy-MM-dd");
                        continue;
                    }
                }
            }

            // Find program/class
            Program targetProgram = null;
            if (row.className != null && !row.className.isBlank()) {
                targetProgram = programMap.get(row.className.trim().toLowerCase());
                if (targetProgram == null) {
                    errors.add("Rad " + rowNum + ": Klasse '" + row.className + "' finnes ikke. Opprett klassen først.");
                    continue;
                }
            }

            // Row passed all validation — store for phase 2
            validatedRows.add(new ValidatedRow(row, gender, birthDate, targetProgram));
        }

        // ═══════════════════════════════════════════════════════════
        // STOP if any validation errors — entire batch is rejected
        // ═══════════════════════════════════════════════════════════

        if (!errors.isEmpty()) {
            LOG.warn("Batch import rejected: {} validation errors in {} rows",
                errors.size(), rows.size());
            return new ImportResult(0, errors.size(), rows.size(), errors);
        }

        // ═══════════════════════════════════════════════════════════
        // PHASE 2: All rows valid — create all users
        // ═══════════════════════════════════════════════════════════

        int created = 0;
        List<CreatedStudent> createdStudents = new ArrayList<>();

        for (ValidatedRow vr : validatedRows) {
            StudentRow row = vr.row;

            String username = generateUsername(row.firstName, row.lastName);
            String password = generatePassword();

            User user = new User();
            user.setUsername(username);
            user.setPassword(authService.hash(password));
            user.setRole("STUDENT");
            user.setFirstName(InputValidator.sanitize(row.firstName.trim()));
            user.setLastName(InputValidator.sanitize(row.lastName.trim()));
            user.setEmail(InputValidator.sanitize(row.email.trim()));
            user.setPhone(InputValidator.sanitize(row.phone.trim()));
            user.setGender(vr.gender);
            user.setBirthDate(vr.birthDate);

            Institution inst = new Institution();
            inst.setId(institutionId);
            user.setInstitution(inst);

            try {
                userDao.save(user);
                created++;

                // Add to program/class
                if (vr.program != null) {
                    ProgramMember pm = new ProgramMember();
                    pm.setUser(user);
                    pm.setProgram(vr.program);
                    pm.setRole("STUDENT");
                    String yearLevel = extractYearLevel(vr.program.getName());
                    pm.setYearLevel(yearLevel);
                    pm.setEnrolledAt(LocalDate.now());
                    memberDao.save(pm);

                    // Auto-assign subjects from the program
                    Program fullProg = programDao.findWithSubjects(vr.program.getId());
                    if (fullProg != null) {
                        no.example.verdan.dao.SubjectAssignmentDao assignDao = new no.example.verdan.dao.SubjectAssignmentDao();
                        for (Subject s : fullProg.getSubjects()) {
                            assignDao.assignStudentToSubject(user.getUsername(), s.getCode(), institutionId);
                        }
                    }
                }

                createdStudents.add(new CreatedStudent(
                    user.getId(), username, row.firstName.trim(), row.lastName.trim(),
                    row.email.trim(), password,
                    vr.birthDate != null ? vr.birthDate.toString() : "",
                    row.className != null ? row.className.trim() : ""
                ));

                LOG.info("Batch import: created user '{}' ({} {}) in program '{}'",
                    username, row.firstName, row.lastName,
                    vr.program != null ? vr.program.getName() : "none");

            } catch (Exception e) {
                // Should not happen after validation, but log if it does
                LOG.error("Unexpected error creating user at row {}: {}", row.email, e.getMessage(), e);
                errors.add("Rad: Kunne ikke opprette bruker for " + row.email + ". Kontakt administrator.");
            }
        }

        LOG.info("Batch import complete: {} created, {} errors out of {} rows",
            created, errors.size(), rows.size());

        return new ImportResult(created, errors.size(), rows.size(), errors, createdStudents);
    }

    /** Holds a validated row ready for phase 2 (creation). */
    private record ValidatedRow(StudentRow row, String gender, LocalDate birthDate, Program program) {}

    /** Extract year level from program name (e.g. "8A" → "8", "VG1 Studiesp" → "VG1"). */
    private String extractYearLevel(String programName) {
        if (programName == null) return null;
        // Try numeric prefix (8A, 9B, 10C)
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)").matcher(programName);
        if (m.find()) return m.group(1);
        // Try VG prefix
        m = java.util.regex.Pattern.compile("^(VG\\d)").matcher(programName.toUpperCase());
        if (m.find()) return m.group(1);
        return null;
    }

    // ── Parsing ──

    private List<StudentRow> parseCsv(byte[] data) throws IOException {
        List<StudentRow> rows = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), "UTF-8"));

        String headerLine = reader.readLine();
        if (headerLine == null) return rows;

        // Detect separator (comma or semicolon)
        char sep = headerLine.contains(";") ? ';' : ',';
        String[] headers = headerLine.split(String.valueOf(sep));
        Map<String, Integer> colMap = buildColumnMap(headers);

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            String[] parts = line.split(String.valueOf(sep), -1);
            rows.add(rowFromParts(parts, colMap));
        }
        return rows;
    }

    private List<StudentRow> parseExcel(byte[] data) throws IOException {
        List<StudentRow> rows = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return rows;

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return rows;

            String[] headers = new String[headerRow.getLastCellNum()];
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                headers[i] = cellToString(cell);
            }
            Map<String, Integer> colMap = buildColumnMap(headers);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String[] parts = new String[row.getLastCellNum()];
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    parts[c] = cellToString(row.getCell(c));
                }
                StudentRow sr = rowFromParts(parts, colMap);
                if (sr.firstName != null && !sr.firstName.isBlank()) {
                    rows.add(sr);
                }
            }
        }
        return rows;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val)) yield String.valueOf((long) val);
                else yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    /** Build a flexible column name mapping (supports Norwegian and English). */
    private Map<String, Integer> buildColumnMap(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase()
                .replace("ø", "o").replace("å", "a").replace("æ", "ae");
            if (h.contains("fornavn") || h.equals("firstname") || h.equals("first_name") || h.equals("first name"))
                map.put("firstName", i);
            else if (h.contains("etternavn") || h.equals("lastname") || h.equals("last_name") || h.equals("last name"))
                map.put("lastName", i);
            else if (h.contains("epost") || h.contains("e-post") || h.equals("email") || h.equals("e_post"))
                map.put("email", i);
            else if (h.contains("telefon") || h.equals("phone") || h.equals("tlf") || h.equals("mobil"))
                map.put("phone", i);
            else if (h.contains("kjonn") || h.contains("kjønn") || h.equals("gender") || h.equals("sex"))
                map.put("gender", i);
            else if (h.contains("klasse") || h.equals("class") || h.equals("program") || h.equals("gruppe"))
                map.put("className", i);
            else if (h.contains("fodselsdato") || h.contains("fødselsdato") || h.contains("fodt") || h.contains("født")
                    || h.equals("birthdate") || h.equals("birth_date") || h.equals("dob") || h.equals("dato"))
                map.put("birthDate", i);
        }
        return map;
    }

    private StudentRow rowFromParts(String[] parts, Map<String, Integer> colMap) {
        return new StudentRow(
            getCol(parts, colMap, "firstName"),
            getCol(parts, colMap, "lastName"),
            getCol(parts, colMap, "email"),
            getCol(parts, colMap, "phone"),
            getCol(parts, colMap, "gender"),
            getCol(parts, colMap, "birthDate"),
            getCol(parts, colMap, "className")
        );
    }

    private String getCol(String[] parts, Map<String, Integer> colMap, String key) {
        Integer idx = colMap.get(key);
        if (idx == null || idx >= parts.length) return null;
        String val = parts[idx];
        return val != null ? val.trim() : null;
    }

    // ── DTOs ──

    public record StudentRow(String firstName, String lastName, String email, String phone, String gender, String birthDate, String className) {}

    public record CreatedStudent(int id, String username, String firstName, String lastName, String email, String password, String birthDate, String className) {}

    public record ImportResult(
        int created, int skipped, int total, List<String> errors, List<CreatedStudent> createdStudents
    ) {
        public ImportResult(int created, int skipped, int total, List<String> errors) {
            this(created, skipped, total, errors, List.of());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  VGS Batch Transfer — move EXISTING students to a new institution
    // ══════════════════════════════════════════════════════════════════

    /**
     * Transfer existing students to a new VGS institution via CSV/Excel.
     * Unlike importStudents(), this does NOT create new users — it finds
     * existing users by email/username and moves them.
     *
     * CSV format: epost (or brukernavn), linje, trinn
     */
    public TransferResult transferStudents(byte[] fileData, String fileName, int targetInstitutionId) {
        List<TransferRow> rows;
        String ext = fileName.toLowerCase();

        try {
            if (ext.endsWith(".xlsx") || ext.endsWith(".xls")) {
                rows = parseTransferExcel(fileData);
            } else if (ext.endsWith(".csv")) {
                rows = parseTransferCsv(fileData);
            } else {
                return new TransferResult(0, 0, 0, List.of("Ugyldig filformat. Bruk .csv eller .xlsx"), List.of());
            }
        } catch (Exception e) {
            LOG.error("Failed to parse transfer file: {}", e.getMessage());
            return new TransferResult(0, 0, 0, List.of("Kunne ikke lese filen."), List.of());
        }

        if (rows.isEmpty()) {
            return new TransferResult(0, 0, 0, List.of("Filen inneholder ingen data"), List.of());
        }

        // Load all programs for the TARGET institution
        List<Program> programs = programDao.findByInstitution(targetInstitutionId);
        Map<String, Program> programMap = new HashMap<>();
        for (Program p : programs) {
            programMap.put(p.getName().toLowerCase().trim(), p);
        }

        // ── Phase 1: Validate ALL rows ──

        List<String> errors = new ArrayList<>();
        List<ValidatedTransferRow> validated = new ArrayList<>();
        Set<String> identifiersInBatch = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            TransferRow row = rows.get(i);
            int rowNum = i + 2;

            // Must have at least an identifier
            if ((row.email == null || row.email.isBlank()) && (row.username == null || row.username.isBlank())) {
                errors.add("Rad " + rowNum + ": Epost eller brukernavn mangler");
                continue;
            }

            // Check duplicate in file
            String identifier = row.email != null && !row.email.isBlank()
                ? row.email.trim().toLowerCase() : row.username.trim().toLowerCase();
            if (!identifiersInBatch.add(identifier)) {
                errors.add("Rad " + rowNum + ": " + identifier + " er duplikat i filen");
                continue;
            }

            // Find the user
            User user = null;
            if (row.email != null && !row.email.isBlank()) {
                List<User> found = userDao.findByEmail(row.email.trim());
                if (!found.isEmpty()) user = found.get(0);
            }
            if (user == null && row.username != null && !row.username.isBlank()) {
                List<User> found = userDao.findByUsername(row.username.trim());
                if (!found.isEmpty()) user = found.get(0);
            }
            if (user == null) {
                errors.add("Rad " + rowNum + ": Bruker '" + identifier + "' finnes ikke i systemet");
                continue;
            }

            // Must be a student
            if (!"STUDENT".equalsIgnoreCase(user.getRole())) {
                errors.add("Rad " + rowNum + ": '" + identifier + "' er ikke en elev (rolle: " + user.getRole() + ")");
                continue;
            }

            // Already at this institution?
            if (user.getInstitution() != null && user.getInstitution().getId() == targetInstitutionId) {
                errors.add("Rad " + rowNum + ": '" + identifier + "' tilhører allerede denne institusjonen");
                continue;
            }

            // Validate linje (program)
            if (row.linje == null || row.linje.isBlank()) {
                errors.add("Rad " + rowNum + ": Linje mangler");
                continue;
            }
            Program targetProgram = programMap.get(row.linje.trim().toLowerCase());
            if (targetProgram == null) {
                errors.add("Rad " + rowNum + ": Linje '" + row.linje + "' finnes ikke. Opprett programmet først.");
                continue;
            }

            // ── Validate that the student is currently in the same linje ──
            List<ProgramMember> currentMemberships = memberDao.findByUser(user.getId());
            boolean matchesCurrentProgram = false;
            String currentProgramName = null;
            for (ProgramMember pm : currentMemberships) {
                if (!pm.isGraduated() && pm.getProgram() != null) {
                    currentProgramName = pm.getProgram().getName();
                    if (pm.getProgram().getName().equalsIgnoreCase(row.linje.trim())) {
                        matchesCurrentProgram = true;
                        break;
                    }
                }
            }
            if (!matchesCurrentProgram) {
                String hint = currentProgramName != null
                    ? " (nåværende linje: " + currentProgramName + ")"
                    : " (eleven er ikke tilknyttet noe program)";
                errors.add("Rad " + rowNum + ": '" + identifier + "' kan kun overføres til samme linje" + hint);
                continue;
            }

            // Validate trinn
            if (row.trinn == null || row.trinn.isBlank()) {
                errors.add("Rad " + rowNum + ": Trinn mangler (f.eks. VG1, VG2, VG3)");
                continue;
            }
            String trinn = row.trinn.trim().toUpperCase();
            if (!trinn.matches("VG[1-3].*")) {
                errors.add("Rad " + rowNum + ": Ugyldig trinn '" + row.trinn + "'. Bruk VG1, VG2 eller VG3");
                continue;
            }

            validated.add(new ValidatedTransferRow(row, user, targetProgram, trinn));
        }

        // Stop if any errors
        if (!errors.isEmpty()) {
            LOG.warn("Batch transfer rejected: {} errors in {} rows", errors.size(), rows.size());
            return new TransferResult(0, errors.size(), rows.size(), errors, List.of());
        }

        // ── Phase 2: Transfer all students ──

        int transferred = 0;
        List<TransferredStudent> transferredStudents = new ArrayList<>();

        for (ValidatedTransferRow vr : validated) {
            User user = vr.user;

            // Save old institution for transfer history
            if (user.getInstitution() != null) {
                user.setTransferredFromInstitutionId(user.getInstitution().getId());
            }

            // Change institution
            Institution targetInst = new Institution();
            targetInst.setId(targetInstitutionId);
            user.setInstitution(targetInst);
            userDao.update(user);

            // Create ProgramMember
            ProgramMember pm = new ProgramMember();
            pm.setUser(user);
            pm.setProgram(vr.program);
            pm.setRole("STUDENT");
            pm.setYearLevel(vr.trinn);
            pm.setEnrolledAt(java.time.LocalDate.now());
            memberDao.save(pm);

            // Auto-assign subjects matching the year level
            Program fullProg = programDao.findWithSubjects(vr.program.getId());
            if (fullProg != null) {
                no.example.verdan.dao.SubjectAssignmentDao assignDao = new no.example.verdan.dao.SubjectAssignmentDao();
                for (Subject s : fullProg.getSubjects()) {
                    if (vr.trinn.equals(s.getYearLevel())
                            || s.getYearLevel() == null || s.getYearLevel().isBlank()) {
                        assignDao.assignStudentToSubject(user.getUsername(), s.getCode(), targetInstitutionId);
                    }
                }
            }

            transferred++;
            transferredStudents.add(new TransferredStudent(
                user.getUsername(),
                (user.getFirstName() != null ? user.getFirstName() : "") + " " +
                (user.getLastName() != null ? user.getLastName() : ""),
                vr.program.getName(), vr.trinn
            ));

            LOG.info("Batch transfer: moved '{}' to institution {} → program '{}' year '{}'",
                user.getUsername(), targetInstitutionId, vr.program.getName(), vr.trinn);
        }

        LOG.info("Batch transfer complete: {} transferred out of {} rows", transferred, rows.size());
        return new TransferResult(transferred, 0, rows.size(), List.of(), transferredStudents);
    }

    // ── Transfer parsing ──

    private List<TransferRow> parseTransferCsv(byte[] data) throws IOException {
        List<TransferRow> rows = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data), "UTF-8"));

        String headerLine = reader.readLine();
        if (headerLine == null) return rows;

        char sep = headerLine.contains(";") ? ';' : ',';
        String[] headers = headerLine.split(String.valueOf(sep));
        Map<String, Integer> colMap = buildTransferColumnMap(headers);

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) continue;
            String[] parts = line.split(String.valueOf(sep), -1);
            rows.add(transferRowFromParts(parts, colMap));
        }
        return rows;
    }

    private List<TransferRow> parseTransferExcel(byte[] data) throws IOException {
        List<TransferRow> rows = new ArrayList<>();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) return rows;

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) return rows;

            String[] headers = new String[headerRow.getLastCellNum()];
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                headers[i] = cellToString(headerRow.getCell(i));
            }
            Map<String, Integer> colMap = buildTransferColumnMap(headers);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String[] parts = new String[row.getLastCellNum()];
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    parts[c] = cellToString(row.getCell(c));
                }
                TransferRow tr = transferRowFromParts(parts, colMap);
                if ((tr.email != null && !tr.email.isBlank()) || (tr.username != null && !tr.username.isBlank())) {
                    rows.add(tr);
                }
            }
        }
        return rows;
    }

    private Map<String, Integer> buildTransferColumnMap(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase()
                .replace("ø", "o").replace("å", "a").replace("æ", "ae");
            if (h.contains("epost") || h.contains("e-post") || h.equals("email") || h.equals("e_post"))
                map.put("email", i);
            else if (h.contains("brukernavn") || h.equals("username") || h.equals("bruker"))
                map.put("username", i);
            else if (h.contains("linje") || h.equals("program") || h.equals("studieretning") || h.equals("line"))
                map.put("linje", i);
            else if (h.contains("trinn") || h.equals("yearlevel") || h.equals("year_level") || h.equals("year") || h.equals("level"))
                map.put("trinn", i);
        }
        return map;
    }

    private TransferRow transferRowFromParts(String[] parts, Map<String, Integer> colMap) {
        return new TransferRow(
            getCol(parts, colMap, "email"),
            getCol(parts, colMap, "username"),
            getCol(parts, colMap, "linje"),
            getCol(parts, colMap, "trinn")
        );
    }

    // ── Transfer DTOs ──

    public record TransferRow(String email, String username, String linje, String trinn) {}
    private record ValidatedTransferRow(TransferRow row, User user, Program program, String trinn) {}
    public record TransferredStudent(String username, String fullName, String program, String yearLevel) {}
    public record TransferResult(int transferred, int skipped, int total, List<String> errors,
                                  List<TransferredStudent> transferredStudents) {}
}
