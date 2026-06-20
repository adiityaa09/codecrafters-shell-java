import java.io.File;
import java.util.List;
import java.util.Scanner;
import java.util.Arrays;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.FileWriter;

public class Main {

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

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = scanner.nextLine().trim();
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
            if (input.contains(" 2> ")) {
                String[] redirParts = input.split(" 2> ");
                input = redirParts[0].trim();
                errorFile = redirParts[1].trim();
            }

            if (input.equals("exit 0") || input.equals("exit")) {
                System.exit(0);
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
                    PrintWriter writer = new PrintWriter(new FileWriter(outputFile,appendMode));
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
                List<String> builtins = List.of("echo", "exit", "type", "pwd", "cd");
                if (builtins.contains(command)) {
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
                            pb.redirectOutput(new File(outputFile));
                        } else {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        }

                        if (errorFile != null) {
                            pb.redirectError(new File(errorFile));
                        } else {
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }
                        Process p = pb.start();
                        p.waitFor();
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