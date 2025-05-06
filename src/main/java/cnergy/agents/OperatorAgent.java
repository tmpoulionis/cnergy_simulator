package cnergy.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.ACLMessage;

import jave.until.*;

public class OperatorAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " has started.");

        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    String sender = msg.getSender().getLocalName();
                    String ontology = msg.getOntology();
                    String content = msg.getContent();

                    if ("GEN_OFFER".equals(ontology)) {
                        System.out.printf("📩 GEN_OFFER from %s: %s%n", sender, content);
                    } else if ("BID".equals(ontology)) {
                        System.out.printf("📥 BID from %s: %s%n", sender, content);
                    } else {
                        System.out.printf("ℹ️ Unknown message from %s: %s (%s)%n", sender, ontology, content);
                    }
                } else {
                    block();
                }
            }
        });
    }
}