package cnergy.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.*;

public class BrokerAgent extends Agent {

    // ------------------------ Parameters ------------------------
    private boolean DebuggingMode = true; // Debug Mode
    // ------------------------- Internal state ------------------------
    private static class Order {
        long   id;            // order id
        AID    owner;
        double qty;
        double price;
        boolean seller;
        Order(long id,AID o,double q,double p,boolean bid) {id=id; owner=o; qty=q; price=p; seller=bid;}
    }

    // priority queues
    private final PriorityQueue<Order> bids = new PriorityQueue<>(
        (a,b) -> {
            int c = Double.compare(b.price, a.price); // priority based on price
            return c != 0 ? c : Long.compare(a.id, b.id); // If same prices, compare based on id (which is older)
        }
    );
    private final PriorityQueue<Order> asks = new PriorityQueue<>(
        (a,b) -> {
            int c = Double.compare(a.price, b.price);
            return c != 0 ? c : Long.compare(a.id, b.id); 
        }
    );

    private final Map<Long, Order> orderBook = new HashMap<>();
    private double lastPrice = 0.06;

    @Override
    protected void setup() {
        register("broker");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            DebuggingMode = Boolean.parseBoolean(args[0].toString());
        }
        System.out.printf("- [%s] (operator) up! %n", getLocalName());

        // Message collector
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return;}

                switch (msg.getPerformative()) {
                    case ACLMessage.PROPOSE: addOrder(msg);
                    case ACLMessage.CANCEL: cancelOrder(msg);
                }
                match();
            }
        });
    }
        // Auction clearing


    // ------- add / cancel orders ---------
    private void addOrder(ACLMessage msg) {
        String content = msg.getContent();
        String [] tokens = content.split(";");

        long id = Long.parseLong(tokens[0].split("=")[1]);
        boolean seller = content.contains("side=sell");
        double qty = Double.parseDouble(tokens[2].split("=")[1]);
        double price = Double.parseDouble(tokens[3].split("=")[1]);
        Order order = new Order(id, msg.getSender(), qty, price, seller);
        orderBook.put(id, order);

        log("NEW %s id=%d %.1f @ %.3f from %s", seller ? "BID":"ASK", id, qty, price, msg.getSender().getLocalName());
    }

    private void cancelOrder(ACLMessage msg) {
        String content = msg.getContent();
        long id = Long.parseLong(content.split("=")[1]);
        Order order = orderBook.remove(id); // remove order from the logbook and save it
        if(order != null) {
            (order.seller ? bids:asks).remove(order); // also remove it from the priority queues
            log("CANCEL id=%d",id);
        }
    }

    // ---------- matching engine -----------
    private void match() {
        while (!bids.isEmpty() && !asks.isEmpty() && bids.peek().price >= asks.peek().price) {
            Order buy = bids.peek();
            Order sell = asks.peek();
            double qty = Math.min(buy.qty, sell.qty);
            lastPrice = sell.price;

            // send messages to participants
            sendFill(sell, qty, lastPrice, buy.owner);
            sendFill(buy, qty, lastPrice, sell.owner);

            // trade log for GUI
            ACLMessage log = new ACLMessage(ACLMessage.INFORM);
            log.addReceiver(new AID("gui", AID.ISLOCALNAME));
            log.setOntology("TRADE_LOG");
            log.setContent("seller="+sell.owner+";buyer="+buy.owner+";qty="+qty+";price="+lastPrice);
            send(log);

            // update order book
            buy.qty -= qty;
            sell.qty -= qty;
            if(buy.qty <= 1e-6) {bids.poll(); orderBook.remove(buy.id);} // poll, removes the front element
            if(sell.qty <= 1e-6) {asks.poll(); orderBook.remove(sell.id);}
        }
        broadcastPrice();
    }

    private void sendFill(Order order, double qty, double price, AID from){
        ACLMessage msg = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
        msg.addReceiver(order.owner);
        msg.setOntology("ORDER");
        msg.setContent("id="+order.id+";qty="+qty+";price="+price+";from="+from.getLocalName());
        send(msg);
    }

    private void broadcastPrice() {
        ACLMessage price=new ACLMessage(ACLMessage.INFORM);
        price.setOntology("PRICE_TICK");
        price.setContent("price="+lastPrice);
        price.addReceiver(getAMS());
        send(price);
    }

    // --------- utils -----------
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