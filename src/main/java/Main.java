import java.io.File;
import java.util.List;
import java.util.Scanner;
import java.util.Arrays;
public class Main {
    public static void main(String[] args) throws Exception {
        // TODO: Uncomment the code below to pass the first stage
       Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.print("$ ");
            System.out.flush();
            
            String input = scanner.nextLine().trim();
            if(input.equals("exit 0") || input.equals("exit")){
    System.exit(0);
}

else if(input.startsWith("echo ")){
    System.out.println(input.substring(5).trim());
}
else if (input.startsWith("type ")) {
    String command = input.substring(5).trim();
    List<String> builtins = List.of("echo", "exit", "type","pwd","cd");
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
}
else if(input.equals("pwd")){
    System.out.println(System.getProperty("user.dir"));
}

else if (input.startsWith("cd ")) {
    String path = input.substring(3).trim();
    File dir;
    if (path.equals("~")) {
    path = System.getenv("HOME");
}
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
else {
    String[] parts = input.split(" ");
    String command = parts[0];
    String pathEnv = System.getenv("PATH");
    String[] dirs = pathEnv.split(":");
    boolean found = false;
    for (String dir : dirs) {
        File f = new File(dir, command);
        if (f.exists() && f.canExecute()) {
            ProcessBuilder pb = new ProcessBuilder(parts);
            pb.inheritIO();
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