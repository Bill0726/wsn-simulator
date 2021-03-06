package WSN;



import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;

import events.Event;
import events.UpdatePosition;
import protocols.*;

/**
 * Created by Gianluca on 16/07/2017.
 */
public class WSN {

    // --------- MAIN SIMULATION PARAMETERS ----------//

    private static long runningTime = 0;
    private static Protocol p;
    private static double simulationTime;
    private int nodeCount;                       // number of nodes in the network
    private long sleepDelay = 0;                      // delay used to extract events
    public static boolean debug = false;            // printing extra information useful for debugging

    final static double maxAvailableThroughput = 11;    // Mb/s
    public static int frameSize = 1500;               // bytes

    private static int windowSize = 1000;                 //  window size used in Fairness calculation
    private double windowDuration = 2;              //  window duration (expressed in seconds) used in Fairness calculation

    public static double PrxThreshold = -82;        // threshold on received power (dBm)
    public static double Ptx = 20;                   // transmission power (dBm)
    public static boolean indoor = false;           // indoor or outdoor scenario
    // ------------------------------------//

    public enum NODE_STATUS {
      SLEEPING, TRANSMITTING, IDLING, RECEIVING, LISTENING, JAMMING
    };

    /***********************************************
     *                  GUI                        *
     ***********************************************/

    private boolean gui = false;
    public static Color txColor = Color.magenta;
    public static Color normColor = Color.blue;
    public static Color sleepColor = Color.pink;
    public static Color listenColor = Color.cyan;
    private WSNWindow guiWindow;
    private int panelW, panelH;
    private JFrame f;

    public void setPanelSize(int w, int h){
        if (gui){
            this.panelW = w;
            this.panelH = h;
            f.setSize(w, h);
        }
    }


    public static double meanInterarrivalTime = 20.0;
    public static double meanBackoff = 200.0;
    public static double sleepTime = 50.0;

    public static double normSize = 10;
    public static double txSize = 15;

    public static double tPLC = 192;
    public static double SIFS = 10;
    public static double DIFS = 50;
    public static double tSlot = 20;
    public static double tACK = (double) Math.round((14 * 8)
            / (1) * 100) / 100 + tPLC;
    public static double txTime =  (double) Math.round((frameSize * 8)
            / (maxAvailableThroughput) * 100) / 100 + tPLC;

    public static int CWmin = 15;
    public static int CWmax = 1023;

    private static int topologyID;
    private static int mobilityID;

    private static double width, height;
    private static double maxRadius;

    public static double getPoisson(double mean) {
        RNG r = RNG.getInstance();
        double L = Math.exp(-mean);
        double k = 0.0;
        double p = 1.0;
        do {
            p = p * r.nextDouble();
            k++;
        } while (p > L);
        return k - 1;

    }

    public static List<Node> nodes;


    // Collison rates
    public static HashSet<Double> collided = new HashSet<>();
    public static HashSet<Double> attempted = new HashSet<>();
    public static ArrayList<Node> transmitting = new ArrayList<Node>();
    public static int access = 0;
    public static int collisions = 0;

    // log of nodes that have transmitted (useful to fairness calculation)
    public static ArrayList<Node> nodeTrace;
    public static ArrayList<Double> nodeTraceTimes;


    //
    // CONTI
    //

    public static double CONTIslotTime = 20;
    public static int probVectSize;

    //
    // GALTIER
    //
    public static List<List<Double>> galtierP = new ArrayList<>();
    public static List<Double> CONTIp = new ArrayList<>();


    //
    // Methods
    //

    /************************      CONSTRUCTOR      ***********************/
    public WSN(int nodeCount, double width, double height, Protocol p, int topologyID, int mobilityID, boolean gui) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, IOException {

        RNG r = RNG.getInstance();
        nodes = new LinkedList<>();
        this.nodeCount = nodeCount;

        WSN.width = width;
        WSN.height = height;

        WSN.topologyID = topologyID;
        WSN.mobilityID = mobilityID;
        this.gui = gui;


        Scheduler scheduler = Scheduler.getInstance();

        this.p = p;
        WSN.nodeTrace = new ArrayList<>();
        WSN.nodeTraceTimes = new ArrayList<>();
        WSN.probVectSize = 0;

        for (int i = 0; i < this.nodeCount; i++) {

            double[] coord = nodePosition();

            double X = coord[0];
            double Y = coord[1];

            Node n = new Node(i, X, Y);
            n.setStatus(WSN.NODE_STATUS.IDLING);
            nodes.add(n);

            Event e = (Event) p.entryPoint().newInstance(n,new Double(0));
            scheduler.schedule(e);
        }

        System.out.println(p.getClass().getSimpleName());

        if (p.getClass().getSimpleName().equals("CONTI")){
            initializeCONTI(nodeCount);
        }

        if (p.getClass().getSimpleName().equals("GALTIER")){
            initializeGALTIER(nodeCount);
        }

        //scheduler.schedule(new UpdatePosition(1000, mobilityID));

        // create GUI window
        if (gui){
            panelW = 500;
            panelH = 530;
            f = new JFrame();
            f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            guiWindow = new WSNWindow(this);
            f.getContentPane().add(guiWindow);
            f.setSize(panelW,panelH);
            f.setLocation(200,200);
            f.setVisible(true);
        }
    }
    /**********************************************************************/

    public static double getWidth() {
        return width;
    }

    public static double getHeight() {
        return height;
    }

    public static double getMaxRadius() {
        return maxRadius;
    }

    public static int getTopologyID() {
        return topologyID;
    }

    private void setMaxRadius(double radius) {
        maxRadius = radius;
    }

    private double[] nodePosition()
    {
        RNG r = RNG.getInstance();
        double[] coord = new double[2];

        double a, theta;
        setMaxRadius(0.5 * Math.min(width, height));

        switch (topologyID) {

            // circular cell
            case 0:
                a = maxRadius * Math.sqrt(r.nextDouble());
                theta = 2 * Math.PI * r.nextDouble();
                coord[0] = a * Math.cos(theta);
                coord[1] = a * Math.sin(theta);
                break;

            // hexagonal cell
            case 1:
                double c = Math.cos(Math.PI / 6);

                do {
                    a = Math.sqrt(r.nextDouble());
                    theta = -Math.PI / 6 + Math.PI / 3 * r.nextDouble();
                } while ((a * Math.cos(theta)) > c);

                a = a * maxRadius;
                theta = theta + Math.PI / 3 * r.nextInt(6);

                coord[0] = a * Math.cos(theta);
                coord[1] = a * Math.sin(theta);
                break;
            default:
                coord[0] = width * r.nextDouble();
                coord[1] = height * r.nextDouble();
                break;
        }

        return coord;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public double[] getNetworkSize() {
        return new double[]{width, height};
    }

    public int nodeCount() {
        return WSN.nodes.size();
    }

    public void setAnimationDelay(int ms) {
        this.sleepDelay = ms;
    }

    public static void setFrameSize(int bytes){
        frameSize = bytes;
        tACK = (double) Math.round((14 * 8)
                / (1) * 100) / 100 + tPLC;
        txTime =  (double) Math.round((frameSize * 8)
                / (maxAvailableThroughput) * 100) / 100 + tPLC;
    }

    // fairness window size
    public void setWindowSize(int size){
        this.windowSize = size;
    }

    public void debugging(boolean enable){
        debug = enable;
    }

    public void run(){
        this.run(Double.POSITIVE_INFINITY);
    }

    public void run(double maxTime) {

        setNeighborsList();

        Scheduler scheduler = Scheduler.getInstance();
        double currentTime = 0;
        long startTime = System.currentTimeMillis();
        while ((!scheduler.isEmpty()) && (currentTime < maxTime)) {

            //System.in.read();
            if (sleepDelay > 0){
                try {
                    Thread.sleep(sleepDelay);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }

            Event e = scheduler.remove();
            currentTime = e.getTime();

            if (debug){
                System.out.println(e);
            }

            e.run();

            //System.out.format("Progress: %.2f %%\n", ((currentTime/maxTime)*100.0));


            if (debug){
                System.out.println("\n");
            }

            // repaint GUI panel
            if (gui){
                guiWindow.paint();
            }
        }

        simulationTime = currentTime;

 //       WSN.printCollisionRate();
 //       WSN.printContentionSlot2();


        System.out.println("\n");
        System.out.println("Collision rate [%]: "+WSN.collisionRate());
        System.out.println("Alternate Collision rate [%]: "+WSN.alternateCollisionRate());
        System.out.println("Number of contention slot: "+WSN.contentionSlot());
        System.out.println("Throughput: "+WSN.throughput(currentTime));
        System.out.println("Delay [us]: "+WSN.delay());
        System.out.println("Normalized fairnessSIZE (only one 5000 trace): "+WSN.fairnessSIZE_2(windowSize));
        System.out.println("Normalized fairnessSIZE (many 5000 traces): "+WSN.fairnessSIZE_3(windowSize));
        System.out.println("Normalized fairnessTIME : "+WSN.fairnessTIME());
        System.out.println("No neighbors [%]: "+WSN.noNeighbors());

        long endTime   = System.currentTimeMillis();
        runningTime = (endTime-startTime)/1000;

    }

    public static void setNeighborsList(){

        for (Node nodeA : WSN.nodes) {
            for (Node nodeB : WSN.nodes) {
                if (nodeB.getId() != nodeA.getId()) {
                    Channel channel = new Channel(nodeA, nodeB, Ptx, indoor);

                    double Prx = channel.getPrx();
                    //System.out.println(Prx);

                    //System.out.println("Node "+nodeB.getId()+" has neighbor Node "+nodeA.getId()+" ? "+(nodeB.findNeighbor(nodeA)));

                    if (Prx >= PrxThreshold && !(nodeB.findNeighbor(nodeA))) {
                        //System.out.println(nodeA.getId() + "\t"+nodeB.getId());
                        nodeA.addNeighbor(nodeB);
                        nodeB.addNeighbor(nodeA);
                    }
                }
            }
        }
    }

    public static void printNeighbors() {

        for(Node node :WSN.nodes){
            ArrayList<Node> neighborsList = node.getNeighborList();
            System.out.print("\n \nNode " + node.getId() + " neighbors list:\t");
            for (Node entry : neighborsList) {
                System.out.print(entry.getId() + "\t");
            }
        }
        System.out.println("\n");
    }

    public static LinkedList<Node> getNeighborsStatus(Node n, NODE_STATUS status ){

        LinkedList<Node> list = n.neighborStatus;
        list.clear();

        for (Node neighbor : n.getNeighborList()){
            if (neighbor.getStatus() == status){ list.add(neighbor); }
        }
        if(WSN.debug){ System.out.println(status+": "+list.size()); }

        return list;
    }



    // output parameters

    public static void printCollisionRate(){

        int transmissions =0 ;
        int collisions = 0;
        System.out.println("\n[ DCF / CONTI ] ");
        System.out.println(" Node ||  Coll/Transm  ||  Normalized Collision Rate ");

        for (Node node : WSN.nodes) {
            collisions += node.getCollisionParam()[0];
            transmissions += node.getCollisionParam()[1];
            System.out.println(node.getId() + "\t\t\t" + node.getCollisionParam()[0] + " / " + node.getCollisionParam()[1] + "\t\t\t\t"+ ((double)node.getCollisionParam()[0])/((double)node.getCollisionParam()[1]));

        }
        double collPerc = (double) collisions / (double) transmissions * 100;
        System.out.println("\n Average Collision Rate = " + Math.round(collPerc * 100.0)/100.0 + " [%]");
    }

    public static double alternateCollisionRate(){

        double rate;
        if (p.getClass().getSimpleName().equals("DCF")){
            rate = (double) collisions/access;
        }else{
            double coll = collided.size();
            double att = attempted.size();
            rate = coll/att;
        }

        return rate*100;
    }

    public static double collisionRate(){
        int transmissions = 0;
        int collisions = 0;
        for (Node node : WSN.nodes) {
            collisions += node.getCollisionParam()[0];
            transmissions += node.getCollisionParam()[1];
        }
        double collPerc = (double) collisions / (double) transmissions * 100;
        return  Math.round(collPerc * 100.0)/100.0;
    }




    public static double contentionSlot(){
        // calculate the average number of contention slot (# of transmission slots that a node spends in a contention).
        // new method
        ArrayList<Integer> list;
        ArrayList<Integer> slotCounterList = new ArrayList<Integer>();
        double numb = WSN.nodes.size();
        for (Node node : WSN.nodes) {
            list = node.getSlotCounterList();
            slotCounterList.addAll(list);
        }
        double allAverageSlotCounter = calculateAverage(slotCounterList);
        return allAverageSlotCounter;
    }



    public static double throughput2() {
        // [OLD] used in presence of multiple parallels channels

        // [(total successfully transmitted packets * frameSize) / total simulation time  ]* maxAvailableThroughput
        double avThroughput = 0;
        double numb = WSN.nodes.size();

        for (Node node : WSN.nodes) {
            int collisions = node.getCollisionParam()[0];
            int transmissions =  node.getCollisionParam()[1];
            ArrayList<Double> delayList = node.getDelayList();

            double totalTime = 0;
            for (double delay : delayList){ totalTime = Math.floor((totalTime + delay)*100)/100; }
            avThroughput += ((((double)(transmissions - collisions)) * (double) (frameSize * 8)) / totalTime ) / numb;
        }
        double normThroughput = avThroughput / maxAvailableThroughput;
        return normThroughput;
    }


    public static double throughput(double currentTime) {
        // [(total successfully transmitted packets * frameSize) / total simulation time  ]* maxAvailableThroughput
        int collisions = 0;
        int transmissions = 0;

        for (Node node : WSN.nodes) {
            collisions += node.getCollisionParam()[0];
            transmissions +=  node.getCollisionParam()[1];
        }
        double avThroughput = (((double)(transmissions - collisions)) * (double) (frameSize * 8)) / currentTime;
        double normThr = avThroughput / maxAvailableThroughput;
        return normThr;
    }



    public static double delay(){
        // (average) time passed between the contention beginning and the successful transmission
        ArrayList<Double> delayList;
        double allAvDelay = 0;
        double numb = WSN.nodes.size();

        for (Node node : WSN.nodes) {
            delayList = node.getDelayList();
            double avDelay = calculateAverageDouble(delayList);
            allAvDelay +=  avDelay / numb;
        }
        // delay in us
        return allAvDelay;
    }



    private static double fairnessSIZE_2(int windowSize){
        // fairness calculation with Jain's fairness index and sliding windows (like into the 2011 paper)
        // [ it works with WINDOW SIZE ]
        // [ IT DEALS ONLY WITH THE FIRST 5000 ELEMENTS OF THE TRACE ]
        // -- -- -- --
        boolean debugFairness = false;      // if true more useful information are displayed
        // -- -- -- --
        double[] windowResults = new double[nodes.size()];
        List<Boolean>  tempWindow = new ArrayList<Boolean>();
        double[] windowsFairness =  new double[0];
        ArrayList<Node> tempTrace = new ArrayList<Node>();

        for (int i = 0; i<5000; i++){
            tempTrace.add(nodeTrace.get(i));
        }

        try {
            windowsFairness = new double[tempTrace.size() - windowSize + 1];
        }
        catch (Exception e){
            System.out.println("\n"+ e + "\nFairness Error!! More simulation time is needed with windowSize = " + windowSize + "\nSystem exit... ");
            System.exit(1);
        }
        if(debugFairness){ System.out.println("\n \n Node Trace "); }

        // initialize an iterator to scan the nodeLog list of the node
        for (Node node : WSN.nodes) { node.setListIterator(); }

        int succCount = 0;
        for (int i=0; i<tempTrace.size(); i++  ) {
            if(debugFairness){  System.out.println("\n[i= " + i+"]"); }

            Node node = tempTrace.get(i);
            boolean res = node.getLog();
            int id = node.getId();
            if(debugFairness){ System.out.println("Node "+id + "\ttransmission result: " + res);}
            tempWindow.add(res);

            if (res) {
                windowResults[id] ++;
                succCount ++;
                if(debugFairness){ System.out.println("tempResult  " + windowResults[id]); }
            }
            if (((i + 1) == windowSize) || ((i + 1) > windowSize)){
                double num = 0;
                double den = 0;
                for (double entry : windowResults) {
                    num += entry / (double) succCount;
                    den += Math.pow(entry/ (double) succCount, 2);
                }
                windowsFairness[i+1 - windowSize] = Math.pow(num, 2) / (den * nodes.size());

                if(debugFairness){ System.out.println("Fairness of window " + (i+1 - windowSize)+":\t" + windowsFairness[(i + 1) - windowSize]); }

                Node headNode = tempTrace.get(i+1 - windowSize);
                int headId = headNode.getId();
                if (tempWindow.remove(0)) {
                    windowResults[headId] --;
                    succCount --;
                }
                if(debugFairness){  System.out.println(tempWindow); }
            }
        }
        double sum = 0;
        for (double entry : windowsFairness) {
            sum += entry;
            if(debugFairness){  System.out.println(entry); }
        }
        //System.out.println("\n[ DCF / CONTI ] ");
        //System.out.println(" Average Fairness with one trace [trace size: "+tempTrace.size()+"]: "+sum/(windowsFairness.length)+"\n");

        return sum / (double) windowsFairness.length;
    }


    private static double fairnessSIZE_3(int windowSize){
        // fairness calculation with Jain's fairness index and sliding windows (like into the 2011 paper)
        // [ it works with WINDOW SIZE ]
        // [ IT WORKS WITH SLOTS OF 5000 TRACE ELEMENTS, THEN IT AVERAGES ALL THE SLOTS OUTPUT  ]
        // -- -- -- --
        boolean debugFairness = false;      // if true more useful information are displayed
        // -- -- -- --
        double[] windowResults = new double[nodes.size()];
        List<Boolean>  tempWindow = new ArrayList<Boolean>();
        double[] windowsFairness =  new double[0];
        int fixedTraceSize = 5000;
        ArrayList<Double>  allStepFairness = new ArrayList<>();

        if (debugFairness) { System.out.println("\n \n Node Trace "); }

        // initialize an iterator to scan the nodeLog list of the node
        for (Node node : WSN.nodes) { node.setListIterator(); }

        if (nodeTrace.size() - windowSize + 1 < 0 ){
            System.out.println("\nFairness Error!! More simulation time is needed with windowSize = " + windowSize + "\nSystem exit... ");
            System.exit(1);
        }

        for (int index = 0; (index + fixedTraceSize) < WSN.nodeTrace.size(); index += fixedTraceSize) {

            windowsFairness = new double[fixedTraceSize - windowSize + 1];
            windowResults = new double[nodes.size()];
            tempWindow.clear();
            int succCount = 0;

            for (int i = index; i < (index + fixedTraceSize); i++) {

                Node node = WSN.nodeTrace.get(i);
                boolean res = node.getLog();
                int id = node.getId();
                if (debugFairness) { System.out.println("Node " + id + "\ttransmission result: " + res); }
                tempWindow.add(res);

                if (res) {
                    windowResults[id]++;
                    succCount++;
                    if (debugFairness) { System.out.println("tempResult  " + windowResults[id]); }
                }
                if (((i - index  + 1) == windowSize) || ((i - index  + 1) > windowSize)) {
                    double num = 0;
                    double den = 0;
                    for (double entry : windowResults) {
                        num += entry / (double) succCount;
                        den += Math.pow(entry / (double) succCount, 2);
                    }
                    windowsFairness[i - index + 1 - windowSize] = Math.pow(num, 2) / (den * nodes.size());

                    if (debugFairness) {
                        System.out.println("Fairness of window " + (i + 1 - windowSize) + ":\t" + windowsFairness[(i + 1) - windowSize]);
                    }

                    Node headNode = WSN.nodeTrace.get(i + 1 - windowSize);
                    int headId = headNode.getId();
                    if (tempWindow.remove(0)) {
                        windowResults[headId]--;
                        succCount--;
                    }
                    if (debugFairness) { System.out.println(tempWindow); }
                }
            }
            double sum = 0;
            for (double entry : windowsFairness) {
                sum += entry;
                if (debugFairness) {
                    System.out.println(entry);
                }
            }
            allStepFairness.add(sum / (double) (windowsFairness.length));
        }

        double sum = 0;
        for (double entry : allStepFairness) {
            sum += entry;
        }
        // System.out.println("\n[ DCF / CONTI ] ");
        // System.out.println(" Average Fairness with many traces [trace size: " + fixedTraceSize + "] : " + sum / (allStepFairness.size()) + "\n");
        return sum / (double) allStepFairness.size();
    }

    private static double fairnessTIME(){
        // fairness calculation with Jain's fairness index and sliding windows (like into the 2011 paper)
        // [ it works with a TIME WINDOW ]
        double windowDuration=0;


        String protocol = WSN.p.getClass().getSimpleName();
        switch (protocol){
            case "DCF":
                windowDuration = 2;
                break;
            case "CONTI":
                windowDuration = 0.5;
                break;
            case "GALTIER":
                windowDuration = 0.5;
                break;
            default:
                System.out.println("Error with protocol name!");
                System.exit(1);
        }

        for (Node node : WSN.nodes) { node.setListIterator(); }

        ArrayList<Double>  allWindowFairness = new ArrayList<>();
        double time;
        double marker =0;
        int j = 0;
        for (int i = 0; i<nodeTrace.size(); i++){
            time = WSN.nodeTraceTimes.get(i);
            if ( (time - marker) >= (windowDuration * 1e6)){
                double fairness = WSN.calcWindowFairness(j,i);
                allWindowFairness.add(fairness);
                marker = time;
                j = i+1;
                //break;        //enable if we want a computation only on the first 2 seconds of simulation time
            }
        }

        double sum = 0;
        for (double entry : allWindowFairness) {
            sum += entry;
        }
        double averageFairness = sum / (double) allWindowFairness.size();
        //System.out.println("\n[ DCF / CONTI ] ");
        //System.out.println(" Average Fairness [window time length = "+windowDuration+"s]: "+averageFairness+"\n");

        return averageFairness;
    }


    private static double calcWindowFairness(int start, int end){
        // fairness calculator, it works into the range [start, end] of nodeTrace
        double[] windowResults = new double[nodes.size()];

        int succCount = 0;
        for (int i=start; i<(end+1); i++ ) {

            Node node = WSN.nodeTrace.get(i);
            boolean res = node.getLog();
            int id = node.getId();

            if (res) {
                windowResults[id] ++;
                succCount ++;
            }
        }

        double num = 0;
        double den = 0;
        for (double entry : windowResults) {
            num += entry /(double) succCount;
            den += Math.pow(entry /(double) succCount, 2);
        }
        double windowFairness = Math.pow(num, 2) / (den * nodes.size());

        return windowFairness;
    }


    private static double noNeighbors(){
        // percentage of no neighbors events over the total transmission attempts
        int transmissions = 0;
        int noNeighborEvents = 0;
        for (Node node : WSN.nodes) {
            transmissions += node.getCollisionParam()[1];
            noNeighborEvents += node.getNoNeighbor();
        }
        double avNoNeighbors = (double) (noNeighborEvents * 100) /(double)  (transmissions + noNeighborEvents);
        return avNoNeighbors;
    }

    private static ArrayList<Double> getDelayList (){
        // concatenate all the delays used in the simulation
        ArrayList<Double> delayList = new ArrayList<>();
        for (Node entry : WSN.nodes){
            delayList.addAll(entry.getDelayList());
        }
        return delayList;
    }


    private static double calculateAverage(List <Integer> list) {
        Integer sum = 0;
        if(!list.isEmpty()) {
            for (Integer entry : list) {
                sum += entry;
            }
            return (((double) sum )/( (double) list.size()));
        }
        return sum;
    }

    private static double calculateAverageDouble(List <Double> list) {
        double sum = 0;
        if(!list.isEmpty()) {
            for (double entry : list) {
                sum += entry;
            }
            return sum / list.size();
        }
        return sum;
    }

    public static void saveToFile(String filename) throws IOException{

        File f = new File(filename);
        String protocol = WSN.p.getClass().getSimpleName();

        // automatically checks if file exists or not.
        // if the file does not exist, it automatically creates it
        f.getParentFile().mkdirs();
        boolean created = f.createNewFile();
        boolean printColumns = created;

        // if the file aleady existed, check wheter it's empty
        if (!created){
            // determine if file is empty. if it is save column names
            BufferedReader br = new BufferedReader(new FileReader(filename));
            printColumns = br.readLine() == null;
            br.close();
        }

        // open file
        FileWriter fw = new FileWriter(filename, true);

        // print column names
        if (printColumns){
            fw.append(String.format("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s", "protocol", "running-time", "nodecount", "simulation-time", "framesize", "width",
                    "height", "tx-time", "collision-rate", "alternate-rate", "throughput", "delay", "contention-slots", "fairness-window-size", "fairness-size", "fairness-time"));
        }

        // save simulation parameters and results
        int seconds = new Double(simulationTime/1e6).intValue();
        fw.append(String.format("\n" +
                        "%s;" +
                        "%d;%d;%d;%d;" +
                        "%.2f;%.2f;" +
                        "%.2f;" +
                        "%.3f;%.3f;" +
                        "%.3f;%.3f;" +
                        "%.2f;" +
                        "%d;%.3f;%.3f",

                protocol,
                runningTime, nodes.size(), seconds , frameSize,
                width, height,
                txTime,
                WSN.collisionRate(), WSN.alternateCollisionRate(),
                WSN.throughput(simulationTime), WSN.delay()/1000,
                WSN.contentionSlot(),
                WSN.windowSize, WSN.fairnessSIZE_3(WSN.windowSize), WSN.fairnessTIME()));

        // close file
        fw.close();

        // export delay list

        filename = String.format("./results/delayList--"+"%s-"+"%d-"+ "%d"+".csv", protocol, nodes.size(), frameSize );

        f = new File(filename);

        // automatically checks if file exists or not.
        // if the file does not exist, it automatically creates it
        f.getParentFile().mkdirs();
        created = f.createNewFile();
        printColumns = created;

        fw = new FileWriter(filename, false);
        for (double entry : WSN.getDelayList()){ fw.append(String.format("%.3f\n", entry)); }

        fw.close();

    }

    public static void initializeCONTI(int nodeCount) throws IOException {
        String filename;
        String s = System.getProperty("scheme");
        switch (s){
            case "paper":
                // p that optmizes throughput
                filename = "data/CONTI/CONTI-optimal.dat";
                break;

            case "optimal":
                // select optimum p for the given number of stations
                filename = String.format("data/CONTI/CONTI-%d.dat", nodeCount);
                break;

            case "generic":
                filename = String.format("data/CONTI/CONTI-100.dat", nodeCount);
                break;

            default:
                filename = String.format("data/CONTI/CONTI-100.dat", nodeCount);
                break;
        }

        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(filename));
            String line = br.readLine();
            Arrays.asList(line.split(";"))
                    .forEach(p -> CONTIp.add(Double.parseDouble(p)));

            br.close();
        }catch (Exception e){
            System.out.println("Error: can't open parameter file for CONTI.");
            System.exit(1);
        }
        probVectSize = CONTIp.size();
    }

    public static void initializeGALTIER(int nodeCount) throws IOException {

        String filepath;
        String s = System.getProperty("scheme");
        switch (s){
            case "paper":
                filepath = "data/galtier/galtier-paper.dat";
                break;

            case "optimal":
                filepath = String.format("data/galtier/galtier-%d.dat", nodeCount);
                break;

            case "7slots":
                filepath = String.format("data/galtier/galtier-100-7.dat", nodeCount);
                break;

            default:
            filepath = "/data/galtier/galtier-paper.dat";
            break;
        }

        try {
            BufferedReader br = new BufferedReader(new FileReader(filepath));
            String line;
            while ( (line = br.readLine()) != null){
                List<Double> doubleData = new ArrayList<>();
                Arrays.asList(line.split(";"))
                        .forEach(p -> doubleData.add(Double.parseDouble(p)));
                galtierP.add(doubleData);
            }
            br.close();
        }catch (Exception e){
            System.out.println("Error: can't open parameter file for GALTIER.");
            System.exit(1);
        }

        probVectSize = galtierP.size();

    }

    /*****************************
     *          GUI              *
     *****************************/
    class WSNWindow extends JPanel{

        Color selectedColor;

        private WSN network;

        private WSNWindow(WSN network){
            setBackground(Color.white);
            selectedColor = Color.blue;
            this.network = network;
        }

        private void paint(){
            repaint();
        }

        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // find the panel dimension
            double panelH = getHeight();
            double panelW = getWidth();

            double[] networkSize = network.getNetworkSize();
            double netW = networkSize[0];
            double netH = networkSize[1];

            // scaling factors only to draw the nodes
            double scaleX = 0.9 * panelW/netW;
            double scaleY = 0.9 * panelH/netH;

            double nodeX, nodeY, nodeSize;
            for (int i = 0; i < network.nodeCount(); i++) {

                Node n = network.getNodes().get(i);

                nodeX = panelW/2 + n.getX() * scaleX;
                nodeY = panelH/2 + n.getY() * scaleY;
                nodeSize = n.getSize();

                try {
                    for (Node neigh : n.getNeighborList()) {
                        g2.setColor(n.getLineColor());

                        double neighX = panelW / 2 + neigh.getX() * scaleX;
                        double neighY = panelH / 2 + neigh.getY() * scaleY;
                        g2.draw(new Line2D.Double(nodeX, nodeY, neighX, neighY));
                    }
                }catch (ConcurrentModificationException exc){

                }

                Ellipse2D e = new Ellipse2D.Double(nodeX-nodeSize/2,nodeY-nodeSize/2,nodeSize,nodeSize);
                g2.setPaint(n.getColor());
                g2.fill(e);

                if(mobilityID == 1) { // draw mobility range in case of Gauss-Markov model
                    double X0 = panelW / 2 + n.X0 * scaleX;
                    double Y0 = panelH / 2 + n.Y0 * scaleY;
                    double range = WSN.getMaxRadius() / 4;

                    e = new Ellipse2D.Double(X0 - range / 2, Y0 - range / 2, range, range);
                    g2.setPaint(Color.red);
                    g2.draw(e);
                }

                Font font = new Font("Serif", Font.PLAIN, 18);
                g2.setFont(font);
                g2.setColor(Color.black);
                g2.drawString(String.valueOf(n.getId()), (int) nodeX, ((int) nodeY)-3);
            }

            g2.setPaint(Color.black);

            switch (topologyID){
                // circular cell
                case 0:
                    g2.draw(new Ellipse2D.Double(0.05 * panelW,0.05 * panelH,0.9 * panelW, 0.9 * panelH));
                    break;

                // hexagonal cell
                case 1:
                    Path2D hexagon = new Path2D.Double();
                    Point2D center = new Point2D.Double(panelW/2, panelH/2);
                    double r = 0.48 * Math.min(panelH, panelW);

                    // initial point
                    hexagon.moveTo(center.getX() + r * Math.cos(Math.PI/6), center.getY() + r * Math.sin(Math.PI/6));

                    for(int i=1; i<6; i++) {
                        hexagon.lineTo(center.getX() + r * Math.cos((2*i+1)*Math.PI/6), center.getY() + r * Math.sin((2*i+1)*Math.PI/6));
                    }
                    hexagon.closePath();

                    g2.draw(hexagon);
                    break;

                default:
                    g2.setPaint(Color.black);
                    g2.draw(new Rectangle2D.Double(1, 1,panelW - 2 , panelH - 3));
                    break;
            }
        }
    }

}


