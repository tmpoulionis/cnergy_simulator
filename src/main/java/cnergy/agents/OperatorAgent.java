package cnergy.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;

public class OperatorAgent extends Agent {

    // ------------------------ Parameters ------------------------
    private boolean DebuggingMode = true; // Debug Mode
    // ------------------------- Internal state ------------------------
    private static class Offer {
        AID aid;
        double qty;
        double price;
        Offer(AID aid, double qty, double price) {
            this.aid = aid;
            this.qty = qty;
            this.price = price;
        }
    }
    private static class Bid {
        AID aid;
        double qty;
        double price;
        Bid(AID aid, double qty, double price) {
            this.aid = aid;
            this.qty = qty;
            this.price = price;
        }
    }

    private final List<Offer> supply = new ArrayList<>();
    private final List<Bid> demand = new ArrayList<>();
    private double qty = 0.0; // Quantity
    private double price = 0.0; // Price

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            DebuggingMode = Boolean.parseBoolean(args[0].toString());
        }
        System.out.printf("- [%s] (operator) up! {DebuggingMode: %s} %n", getLocalName(), DebuggingMode);

        // Message collector
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return;}

                String content = msg.getContent();
                String ontology = msg.getOntology();
                String [] tokens = content.split(";");
                qty = Double.parseDouble(tokens[0].split("=")[1]);
                price = Double.parseDouble(tokens[1].split("=")[1]);

                if (ontology.equals("GEN_OFFER")) supply.add(new Offer(msg.getSender(), qty, price));
                else if (ontology.equals("BID")) demand.add(new Bid(msg.getSender(), qty, price));
                /*DEBUG*/ if (DebuggingMode == true) { System.out.printf("%s - Received %s from %s: qty=%.2f; price=%.2f%n", getLocalName(), ontology, msg.getSender().getLocalName(), qty, price); }
            }
        });
        
        // Auction clearing
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() { 
                /*DEBUG*/ if (DebuggingMode == true) { System.out.println("CLEARING MARKET...");}
                clearMarket(); 
            }
        });
    }

    // Auction logic
    private void clearMarket() {
        if (supply.isEmpty() || demand.isEmpty()) {
            supply.clear(); 
            demand.clear(); 
            return;
        }

        supply.sort(Comparator.comparingDouble(o -> o.price));
        demand.sort(Comparator.comparingDouble((Bid b) -> b.price).reversed());

        Map<AID, Double> awardedSupply = new HashMap<>();
        Map<AID, Double> awardedDemand = new HashMap<>();
        double remainingDemand = demand.stream().mapToDouble(o -> o.qty).sum();
        double clearingPrice = 0.0;
        double clearedQty = 0.0;

        Iterator<Offer> supplyIter = supply.iterator();
        while (remainingDemand > 1e-6 && supplyIter.hasNext()) {
            Offer offer = supplyIter.next();
            double exchange = Math.min(offer.qty, remainingDemand);
            awardedSupply.merge(offer.aid, exchange, Double::sum);
            remainingDemand -= exchange;
            clearedQty += exchange;
            clearingPrice = offer.price;
        }

        for (Bid bid : demand) {
            if (clearedQty <= 1e-6) break; // demand is sorted, break when reaches empty array
            double fill = Math.min(bid.qty, clearedQty);
            awardedDemand.merge(bid.aid, fill, Double::sum);
            clearedQty -= fill;
        }

        // Send AWARD messages
        sendAwards(awardedSupply, clearingPrice, true); // true = producer
        sendAwards(awardedDemand, clearingPrice, false); // false = consumer

        // Broadcast last clearing price to everyone
        ACLMessage priceTick = new ACLMessage(ACLMessage.INFORM);
        priceTick.setOntology("PRICE_TICK");
        priceTick.setContent("price=" + clearingPrice);
        priceTick.addReceiver(getAMS()); // <- AMS will relay to everyone
        send(priceTick);

        /*DEBUG*/ if (DebuggingMode == true) { System.out.printf("🧮 AUCTION cleared at %.2f euro/kWh%n", clearingPrice); }
        supply.clear();
        demand.clear();
    }

    private void sendAwards(Map<AID, Double> map, double price, boolean producer) {
        for (Map.Entry<AID, Double> pair : map.entrySet()) {
            ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
            reply.addReceiver(pair.getKey());
            reply.setContent("qty=" + pair.getValue() + ";price=" + price + ";role=" + producer);
            reply.setOntology("AWARD");
            send(reply);
            /*DEBUG*/ if (DebuggingMode == true) { System.out.printf("%s - Award sent to %s: %.2f kWh @ %.2f euro/kWh%n", getLocalName(), pair.getKey().getLocalName(), pair.getValue(), price);  }
        }
    }
}