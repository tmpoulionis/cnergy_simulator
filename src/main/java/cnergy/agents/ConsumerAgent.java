package cnergy.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class ConsumerAgent extends Agent{
    // ------------------------ Parameters ------------------------
    private double margin = 0.005; // initial margin
    private double alpha = 0.03; // learning rate
    private double utilityCap = 0.12; // max euros/kWh willing to pay
    private double[] hourlyLoad = {
        1,1,1,1,1,1, 2,3,3,2,2,2,
        2,2,2,2,3,5, 5,4,3,2,1,1
    };

    private boolean DebuggingMode = false; // Debug mode

    private int tick = 0;
    @Override
    protected void setup() {
        register("consumer");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            margin = Double.parseDouble(args[0].toString());
            alpha = Double.parseDouble(args[1].toString());
            utilityCap = Double.parseDouble(args[2].toString());
            hourlyLoad = (double[]) args[3];
            DebuggingMode = Boolean.parseBoolean(args[4].toString());
        }
        System.out.printf("- [%s] (consumer) up! {margin: %.2f | alpha: %.2f | utilityCap: %.2f | hourlyLoad: %s}%n", getLocalName(), margin, alpha, utilityCap, java.util.Arrays.toString(hourlyLoad));

        // --------------------- Receive messages and update internal state -----------------------
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                updateInternalState(msg);
            }
        });

        // ------------------------ Hourly consumption and bid ------------------------------
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                tick++;
                int hour = tick % 24;
                double need = hourlyLoad[hour];
                double bidPrice = utilityCap - margin; // max price willing to pay

                ACLMessage bid = new ACLMessage(ACLMessage.INFORM);
                bid.setOntology("BID");
                bid.setContent("qty=" + need + ";price=" + bidPrice);
                bid.addReceiver(new AID("operator", AID.ISLOCALNAME));
                send(bid);
                /*DEBUG*/ if (DebuggingMode == true) System.out.printf("%s - Sending bid: %.2f kWh @ %.2f euro/kWh %n", getLocalName(), need, bidPrice);
            }
        });
    }

    // ---------------------------- FUNCTIONS -------------------------------------
    private void updateInternalState(ACLMessage msg) {
        if (msg.getOntology() != "AWARD") {return;}
        String content = "";
        String [] tokens = null;

        content = msg.getContent();
        tokens = content.split(";");
        double won = Double.parseDouble(tokens[0].split("=")[1]);
        double price = Double.parseDouble(tokens[1].split("=")[1]);
        double needed = hourlyLoad[(tick-1) % 24];
        double util = needed < 1e-6 ? 0.0 : won/needed; // 0...1
        margin += alpha * (util - 0.9); // if oversupplied, lower margin
        margin = Math.max(0.005, Math.min(0.05, margin)); // Clamp margin to [0.005, 0.05]
        /*DEBUG*/ if (DebuggingMode == true) System.out.printf("%s - Award received: %.2f kWh @ %.2f euro/kWh | Needed: %.2f| Margin updated: %.2f%n", getLocalName(), won, price, needed, margin);
    }

    private void register(String type) {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd  = new ServiceDescription();
            sd.setType(type);                // e.g. "solar-producer"
            sd.setName("energy-market");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (Exception e) { e.printStackTrace(); }
    }

}



