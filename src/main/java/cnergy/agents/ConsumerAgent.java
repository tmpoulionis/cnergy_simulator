package cnergy.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.concurrent.atomic.AtomicLong;

public class ConsumerAgent extends Agent{
    // ------------------------ parameters ------------------------
    private double margin = 0.005; // initial margin
    private double alpha = 0.03; // learning rate
    private double utilityCap = 0.12; // max euros/kWh willing to pay
    private double[] hourlyLoad = {
        1,1,1,1,1,1, 2,3,3,2,2,2,
        2,2,2,2,3,5, 5,4,3,2,1,1
    };
    private boolean DebuggingMode = false; // Debug mode

    // --------------------- internal state ------------------------
    private int tick = 0;
    private double backlog = 0; // unmet demand carried forward

    private static final AtomicLong SEQ = new AtomicLong();
    private long openOrderId = -1;
    private double openQty = 0;

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
                switch (msg.getPerformative()) {
                    case ACLMessage.ACCEPT_PROPOSAL: {onFill(msg, false);}
                    case ACLMessage.REJECT_PROPOSAL: {onReject(msg);}
                }
            }
        });

        // ------------------------ Hourly consumption and bid ------------------------------
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                tick++;
                int hour = tick%24;
        
                // 1. cancel stale order 
                if (openOrderId != -1) {
                    ACLMessage cancel = new ACLMessage(ACLMessage.CANCEL);
                    cancel.addReceiver(new AID("broker", AID.ISLOCALNAME));
                    cancel.setOntology("ORDER");
                    cancel.setContent("id="+openOrderId);
                    send(cancel);
                    backlog += openQty;
                    openOrderId=-1; openQty=0;
                }

                // 2. calculate demand
                double demand = hourlyLoad[hour] + backlog;
                if (demand<1e-6) return;

                // 3. calculate price
                double price = Math.max(0.0, utilityCap + margin);
                openOrderId = SEQ.incrementAndGet(); // increase by 1 every time its called
                openQty = demand;

                // 4. send order
                ACLMessage order = new ACLMessage(ACLMessage.PROPOSE);
                order.addReceiver(new AID("broker", AID.ISLOCALNAME));
                order.setOntology("ORDER");
                order.setContent("id="+openOrderId+";side=buy;qty="+openQty+";price="+price);
                send(order);
                log("BID id=%d qty=%.1f kWh @ %.3f",openOrderId, openQty, price);
            }
        });
    }

    // ---------------------------- FUNCTIONS -------------------------------------
    private void onFill(ACLMessage msg, boolean seller) {
        String content = msg.getContent();
        String [] tokens = content.split(";");
        long id = Long.parseLong(tokens[0].split("=")[1]);
        if(id!=openOrderId) return;
        double qty = Double.parseDouble(tokens[2].split("=")[1]);
        double price = Double.parseDouble(tokens[3].split("=")[1]);

        openQty -= qty;
        backlog = Math.max(0, backlog - qty);
        if(openQty < 1e-6) openOrderId = -1;

        double util = qty / (qty + openQty + 1e-9);
        margin += alpha*(0.9 - util); // satisfied -> decrease margin (pay less)
        margin = Math.max(0.005, Math.min(0.05, margin));
        log("FILL %.1f kWh @ %.3f | backlog %.1f | new margin %.3f", qty, price, backlog, margin);
    }

    private void onReject(ACLMessage msg) {
        String content = msg.getContent();
        String[] tokens = content.split(";");
        long id = Long.parseLong(tokens[0].split("=")[1]);
        if(id != openOrderId) return;

        backlog += openQty;
        openOrderId=-1; openQty=0;
        margin = Math.max(0.005, margin + 0.002);   // pay a bit more next time
        log("REJECT id=%d | backlog %.1f | margin %.3f", id, backlog, margin);
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

        /* print format */
    private void log(String fmt, Object... args) {
        if (DebuggingMode)                                // only print when debug=true
            System.out.printf("%s » " + fmt + "%n", getLocalName(), args);
    }
}



