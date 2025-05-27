package cnergy.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class ConventionalAgent extends Agent {
    // ------------------------ Parameters ------------------------
    private double margin = 0.05;
    private boolean DebuggingMode = false;
    // ------------------------- Internal state ------------------------
    private double lastPrice = 0.06;
    private double faultDuration = 0.0;
    private boolean isFaulty = false;

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
                    case ACLMessage.ACCEPT_PROPOSAL: onFill(msg); break;
                    case ACLMessage.INFORM: onInform(msg); break;
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
                        if(DebuggingMode) System.out.printf("%s >> Faulty... %.2f seconds remaining%n", getLocalName(), faultDuration);
                        return;
                    } else {
                        isFaulty = false;
                        if(DebuggingMode) System.out.printf("%s >> Recovered!%n", getLocalName());
                    }
                }

                // calculate price
                double price = lastPrice + margin;
                
                // send order
                ACLMessage order = new ACLMessage(ACLMessage.PROPOSE);
                order.addReceiver(new AID("broker", AID.ISLOCALNAME));
                order.setOntology("ORDER");
                order.setContent("qty="+Double.POSITIVE_INFINITY+";price="+price+";side=sell");
                send(order);
                if(DebuggingMode) System.out.printf("%s >> SELL ORDER <inf> kWh @ %.3f%n", getLocalName(), price);

                ACLMessage gui = new ACLMessage(ACLMessage.INFORM);
                gui.addReceiver(new AID("gui", AID.ISLOCALNAME));
                gui.setOntology("PRODUCER_STATUS");
                gui.setContent("name="+getLocalName()
                        +";prod="+Double.POSITIVE_INFINITY
                        +";fault="+isFaulty);
                send(gui);
            }
        });
    }

    // ------------- functions --------------------
    private void onFill(ACLMessage msg) {
        String content = msg.getContent();
        String [] tokens = content.split(";");
        long id = Long.parseLong(tokens[0].split("=")[1]);

        double qty = Double.parseDouble(tokens[1].split("=")[1]);
        double price = Double.parseDouble(tokens[2].split("=")[1]);
        String from = tokens[3].split("=")[1];
        if(DebuggingMode) System.out.printf("%s >> FILLED order id=%d %.1f kWh @ %.3f (backup) from %s%n", getLocalName(), id, qty, price, from);
    }

    private void onInform(ACLMessage msg) {
        String content = "";
        String [] tokens = null;

        switch (msg.getOntology()) {
            case "PRICE_TICK":
                // Update last clearing price                
                content = msg.getContent();
                lastPrice = Double.parseDouble(content.split("=")[1]);
                if(DebuggingMode) System.out.printf("%s >> Price tick: %.2f%n", getLocalName(), lastPrice);
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
}
