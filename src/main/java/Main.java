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
            else if(input.startsWith("echo")){
                input.substring(5);
            }
            else{
            System.out.println(input + ": command not found");
            }
        }
    }
}