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
           else if (input.startsWith("type ")) {
    String command = input.substring(5).trim();
    
    List<String> builtins = List.of("echo", "exit", "type");
    
    if (builtins.contains(command)) {
        // print "command is a shell builtin"
    } else {
        String pathEnv = System.getenv("PATH");
        String[] dirs = pathEnv.split(":");
        boolean found = false;
        for (String dir : dirs) {
            File f = new File(dir, command);
            if (f.exists()) {
                // print "command is /full/path"
                found = true;
                break;
            }
        }
        if (!found) {
            // print "command: not found"
        }
    }
}
        }}}