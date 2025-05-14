package cnergy.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.core.AID;

public class WindAgent extends Agent {
    // ------------------------ Parameters ------------------------
    private double capacity = 50.0; // Total MW capacity
    private double baseCost = 0.045; // euro/kWh
    private double margin = 0.005; // Initial margin
    private double alpha = 0.03; // learning rate
    private double coeffWindy = 1.0;
    private double coeffCalm = 0.2;
    private boolean DebuggingMode = true; // Debug mode

    // ------------------------- Internal state ------------------------
    private double lastClearingPrice = 0.0;
    private double lastOfferQty = 0.0;
    private double production = 0.0; 
    private String windToken = "";
    private String timeToken = "";
    private double faultDuration = 0.0;
    private boolean isFaulty = false;

    @Override
    protected void setup() {
        register("wind-producer");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            capacity = Double.parseDouble(args[0].toString());
            baseCost = Double.parseDouble(args[1].toString());
            margin = Double.parseDouble(args[2].toString());
            alpha = Double.parseDouble(args[3].toString());
            coeffWindy = Double.parseDouble(args[4].toString());
            coeffCalm = Double.parseDouble(args[5].toString());
            DebuggingMode = Boolean.parseBoolean(args[6].toString());
        }
        System.out.printf("- [%s] (wind) up! {baseCost: %.2f | margin: %.2f | alpha: %.2f | coeffWindy: %.2f | coeffCalm: %.2f}%n", getLocalName(), baseCost, margin, alpha, coeffWindy, coeffCalm);

        // --------------------- Receive messages and update internal state -----------------------
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }
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

                // Production calculation
                double factor = "WINDY".equals(windToken) ? coeffWindy : coeffCalm;
                production = capacity * factor;
                /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Generating.. %.2f kWh %n", getLocalName(), production);}

                // Send offer message
                double ask = baseCost + margin;
                ask = Math.max(ask, lastClearingPrice - 0.02); // Avoid undercutting the market
                
                ACLMessage offer = new ACLMessage(ACLMessage.INFORM);
                offer.setOntology("GEN_OFFER");
                offer.setContent("qty=" + production + ";price=" + ask);
                offer.addReceiver(new AID("operator", AID.ISLOCALNAME));
                send(offer);

                lastOfferQty = production;

                /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Offer sent: %.2f kWh @ %.2f euro/kWh %n", getLocalName(), production, ask);}
            }
        });
    }

    // ---------------------------- FUNCTIONS -------------------------------------
    private void updateInternalState(ACLMessage msg) {
        String content = "";
        String [] tokens = null;
        String [] pair = null;

        switch (msg.getOntology()) {
            case "WEATHER":
                // Update weather
                content = msg.getContent();
                tokens = content.split(";");
                windToken = tokens[1].split("=")[1];
                timeToken = tokens[2].split("=")[1];
                /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Weather update: %s | %s%n", getLocalName(), windToken, timeToken);}
                break;
            case "PRICE_TICK":
                // Update last clearing price                
                content = msg.getContent();
                lastClearingPrice = Double.parseDouble(content.split("=")[1]);
                /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Price tick: %.2f%n", getLocalName(), lastClearingPrice);}
                break;
            case "AWARD":
                // Pricing logic - margin calculation
                content = msg.getContent();
                content = msg.getContent();
                tokens = content.split(";");
                lastClearingPrice = Double.parseDouble(tokens[1].split("=")[1]);
                double won = Double.parseDouble(tokens[0].split("=")[1]);
                double util = lastOfferQty < 1e-6 ? 0.0 : won/lastOfferQty; // 0...1
                margin += alpha * (0.9 - util);
                margin = Math.max(-0.02, Math.min(0.02, margin)); // Clamp margin to [-0.2, 0.2]
                /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Award received: %.2f kWh @ %.2f euro/kWh | Margin updated: %.2f%n", getLocalName(), won, lastClearingPrice, margin);}
                break;
            case "FAULT":
                content = msg.getContent();
                tokens = content.split(";");
                faultDuration = Double.parseDouble(tokens[0].split("=")[1]);
        }
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