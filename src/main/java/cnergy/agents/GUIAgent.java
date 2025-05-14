package cnergy.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class GUIAgent extends Agent {
    private Dashboard fx;
    
    protected void setup() {
        fx = (Dashboard) getArguments()[0];
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {block(); return;}

                fx.enqueue(msg);
        });
    }
}
