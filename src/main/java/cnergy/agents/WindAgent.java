package cnergy.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

public class WindAgent extends Agent {
    private final double installedCapacityMW = 50.0; // Total MW capacity
    private double production = 0.0; 
    private String windToken = "";
    private String timeToken = "";

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " has started.");

        // Get weather updates from WeatherAgent
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null && msg.getOntology().equals("WEATHER")) {
                    String content = msg.getContent();
                    String[] tokens = content.split(";");
                    windToken = tokens[0].split("=")[1];
                    timeToken = tokens[1].split("=")[1];
                } else {
                    block(); // Wait for new messages
                }
            }
        });

        // Production calculation based on weather tokens
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                    if (windToken.equals("WINDY")) {
                        production = installedCapacityMW * 1.0; // Full output
                    } else if (windToken.equals("CALM")) {
                        production = installedCapacityMW * 0.2; // reduced output
                    } else {
                        production = 0.0;
                    }
                    

                    System.out.printf("%s - Generating.. %.2f MWh%n", getLocalName(), production);
            }
        });
    }
}