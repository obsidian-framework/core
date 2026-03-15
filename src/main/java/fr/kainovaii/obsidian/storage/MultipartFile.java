package fr.kainovaii.obsidian.storage;

import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wrapper around a servlet {@link Part} for multipart file uploads.
 */
public class MultipartFile
{
    private final Part part;

    /**
     * Creates a new MultipartFile wrapping the given servlet part.
     *
     * @param part The raw servlet part from the multipart request
     */
    public MultipartFile(Part part) {
        this.part = part;
    }

    /**
     * Returns the original filename as sent by the client.
     *
     * @return The original filename, or "upload" if not present
     */
    public String getOriginalName() {
        String header = part.getHeader("content-disposition");
        if (header == null) return "upload";
        for (String token : header.split(";")) {
            token = token.trim();
            if (token.startsWith("filename=")) {
                return token.substring(9).replace("\"", "").trim();
            }
        }
        return "upload";
    }

    /**
     * Returns the MIME content type of the uploaded file.
     *
     * @return The content type as reported by the client
     */
    public String getContentType() {
        return part.getContentType();
    }

    /**
     * Returns the size of the uploaded file in bytes.
     *
     * @return The file size in bytes
     */
    public long getSize() {
        return part.getSize();
    }

    /**
     * Opens an InputStream to read the uploaded file content.
     *
     * @return An open InputStream for the file content
     */
    public InputStream getInputStream() {
        try {
            return part.getInputStream();
        } catch (IOException e) {
            throw new StorageException("Cannot read multipart stream", e);
        }
    }

    /**
     * Extracts the file extension from the original filename.
     *
     * @return The lowercase extension without the dot, or an empty string if none
     */
    public String getExtension() {
        String name = getOriginalName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
    }
}