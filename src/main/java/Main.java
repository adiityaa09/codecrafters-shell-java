import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Scanner;
import java.util.Arrays;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.FileWriter;

public class Main {

    static int getNextJobNumber(List<Job> jobsList) {
        if (jobsList.isEmpty()) {
            return 1;
        }
        int max = 0;
        for (Job job : jobsList) {
            if (job.jobNumber > max) {
                max = job.jobNumber;
            }
        }
        return max + 1;
    }

    static class Job {
        int jobNumber;
        long pid;
        String command;
        String status;

        Job(int jobNumber, long pid, String command, String status) {
            this.jobNumber = jobNumber;
            this.pid = pid;
            this.command = command;
            this.status = status;
        }
    }

    private static void reapJobs(List<Job> jobsList) {
        List<Job> finishedJobs = new ArrayList<>();
        int total = jobsList.size();

        for (int i = 0; i < total; i++) {
            Job job = jobsList.get(i);
            ProcessHandle ph = ProcessHandle.of(job.pid).orElse(null);

            if (ph == null || !ph.isAlive()) {
                String marker = (i == total - 1) ? "+" : (i == total - 2) ? "-" : " ";
                System.out.printf("[%d]%s  %-24s%s%n",
                        job.jobNumber, marker, "Done", job.command.replace(" &", ""));
                finishedJobs.add(job);
            }
        }
        jobsList.removeAll(finishedJobs);
    }

    static List<String> parseArgs(String input) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\\' && !inSingleQuote && !inDoubleQuote) {
                i++;
                if (i < input.length()) {
                    current.append(input.charAt(i));
                }
            } else if (c == '\\' && inDoubleQuote) {
                if (i + 1 < input.length() &&
                        (input.charAt(i + 1) == '"' || input.charAt(i + 1) == '\\')) {
                    i++;
                    current.append(input.charAt(i));
                } else {
                    current.append(c);
                }
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == ' ' && !inSingleQuote && !inDoubleQuote) {
                if (current.length() > 0) {
                    args.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            args.add(current.toString());
        }
        return args;
    }

    // ---------- Helpers shared between standalone builtins and pipeline builtins ----------

    static final List<String> BUILTINS = List.of("echo", "exit", "type", "pwd", "cd", "jobs");

    static boolean isBuiltin(String command) {
        return BUILTINS.contains(command);
    }

    // Formats the argument text of an "echo" command the same way the shell's
    // quote/escape-aware tokenizer does (collapsing unquoted whitespace).
    static String formatEcho(String rest) {
        List<String> tokens = parseArgs(rest);
        return String.join(" ", tokens);
    }

    // Resolves "type <command>" to its descriptive line (no trailing newline).
    static String typeLookup(String command) {
        if (isBuiltin(command)) {
            return command + " is a shell builtin";
        } else {
            String pathEnv = System.getenv("PATH");
            String[] dirs = pathEnv.split(":");
            for (String dir : dirs) {
                File f = new File(dir, command);
                if (f.exists() && f.canExecute()) {
                    return command + " is " + f.getAbsolutePath();
                }
            }
            return command + ": not found";
        }
    }

    // Builds the "jobs" listing text (and reaps finished jobs as a side effect),
    // mirroring the standalone "jobs" builtin's behavior.
    static String jobsListing(List<Job> jobsList) {
        StringBuilder sb = new StringBuilder();
        List<Job> finishedJobs = new ArrayList<>();
        int total = jobsList.size();

        for (int i = 0; i < total; i++) {
            Job job = jobsList.get(i);
            ProcessHandle ph = ProcessHandle.of(job.pid).orElse(null);
            String marker = (i == total - 1) ? "+" : (i == total - 2) ? "-" : " ";

            if (ph == null || !ph.isAlive()) {
                sb.append(String.format("[%d]%s  %-24s%s%n",
                        job.jobNumber, marker, "Done", job.command.replace(" &", "")));
                finishedJobs.add(job);
            } else {
                sb.append(String.format("[%d]%s  %-24s%s%n",
                        job.jobNumber, marker, "Running", job.command));
            }
        }
        jobsList.removeAll(finishedJobs);
        return sb.toString();
    }

    // Performs "cd <path>", printing an error directly if it fails (matches
    // the standalone "cd" builtin's behavior).
    static void doCd(String path) throws IOException {
        if (path.equals("~")) {
            path = System.getenv("HOME");
        }
        File dir;
        if (path.startsWith("/")) {
            dir = new File(path);
        } else {
            dir = new File(System.getProperty("user.dir"), path);
        }
        if (dir.exists() && dir.isDirectory()) {
            System.setProperty("user.dir", dir.getCanonicalPath());
        } else {
            System.out.println("cd: " + path + ": No such file or directory");
        }
    }

    // Runs an external command as one stage of a pipeline. Feeds inputBytes
    // to its stdin and captures its stdout fully into a byte array so it can
    // be forwarded to the next stage (which may itself be a builtin).
    static byte[] runExternalStage(String[] cmdArr, byte[] inputBytes, boolean isLastStage,
                                    String errorFile, boolean errorAppendMode) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmdArr);
        pb.redirectInput(ProcessBuilder.Redirect.PIPE);
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE);

        if (isLastStage && errorFile != null) {
            if (errorAppendMode) {
                pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(errorFile)));
            } else {
                pb.redirectError(new File(errorFile));
            }
        } else {
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        }

        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            System.out.println(cmdArr[0] + ": command not found");
            return new byte[0];
        }

        final byte[] dataToWrite = (inputBytes != null) ? inputBytes : new byte[0];
        Thread writer = new Thread(() -> {
            try (OutputStream os = p.getOutputStream()) {
                if (dataToWrite.length > 0) {
                    os.write(dataToWrite);
                }
            } catch (IOException e) {
                // Downstream process may have closed its stdin early (e.g. "head"); ignore.
            }
        });
        writer.start();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = p.getInputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
        }

        writer.join();
        p.waitFor();
        return baos.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        int jobcounter = 0;
        List<Job> jobsList = new ArrayList<>();

        while (true) {
            reapJobs(jobsList);
            System.out.print("$ ");
            System.out.flush();

            String input = scanner.nextLine().trim();
            boolean background = false;
            if (input.endsWith("&")) {
                background = true;
                input = input.substring(0, input.length() - 1).trim();
            }

            String outputFile = null;
            boolean appendMode = false;

            if (input.contains(" >> ") || input.contains(" 1>> ")) {
                String[] redirParts = input.split(" >> | 1>> ");
                input = redirParts[0].trim();
                outputFile = redirParts[1].trim();
                appendMode = true;
            } else if (input.contains(" > ") || input.contains(" 1> ")) {
                String[] redirParts = input.split(" > | 1> ");
                input = redirParts[0].trim();
                outputFile = redirParts[1].trim();
            }

            String errorFile = null;
            boolean errorAppendMode = false;

            if (input.contains(" 2>> ")) {
                String[] redirParts = input.split(" 2>> ");
                input = redirParts[0].trim();
                errorFile = redirParts[1].trim();
                errorAppendMode = true;
            } else if (input.contains(" 2> ")) {
                String[] redirParts = input.split(" 2> ");
                input = redirParts[0].trim();
                errorFile = redirParts[1].trim();
            }

            if (input.equals("exit 0") || input.equals("exit")) {
                System.exit(0);
            } else if (input.contains(" | ")) {
                String[] commandStrings = input.split("\\|");
                byte[] currentData = new byte[0];
                int n = commandStrings.length;

                for (int i = 0; i < n; i++) {
                    String seg = commandStrings[i].trim();
                    boolean isLastStage = (i == n - 1);

                    List<String> segParts = parseArgs(seg);
                    if (segParts.isEmpty()) {
                        continue;
                    }
                    String cmdName = segParts.get(0);

                    if (cmdName.equals("echo")) {
                        String rest = seg.length() > 5 ? seg.substring(5) : "";
                        String formatted = formatEcho(rest);
                        currentData = (formatted + "\n").getBytes();
                    } else if (cmdName.equals("type")) {
                        String typeArg = segParts.size() > 1 ? segParts.get(1) : "";
                        String result = typeLookup(typeArg);
                        currentData = (result + "\n").getBytes();
                    } else if (cmdName.equals("pwd")) {
                        currentData = (System.getProperty("user.dir") + "\n").getBytes();
                    } else if (cmdName.equals("cd")) {
                        String path = segParts.size() > 1 ? segParts.get(1) : "";
                        doCd(path);
                        currentData = new byte[0];
                    } else if (cmdName.equals("jobs")) {
                        currentData = jobsListing(jobsList).getBytes();
                    } else {
                        String[] cmdArr = segParts.toArray(new String[0]);
                        currentData = runExternalStage(cmdArr, currentData, isLastStage, errorFile, errorAppendMode);
                    }
                }

                if (outputFile != null) {
                    try (FileOutputStream fos = new FileOutputStream(outputFile, appendMode)) {
                        fos.write(currentData);
                    }
                } else {
                    System.out.write(currentData);
                    System.out.flush();
                }
            } else if (input.startsWith("echo ")) {
                String rest = input.substring(5);
                StringBuilder result = new StringBuilder();
                boolean inSingleQuote = false;
                boolean inDoubleQuote = false;

                for (int i = 0; i < rest.length(); i++) {
                    char c = rest.charAt(i);

                    if (c == '\\' && !inSingleQuote && !inDoubleQuote) {
                        i++;
                        if (i < rest.length()) {
                            result.append(rest.charAt(i));
                        }
                    } else if (c == '\\' && inDoubleQuote) {
                        if (i + 1 < rest.length() &&
                                (rest.charAt(i + 1) == '"' || rest.charAt(i + 1) == '\\')) {
                            i++;
                            result.append(rest.charAt(i));
                        } else {
                            result.append(c);
                        }
                    } else if (c == '\'' && !inDoubleQuote) {
                        inSingleQuote = !inSingleQuote;
                    } else if (c == '"' && !inSingleQuote) {
                        inDoubleQuote = !inDoubleQuote;
                    } else if (c == ' ' && !inSingleQuote && !inDoubleQuote) {
                        if (result.length() > 0 && result.charAt(result.length() - 1) != ' ') {
                            result.append(' ');
                        }
                    } else {
                        result.append(c);
                    }
                }

                if (outputFile != null) {
                    PrintWriter writer = new PrintWriter(new FileWriter(outputFile, appendMode));
                    writer.println(result.toString().trim());
                    writer.close();
                } else {
                    System.out.println(result.toString().trim());
                }
                if (errorFile != null) {
                    new File(errorFile).createNewFile();
                }
            } else if (input.startsWith("type ")) {
                String command = input.substring(5).trim();
                if (isBuiltin(command)) {
                    System.out.println(command + " is a shell builtin");
                } else {
                    String pathEnv = System.getenv("PATH");
                    String[] dirs = pathEnv.split(":");
                    boolean found = false;
                    for (String dir : dirs) {
                        File f = new File(dir, command);
                        if (f.exists() && f.canExecute()) {
                            System.out.println(command + " is " + f.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        System.out.println(command + ": not found");
                    }
                }
            } else if (input.equals("jobs")) {
                List<Job> finishedJobs = new ArrayList<>();
                int total = jobsList.size();

                for (int i = 0; i < total; i++) {
                    Job job = jobsList.get(i);
                    ProcessHandle ph = ProcessHandle.of(job.pid).orElse(null);
                    String marker = (i == total - 1) ? "+" : (i == total - 2) ? "-" : " ";

                    if (ph == null || !ph.isAlive()) {
                        System.out.printf("[%d]%s  %-24s%s%n",
                                job.jobNumber, marker, "Done", job.command.replace(" &", ""));
                        finishedJobs.add(job);
                    } else {
                        System.out.printf("[%d]%s  %-24s%s%n",
                                job.jobNumber, marker, "Running", job.command);
                    }
                }
                jobsList.removeAll(finishedJobs);
            } else if (input.equals("pwd")) {
                System.out.println(System.getProperty("user.dir"));
            } else if (input.startsWith("cd ")) {
                String path = input.substring(3).trim();
                if (path.equals("~")) {
                    path = System.getenv("HOME");
                }
                File dir;
                if (path.startsWith("/")) {
                    dir = new File(path);
                } else {
                    dir = new File(System.getProperty("user.dir"), path);
                }
                if (dir.exists() && dir.isDirectory()) {
                    System.setProperty("user.dir", dir.getCanonicalPath());
                } else {
                    System.out.println("cd: " + path + ": No such file or directory");
                }
            } else {
                List<String> partsList = parseArgs(input);
                String[] parts = partsList.toArray(new String[0]);
                String command = parts[0];
                String pathEnv = System.getenv("PATH");
                String[] dirs = pathEnv.split(":");
                boolean found = false;
                for (String dir : dirs) {
                    File f = new File(dir, command);
                    if (f.exists() && f.canExecute()) {
                        ProcessBuilder pb = new ProcessBuilder(parts);

                        if (outputFile != null) {
                            if (appendMode) {
                                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(new File(outputFile)));
                            } else {
                                pb.redirectOutput(new File(outputFile));
                            }
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }

                        if (errorFile != null) {
                            if (errorAppendMode) {
                                pb.redirectError(ProcessBuilder.Redirect.appendTo(new File(errorFile)));
                            } else {
                                pb.redirectError(new File(errorFile));
                            }
                        } else {
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }

                        Process p = pb.start();
                        if (background) {
                            int newJobNumber = getNextJobNumber(jobsList);
                            Job job = new Job(newJobNumber, p.pid(), input + " &", "Running");
                            jobsList.add(job);
                            System.out.println("[" + newJobNumber + "] " + p.pid());
                        } else {
                            p.waitFor();
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    System.out.println(command + ": command not found");
                }
            }
        }
    }
}