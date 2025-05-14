package cnergy;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class Main {
    public static void main(String[] args) {
        try {
            // Initialize the JADE runtime
            Runtime runtime = Runtime.instance();

            // Create the profile for the main container
            Profile p = new ProfileImpl();
            p.setParameter(Profile.GUI, "true");
            p.setParameter(Profile.PLATFORM_ID, "CNERGY");

            // Create the main container
            ContainerController mainContainer = runtime.createMainContainer(p);

            System.out.println("==========================================================");
            System.out.println("      C N E R G Y   -   S I M U L A T O R");
            System.out.println("==========================================================");
            System.out.println("Starting...%n");
            AgentController weather = mainContainer.createNewAgent("weather", "cnergy.agents.WeatherAgent", new Object[] {3, 0.5, 0.5, true}); 
            AgentController solar = mainContainer.createNewAgent("solar", "cnergy.agents.SolarAgent", new Object[] {50.0, 0.035, 0.005, 0.03, 1.0, 0.4, true}); 
            AgentController wind = mainContainer.createNewAgent("wind", "cnergy.agents.WindAgent", new Object[] {50.0, 0.045, 0.005, 0.03, 1.0, 0.2, true}); 
            AgentController operator = mainContainer.createNewAgent("operator", "cnergy.agents.OperatorAgent", new Object[] {true}); 
            AgentController consumer = mainContainer.createNewAgent("consumer", "cnergy.agents.ConsumerAgent", new Object[] {0.005, 0.03, 0.12, new double[]{1,1,1,1,1,1,2,3,3,2,2,2,2,2,2,2,3,5,5,4,3,2,1,1}, true});
            AgentController battery = mainContainer.createNewAgent("battery", "cnergy.agents.BatteryAgent", new Object[] {50.0, 25, 0.005, 10, true});
            AgentController faultInjector = mainContainer.createNewAgent("fault-injector", "cnergy.agents.FaultAgent", new Object[] {10 ,new String[]{"solar-producer", "wind-producer", "battery", "conventional-producer"}, 5, true});
            AgentController conventional = mainContainer.createNewAgent("conventional", "cnergy.agents.ConventionalAgent", new Object[] {0.02, 0.08, true});
            weather.start(); solar.start(); wind.start(); operator.start(); consumer.start(); battery.start(); faultInjector.start(); conventional.start();
            System.out.println("==========================================================");
        } catch (Exception e) {
        e.printStackTrace();
        }
    }
}