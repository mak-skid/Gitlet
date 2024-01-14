package gitlet;

import static gitlet.Repository.*;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author mak.skid
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Please enter a command.");
            System.exit(0);
        }
        String firstArg = args[0];
        switch (firstArg) {
            case "init":
                validateArgs(args, 1);
                init();
                break;
            case "add":
                validateArgs(args, 2);
                add(args[1]);
                break;
            case "commit":
                validateArgs(args, 2);
                String message = args[1];
                if (message.length() == 0) {
                    System.out.println("Please enter a commit message.");
                    System.exit(0);
                }
                commit(message);
                break;
            case "rm":
                validateArgs(args, 2);
                rm(args[1]);
                break;
            case "log":
                validateArgs(args, 1);
                log();
                break;
            case "global-log":
                validateArgs(args, 1);
                globalLog();
                break;
            case "find":
                validateArgs(args, 2);
                find(args[1]);
                break;
            case "status":
                validateArgs(args, 1);
                status();
                break;
            case "checkout":
                if (args[1].equals("--")) {
                    validateArgs(args, 3);
                    String fileName = args[2];
                    checkoutFile(fileName);
                } else if (args.length == 4 && args[2].equals("--")) {
                    validateArgs(args, 4);
                    String id = args[1];
                    String fileName = args[3];
                    checkoutId(id, fileName);
                } else {
                    validateArgs(args, 2);
                    String branchToCheckoutName = args[1];
                    checkoutBranch(branchToCheckoutName);
                }
                break;
            case "branch":
                validateArgs(args, 2);
                String branchName = args[1];
                branch(branchName);
                break;
            case "rm-branch":
                validateArgs(args, 2);
                String branchToRmName = args[1];
                rmBranch(branchToRmName);
                break;
            case "reset":
                validateArgs(args, 2);
                String commitId = args[1];
                reset(commitId);
                break;
            case "merge":
                validateArgs(args, 2);
                String branchToMergeName = args[1];
                merge(branchToMergeName);
                break;
            default:
                System.out.println("No command with that name exists.");
                break;
        }
    }

    public static void validateArgs(String[] args, int n) {
        if (!args[0].equals("init") && !GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
        if (args.length != n) {
            System.out.println("Incorrect operands.");
            System.exit(0);
        }
    }
}
