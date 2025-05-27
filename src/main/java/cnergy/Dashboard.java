package cnergy;

import java.util.Comparator;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.collections.transformation.SortedList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.Stage;

public class Dashboard extends Application {
    public static Dashboard INSTANCE;

    // ---- TOP ------
    private Label weatherLabel;
    private Label lastTradeLabel;

    // ---- LEFT: producers ------
    public static class ProducerStatus {
        private final StringProperty name = new SimpleStringProperty();
        private final DoubleProperty socPct = new SimpleDoubleProperty();
        private final DoubleProperty production = new SimpleDoubleProperty();
        private final IntegerProperty faultTicks = new SimpleIntegerProperty();

        public ProducerStatus(String name) {this.name.set(name);}
        public String getName() {return name.get();}
        public double getSocPct() {return socPct.get();}
        public double getProduction() {return production.get();}
        public int getTicksLeft() {return faultTicks.get();}

        public StringProperty nameProperty() {return name;}
        public DoubleProperty socPctProperty() {return socPct;}
        public DoubleProperty productionProperty() {return production;}
        public IntegerProperty faultTicksProperty() {return faultTicks;}
        }
        public final ObservableList<ProducerStatus> producers = FXCollections.observableArrayList();
        public Label faultyLabel;
    // ----- RIGHT: consumers -------
    private final IntegerProperty consumerCount = new SimpleIntegerProperty(0);

    public static class ConsumerStatus {
        private final StringProperty name     = new SimpleStringProperty();
        private final DoubleProperty demand   = new SimpleDoubleProperty();
        private final DoubleProperty backlog  = new SimpleDoubleProperty();

        public ConsumerStatus(String n) { name.set(n); }

        public String getName()      { return name.get(); }
        public double getDemand()    { return demand.get(); }
        public double getBacklog()   { return backlog.get(); }

        public StringProperty nameProperty()     { return name; }
        public DoubleProperty demandProperty()   { return demand; }
        public DoubleProperty backlogProperty()  { return backlog; }
    }
public final ObservableList<ConsumerStatus> consumers = FXCollections.observableArrayList();

    // ----- CENTER: market table ------
    // Last trade
    private final Label sellerTrade = new Label("-");
    private final Label buyerTrade  = new Label("-");
    private final Label arrow = new Label("→");
    private final Label qtyTrade    = new Label("-");
    private final Label priceTrade  = new Label("-");

    // Order book
    public static class Order {
        private final LongProperty id = new SimpleLongProperty();
        private final DoubleProperty qty = new SimpleDoubleProperty();
        private final DoubleProperty price = new SimpleDoubleProperty();
        private final StringProperty owner = new SimpleStringProperty();
        public Order(long id, double qty, double price, String owner) {
            this.id.set(id);
            this.qty.set(qty);
            this.price.set(price);
            this.owner.set(owner);
        }
        public long getId() {return id.get();}
        public double getQty() {return qty.get();}
        public double getPrice() {return price.get();}
        public String getOwner() {return owner.get();}

        public LongProperty idProperty() {return id;}
        public DoubleProperty qtyProperty() {return qty;}
        public DoubleProperty priceProperty() {return price;}
        public StringProperty ownerProperty() {return owner;}
    }
    private final ObservableList<Order> buyOrders = FXCollections.observableArrayList();
    private final ObservableList<Order> sellOrders = FXCollections.observableArrayList();

    @Override
    public void start(Stage stage) {
        INSTANCE = this;
        stage.setTitle("C-NERGY Market Panel");

        // -------------- TOP: weather and time  ------------------
        weatherLabel = new Label("Weather: --");

        HBox topBar = new HBox(20, weatherLabel);
        topBar.setPadding(new Insets(10));
        topBar.setAlignment(Pos.CENTER);
        topBar.setStyle("-fx-background-color:rgb(255, 255, 255)");
        
        // --------- LEFT: list of producers -------------
        faultyLabel = new Label("Faulty: 0");
        faultyLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        TableView<ProducerStatus> prodTable = new TableView<>(producers);
        prodTable.setPrefWidth(320);
        TableColumn<ProducerStatus, String> nameCol = new TableColumn<>("Producer");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<ProducerStatus, Number> socCol = new TableColumn<>("SoC (%)");
        socCol.setCellValueFactory(new PropertyValueFactory<>("socPct"));
        TableColumn<ProducerStatus, Number> prodCol = new TableColumn<>("Offer (kWh)");
        prodCol.setCellValueFactory(new PropertyValueFactory<>("production"));
        TableColumn<ProducerStatus, Number> faultCol = new TableColumn<>("Fault ticks");
        faultCol.setCellValueFactory(new PropertyValueFactory<>("faultTicks"));

        prodTable.getColumns().addAll(nameCol, socCol, prodCol, faultCol);

        // faulty color styling
        prodTable.setRowFactory(tv -> {
            TableRow<ProducerStatus> row = new TableRow<>();
            row.itemProperty().addListener((obs, oldPs, newPs) -> {
                if (newPs == null) {
                    row.setStyle("");
                } else {
                    newPs.faultTicksProperty().addListener((o,ov,nv)-> {
                        row.setStyle(nv.intValue()>0 ?
                             "-fx-background-color:rgba(255,100,100,0.5);" : "");
                    });
                    row.setStyle(newPs.getTicksLeft()>0 ?
                         "-fx-background-color:rgba(255,100,100,0.5);" : "");
                }
            });
            return row;
        });


        VBox leftPane = new VBox(5, faultyLabel, new Label("Producers"), prodTable);
        leftPane.setPadding(new Insets(10));

        // ----------- RIGHT: consumers ---------------
        Label consLabel = new Label();
        consLabel.textProperty().bind(consumerCount.asString("Consumers: %d"));

        TableView<ConsumerStatus> consTable = new TableView<>(consumers);
        consTable.setPrefWidth(250);
        TableColumn<ConsumerStatus,String> cNameCol   = new TableColumn<>("Consumer");
        cNameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        TableColumn<ConsumerStatus,Number> cDemCol    = new TableColumn<>("Demand (kWh)");
        cDemCol.setCellValueFactory(new PropertyValueFactory<>("demand"));
        TableColumn<ConsumerStatus,Number> cBackCol   = new TableColumn<>("Backlog");
        cBackCol.setCellValueFactory(new PropertyValueFactory<>("backlog"));

        consTable.getColumns().addAll(cNameCol, cDemCol, cBackCol);

        VBox rightPane = new VBox(5, consLabel, new Label("Consumers"), consTable);
        rightPane.setPadding(new Insets(10));

        // --------- CENTER: Order book & last Price -------------
        Label ordersBanner = new Label("Order Book");
        ordersBanner.setFont(Font.font("Verdana", FontWeight.BOLD, 18));

        // ON TOP: Last trade
        Font tradeFont = Font.font("Verdana", FontWeight.BOLD, 12);   // ← 20-pt banner
        sellerTrade.setFont(tradeFont);
        arrow.setFont(tradeFont);
        buyerTrade.setFont(tradeFont);
        qtyTrade.setFont(tradeFont);
        priceTrade.setFont(tradeFont);

        HBox tradeStrip = new HBox(12,
                sellerTrade,
                arrow,
                buyerTrade ,
                qtyTrade  ,
                priceTrade);
        tradeStrip.setAlignment(Pos.CENTER);
        tradeStrip.setPadding(new Insets(4));
        tradeStrip.setStyle("-fx-border-color: lightgray; -fx-border-width: 0 0 1 0;"); // bottom border

        lastTradeLabel = new Label("Current price: -- €/kWh");
        lastTradeLabel.setFont(Font.font("Verdana", FontWeight.BOLD, 15));

        // SELL side – lowest price first
        SortedList<Order> sellsSorted = new SortedList<>(sellOrders,
            Comparator.<Order>comparingDouble(Order::getPrice)
                      .thenComparingLong(Order::getId));
        TableView<Order> sellTable = new TableView<>(sellsSorted);

        sellTable.setPlaceholder(new Label("No sell orders"));
        TableColumn<Order,Number> sellIdCol    = new TableColumn<>("ID");
        sellIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<Order,Number> sellQtyCol   = new TableColumn<>("Qty");
        sellQtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        TableColumn<Order,Number> sellPriceCol = new TableColumn<>("Price");
        sellPriceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        TableColumn<Order,String> sellOwnCol    = new TableColumn<>("Owner");
        sellOwnCol.setCellValueFactory(new PropertyValueFactory<>("owner"));
        sellTable.getColumns().addAll(sellIdCol, sellQtyCol, sellPriceCol, sellOwnCol);

        // BUY side – highest price first
        SortedList<Order> buysSorted = new SortedList<>(buyOrders,
            Comparator.<Order>comparingDouble(Order::getPrice).reversed()
                      .thenComparingLong(Order::getId));
        TableView<Order> buyTable = new TableView<>(buysSorted);

        buyTable.setPlaceholder(new Label("No buy orders"));
        TableColumn<Order,Number> buyIdCol    = new TableColumn<>("ID");
        buyIdCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        TableColumn<Order,Number> buyQtyCol   = new TableColumn<>("Qty");
        buyQtyCol.setCellValueFactory(new PropertyValueFactory<>("qty"));
        TableColumn<Order,Number> buyPriceCol = new TableColumn<>("Price");
        buyPriceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        TableColumn<Order,String> buyOwnCol    = new TableColumn<>("Owner");
        buyOwnCol.setCellValueFactory(new PropertyValueFactory<>("owner"));
        buyTable.getColumns().addAll(buyIdCol, buyQtyCol, buyPriceCol, buyOwnCol);

        HBox tables = new HBox(10, new VBox(new Label("Sells"), sellTable), new VBox(new Label("Buys"), buyTable));
        tables.setAlignment(Pos.CENTER);
        tables.setPadding(new Insets(10));

        VBox centerPane = new VBox(10, ordersBanner, lastTradeLabel, tradeStrip, tables);
        centerPane.setAlignment(Pos.TOP_CENTER);
        centerPane.setPadding(new Insets(10));

        // ------------- Assemble --------------------
        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setLeft(leftPane);
        root.setCenter(centerPane);
        root.setRight(rightPane);

        Scene scene = new Scene(root, 1500, 700);
        stage.setScene(scene);
        stage.show();
    }

    // --------- Exposed update methods -------------
    public void updateWeather(String sunToken, String windToken, String dayToken, Integer hour) {
        Platform.runLater(() -> weatherLabel.setText(String.format("Time %d:00 %s | Weather --> Sun: %s   Wind: %s", hour, dayToken, sunToken, windToken)));
    }

    public void updateFaults(Integer ticksRemain, String victim) {
        Platform.runLater(() -> {
            for (ProducerStatus ps : producers) {
                if (ps.getName().equals(victim)) {
                    ps.faultTicksProperty().set(ticksRemain);
                    break;
                }
            }

            long downCount = producers.stream().filter(ps->ps.getTicksLeft()>0).count();
            faultyLabel.setText("Faulty: " + downCount);
        });
    }

    public void updateLastTrade(double price) {
        Platform.runLater(() -> lastTradeLabel.setText(
            String.format(" Current price: %.3f €/kWh", price)));
    }

    public void setConsumerCount(int n) {
        Platform.runLater(() -> consumerCount.set(n));
    }

    public void addProducer(String name) {
        Platform.runLater(() -> producers.add(new ProducerStatus(name)));
    }

    public void updateProducer(String name, double socPct, double prod, boolean inFault) {
        Platform.runLater(() -> {
            for (ProducerStatus ps : producers) {
                if (ps.getName().equals(name)) {
                    ps.socPctProperty().set(socPct);
                    ps.productionProperty().set(prod);
                    break;
                }
            }
        });
    }

    public void addConsumer(String name) {
        Platform.runLater(() -> consumers.add(new ConsumerStatus(name)));
    }

    public void updateConsumer(String name, double demand, double backlog) {
        Platform.runLater(() -> {
            consumers.stream()
                     .filter(c -> c.getName().equals(name))
                     .findFirst()
                     .ifPresent(c -> {
                          c.demandProperty().set(demand);
                          c.backlogProperty().set(backlog);
                     });
        });
    }

    public void addBuyOrder(long id, double qty, double price, String owner) {
        Platform.runLater(() -> buyOrders.add(new Order(id,qty,price,owner)));
    }

    public void addSellOrder(long id, double qty, double price, String owner) {
        Platform.runLater(() -> sellOrders.add(new Order(id,qty,price,owner)));
    }

    public void removeOrder(long id) {
        Platform.runLater(() -> {
            buyOrders.removeIf(o -> o.getId() == id);
            sellOrders.removeIf(o -> o.getId() == id);
        });
    }

    public void showTrade(String seller, String buyer,
                          double qty, double price) {
        Platform.runLater(() -> {
            sellerTrade.setText(seller);
            sellerTrade.setStyle("-fx-text-fill: red;  -fx-font-weight: bold;");

            buyerTrade.setText(buyer);
            buyerTrade.setStyle("-fx-text-fill: green;-fx-font-weight: bold;");

            qtyTrade  .setText(String.format("%.1f kWh", qty));
            priceTrade.setText(String.format("@ %.3f €/kWh", price));
        });
    }

    public void purgeAllOrdersFrom(String owner) {
        Platform.runLater(() -> {
            buyOrders.removeIf(o -> o.getOwner().equals(owner));
            sellOrders.removeIf(o -> o.getOwner().equals(owner));
        });
    }

    public static void main(String[] args) {
        launch(args);
    }

}