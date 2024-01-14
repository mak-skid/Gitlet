package gitlet;

import java.io.File;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import static gitlet.Repository.OBJECTS_DIR;
import static gitlet.Utils.*;

public class Blob implements Serializable {
    private String id;
    private byte[] content;
    private File file;

    public Blob(File sourceFile) {
        String filePath = sourceFile.getPath();
        content = readContents(sourceFile);
        id = sha1(filePath, content);
        file = join(OBJECTS_DIR, id);
    }

    public String getId() {
        return id;
    }

    public static String generateId(File srcFile) {
        String filePath = srcFile.getPath();
        byte[] fileContent = readContents(srcFile);
        return sha1(filePath, fileContent);
    }

    public void create() {
        writeObject(file, this);
    }

    public byte[] getContent() {
        return content;
    }

    public static Blob fromFile(String blobId) {
        File blobFile = join(OBJECTS_DIR, blobId);
        return readObject(blobFile, Blob.class);
    }

    public String getContentAsString() {
        return new String(content, StandardCharsets.UTF_8);
    }
}
