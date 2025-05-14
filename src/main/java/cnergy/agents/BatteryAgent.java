package cnergy.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.core.AID;

public class BatteryAgent extends Agent {
    // ------------------------ Parameters ------------------------
    private double capacity = 100.0;
    private double soc = 50; // Initial state of charge
    private double margin = 0.01;
    private double chargeRate = 10.0;
    private boolean DebuggingMode = true; // Debug mode
    // ------------------------- Internal state ------------------------
    private double lastClearingPrice = 0.0;
    private double qty = 0.0;
    private double faultDuration = 0.0;
    private boolean isFaulty = false;

    @Override protected void setup() {
        register("battery");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            capacity = Double.parseDouble(args[0].toString());
            soc = Double.parseDouble(args[1].toString());
            margin = Double.parseDouble(args[2].toString());
            chargeRate = Double.parseDouble(args[3].toString());
            DebuggingMode = Boolean.parseBoolean(args[4].toString());
        }
        System.out.printf("- [%s] (battery) up! {capacity: %.2f | SoC: %.2f | margin: %.2f | chargeRate: %.2f} %n", getLocalName(), capacity, soc, margin, chargeRate);

        // --------------------- Receive messages and update internal state -----------------------
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if(msg == null) {block(); return;}
                updateInternalState(msg);
                }
            });

        // ------------------------ Hourly production and offer ------------------------------
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() {
                if (isFaulty) {
                    if (faultDuration > 0) {
                        faultDuration -= 1;
                        /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Faulty... %.2f seconds remaining%n", getLocalName(), faultDuration);}
                        return;
                    } else {
                        isFaulty = false;
                        /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Recovered!", getLocalName());}
                    }
                }

                double socPercentage = soc / capacity;
                if(socPercentage < 0.3) {
                    sendBid(chargeRate, 0);
                } else if (socPercentage >= 0.3 && socPercentage < 0.5) {
                    sendBid(chargeRate, lastClearingPrice - margin);
                } else if (socPercentage >= 0.5 && socPercentage < 0.7) {
                    sendOffer(chargeRate, lastClearingPrice);
                } else if (socPercentage >= 0.7) {
                    sendOffer(chargeRate, lastClearingPrice + margin);
                }
                /*DEBUG*/ if (DebuggingMode == true && socPercentage < 0.5) System.out.printf("%s - Sending bid: %.2f kWh @ %.2f euro/kWh %n", getLocalName(), chargeRate, lastClearingPrice);
                /*DEBUG*/ if (DebuggingMode == true && socPercentage >= 0.5) System.out.printf("%s - Sending offer: %.2f kWh @ %.2f euro/kWh %n", getLocalName(), chargeRate, lastClearingPrice);
            }
        });
    }

    private void updateInternalState(ACLMessage msg) {
        String content ="";
        String[] tokens = null;

        switch (msg.getOntology()) {
            case "PRICE_TICK":
                content = msg.getContent();
                tokens = content.split((";"));
                lastClearingPrice = Double.parseDouble(tokens[0].split("=")[1]);
                /*DEBUG*/ if (DebuggingMode == true) System.out.printf("%s - Last clearing price: %.2f euro/kWh %n", getLocalName(), lastClearingPrice);
                break;
            case "AWARD":
                content = msg.getContent();
                tokens = content.split(";");
                qty = Double.parseDouble(tokens[0].split("=")[1]);
                boolean producer = Boolean.parseBoolean(tokens[2].split("=")[1]);
                lastClearingPrice = Double.parseDouble(tokens[1].split("=")[1]);
                soc = Math.max(0, Math.min(capacity, producer ? soc - qty : soc + qty));
                /*DEBUG*/ if (DebuggingMode == true) System.out.printf("%s (as %s)- Awarded %.2f kWh at %.2f euro/kWh. SoC: %.2f %n", getLocalName(), producer ? "producer" : "consumer", qty, lastClearingPrice, soc);
                break;
            case "FAULT":
                content = msg.getContent();
                tokens = content.split(";");
                faultDuration = Double.parseDouble(tokens[0].split("=")[1]);
        }
    }

    private void sendOffer(double qty, double price) {
        ACLMessage offer = new ACLMessage(ACLMessage.INFORM);
        offer.setOntology("GEN_OFFER");
        offer.setContent("qty=" + qty + ";price=" + price);
        offer.addReceiver(new AID("operator", AID.ISLOCALNAME));
        send(offer);
        /*DEBUG*/ if (DebuggingMode == true) System.out.printf("%s - Offer sent: %.2f kWh @ %.2f euro/kWh %n", getLocalName(), qty, price);
    }

    private void sendBid(double qty, double bid) {
        ACLMessage m = new ACLMessage(ACLMessage.INFORM);
        m.setOntology("BID");
        m.setContent("qty=" + qty + ";price=" + bid);
        m.addReceiver( new AID("operator", AID.ISLOCALNAME) );
        send(m);
        /*DEBUG*/ if (DebuggingMode == true) System.out.printf("%s - Bid sent: %.2f kWh @ %.2f euro/kWh %n", getLocalName(), qty, bid);
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
