package cnergy.agents;

import cnergy.Dashboard;
import javafx.application.Application;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.*;

public class GUIAgent extends Agent {
    // Keep track of fault tickers
    private final Map<String, Integer> faultMap = new HashMap<>();

    @Override
    protected void setup() {
        // 1) Launch the JavaFX dashboard in its own thread
        new Thread(() -> Application.launch(Dashboard.class)).start();

        // 2) Wait briefly for Dashboard.INSTANCE to appear
        addBehaviour(new TickerBehaviour(this, 200) {
            private int ticks = 0;
            @Override
            protected void onTick() {
                ticks++;
                if (Dashboard.INSTANCE != null || ticks > 20) {
                    // Once up, populate producers & consumers
                    discoverProducers();
                    discoverConsumers();
                    this.stop();
                    // start listening to updates
                    addBehaviour(new DashboardListener());
                }
            }
        });

        // Tickers countdown on faulty agents
        addBehaviour(new TickerBehaviour(this, 1000) {
          @Override
          protected void onTick() {
            // Iterate over a copy to allow removal
            new ArrayList<>(faultMap.entrySet()).forEach(e -> {
              String name      = e.getKey();
              int remaining = e.getValue() - 1;
              if (remaining > 0) {
                faultMap.put(name, remaining);
                Dashboard.INSTANCE.updateFaults(remaining, name);
              } else {
                faultMap.remove(name);
                Dashboard.INSTANCE.updateFaults(0, name);
              }
            });
          }
        });

    }

    private void discoverProducers() {
        String[] types = {
            "solar-producer",
            "wind-producer",
            "conventional-producer"
        };
        for (String t : types) {
            try {
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType(t);
                template.addServices(sd);
                DFAgentDescription[] result = DFService.search(this, template);
                for (DFAgentDescription dfd : result) {
                    String name = dfd.getName().getLocalName();
                    Dashboard.INSTANCE.addProducer(name);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void discoverConsumers() {
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("consumer");
            template.addServices(sd);
            DFAgentDescription[] result = DFService.search(this, template);
            Dashboard.INSTANCE.setConsumerCount(result.length);
            for (DFAgentDescription dfd : result) {
                String localName = dfd.getName().getLocalName();
                Dashboard.INSTANCE.addConsumer(localName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class DashboardListener extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg == null) {
                block();
                return;
            }
            String ont = msg.getOntology();
            String content = msg.getContent();
            String [] tokens = content.split(";");

            switch (ont) {
                case "WEATHER":
                    // content -> ("SUN="+solarToken+";WIND="+windToken+";TIME="+timeToken+";hour="+hour)
                    String sunToken = tokens[0].split("=")[1];
                    String windToken = tokens[1].split("=")[1];
                    String dayToken = tokens[2].split("=")[1];
                    Integer hour = Integer.parseInt(tokens[3].split("=")[1]);
                    Dashboard.INSTANCE.updateWeather(sunToken, windToken, dayToken, hour);
                    break;

                case "FAULT":
                    // content -> ("outage="+faultDuration+";victim="+victim)
                    Integer duration = Integer.parseInt(tokens[0].split("=")[1]);
                    String victim = tokens[1].split("=")[1];
                    faultMap.put(victim, duration);
                    Dashboard.INSTANCE.updateFaults(duration, victim);
                    break;

                case "PRICE_TICK":
                    // content "price=0.042"
                    double p = Double.parseDouble(content.split("=")[1]);
                    Dashboard.INSTANCE.updateLastTrade(p);
                    break;

                case "ORDER":
                    // new order from producer/consumer
                    // producers send side=sell, consumers side=buy
                    // content "id=7;side=sell;qty=20.0;price=0.040"
                    String[] tok = content.split(";");
                    long   id   = Long.parseLong(tok[0].split("=")[1]);
                    String side = tok[1].split("=")[1];
                    double qty  = Double.parseDouble(tok[2].split("=")[1]);
                    double pr   = Double.parseDouble(tok[3].split("=")[1]);
                    String owner=  tok[4].split("=")[1];
                    if (side.equals("sell")) {
                        Dashboard.INSTANCE.addSellOrder(id, qty, pr, owner);
                    } else {
                        Dashboard.INSTANCE.addBuyOrder(id, qty, pr, owner);
                    }
                    break;

                case "PRODUCER_STATUS":
                    try {
                        Map<String,String> kv = new HashMap<>();
                        for (String t : content.split(";")) {          // key=value
                            String[] kvp = t.split("=", 2);
                            if (kvp.length == 2) kv.put(kvp[0], kvp[1]);
                        }
                        String  name   = kv.get("name");
                        double  socPct = Double.parseDouble(kv.getOrDefault("soc",  "0"));
                        double  prod   = Double.parseDouble(kv.getOrDefault("prod", "0"));
                        boolean fault  = Boolean.parseBoolean(kv.getOrDefault("fault","false"));
                    
                        Dashboard.INSTANCE.updateProducer(name, socPct, prod, fault);
                    } catch (Exception ex) {
                        System.err.println("GUI-Agent ignored bad PRODUCER_STATUS: " + content);
                    }
                    break;

                case "CONSUMER_STATUS":
                    // content: name=<name>;demand=<d>;backlog=<b>
                    String cname   = tokens[0].split("=")[1];
                    double cdemand = Double.parseDouble(tokens[1].split("=")[1]);
                    double cback   = Double.parseDouble(tokens[2].split("=")[1]);
                    Dashboard.INSTANCE.updateConsumer(cname, cdemand, cback);
                    break;

                case "ORDER_REMOVE":
                    long rid = Long.parseLong(content.split("=")[1]);
                    Dashboard.INSTANCE.removeOrder(rid);
                    break;

                case "TRADE_LOG":
                    // content: "seller=wind;buyer=consumer;qty=1.0;price=0.040"
                    String[] seg = content.split(";");
                    String seller = seg[0].split("=")[1];
                    String buyer = seg[1].split("=")[1];
                    double amount = Double.parseDouble(seg[2].split("=")[1]);
                    double price = Double.parseDouble(seg[3].split("=")[1]);
                    Dashboard.INSTANCE.showTrade(seller, buyer, amount, price);
                    Dashboard.INSTANCE.updateLastTrade(price);
                    break;
            }
        }
    }
}
