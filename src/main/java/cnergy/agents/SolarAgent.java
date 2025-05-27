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
    private boolean hasBattery = true;
    private double battCapacity = 100;
    private double coeffSunny = 1.0;
    private double coeffCloudy = 0.4;


    private double baseCost = 0.035; // euro/kWh
    private double margin = 0.005; // Initial margin
    private double alpha = 0.003; // learning rate

    private boolean DebuggingMode = false; // Debug mode
    
    // ------------------------- Internal state ------------------------
    private double production = 0.0; 
    private double lastClearingPrice = 0.0;
    private String solarToken = "";
    private String timeToken = "";
    private double faultDuration = 0.0;
    private double soc = 0.0;
    private boolean isFaulty = false;
    private double openQty = 0; 

    @Override
    protected void setup() {
        register("solar-producer");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            capacity = Double.parseDouble(args[0].toString());
            hasBattery = Boolean.parseBoolean(args[1].toString());
            battCapacity = Double.parseDouble(args[2].toString());
            coeffSunny = Double.parseDouble(args[3].toString());
            coeffCloudy = Double.parseDouble(args[4].toString());
            baseCost = Double.parseDouble(args[5].toString());
            margin = Double.parseDouble(args[6].toString());
            alpha = Double.parseDouble(args[7].toString());
            DebuggingMode = Boolean.parseBoolean(args[8].toString());
        }
        System.out.printf("- [%s]  UP - {capacity: %.2f | hasBattery: %b | battCapacity: %.2f | baseCost: %.2f | margin: %.2f | alpha: %.2f | coeffSunny: %.2f | coeffCloudy: %.2f}%n", getLocalName(), capacity, hasBattery, battCapacity, baseCost, margin, alpha, coeffSunny, coeffCloudy);

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
                        if(DebuggingMode) System.out.printf("%s >> Faulty... %.2f seconds remaining%n", getLocalName(), faultDuration);
                        return;
                    } else {
                        isFaulty = false;
                        if(DebuggingMode) System.out.printf("%s >> Recovered!%n", getLocalName());
                    }
                }

                // produce energy
                if (timeToken.equals("DAY")) {
                    double factor = "SUNNY".equals(solarToken) ? coeffSunny : coeffCloudy;
                    production = capacity * factor;
                    production = Math.min(production, battCapacity - soc);
                    if(DebuggingMode) System.out.printf("%s >> Generating.. %.2f kWh %n", getLocalName(), production);
                } else {production = 0;}

                // calculate price
                double available = production + soc;
                if (available < 1e-6) {return;} // nothing to sell
                double price = baseCost + margin;
                // price = Math.max(price, lastClearingPrice - 0.02); // avoid undercutting the market
                
                // send order
                openQty = available;
                ACLMessage order = new ACLMessage(ACLMessage.PROPOSE);
                order.addReceiver(new AID("broker", AID.ISLOCALNAME));
                order.setOntology("ORDER");
                order.setContent("qty="+available+";price="+price+";side=sell");
                send(order);
                if(DebuggingMode) System.out.printf("%s >> SELL ORDER qty=%.2f kWh @ %.3f%n", getLocalName(), available, price);

                ACLMessage gui = new ACLMessage(ACLMessage.INFORM);
                gui.addReceiver(new AID("gui", AID.ISLOCALNAME));
                gui.setOntology("PRODUCER_STATUS");
                gui.setContent("name="+getLocalName()+";soc="+soc/battCapacity*100+";prod="+production+";fault="+isFaulty);
                send(gui);

            }
        });
    }

    // ---------------------------- FUNCTIONS -------------------------------------
    private void onInform(ACLMessage msg) {
        String content = "";
        String [] tokens = null;

        switch (msg.getOntology()) {
            case "WEATHER":
                // Update weather
                content = msg.getContent();
                tokens = content.split(";");
                solarToken = tokens[0].split("=")[1];
                timeToken = tokens[2].split("=")[1];
                if(DebuggingMode) System.out.printf("%s >> Weather update: %s | %s%n", getLocalName(), solarToken, timeToken);
                break;
            case "PRICE_TICK":
                // Update last clearing price                
                content = msg.getContent();
                lastClearingPrice = Double.parseDouble(content.split("=")[1]);
                if(DebuggingMode) System.out.printf("%s >> Price tick: %.2f%n", getLocalName(), lastClearingPrice);
                break;
            case "FAULT":
                content = msg.getContent();
                tokens = content.split(";");
                faultDuration = Double.parseDouble(tokens[0].split("=")[1]);
                isFaulty = true;
                if(DebuggingMode) System.out.printf("%s >> Fault occured | duration: %.2f%n", getLocalName(), faultDuration);
                break;
        }
    }

    private void onFill(ACLMessage msg, boolean seller) {
        String content = msg.getContent();
        String [] tokens = content.split(";");

        long id = Long.parseLong(tokens[0].split("=")[1]);
        double qty = Double.parseDouble(tokens[1].split("=")[1]);
        double price = Double.parseDouble(tokens[2].split("=")[1]);
        String from = tokens[3].split("=")[1];

        openQty -= qty;
        if(openQty <= 1e-6) {openQty = 0;}
        
        // Remove energy taken from batteries 
        soc -= qty - production; 
        soc = Math.max(0, Math.min(soc, battCapacity));
        
        margin += alpha;
        margin = Math.min(margin, 0.1);
        if(DebuggingMode) System.out.printf("%s >> FILLED order id=%d %.1f kWh @ %.3f from %s | new margin %.3f%n", getLocalName(), id, qty, price, from, margin);
    }

    private void onReject(ACLMessage msg) {
        String content = msg.getContent();
        String[] tokens = content.split(";");
        
        long id = Long.parseLong(tokens[0].split("=")[1]);
        soc = Math.min(battCapacity, soc + openQty);
        openQty=0;

        margin -= alpha;
        margin = Math.max(-0.02, margin);
        if(DebuggingMode) System.out.printf("%s >> REJECTED id=%d -> energy returned to battery (SoC=%.0f %.1f%%)%n", getLocalName(), id, soc, soc/battCapacity*100);
    }

    // -------- utilities ----------
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