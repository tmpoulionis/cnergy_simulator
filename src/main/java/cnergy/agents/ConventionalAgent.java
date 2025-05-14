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
    private double baseCost = 0.08;
    private boolean DebuggingMode = false;
    // ------------------------- Internal state ------------------------
    private double lastClearingPrice = 0.0;
    private double faultDuration = 0.0;
    private boolean isFaulty = false;
    private double qty = 0.0;
    @Override
    protected void setup() {
        register("conventional-producer");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            margin = Double.parseDouble(args[0].toString());
            baseCost = Double.parseDouble(args[1].toString());
            DebuggingMode = Boolean.parseBoolean(args[2].toString());
        }
        System.out.printf("- [%s] (conventional) up! {DebuggingMode: %s}%n", getLocalName(), DebuggingMode);

        // --------------------- Receive messages and update internal state -----------------------
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {block(); return;}
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

                double ask = Math.max(baseCost, lastClearingPrice + margin);

                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.setOntology("GEN_OFFER");
                msg.setContent("qty=" + Double.POSITIVE_INFINITY + ";price=" + ask);
                msg.addReceiver(new AID("operator", AID.ISLOCALNAME));
                send(msg);
                /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Offer sent: %s kWh @ %.2f euro/kWh%n", getLocalName(), "*Unlimited supply*", ask);}
            }
        });
    }

    private void updateInternalState(ACLMessage msg) {
        String content = msg.getContent();
        String [] tokens = content.split(";");
        switch (msg.getOntology()) {
            case "PRICE_TICK":
                lastClearingPrice = Double.parseDouble(tokens[0].split("=")[1]);
                /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Price tick: %.2f%n", getLocalName(), lastClearingPrice);}
                break;
            case "AWARD":
                qty = Double.parseDouble(tokens[0].split("=")[1]);
                lastClearingPrice = Double.parseDouble(tokens[1].split("=")[1]);
                /*DEBUG*/ if (DebuggingMode == true) {System.out.printf("%s - Award received: %.2f kWh @ %.2f euro/kWh", getLocalName(), qty, lastClearingPrice);}
                break;
            case "FAULT":
                faultDuration = Double.parseDouble(tokens[0].split("=")[1]);
                break;
        }
    }

    private void register(String t){
        try{
            DFAgentDescription dfd=new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd=new ServiceDescription();
            sd.setType(t); sd.setName("energy-market");
            dfd.addServices(sd);
            DFService.register(this,dfd);
        }catch(Exception e){e.printStackTrace();}
    }
}
