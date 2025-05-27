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
            // ------ single instances --------
            AgentController broker = cc.createNewAgent("broker", "cnergy.agents.BrokerAgent",
                    new Object[]{3, true});
            AgentController weather = cc.createNewAgent("weather", "cnergy.agents.WeatherAgent",
                    new Object[]{3, 0.50, 0.50, true});
            AgentController faultInjector = cc.createNewAgent("fault-injector", "cnergy.agents.FaultAgent",
                    new Object[]{10, new String[]{"solar-producer", "wind-producer", "conventional-producer"}, 6, true});
            AgentController conventional = cc.createNewAgent("conventional", "cnergy.agents.ConventionalAgent",
                    new Object[]{0.05, true});
            AgentController gui = cc.createNewAgent("gui", "cnergy.agents.GUIAgent", null);
            AgentController sniffer = cc.createNewAgent("sniffer", "jade.tools.sniffer.Sniffer", new Object[]{});
            sniffer.start();
            broker.start();
            weather.start();
            faultInjector.start();
            conventional.start();
            gui.start();

            // ------ multiple instances --------
            int N_SOLAR = 5;
            int N_WIND = 5;
            int N_CONSUMERS_1 = 10;
            int N_CONSUMERS_2 = 10;
            int multFactor = 1;
            
            for (int i = 1; i <= N_SOLAR; i++) {
                cc.createNewAgent("solar"+i, "cnergy.agents.SolarAgent",
                        new Object[]{25, true, 100.0, 1.0, 0.4, 0.035, 0.005, 0.005, true})
                  .start();
            }

            for (int i = 1; i <= N_WIND; i++) {
                cc.createNewAgent("wind"+i, "cnergy.agents.WindAgent",
                        new Object[]{25, true, 100.0, 1.0, 0.2, 0.035, 0.005, 0.005, true})
                  .start();
            }

            double [] homeLoad = new double[] {1,1,1,1,1,1, 2,3,3,2,2,2, 2,2,2,2,3,5, 5,4,3,2,1,1};
            multFactor = 1;
            for (int i = 1; i <= N_CONSUMERS_1; i++) {
                cc.createNewAgent("household"+i, "cnergy.agents.ConsumerAgent",
                        new Object[]{0.005, 0.003, 0.14, homeLoad, multFactor, true})
                  .start();
            }

            double[] evLoad = new double[]{0,0,0,0,0,0,  0,0,0,0,0,0, 0,0,0,0,8, 8,8,7,7,6,5,4};
            multFactor = 1;
            for (int i = 1; i <= N_CONSUMERS_2; i++) {
                cc.createNewAgent("EV"+i, "cnergy.agents.ConsumerAgent",
                        new Object[]{0.005, 0.003, 0.12, evLoad, multFactor, true})
                  .start();
            }

            System.out.println("==========================================================");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
