package cnergy.agents;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.core.AID;

import java.util.Random;

public class WeatherAgent extends Agent {
    private final Random rand = new Random();
    private final double solarProb = 0.5;
    private final double windProb = 0.5;

    private int tick = 0;
    @Override
    protected void setup() {
        System.out.println(getLocalName() + " has started.");

        addBehaviour(new TickerBehaviour(this, 3000) { // every 3 seconds
            @Override
            protected void onTick() {
                tick = tick + 3;
                int hour = tick % 24;

                String timeToken = (hour >= 7 && hour <= 21) ? "DAY" : "NIGHT";
                String solarToken = rand.nextDouble() < solarProb ? "SUNNY" : "CLOUDY";
                String windToken = rand.nextDouble() < windProb ? "WINDY" : "CALM";
                System.out.printf("Weather Update: Tick %d | Time: %d:00 '%s' | Sun: %s | Wind: %s%n", tick, hour, timeToken, solarToken, windToken);

                ACLMessage solarMsg = new ACLMessage(ACLMessage.INFORM);
                solarMsg.setContent("SUN=" + solarToken + ";TIME=" + timeToken);
                solarMsg.setOntology("WEATHER");
                solarMsg.addReceiver(new AID("solar", AID.ISLOCALNAME));
                send(solarMsg);

                ACLMessage windMsg = new ACLMessage(ACLMessage.INFORM);
                windMsg.setContent("WIND=" + windToken + ";TIME=" + timeToken);
                windMsg.setOntology("WEATHER");
                windMsg.addReceiver(new AID("wind", AID.ISLOCALNAME));
                send(windMsg);
            }
        });
    }
}