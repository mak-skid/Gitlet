package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.*;

import static gitlet.Repository.INDEX;
import static gitlet.Utils.*;

public class Index implements Serializable {
    private Map<String, String> staged;  /* <filePath, stagingArea_hash> */
    private Set<String> rmStaged;
    private Map<String, String> tracked;

    public Index() {
        staged = new HashMap<>();
        rmStaged = new HashSet<>();
        tracked = new HashMap<>();
    }

    public static Index fromFile() {
        if (!INDEX.exists()) { return null; }
        return readObject(INDEX, Index.class);
    }

    public String findBlobId(String filePath) {
        return staged.get(filePath);
    }

    public void save() {
        writeObject(INDEX, this);
    }

    public void clear() {
        staged.clear();
        rmStaged.clear();
        save();
    }

    public boolean isClean() {
        return staged.isEmpty() && rmStaged.isEmpty();
    }

    public Map<String, String> getStaged() {
        return staged;
    }

    public Map<String, String> getTracked() {
        return tracked;
    }

    public void add(String filePath, String blobId) {
        staged.put(filePath, blobId);
        rmStaged.remove(filePath);
        tracked.put(filePath, blobId);
    }

    public void add(File file) {
        String filePath = file.getPath();
        Blob blob = new Blob(file);
        blob.create();
        String blobId = blob.getId();
        add(filePath, blobId);
    }

    public boolean isStaged(File file) {
        return staged.containsKey(file.getPath());
    }

    public boolean isStaged(String filePath) {
        return staged.containsKey(filePath);
    }

    public void remove(String filePath) {
        staged.remove(filePath);
        rmStaged.add(filePath);
    }

    public void unstage(String filePath) {
        staged.remove(filePath);
    }

    public void unremove(String filePath) {
        rmStaged.remove(filePath);
    }

    public Set<String> getRmStaged() {
        return rmStaged;
    }

    public boolean isRmStaged(String filePath) {
        return rmStaged.contains(filePath);
    }
}
