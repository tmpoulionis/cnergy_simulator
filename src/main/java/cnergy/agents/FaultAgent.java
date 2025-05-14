package cnergy.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.*;

public class FaultAgent extends Agent {
    // ------------------------ Parameters ------------------------
    private int periodFault = 20;
    private String[] targets = {"solar-producer", "wind-producer", "battery", "conventional-producer"};
    private int faultDuration = 5; // in ticks 
    private boolean DebuggingMode = false;

    // ------------------------- Internal state ------------------------
    private final Random rand = new Random();

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            periodFault = Integer.parseInt(args[0].toString());
            targets = (String []) args[1];
            faultDuration = Integer.parseInt(args[2].toString());
            DebuggingMode = Boolean.parseBoolean(args[3].toString());
        }
        System.out.printf("- [%s] (fault) up! {periodFault: %d | targets: %s | faultDuration: %d}%n", getLocalName(), periodFault, Arrays.toString(targets), faultDuration);

        addBehaviour(new TickerBehaviour(this, 1000*periodFault) {
            protected void onTick() {
                List<AID> producers = new ArrayList<>();
                for (String target : targets) {
                    List<AID> agents = search(target);
                    /*DEBUF*/ if(DebuggingMode == true) {System.out.printf("FaultAgent found %d solar producers%n", agents.size());}

                    if (agents.isEmpty()) { continue; }
                    producers.addAll(agents);
                }
                if (producers.isEmpty()) {return;}
                AID victim = producers.get(rand.nextInt(producers.size()));

                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.setOntology("FAULT");
                msg.setContent("outage="+faultDuration);
                msg.addReceiver(victim);
                send(msg);
                /*DEBUG*/ if (DebuggingMode == true) { System.out.printf("%s - Fault injected on %s for %d ticks%n", getLocalName(), victim.getLocalName(), faultDuration); }
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
