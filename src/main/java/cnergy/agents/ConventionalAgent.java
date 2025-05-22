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

public class ConventionalAgent extends Agent {
    // ------------------------ Parameters ------------------------
    private double margin = 0.05;
    private boolean DebuggingMode = false;
    // ------------------------- Internal state ------------------------
    private double lastPrice = 0.06;
    private double faultDuration = 0.0;
    private boolean isFaulty = false;

    // order tracking
    private static final AtomicLong SEQ = new AtomicLong();
    private long openOrderId = -1;
    
    @Override
    protected void setup() {
        register("conventional-producer");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            margin = Double.parseDouble(args[0].toString());
            DebuggingMode = Boolean.parseBoolean(args[1].toString());
        }
        System.out.printf("- [%s] (conventional) up! %n", getLocalName());

        // --------------------- message handling -----------------------
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {block(); return;}
                switch(msg.getPerformative()) {
                    case ACLMessage.ACCEPT_PROPOSAL: onFill(msg);
                    case ACLMessage.INFORM: onInform(msg);
                }
            }
        });

        // ------------------------ hourly cicle ------------------------------
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                if (isFaulty) {
                    if (faultDuration > 0) {
                        faultDuration -= 1;
                        log("Faulty... %.2f seconds remaining%n", faultDuration);
                        return;
                    } else {
                        isFaulty = false;
                        log("Recovered!");
                    }
                }

                // 1. cancel stale order
                if (openOrderId!=-1) {
                    ACLMessage cancel = new ACLMessage(ACLMessage.CANCEL);
                    cancel.addReceiver(new AID("broker", AID.ISLOCALNAME));
                    cancel.setOntology("ORDER");
                    cancel.setContent("id="+openOrderId);
                    send(cancel);
                    openOrderId = -1;
                }

                // 2. calculate price
                double price = lastPrice + margin;
                
                // 3. send order
                openOrderId = SEQ.incrementAndGet();

                ACLMessage offer = new ACLMessage(ACLMessage.PROPOSE);
                offer.addReceiver(new AID("broker", AID.ISLOCALNAME));
                offer.setOntology("ORDER");
                offer.setContent("id="+openOrderId+";side=sell;qty="+Double.POSITIVE_INFINITY+";price="+price);
                send(offer);
                log("OFFER id=%d <inf> kWh @ %.3f", openOrderId, price);
            }
        });
    }

    // ------------- functions --------------------
    private void onFill(ACLMessage msg) {
        String content = msg.getContent();
        String [] tokens = content.split(";");
        long id = Long.parseLong(tokens[0].split("=")[1]);
        if(id != openOrderId) return; // ignore old fills
        double qty = Double.parseDouble(tokens[1].split("=")[1]);
        double price = Double.parseDouble(tokens[2].split("=")[1]);
        String from = tokens[3].split("=")[1];
        log("FILLED %.1f kWh @ %.3f (backup)", qty, price);
    }

    private void onInform(ACLMessage msg) {
        String content = "";
        String [] tokens = null;

        switch (msg.getOntology()) {
            case "PRICE_TICK":
                // Update last clearing price                
                content = msg.getContent();
                lastPrice = Double.parseDouble(content.split("=")[1]);
                log("Price tick: %.2f%n", lastPrice);
                break;
            case "FAULT":
                content = msg.getContent();
                tokens = content.split(";");
                faultDuration = Double.parseDouble(tokens[0].split("=")[1]);
                log("Fault occured | duration: %d", faultDuration);
                break;
        }
    }

    // ------------- utilities -----------------
    /** register this agent under a market service-type */
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
