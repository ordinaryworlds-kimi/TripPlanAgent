package managerAgent;

import managerAgent.agents.ManagerAgent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ManagerAgentApplication {

    public static void main(String[] args) {
        if (isConsoleMode(args)) {
            new ManagerAgent().run();
            return;
        }
        SpringApplication.run(ManagerAgentApplication.class, args);
    }

    private static boolean isConsoleMode(String[] args) {
        for (String arg : args) {
            if ("--console".equals(arg) || "console".equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
