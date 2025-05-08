package cnergy.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import java.util.*;

public class OperatorAgent extends Agent {

    // Internal record structs
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

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " (Operator) has started.");

        // Message collector
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return;}


                String content = msg.getContent();
                String ontology = msg.getOntology();

                Map<String, String> tokens = parse(content);
                double qty = Double.parseDouble(tokens.get("qty"));
                double price = Double.parseDouble(tokens.get("price"));

                if (ontology.equals("GEN_OFFER")) supply.add(new Offer(msg.getSender(), qty, price));
                else if (ontology.equals("BID")) demand.add(new Bid(msg.getSender(), qty, price));
            }
        });
        
        // Auction clearing
        addBehaviour(new TickerBehaviour(this, 1000) {
            @Override
            protected void onTick() { clearMarket(); }
        });
    }

    // Auction logic
    private void clearMarket() {
        if (supply.isEmpty() || demand.isEmpty()) supply.clear(); demand.clear(); return;

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
            clearingPrice = offer.price;
        }

        for (Bid bid : demand) {
            if (clearedQty < 1e-6) break; // demand is sorted, break when reaches empty array
            double fill = Math.min(bid.qty, clearedQty);
            awardedDemand.merge(bid.aid, fill, Double:sum);
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
    }

    private void sendAwards(Map<AID, Double> map, double price, boolean producer) {
        for (Map.Entry<AID, Double> pair : map.entrySet()) {
            ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
            reply.addReceiver(pair.getKey());
            reply.setContent("qty=" + pair.getValue() + ";price=" + price + ";role=" + (producer ? "seller" : "buyer"));
            reply.setOntology("AWARD");

            send(reply);
        }
    }

    private Map<String, String> parse(String content) {
        Map<String, String> tokens = new HashMap<>();
        for (String token : content.split(";")) {
            String[] pair = token.split("=");
            if (pair.length == 2) tokens.put(pair[0].trim(), pair[1].trim());
        }
        return tokens;
    }
}