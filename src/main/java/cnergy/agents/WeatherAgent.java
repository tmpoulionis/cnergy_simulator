package cnergy.agents;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.core.AID;

import java.util.*;

public class WeatherAgent extends Agent {
    // ------------------------ Parameters ------------------------
    private int period = 3;
    private double solarProb = 0.5;
    private double windProb = 0.5;
    private boolean DebuggingMode = true; //Debug mode

    private final Random rand = new Random();
    private int tick = 0;
    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            period = Integer.parseInt(args[0].toString());
            solarProb = Double.parseDouble(args[1].toString());
            windProb = Double.parseDouble(args[2].toString());
            DebuggingMode = Boolean.parseBoolean(args[3].toString());
        }
        System.out.printf("- [%s] (weather) up! {period: %d | solarProb: %.2f | windProb: %.2f} %n", getLocalName(), period, solarProb, windProb);

        addBehaviour(new TickerBehaviour(this, 1000*period) { // every 3 seconds
            @Override
            protected void onTick() {
                tick = tick + 3;
                int hour = tick % 24;

                String timeToken = (hour >= 7 && hour <= 21) ? "DAY" : "NIGHT";
                String solarToken = rand.nextDouble() < solarProb ? "SUNNY" : "CLOUDY";
                String windToken = rand.nextDouble() < windProb ? "WINDY" : "CALM";
                /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("Weather Update: Tick %d | Time: %d:00 '%s' | Sun: %s | Wind: %s%n", tick, hour, timeToken, solarToken, windToken);}

                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.setOntology("WEATHER");
                msg.setContent("SUN="+solarToken+";WIND="+windToken+";TIME="+timeToken+";hour="+hour);
                search("solar-producer").forEach(msg::addReceiver);
                search("wind-producer").forEach(msg::addReceiver);
                msg.addReceiver(new AID("gui", AID.ISLOCALNAME));
                send(msg);
            }
        });
    }
    private List<AID> search(String type) {
        List<AID> res = new ArrayList<>();
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType(type);
            template.addServices(sd);
            DFAgentDescription[] r = DFService.search(this, template);
            for (DFAgentDescription d : r) res.add(d.getName());
        } catch (Exception e) { e.printStackTrace(); }
        return res;
    }

}