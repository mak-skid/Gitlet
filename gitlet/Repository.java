package gitlet;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static gitlet.Utils.*;
import static java.lang.System.exit;

/** Represents a gitlet repository.
 *
 *  @author mak.skid
 */
public class Repository {
    /**
     * List all instance variables of the Repository class here with a useful
     * comment above them describing what that variable represents and how that
     * variable is used. We've provided two examples for you.
     */

    /**
     * The current working directory.
     */
    private static final String DEFAULT_BRANCH_NAME = "master";
    public static final String HEAD_BRANCH_REF_PATH = "ref: refs/heads/";
    public static final File CWD = new File(System.getProperty("user.dir"));
    /**
     * The .gitlet directory.
     */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /**
     * The /objects directory. stores blob files.
     */
    public static final File OBJECTS_DIR = join(GITLET_DIR, "objects");
    /**
     * commit directory. separetely save commit files.
     */
    public static final File COMMITS_DIR = join(GITLET_DIR, "commits");
    /**
     * staging files
     */
    public static final File INDEX = join(GITLET_DIR, "index");

    /**
     * points to the branch that is currently checked out (the "HEAD" of the repository).
     * the .git/HEAD file contains a String reference to the current branch.
     */
    public static final File HEAD = join(GITLET_DIR, "HEAD");

    /**
     * contains references to commit objects
     */
    public static final File REFS_DIR = join(GITLET_DIR, "refs");

    /**
     * contains reference files to branch heads each named as the branch name.
     * The content of each file is the SHA-1 hash of the commit that the branch currently points to.
     */
    public static final File BRANCH_HEADS_DIR = join(REFS_DIR, "heads");

    public static final File[] CWD_FILES = CWD.listFiles(File::isFile);

    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            exit(0);
        } else {
            GITLET_DIR.mkdir();
            OBJECTS_DIR.mkdir();
            COMMITS_DIR.mkdir();
            REFS_DIR.mkdir();
            BRANCH_HEADS_DIR.mkdir();
            setCurrentBranch(DEFAULT_BRANCH_NAME);
            createInitialCommit();
        }
    }

    private static void setCurrentBranch(String branchName) {
        writeContents(HEAD, HEAD_BRANCH_REF_PATH + branchName);
    }

    private static void updateBranchHead(String commitId) {
        writeContents(getCurrBranchHeadRefFile(), commitId);
    }

    public static void createInitialCommit() {
        Commit initialCommit = new Commit();
        initialCommit.create(); // add a commit file to objects directry
        File masterFile = join(BRANCH_HEADS_DIR, DEFAULT_BRANCH_NAME);
        writeContents(masterFile, initialCommit.getId()); // add a 'master' reference file to refs/heads directory
    }


    public static void add(String filename) {
        File fileToAdd = join(CWD, filename);
        if (!fileToAdd.exists()) {
            System.out.println("File does not exist.");
            exit(0);
        }

        if (!INDEX.exists()) {
            Index newStagingFile = new Index();
            newStagingFile.add(fileToAdd);
            newStagingFile.save();
            return;
        }
        Index stagingFile = Index.fromFile();
        Commit head = getCurrentBranchHeadCommit();
        if (head.hasIdentical(fileToAdd)) {
            stagingFile.unstage(fileToAdd.getPath());
            stagingFile.unremove(fileToAdd.getPath());
            stagingFile.save();
            return;
        }
        stagingFile.add(fileToAdd);
        stagingFile.save();
    }

    public static void commit(String message) {
        commit(message, null);
    }
    /* Second Parent parameter is used for merging */
    public static void commit(String message, String secondParent) {
        Index stagingArea = Index.fromFile();
        if (stagingArea == null || stagingArea.isClean()) {
            System.out.println("No changes added to the commit.");
            exit(0);
        }
        Commit currCommit = getCurrentBranchHeadCommit();
        Commit newCommit = new Commit(message, currCommit);
        newCommit.updateTracked();
        newCommit.updateParentRefs(currCommit.getId(), secondParent);
        newCommit.create();
        stagingArea.clear();
    }

    public static void rm(String filename) {
        Commit currCommit = getCurrentBranchHeadCommit();
        Index stagedFile = Index.fromFile();
        File fileToRemove = join(CWD, filename);
        String filePath = fileToRemove.getPath();

        if (stagedFile == null || !stagedFile.isStaged(fileToRemove)) {
            if (currCommit.isTracked(filePath)) {
                stagedFile.remove(filePath);
                stagedFile.save();
                fileToRemove.delete();
            } else {
                System.out.println("No reason to remove the file.");
                exit(0);
            }
        } else {
            if (currCommit.isTracked(filePath)) {
                stagedFile.remove(filePath);
                stagedFile.save();
                fileToRemove.delete();
            } else {
                stagedFile.unstage(filePath);
                stagedFile.save();
            }
        }
    }

    public static void log() {
        Commit currCommit = getCurrentBranchHeadCommit();
        StringBuilder logBuilder = new StringBuilder();
        while (true) {
            logBuilder.append(currCommit.getLog()).append("\n");
            List<String> parentCommitIDs = currCommit.getParents();
            if (parentCommitIDs.size() == 0) { break; }
            String nextCommitId = parentCommitIDs.get(0);
            currCommit = Commit.find(nextCommitId);
        }
        System.out.println(logBuilder);
    }

    public static void globalLog() {
        List<String> commits = plainFilenamesIn(COMMITS_DIR);
        StringBuilder logBuilder = new StringBuilder();
        for (String hash: commits) {
            Commit commit = Commit.find(hash);
            String log = commit.getLog();
            logBuilder.append(log).append("\n");
        }
        System.out.println(logBuilder);
    }

    public static void find(String msg) {
        Commit commit = new Commit();
        File[] commitSubDirs = COMMITS_DIR.listFiles(File::isDirectory);
        boolean isFound = false;
        for (File subDir: commitSubDirs) {
            List<String> commits = plainFilenamesIn(subDir);
            for (String commitId: commits) {
                commit = commit.find(subDir.getName()+commitId);
                String message = commit.getMessage();
                if (msg.equals(message)) {
                    System.out.println(commit.getId());
                    isFound = true;
                }
            }
        }
        if (!isFound) {
            System.out.println("Found no commit with that message.");
        }
    }

    public static void status() {
        StringBuilder statusLog = new StringBuilder("=== Branches ===\n");
        List<String> branchLists = plainFilenamesIn(BRANCH_HEADS_DIR);
        for (String branchName: branchLists) {
            if (isCurrBranch(branchName)) { statusLog.append("*"); }
            statusLog.append(branchName + "\n");
        }
        if (!INDEX.exists()) { // gitlet not in initialised state
            statusNullIndex(statusLog);
            return;
        }
        List<String> cwdFileList = plainFilenamesIn(CWD);
        Index stagingFiles = Index.fromFile();
        Map<String, String> stageMap = stagingFiles.getStaged();
        StringBuilder stagedButDeleted = new StringBuilder();
        StringBuilder stagedButModded = new StringBuilder();

        statusLog.append("\n=== Staged Files === \n");
        for (String stagedPath: stageMap.keySet()) {
            File cwdFile = new File(stagedPath);
            String cwdHash = sha1(cwdFile.getPath(), readContents(cwdFile));
            String stagedFileName = Paths.get(stagedPath).getFileName().toString();
            if (!cwdFile.exists()) {
                stagedButDeleted.append(stagedFileName + " (deleted)\n");
            } else if (!cwdHash.equals(stageMap.get(stagedPath))) {
                stagedButModded.append(stagedFileName + " (modified)\n");
            } else {
                statusLog.append(stagedFileName + "\n");
            }
            cwdFileList.remove(stagedFileName);
        }

        statusLog.append("\n=== Removed Files ===\n");
        Set<String> rmStagedSet = stagingFiles.getRmStaged();
        for (String rmStagedPath: rmStagedSet) {
            String rmStagedFileName = Paths.get(rmStagedPath).getFileName().toString();
            statusLog.append(rmStagedFileName + '\n');
        }
        statusLog.append("\n=== Modifications Not Staged For Commit ===\n"
                + stagedButDeleted + stagedButModded
                + "\n=== Untracked Files ===\n");
        for (String untrackedFileName: cwdFileList) {
            String untrackedFilePath = join(CWD, untrackedFileName).getPath();
            if (stagingFiles.getTracked().containsKey(untrackedFilePath)) { continue; }
            statusLog.append(untrackedFileName + "\n");
        }
        statusLog.append("\n");
        System.out.print(statusLog);
    }

    private static void statusNullIndex(StringBuilder statusLog) {
         statusLog.append("\n=== Staged Files === \n")
                .append("\n=== Removed Files ===\n")
                .append("\n=== Modifications Not Staged For Commit ===\n")
                .append("\n=== Untracked Files ===\n");
         System.out.print(statusLog);
    }

    private static boolean isCurrBranch(String branchName) {
        return getCurrBranchName().equals(branchName);
    }

    public static Commit getCurrentBranchHeadCommit() {
        String commitId = getCurrBranchHeadCommitId();
        return Commit.find(commitId);
    }

    public static File getCurrBranchHeadRefFile() {
        String currentBranchName = getCurrBranchName();
        return join(BRANCH_HEADS_DIR, currentBranchName);
    }

    private static String getCurrBranchName() {
        String headRef = readContentsAsString(HEAD);
        return headRef.replace(HEAD_BRANCH_REF_PATH, "");
    }

    public static String getCurrBranchHeadCommitId() {
        File refFile = getCurrBranchHeadRefFile();
        return readContentsAsString(refFile);
    }

    private static Map<String, String> getCurrentFilesMap() {
        Map<String, String> filesMap = new HashMap<>();
        for (File file : CWD_FILES) {
            String filePath = file.getPath();
            String blobId = Blob.generateId(file);
            filesMap.put(filePath, blobId);
        }
        return filesMap;
    }

    public static void checkout(Commit targetCommit, String fileName) {
        File file = join(CWD, fileName);
        String targetCommitBlobId = targetCommit.getBlobId(file.getPath());
        if (targetCommitBlobId == null) {
            System.out.println("File does not exist in that commit.");
            exit(0);
        }
        byte[] blobContent = Blob.fromFile(targetCommitBlobId).getContent();
        File fileToOverwrite = file;
        writeContents(fileToOverwrite, blobContent);
    }

    public static void checkoutFile(String fileName) {
        Commit head = getCurrentBranchHeadCommit();
        checkout(head, fileName);
    }

    public static void checkoutId(String commitId, String fileName) {
        String fullCommitId = getFullCommitId(commitId);
        Commit designatedCommit = Commit.find(fullCommitId);
        checkout(designatedCommit, fileName);
    }

    private static String getFullCommitId(String commitId) {
        if (commitId.length() == 40) {
            return commitId;
        }
        if (commitId.length() < 4) {
            System.out.println("Commit id should contain at least 4 characters.");
            System.exit(0);
        }
        String commitSubDirName = commitId.substring(0, 2);
        File commitSubDir = join(COMMITS_DIR, commitSubDirName);
        if (!commitSubDir.exists()) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        boolean isFound = false;
        String commitFileNamePrefix = commitId.substring(2);
        for (File commitFile: commitSubDir.listFiles()) {
            String commitFileName = commitFile.getName();
            if (commitFileName.startsWith(commitFileNamePrefix)) {
                if (isFound) {
                    System.out.println("More than one commit has the same id prefix.");
                }
                commitId = commitSubDirName + commitFileName;
                isFound = true;
            }
        }
        if (!isFound) {
            System.out.println("No commit with that id exists.");
            System.exit(0);
        }
        return commitId;
    }

    public static void checkoutBranch(String branchName) {
        File branchHead = join(BRANCH_HEADS_DIR, branchName);
        if (!branchHead.exists()) {
            System.out.println("No such branch exists.");
            exit(0);
        }
        if (isCurrBranch(branchName)) {
            System.out.println("No need to checkout the current branch.");
            exit(0);
        }
        String branchHeadId = readContentsAsString(branchHead);
        Commit headCommit = Commit.find(branchHeadId);
        checkoutAll(headCommit);
        setCurrentBranch(branchName);
    }

    private static void checkoutAll(Commit targetCommit) {
        /* CWD - tracked = untracked by the commit
           Here the untracked files need to be deleted.
           firstly, change file[] to a list of file paths and create untracked file list.t,l
         */
        List<String> untracked = new ArrayList<>();
        for (File file: CWD_FILES) {
            untracked.add(file.getPath());
        }
        Map<String, String> tracked = targetCommit.getTrackedBlobs();
        for (String filePath: tracked.keySet()) {
            File file = new File(filePath);
            untracked.remove(filePath);
            String fileName = file.getName();
            checkUntracked(targetCommit);
            checkout(targetCommit, fileName);
        }
        for (String fileToDeletePath: untracked) {
            File fileToDelete = new File(fileToDeletePath);
            fileToDelete.delete();
        }
    }

    private static void checkUntracked(Commit targetCommit) {
        Map<String, String> currentFilesMap = getCurrentFilesMap();
        Map<String, String> trackedFilesMap = getCurrentBranchHeadCommit().getTrackedBlobs();
        Map<String, String> addedFilesMap = Index.fromFile().getStaged();
        Set<String> removedFilePathsSet = Index.fromFile().getRmStaged();

        List<String> untrackedFilePaths = new ArrayList<>();

        for (String filePath : currentFilesMap.keySet()) {
            if (trackedFilesMap.containsKey(filePath)) {
                if (removedFilePathsSet.contains(filePath)) {
                    untrackedFilePaths.add(filePath);
                }
            } else {
                if (!addedFilesMap.containsKey(filePath)) {
                    untrackedFilePaths.add(filePath);
                }
            }
        }

        Map<String, String> targetCommitTrackedFilesMap = targetCommit.getTrackedBlobs();

        for (String filePath : untrackedFilePaths) {
            String blobId = currentFilesMap.get(filePath);
            String targetBlobId = targetCommitTrackedFilesMap.get(filePath);
            if (!blobId.equals(targetBlobId)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                System.exit(0);
            }
        }
    }

    public static void reset(String commitId) {
        Commit targetCommit = Commit.find(commitId);
        checkoutAll(targetCommit);
        targetCommit.removeTracked();
        Index stagingArea = Index.fromFile();
        stagingArea.clear();
        updateBranchHead(commitId);
    }

    public static void branch(String branchName) {
        File newBranchFile = join(BRANCH_HEADS_DIR, branchName);
        validateBranch(newBranchFile);
        String commitId = getCurrBranchHeadCommitId();
        writeContents(newBranchFile, commitId);
    }

    public static void rmBranch(String branchName) {
        File branchToRm = join(BRANCH_HEADS_DIR, branchName);
        exitIfNotExists(branchToRm);
        if (isCurrBranch(branchName)) {
            System.out.println("Cannot remove the current branch.");
            exit(0);
        }
        branchToRm.delete();
    }

    private static void validateBranch(File branchFile) {
        if (branchFile.exists()) {
            System.out.println("A branch with that name already exists.");
            exit(0);
        }
    }

    private static void exitIfNotExists(File branchFile) {
        if (!branchFile.exists()) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        }
    }

    public static void merge(String branchName) {
        Index stagingArea = Index.fromFile();
        File branchFile = join(BRANCH_HEADS_DIR, branchName);
        exitIfNotExists(branchFile);
        String givenBranchHeadId = readContentsAsString(branchFile);
        Commit currBranchHead = getCurrentBranchHeadCommit();
        checkUntracked(currBranchHead);
        if (!stagingArea.isClean()) {
            System.out.println("You have uncommitted changes.");
            exit(0);
        }
        if (givenBranchHeadId.equals(currBranchHead.getId())) {
            System.out.println("Cannot merge a branch with itself.");
            exit(0);
        }
        Commit givenBranchHead = Commit.find(givenBranchHeadId);
        Commit splitPoint = findSplitPoint(currBranchHead, givenBranchHead);
        if (splitPoint.getId().equals(givenBranchHead.getId())) {
            System.out.println("Given branch is an ancestor of the current branch.");
            exit(0);
        }
        if (splitPoint.getId().equals(currBranchHead.getId())) {
            checkoutAll(givenBranchHead);
            setCurrentBranch(branchName);
            System.out.println("Current branch fast-forwarded.");
            exit(0);
        }

        Map<String, String> splitPointTrackedBlobs = splitPoint.getTrackedBlobs();
        Map<String, String> currBranchTrackedBlobs = currBranchHead.getTrackedBlobs();
        Map<String, String> givenBranchTrackedBlobs = givenBranchHead.getTrackedBlobs();
        boolean isConflicted = false;
        for (String blobKey: splitPointTrackedBlobs.keySet()) {
            String spBlobId = splitPointTrackedBlobs.get(blobKey);
            String cbBlobId = currBranchTrackedBlobs.get(blobKey);
            String gbBlobId = givenBranchTrackedBlobs.get(blobKey);

            if (gbBlobId == null && !isModified(spBlobId, cbBlobId)) { // case 6
                stagingArea.remove(blobKey);
                File file = new File(blobKey);
                file.delete();
            } else if (cbBlobId == null && !isModified(spBlobId, gbBlobId)) { // case 7 do nothing
                givenBranchTrackedBlobs.remove(blobKey);
            } else if (isModified(spBlobId, gbBlobId)
                    && !isModified(spBlobId, cbBlobId)) { // case 1
                stagingArea.add(blobKey, gbBlobId);
                writeContents(new File(blobKey), Blob.fromFile(gbBlobId).getContentAsString());
            } else if (!isModified(spBlobId, gbBlobId)
                    && isModified(spBlobId, cbBlobId)) { // case 2
            } else if ((gbBlobId == null && cbBlobId == null)
                    || (
                            isModified(spBlobId, gbBlobId)
                                    && isModified(spBlobId, cbBlobId)
                                    && spBlobId.equals(cbBlobId))
                    ) { // case 3
            } else if (isModified(spBlobId, gbBlobId)
                    && isModified(spBlobId, cbBlobId)
                    && !spBlobId.equals(cbBlobId)) { // case 8 conflicted
                String cbContent = cbBlobId == null ? "" : Blob.fromFile(cbBlobId).getContentAsString();
                String gbContent = gbBlobId == null ? "" : Blob.fromFile(gbBlobId).getContentAsString();
                String conflictContent = "<<<<<<< HEAD\n" + cbContent + "=======\n" + gbContent + ">>>>>>>";
                File newBlobFile = new File(blobKey);
                writeContents(newBlobFile, conflictContent);
                stagingArea.add(newBlobFile);
                isConflicted = true;
            }

            givenBranchTrackedBlobs.remove(blobKey); // remove item from given branch for case 5 operation
        }
        // case 4 do nothing

        for (String blobKey: givenBranchTrackedBlobs.keySet()) { // case 5
            File file = new File(blobKey);
            String fileName = file.getName();
            checkout(givenBranchHead, fileName);
            stagingArea.add(blobKey, givenBranchTrackedBlobs.get(blobKey));
        }

        String mergeCommitMessage = "Merged " + branchName + " into " + getCurrBranchName();
        stagingArea.save();
        commit(mergeCommitMessage, currBranchHead.getId());

        if (isConflicted) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /** finds a split point. maybe contains a bug because the list might need to be reversed.
     *
     * @param currHead
     * @param givenHead
     * @return
     */
    @SuppressWarnings("ConstantConditions")
    private static Commit findSplitPoint(Commit currHead, Commit givenHead) {
        Comparator<Commit> commitComparator = Comparator.comparing(Commit::getDate).reversed();
        Queue<Commit> commitsQueue = new PriorityQueue<>(commitComparator);
        commitsQueue.add(currHead);
        commitsQueue.add(givenHead);
        Set<String> checkedCommitIds = new HashSet<>();
        while (true) {
            Commit latestCommit = commitsQueue.poll();
            List<String> parentCommitIds = latestCommit.getParents();
            String firstParentCommitId = parentCommitIds.get(0);
            Commit firstParentCommit = Commit.find(firstParentCommitId);
            if (checkedCommitIds.contains(firstParentCommitId)) {
                return firstParentCommit;
            }
            commitsQueue.add(firstParentCommit);
            checkedCommitIds.add(firstParentCommitId);

        }
    }

    private static String findSmallerParentBranchName(List<String> curr, List<String> given) {
        if (curr.size() <= given.size()) {
            return "curr";
        } else {
            return "given";
        }
    }

    private static boolean isModified(String firstBlobId, String secondBlobId) {
        if (firstBlobId == null) {
            if (secondBlobId == null) {
                return false;
            } else {
                return true;
            }
        }
        return !firstBlobId.equals(secondBlobId);
    }
}

