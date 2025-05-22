package cnergy;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class Main {

    public static void main(String[] args) {

        try {
            /* ============ JADE bootstrap ============ */
            Runtime rt = Runtime.instance();
            Profile p  = new ProfileImpl();
            p.setParameter(Profile.GUI, "true");
            p.setParameter(Profile.PLATFORM_ID,"CNERGY");
            ContainerController cc = rt.createMainContainer(p);

            System.out.println("==========================================================");
            System.out.println("      C N E R G Y   -   S I M U L A T O R");
            System.out.println("==========================================================");
            System.out.println("Starting...\n");

            /* ============ Agents ============ */

            AgentController broker =
                cc.createNewAgent("broker", "cnergy.agents.BrokerAgent",
                new Object[]{true}); // DebuggingMode
            AgentController weather =
                cc.createNewAgent("weather", "cnergy.agents.WeatherAgent",
                    new Object[]{ 3, 0.50, 0.50, true });  // period, solarProb, windProb, DebuggingMode
            AgentController faultInjector =
                cc.createNewAgent("fault-injector", "cnergy.agents.FaultAgent",
                    new Object[]{ 10, new String[]{"solar-producer", "wind-producer", "conventional-producer"}, 5, true}); // periodFault, targets, faultDuration, DebuggingMode
            AgentController solar =
                cc.createNewAgent("solar", "cnergy.agents.SolarAgent",
                    new Object[]{ 50.0, true, 100.0, 1.0, 0.4, 0.035, 0.005, 0.03, true }); // capacity, hasBatt, battCapacity, coeffSunny, coeffCloudy, baseCost, margin, alpha, DebuggingMode
            AgentController wind =
                cc.createNewAgent("wind", "cnergy.agents.WindAgent",
                    new Object[]{50.0, true, 100, 1.0, 0.2, 0.035, 0.005, 0.03, true}); // capacity, hasBatt, battCapacity, coeffWindy, coeffCalm, baseCost, margin, alpha, DebuggingMode
            AgentController conventional =
                cc.createNewAgent("conventional", "cnergy.agents.ConventionalAgent",
                    new Object[]{0.05, true});   // margin, DebuggingMode
            AgentController consumer =
                cc.createNewAgent("consumer", "cnergy.agents.ConsumerAgent",
                    new Object[]{ 0.005, 0.03, 0.12, new double[]{1,1,1,1,1,1, 2,3,3,2,2,2, 2,2,2,2,3,5, 5,4,3,2,1,1}, true}); // margin, alpha, utilityCap, hourlyLoad, DebuggingMode
            AgentController trader =
                cc.createNewAgent("trader", "cnergy.agents.TraderAgent",
                    new Object[]{ 0.010, 10.0, 50.0, true }); // margin, posLimit, orderSize, DebuggingMode

            /* ============ START ============ */
            broker.start();
            weather.start();
            faultInjector.start();
            solar.start();
            wind.start();
            conventional.start();
            consumer.start();
            trader.start();

            System.out.println("==========================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
