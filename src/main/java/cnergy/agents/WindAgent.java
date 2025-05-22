package cnergy.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.core.AID;

import java.util.concurrent.atomic.AtomicLong;

public class WindAgent extends Agent {
    // ------------------------ Parameters ------------------------
    private double capacity = 50.0; // Total kW capacity
    private boolean hasBattery = true;
    private double battCapacity = 100;
    private double coeffWindy = 1.0;
    private double coeffCalm = 0.2;


    private double baseCost = 0.035; // euro/kWh
    private double margin = 0.005; // Initial margin
    private double alpha = 0.03; // learning rate

    private boolean DebuggingMode = true; // Debug mode
    
    // ------------------------- Internal state ------------------------
    private double production = 0.0; 
    private double lastClearingPrice = 0.0;
    private String windToken = "";
    private String timeToken = "";
    private double faultDuration = 0.0;
    private double soc = 0.0;
    private boolean isFaulty = false;

    // ------------------ order tracking --------------------------
    private static final AtomicLong SEQ = new AtomicLong();
    private long openOrderId = -1; // -1 means no order is live
    private double openQty = 0; 
    @Override
    protected void setup() {
        register("wind-producer");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            capacity = Double.parseDouble(args[0].toString());
            hasBattery = Boolean.parseBoolean(args[1].toString());
            battCapacity = Double.parseDouble(args[2].toString());
            coeffWindy = Double.parseDouble(args[3].toString());
            coeffCalm = Double.parseDouble(args[4].toString());
            baseCost = Double.parseDouble(args[5].toString());
            margin = Double.parseDouble(args[6].toString());
            alpha = Double.parseDouble(args[7].toString());
            DebuggingMode = Boolean.parseBoolean(args[8].toString());
        }
        System.out.printf("- [%s]  UP - {capacity: %.2f | hasBattery: %b | battCapacity: %.2f | baseCost: %.2f | margin: %.2f | alpha: %.2f | coeffSunny: %.2f | coeffCloudy: %.2f}%n", getLocalName(), capacity, hasBattery, battCapacity, baseCost, margin, alpha, coeffWindy, coeffCalm);

        // --------------------- message handling -----------------------
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
                switch (msg.getPerformative()) {
                    case ACLMessage.INFORM: onInform(msg); break;
                    case ACLMessage.ACCEPT_PROPOSAL: onFill(msg, true); break;
                    case ACLMessage.REJECT_PROPOSAL: onReject(msg); break;
                }
            }
        });
        // ------------------------ hourly cicle ------------------------------
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                // fault Handling
                if (isFaulty) {
                    if (faultDuration > 0) {
                        faultDuration -= 1;
                        log("Faulty... %.2f seconds remaining", faultDuration);
                        return;
                    } else {
                        isFaulty = false;
                        log("Recovered!");
                    }
                }

                // 1. cancel stale order so leftover energy returns to battery
                if (openOrderId!=-1) {
                    ACLMessage cancel = new ACLMessage(ACLMessage.CANCEL);
                    cancel.addReceiver(new AID("broker", AID.ISLOCALNAME));
                    cancel.setOntology("ORDER");
                    cancel.setContent("id="+openOrderId);
                    send(cancel);
                    soc = Math.min(battCapacity, soc + openQty);
                    openOrderId = -1; openQty = 0;
                }

                // 2. produce energy
                double factor = "SUNNY".equals(windToken) ? coeffWindy : coeffCalm;
                production = capacity * factor;
                production = Math.min(production, battCapacity - soc);
                log("Generating.. %.2f kWh %n", production);

                // 3. calculate price
                double available = production + soc;
                if (available < 1e-6) {return;} // nothing to sell
                double price = baseCost + margin;
                price = Math.max(price, lastClearingPrice - 0.02); // avoid undercutting the market
                
                // 4. send order
                openQty = available;
                openOrderId = SEQ.incrementAndGet();

                ACLMessage offer = new ACLMessage(ACLMessage.PROPOSE);
                offer.addReceiver(new AID("broker", AID.ISLOCALNAME));
                offer.setOntology("ORDER");
                offer.setContent("id="+openOrderId+"side=sell;qty="+available+";price="+price);
                send(offer);
                log("OFFER id=%d qty=%.2f kWh @ %.3f", openOrderId, available, price);
            }
        });
    }

    // ---------------------------- FUNCTIONS -------------------------------------
    private void onInform(ACLMessage msg) {
        String content = msg.getContent();
        String[] tokens;

        switch (msg.getOntology()) {
            case "WEATHER":
                tokens = content.split(";");
                windToken = tokens[1].split("=")[1];
                timeToken = tokens[2].split("=")[1];
                log("Weather update: %s | %s", windToken, timeToken);
                break;
            case "PRICE_TICK":
                lastClearingPrice = Double.parseDouble(content.split("=")[1]);
                log("Price tick: %.2f", lastClearingPrice);
                break;
            case "FAULT":
                tokens = content.split(";");
                faultDuration = Double.parseDouble(tokens[0].split("=")[1]);
                isFaulty = true;
                log("Fault occurred | duration: %.2f", faultDuration);
                break;
        }
    }

    private void onFill(ACLMessage msg, boolean seller) {
        String content = msg.getContent();
        String[] tokens = content.split(";");
        long id = Long.parseLong(tokens[0].split("=")[1]);
        double qty = Double.parseDouble(tokens[1].split("=")[1]);
        double price = Double.parseDouble(tokens[2].split("=")[1]);
        String from = tokens[3].split("=")[1];

        if (id != openOrderId) return; // Ignore old fills
        openQty -= qty;
        if (openQty < 1e-6) openOrderId = -1;

        double util = qty / (qty + openQty + 1e-8);
        margin += alpha * (0.9 - util);
        margin = Math.max(-0.02, Math.min(0.02, margin));
        log("FILLED %.1f kWh @ %.3f from %s | new margin %.3f", qty, price, from, margin);
    }

    private void onReject(ACLMessage msg) {
        String content = msg.getContent();
        String[] tokens = content.split(";");
        long id = Long.parseLong(tokens[0].split("=")[1]);
        soc = Math.min(battCapacity, soc + openQty);
        openOrderId=-1; openQty=0;
        log("REJECTED id=%d → energy returned to battery (SoC=%.0f %d%)" ,id,soc, soc/battCapacity*100);
    }

    /** Register this agent under a market service-type */
    private void register(String type) {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType(type);
            sd.setName("energy-market");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void log(String fmt, Object... args) {
        if (DebuggingMode)
            System.out.printf("%s » " + fmt + "%n", getLocalName(), args);
    }
}