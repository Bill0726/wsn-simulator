package protocols.DCF;

import WSN.Node;
import WSN.WSN;
import WSN.Scheduler;
import events.Event;

import java.util.*;


/**
 * Created by gianluca on 28/07/17.
 */

public class CheckChannelStatus extends Event {

    private double duration;

    public CheckChannelStatus(Node n, double time, double duration){
        super(n, time, WSN.listenColor);
        this.duration = duration;
    }

    public void run(){
        super.run();

        Scheduler scheduler = Scheduler.getInstance();

        // if both the sender node (this) and the destination node sense the channel free new events are scheduled

        // if (n.freeChannel && (n.getNextPacket().getDestination().freeChannel)){
        if (n.freeChannel && (!checkDestNeighbors(scheduler))){

            if (WSN.debug){ System.out.println("Channel has been free for: " + duration);}
            if (duration == WSN.tSlot){

                // decrease BO counter
                int bo = n.decreaseCounter();
                if (WSN.debug){ System.out.println("BO Counter decreased: " + bo);};

                if (bo > 0){
                    scheduler.schedule(new CheckChannelStatus(n,time + WSN.tSlot, WSN.tSlot));

                }else{
                    // transmit
                    if (WSN.debug){ System.out.println("-> This node (" + this.n.getId() + ") will now start transmitting.");};

                    // increment transmissions counter
                    n.addTransmission();
                    // keep track of the nodes that start a transmission (useful to Fairness calculation)
                    WSN.nodeTrace.add(this.n);
                    WSN.nodeTraceTimes.add(this.time);

                    scheduler.schedule(new StartTxEvent(n, time));
                }
                // increment contention slot counter
                this.n.addContSlot();
            }
            else if (duration == WSN.DIFS){

                // restart BO counter
                if (n.getBOcounter() == 0){
                    if (WSN.debug){ System.out.println("-> This node (" + this.n.getId() + ") will now start transmitting.");};
                    // transmit

                    // increment transmissions counter
                    n.addTransmission();
                    // keep track of the nodes that start a transmission (useful to Fairness calculation)
                    WSN.nodeTrace.add(this.n);
                    WSN.nodeTraceTimes.add(this.time);

                    scheduler.schedule(new StartTxEvent(n,  time));
                }else {

                    scheduler.schedule(new CheckChannelStatus(n, time + WSN.tSlot, WSN.tSlot));
                }

                // add a DIFS time to the packet transmission time
            }
        }
    }

    private boolean checkDestNeighbors(Scheduler scheduler){
        // handle the hidden terminal problem
        // check if the destination node is already the destination node of a neighbor of him (surely I have to exclude (this) node from the neighbors)
        boolean statement = false;
        Node dest = n.getNextPacket().getDestination();
        LinkedList<Node> destNeighbors = WSN.getNeighborsStatus(dest, WSN.NODE_STATUS.TRANSMITTING);

        if (!dest.freeChannel && this.n.freeChannel) {
            for (Node entry : destNeighbors) {
                if (entry.getNextPacket().getDestination().getId() == dest.getId() && (entry.getId() != this.n.getId())) {
                    statement = true;
                    if (WSN.debug) {System.out.println(" Channel busy! Destination Node "+dest.getId()+" is receiving from Node: "+entry.getId()); }
                    scheduler.schedule(new CheckChannelStatus(n, time + WSN.DIFS, WSN.DIFS));
                    if (duration == WSN.tSlot){ this.n.addContSlot(); }
                    break;
                }
            }
        }
        return statement;
    }

    @Override
    public String toString(){
        return "[" + time + "][CheckChannelStatus] from node " +  this.n;
    }
}
