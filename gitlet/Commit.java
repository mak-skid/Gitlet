package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static gitlet.Repository.*;
import static gitlet.Utils.*;
import static gitlet.Utils.readObject;

/** Represents a gitlet commit object.
 *
 *  @author mak.skid
 */
public class Commit implements Serializable {
    /**
     * List all instance variables of the Commit class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided one example for `message`.
     */

    /** The message of this Commit. */
    private final String message;
    private final Date date;

    /** stores a reference to a blob. */
    private final ConcurrentHashMap<String, String> trackedBlobs;

    /** stores ids of parent commits */
    private final List<String> parentRefs;
    private final String id;

    public Commit(String m, Commit currCommit) {
        message = m;
        date = new Date();
        trackedBlobs = currCommit.getTrackedBlobs();
        parentRefs = new ArrayList<>();
        id = generateId();
    }

    public Commit() {
        message = "initial commit";
        date = new Date(0);
        trackedBlobs = new ConcurrentHashMap<>();
        parentRefs = new ArrayList<>();
        id = generateId();
    }

    public static Commit find(String hash) {
        File subDir = join(COMMITS_DIR, hash.substring(0, 2));
        File commitFileToRead = join(subDir, hash.substring(2));
        if (!commitFileToRead.exists()) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        return readObject(commitFileToRead, Commit.class);
    }

    public String getBlobId(String filePath) {
        return trackedBlobs.get(filePath);
    }

    public void updateTracked() {
        Index stagingFiles = Index.fromFile();
        Map<String, String> stage = stagingFiles.getStaged();
        Set<String> rmStaged = stagingFiles.getRmStaged();

        for (String key: trackedBlobs.keySet()) {
            String blobWithSameKeyInStage = stage.remove(key);
            if (rmStaged.contains(key)) {
                trackedBlobs.remove(key);
            } else if (blobWithSameKeyInStage != null) { // if stage contains key
                trackedBlobs.replace(key, blobWithSameKeyInStage);
            }
        }
        for (String stageKey: stage.keySet()) {
            trackedBlobs.put(stageKey, stage.get(stageKey));
        }
    }

    public void removeTracked() {
        for (String filePath: trackedBlobs.keySet()) {
            File fileInCWD = new File(filePath);
            if (!fileInCWD.exists()) {
                trackedBlobs.remove(filePath);
            }
        }
    }

    public boolean isTracked(String filePath) {
        return trackedBlobs.get(filePath) != null;
    }

    public void updateParentRefs(String firstParent, String secondParent) {
        if (secondParent != null) {
            parentRefs.add(secondParent);
        }
        parentRefs.add(firstParent);
    }

    public String getId() {
        return id;
    }

    public Date getDate() { return date; }

    public String getMessage() {
        return message;
    }

    public void create() {
        String newCommitSubDirName = id.substring(0, 2);
        File newCommitSubDir = join(COMMITS_DIR, newCommitSubDirName);
        newCommitSubDir.mkdir();
        File newCommitFile = join(newCommitSubDir, id.substring(2));
        writeObject(newCommitFile, this);
        File refFile = getCurrBranchHeadRefFile();
        writeContents(refFile, id);
    }

    public List<String> getParents() {
        return parentRefs;
    }

    public ConcurrentHashMap<String, String> getTrackedBlobs() { return trackedBlobs; }

    public boolean hasIdentical(File file) {
        String hash = sha1(file.getPath(), readContents(file));
        String blobRef = trackedBlobs.get(file.getPath());
        return hash.equals(blobRef);
    }

    private String generateId() {
        return sha1(message, getTimestamp(), trackedBlobs.toString(), parentRefs.toString());
    }

    public String getTimestamp() {
        // Thu Jan 1 00:00:00 1970 +0000
        DateFormat dateFormat = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.ENGLISH);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT+09:00"));
        return dateFormat.format(date);
    }

    public String getLog() {
        StringBuilder sb = new StringBuilder("===\ncommit ");
        sb.append(getId() + "\n");
        StringBuilder shortenedIDs = new StringBuilder("Merge:");
        if (parentRefs.size() > 1) {
            for (String parent: parentRefs) {
                shortenedIDs.append(" " + parent.substring(0, 7));
            }
            sb.append(shortenedIDs + "\n");
        }
        sb.append("Date: " + getTimestamp() + "\n");
        sb.append(getMessage() + "\n");
        return sb.toString();
    }
}
