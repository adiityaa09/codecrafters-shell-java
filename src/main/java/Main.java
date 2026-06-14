import java.io.File;
import java.util.List;
import java.util.Scanner;

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
    List<String> builtins = List.of("echo", "exit", "type");
    if (builtins.contains(command)) {
        System.out.println(command + " is a shell builtin");
    } else {
        String pathEnv = System.getenv("PATH");
        String[] dirs = pathEnv.split(":");
        boolean found = false;
        for (String dir : dirs) {
            File f = new File(dir, command);
            if (f.exists()) {
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
else {
    System.out.println(input + ": command not found");
}
        }
    }
}