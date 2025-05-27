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
    // ------------------------ parameters ------------------------
    private double margin = 0.005; // initial margin
    private double alpha = 0.003; // learning rate
    private double utilityCap = 0.12; // max euros/kWh willing to pay
    private double[] hourlyLoad = {
        1,1,1,1,1,1, 2,3,3,2,2,2,
        2,2,2,2,3,5, 5,4,3,2,1,1
    };
    private double multFactor = 1;
    private boolean DebuggingMode = false; // Debug mode

    // --------------------- internal state ------------------------
    private int tick = 0;
    private double backlog = 0; // unmet demand carried forward
    private double openQty = 0;
    private double demand = 0;
    private int hour = 0;

    @Override
    protected void setup() {
        register("consumer");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            margin = Double.parseDouble(args[0].toString());
            alpha = Double.parseDouble(args[1].toString());
            utilityCap = Double.parseDouble(args[2].toString());
            hourlyLoad = (double[]) args[3];
            multFactor = Double.parseDouble(args[4].toString());
            DebuggingMode = Boolean.parseBoolean(args[5].toString());
        }
        System.out.printf("- [%s] (consumer) up! {margin: %.2f | alpha: %.2f | utilityCap: %.2f | hourlyLoad: %s | multFactor: %.1f }%n", getLocalName(), margin, alpha, utilityCap, java.util.Arrays.toString(hourlyLoad), multFactor);

        // --------------------- Receive messages and update internal state -----------------------
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                switch (msg.getPerformative()) {
                    case ACLMessage.ACCEPT_PROPOSAL: {onFill(msg, false);} break;
                    case ACLMessage.REJECT_PROPOSAL: {onReject(msg);} break;
                }
            }
        });

        // ------------------------ Hourly consumption and bid ------------------------------
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                tick++;
                hour = tick%24;

                // calculate demand
                demand = hourlyLoad[hour]*multFactor + backlog;
                if (demand<1e-6) return;

                // calculate price
                double price = utilityCap - margin;
                price = Math.min(utilityCap, Math.max(0, price));

                // send order
                openQty = demand;
                ACLMessage order = new ACLMessage(ACLMessage.PROPOSE);
                order.addReceiver(new AID("broker", AID.ISLOCALNAME));
                order.setOntology("ORDER");
                order.setContent("qty="+openQty+";price="+price+";side=buy");
                send(order);

                // notify gui agent
                ACLMessage gui = new ACLMessage(ACLMessage.INFORM);
                gui.addReceiver(new AID("gui", AID.ISLOCALNAME));
                gui.setOntology("CONSUMER_STATUS");
                gui.setContent(String.format("name="+getLocalName()+";demand="+demand+";backlog="+backlog));
                send(gui);

                if(DebuggingMode) System.out.printf("%s >> BUY ORDER qty=%.1f kWh @ %.3f%n", getLocalName(), openQty, price);
            }
        });
    }

    // ---------------------------- FUNCTIONS -------------------------------------
    private void onFill(ACLMessage msg, boolean seller) {
        String content = msg.getContent();
        String [] tokens = content.split(";");

        long id = Long.parseLong(tokens[0].split("=")[1]);
        double qty = Double.parseDouble(tokens[1].split("=")[1]);
        double price = Double.parseDouble(tokens[2].split("=")[1]);
        String from = tokens[3].split("=")[1];

        openQty -= qty;
        backlog = Math.max(0, backlog - qty);
        if(openQty < 1e-6) {openQty = 0;}

        margin += alpha; // increase margin -> decrease price
        margin = Math.max(0.005, margin);
        if(DebuggingMode) System.out.printf("%s >> FILL order id=%d %.1f kWh @ %.3f from %s | backlog %.1f | new margin %.3f%n", getLocalName(), id, qty, price, from, backlog, margin);
    }

    private void onReject(ACLMessage msg) {
        String content = msg.getContent();
        String[] tokens = content.split(";");
        long id = Long.parseLong(tokens[0].split("=")[1]);

        backlog = openQty;
        openQty=0;

        margin -= alpha; // reduce margin -> increase price 
        margin = Math.min(0.001, margin);
        if(DebuggingMode) System.out.printf("%s >> REJECTED id=%d | backlog %.1f | margin %.3f%n", getLocalName(), id, backlog, margin);
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
