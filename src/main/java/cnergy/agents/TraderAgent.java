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

public class TraderAgent extends Agent {
    // --------- parameters ------------
    private double margin = 0.005;
    private double posLimit = 50;
    private double orderSize = 10;
    private boolean DebuggingMode = false;

    // -------- internal state -----------
    private double lastPrice = 0.06;
    private double position = 0;
    // -------- order tracking -----------
    private AtomicLong SEQ = new AtomicLong();
    private long bidId = -1;
    private long askId = -1;

    @Override
    protected void setup() {
        register("trader");
        Object [] args = getArguments();
        if(args != null && args.length >= 1) {
            margin = Double.parseDouble(args[0].toString());
            posLimit = Double.parseDouble(args[1].toString());
            orderSize = Double.parseDouble(args[2].toString());
            DebuggingMode = Boolean.parseBoolean(args[3].toString());
        }
        System.out.printf("- [%s] UP - {margin: %.2f | posLimit: %.2f | orderSize: %.2f}%n", getLocalName(), margin, posLimit, orderSize);

    // --------- message handling ----------
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) {block(); return;}
                switch(msg.getPerformative()) {
                    case ACLMessage.ACCEPT_PROPOSAL: onFill(msg); break;
                    case ACLMessage.INFORM: onInform(msg); break;
                }
            }    
        });

    // --------- hourly cicle -----------
        addBehaviour(new TickerBehaviour(this, 1000) {
            public void onTick() {
                // 1. cancel stale orders
                cancel(bidId); cancel(askId); bidId=askId=-1;

                // 2. place orders
                if (position < posLimit) {
                    double bid = Math.max(0, lastPrice - margin);
                    bidId = sendOrder("buy", orderSize, bid);
                    if(DebuggingMode) System.out.printf("%s >> BID id=%d %.1f @ %.3f%n", getLocalName(), bidId, orderSize, bid);
                }
            }
        });
    }

    // -------- function -----------
    private long sendOrder(String side, double qty, double price) {
        Long id = SEQ.incrementAndGet();
        ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
        msg.addReceiver(new AID("broker", AID.ISLOCALNAME));
        msg.setOntology("ORDER");
        msg.setContent("id="+id+";side="+side+";qty="+qty+";price="+price);
        send(msg);

        if(DebuggingMode) System.out.printf("%s >> %s ORDER id=%d qty=%.2f kWh @ %.3f%n", getLocalName(), side == "seller" ? "SELL" : "BUY", id, qty, price);
        return id;
    }

    private void cancel(long id){
        if(id == -1) return;
        ACLMessage msg = new ACLMessage(ACLMessage.CANCEL);
        msg.addReceiver(new AID("broker",AID.ISLOCALNAME));
        msg.setOntology("ORDER");
        msg.setContent("id="+id);
        send(msg);
    }

    private void onFill(ACLMessage msg){
        String content = msg.getContent();
        String [] tokens = content.split(";");

        long id = Long.parseLong(tokens[0].split("=")[1]);
        double qty = Double.parseDouble(tokens[1].split("=")[1]);
        double price= Double.parseDouble(tokens[2].split("=")[1]);
        String from = tokens[3].split("=")[1];
        boolean isBuy = (id==bidId);
        if(isBuy) position += qty; else position -= qty;
        if(DebuggingMode) System.out.printf("%s >> FILL %s %.1f @ %.4f from %s | inv=%.1f%n", getLocalName(), isBuy?"BUY":"SELL", qty, price, from, position);

        margin = Math.max(0.002, margin);
    }

    private void onInform(ACLMessage msg) {
        if (msg.getOntology().equals("PRICE_TICK")) {
            String content = msg.getContent();
            lastPrice = Double.parseDouble(content.split("=")[1]);
            if(DebuggingMode) System.out.printf("%s >> Price tick: %.2f%n", getLocalName(), lastPrice);
        }
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
