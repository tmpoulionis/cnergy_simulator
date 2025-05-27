package cnergy.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
public class BrokerAgent extends Agent {

    // ------------------------ Parameters ------------------------
    private int expiryTicks = 3;
    private boolean DebuggingMode = true; // Debug Mode
    // ------------------------- Internal state ------------------------
    private static class Order {
        long   id;            // order id
        AID    owner;
        double qty;
        double price;
        boolean seller;
        int expiry;
        Order(long Id, AID o, double q, double p, boolean sell, int exp) {id=Id; owner=o; qty=q; price=p; seller=sell; expiry=exp;}
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

    // order tracking
    private AtomicLong SEQ = new AtomicLong();
    private final Map<Long, Order> orderBook = new HashMap<>();
    private double lastPrice = 0.06;
    private int tick = 0;

    @Override
    protected void setup() {
        register("broker");
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            expiryTicks = Integer.parseInt(args[0].toString());
            DebuggingMode = Boolean.parseBoolean(args[0].toString());
        }
        System.out.printf("- [%s] (operator) up! %n", getLocalName());

        // Message collector
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return;}
                if (msg.getPerformative() == ACLMessage.PROPOSE) {
                    addOrder(msg);
                    match();
                }
            }
        });
    
        addBehaviour(new TickerBehaviour(this, 1000) {
            public void onTick() {
                tick++;
                expireOrder();
            }
        });
    }

    // ------- add / cancel orders ---------
    private void addOrder(ACLMessage msg) {
        String content = msg.getContent();
        String [] tokens = content.split(";");

        double qty = Double.parseDouble(tokens[0].split("=")[1]);
        double price = Double.parseDouble(tokens[1].split("=")[1]);
        boolean seller = content.contains("side=sell");

        long id = SEQ.incrementAndGet();
        int expiry = tick + expiryTicks;

        Order order = new Order(id, msg.getSender(), qty, price, seller, expiry);
        orderBook.put(id, order);
        if (seller) {
            asks.add(order);
        } else {
            bids.add(order);
        }

        // --- forward to GUI (safe) ---
        ACLMessage gui = new ACLMessage(ACLMessage.INFORM);   // create new message
        gui.addReceiver(new AID("gui", AID.ISLOCALNAME));
        gui.setOntology("ORDER");
        gui.setContent(String.format(
            "id=%d;side=%s;qty=%s;price=%s;from=%s",
            id,
            seller ? "sell" : "buy",
            qty,                         // always a number, never “Infinity”
            price,
            msg.getSender().getLocalName()));
        send(gui);

        if(DebuggingMode) System.out.printf("%s >> NEW %s ORDER id=%d %.1f @ %.3f from %s %n", getLocalName(), seller ? "SELL":"BUY", id, qty, price, msg.getSender().getLocalName());
    }

    private void expireOrder() {
        Iterator<Order> it = orderBook.values().iterator();
        while (it.hasNext()) {
            Order order = it.next();
            if (order.expiry <= tick) {
                ACLMessage rej = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
                rej.addReceiver(order.owner);
                rej.setOntology("ORDER");
                rej.setContent("id="+order.id);
                send(rej);

                it.remove();
                if (order.seller) {asks.remove(order);}
                else {bids.remove(order);}
                
                // notify the GUI so it can drop the row 
                ACLMessage gui = new ACLMessage(ACLMessage.INFORM);
                gui.addReceiver(new AID("gui", AID.ISLOCALNAME));
                gui.setOntology("ORDER_REMOVE");
                gui.setContent("id=" + order.id);
                send(gui);

                if(DebuggingMode) System.out.printf("%s >> ORDER EXPIRED -> %s - id=%d %n",getLocalName(), order.owner.getLocalName(), order.id);
            }
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
            System.out.printf("%s >> TRADE %s <--> %s %.1f @ %.3f%n", getLocalName(), buy.owner.getLocalName(), sell.owner.getLocalName(), qty, lastPrice);
            // trade log for GUI
            ACLMessage log = new ACLMessage(ACLMessage.INFORM);
            log.addReceiver(new AID("gui", AID.ISLOCALNAME));
            log.setOntology("TRADE_LOG");
            log.setContent("seller="+sell.owner.getLocalName()+";buyer="+buy.owner.getLocalName()+";qty="+qty+";price="+lastPrice);
            send(log);

            // update order book
            buy.qty -= qty;
            sell.qty -= qty;
            if(buy.qty <= 1e-6) {
                bids.poll(); 
                orderBook.remove(buy.id);

                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID("gui", AID.ISLOCALNAME));
                msg.setOntology("ORDER_REMOVE");
                msg.setContent("id="+buy.id);
                send(msg);
            } // poll, removes the front element

            if(sell.qty <= 1e-6) {
                asks.poll(); 
                orderBook.remove(sell.id);

                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.addReceiver(new AID("gui", AID.ISLOCALNAME));
                msg.setOntology("ORDER_REMOVE");
                msg.setContent("id="+sell.id);
                send(msg);
            }
        }
        broadcastPrice();
    }

    // --------- messages ----------
    private void sendFill(Order order, double qty, double price, AID from){
        ACLMessage msg = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
        msg.addReceiver(order.owner);
        msg.setOntology("ORDER");
        msg.setContent("id="+order.id+";qty="+qty+";price="+price+";from="+from.getLocalName());
        send(msg);
        if(DebuggingMode) System.out.printf("%s >> SENDING FILL to %s id=%d %.1f @ %.3f from %s%n", getLocalName(), order.owner.getLocalName(), order.id, qty, price, from.getLocalName());
    }

    private void broadcastPrice() {
        ACLMessage price=new ACLMessage(ACLMessage.INFORM);
        price.setOntology("PRICE_TICK");
        price.setContent("price="+lastPrice);
        price.addReceiver(getAMS());
        send(price);
        System.out.printf("$$ CURRENT PRICE: %.3f%n", lastPrice); 
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
}