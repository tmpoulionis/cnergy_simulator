package cnergy.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.core.AID;

public class SolarAgent extends Agent {
    // ------------------------ Parameters ------------------------
    private double capacity = 50.0; // Total kW capacity
    private double baseCost = 0.035; // euro/kWh
    private double margin = 0.005; // Initial margin
    private double alpha = 0.03; // learning rate
    private double coeffSunny = 1.0;
    private double coeffCloudy = 0.4;
    private boolean DebuggingMode = true; // Debug mode
    
    // ------------------------- Internal state ------------------------
    private double production = 0.0; 
    private double lastClearingPrice = 0.0;
    private double lastOfferQty = 0.0;
    private String solarToken = "";
    private String timeToken = "";
    private double faultDuration = 0.0;
    private boolean isFaulty = false;

    @Override
    protected void setup() {
        register("solar-producer");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            capacity = Double.parseDouble(args[0].toString());
            baseCost = Double.parseDouble(args[1].toString());
            margin = Double.parseDouble(args[2].toString());
            alpha = Double.parseDouble(args[3].toString());
            coeffSunny = Double.parseDouble(args[4].toString());
            coeffCloudy = Double.parseDouble(args[5].toString());
            DebuggingMode = Boolean.parseBoolean(args[6].toString());
        }
        System.out.printf("- [%s] (solar) up! {capacity: %.2f | baseCost: %.2f | margin: %.2f | alpha: %.2f | coeffSunny: %.2f | coeffCloudy: %.2f}%n", getLocalName(), capacity, baseCost, margin, alpha, coeffSunny, coeffCloudy);

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
                if (timeToken.equals("NIGHT")) {production = 0.0; return;}
                double factor = "SUNNY".equals(solarToken) ? coeffSunny : coeffCloudy;
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

        switch (msg.getOntology()) {
            case "WEATHER":
                // Update weather
                content = msg.getContent();
                tokens = content.split(";");
                solarToken = tokens[0].split("=")[1];
                timeToken = tokens[2].split("=")[1];
                /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Weather update: %s | %s%n", getLocalName(), solarToken, timeToken);}
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
}