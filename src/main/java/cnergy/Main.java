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

            AgentController weather = mainContainer.createNewAgent("weather", "cnergy.agents.WeatherAgent", null); // Weather agent
            weather.start();

            AgentController solar = mainContainer.createNewAgent("solar", "cnergy.agents.SolarAgent", null); // Solar agent
            solar.start();

            AgentController wind = mainContainer.createNewAgent("wind", "cnergy.agents.WindAgent", null); // Wind agent
            wind.start();
        } catch (Exception e) {
        e.printStackTrace();
        }
    }
}