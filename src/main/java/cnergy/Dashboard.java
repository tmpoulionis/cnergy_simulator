package cnergy;

import jade.lang.acl.ACLMessage;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@SuppressWarnings("FieldCanBeLocal")
public class Dashboard extends Application {
    // Static access so GuiAgent can enqueue messages into the FX thread
    private static volatile Dashboard INSTANCE;
    public static Dashboard get() {return INSTANCE;}

    // ---------------- Message bridge ------------------
    private final BlockingQueue<ACLMessage> inbox = new LinkedBlockingQueue<>();
    public void enqueue(ACLMessage msg) {inbox.offer(msg);}
    
}
