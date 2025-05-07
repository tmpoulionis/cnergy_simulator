package cnergy.agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.ACLMessage;

import jave.until.*;

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

                String reveiver = msg.getSender();
                String content = msg.getContent();
                String ontology = msg.getOntology();

                Map<String, String> tokens = parse(content);
                double qty = Double.parseDouble(tokens.get("qty"));
                double price = Double.parseDouble(tokens.get("price"));

                if (ontology.equals("GEN_OFFER")) supply.add(new Offer(receiver, qty, price));
                else if (ontology.equals("BID")) demand.add(new Bid(receiver, qty, price));
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
        demand.sort(Comparator.comperingDouble(o -> o.price).reserved());

        double remainingSupply = supply.stream().maptoDouble(o -> o.qty).sum();
        double remainingDemand = demand.stream().maptoDouble(o -> o.qty).sum();
        double clearingPrice = 0.0;
        double clearedQty = 0.0;

        Iterator<Offer> supplyIter = supply.iterator();
        while (remainingDemand > 1e-6 && supplyIter.hasNext()) {
            
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