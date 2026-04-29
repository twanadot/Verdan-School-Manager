package no.example.verdan.service;

import no.example.verdan.dao.ChatAttachmentDao;
import no.example.verdan.model.ChatAttachment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/**
 * Handles file upload and download for chat attachments.
 * Files are stored in /app/data/uploads/ (Docker volume).
 */
public class FileService {

    private static final Logger LOG = LoggerFactory.getLogger(FileService.class);
    private static final String UPLOAD_DIR = "/app/data/uploads/";
    private static final long MAX_FILE_SIZE = 25 * 1024 * 1024; // 25MB

    /** Dangerous file extensions that are blocked from upload. */
    private static final java.util.Set<String> BLOCKED_EXTENSIONS = java.util.Set.of(
        ".exe", ".bat", ".cmd", ".msi", ".scr"
    );

    private final ChatAttachmentDao attachmentDao;

    public FileService() {
        this(new ChatAttachmentDao());
        ensureUploadDir();
    }

    public FileService(ChatAttachmentDao attachmentDao) {
        this.attachmentDao = attachmentDao;
        ensureUploadDir();
    }

    private void ensureUploadDir() {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            LOG.warn("Could not create upload dir: {}", e.getMessage());
        }
    }

    /**
     * Upload a file and create an unlinked ChatAttachment (linked to message later).
     * Returns the attachment entity with its generated ID.
     */
    public ChatAttachment upload(InputStream inputStream, String fileName, String mimeType, long fileSize) {
        if (fileSize > MAX_FILE_SIZE) {
            throw new ValidationException(java.util.List.of("File too large. Maximum size is 25MB."));
        }

        // Check for blocked file extensions
        String lowerName = fileName.toLowerCase();
        for (String blocked : BLOCKED_EXTENSIONS) {
            if (lowerName.endsWith(blocked)) {
                throw new ValidationException(java.util.List.of(
                    "File type " + blocked + " is not allowed for security reasons."));
            }
        }

        // Generate unique stored filename
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) ext = fileName.substring(dot);
        String storedName = UUID.randomUUID().toString() + ext;
        String storedPath = UPLOAD_DIR + storedName;

        try {
            Files.copy(inputStream, Paths.get(storedPath), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }

        // Create attachment record (message will be linked when the chat message is sent)
        ChatAttachment att = new ChatAttachment();
        att.setFileName(fileName);
        att.setStoredPath(storedPath);
        att.setFileSize(fileSize);
        att.setMimeType(mimeType != null ? mimeType : "application/octet-stream");
        attachmentDao.save(att);

        LOG.info("File uploaded: {} ({} bytes) → {}", fileName, fileSize, storedPath);
        return att;
    }

    /** Get the file path for download. */
    public ChatAttachment getAttachment(int attachmentId) {
        return attachmentDao.find(attachmentId);
    }

    /** Get the actual file input stream. */
    public InputStream getFileStream(String storedPath) throws IOException {
        Path path = Paths.get(storedPath);
        if (!Files.exists(path)) throw new NotFoundException("File not found");
        return Files.newInputStream(path);
    }
}
