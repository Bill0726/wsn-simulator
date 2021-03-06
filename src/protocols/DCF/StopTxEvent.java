package protocols.DCF;
import WSN.*;
import events.Event;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Random;

/**
 * Created by Gianluca on 16/07/2017.
 */
public class StopTxEvent extends Event {

    LinkedList<Node> listeningAtTX;

    public StopTxEvent(StartTxEvent e, double time, LinkedList<Node> listeningAtTX) {
        super(e.getNode(), time, WSN.normColor);
        this.listeningAtTX = listeningAtTX;
    }

    @Override
    public String toString() {
        return "[" + time + "][StopTxEvent] from node " + this.n;
    }

    public void run() {
        super.run();

        Scheduler scheduler = Scheduler.getInstance();

        this.n.setSize(WSN.normSize);
        n.setStatus(WSN.NODE_STATUS.IDLING);
        this.n.setLineColor(Color.lightGray);
        RNG r = RNG.getInstance();

        // add txTime to the total packet transmission time


        if (n.collided) {
            // unsuccessful transmission

            if (WSN.debug) {
                System.out.println("Transmission unsuccessful");
                System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            }

            if (WSN.transmitting.size() == 1){
                // i'm the last transmitting node
                WSN.collisions++;
                if (WSN.debug){
                    System.out.println("Contention round ended with COLLISION (" + (double) WSN.collisions/WSN.access + ")");
                }
            }

            n.collided = false;         // NB: check if it is useful or a problem !!
            // increment collision counter of this node
            this.n.addCollision();


            int oldCW = n.getCW();
            int newCW = Math.min(2 * (oldCW + 1) - 1, WSN.CWmax);

            n.setCW(newCW);

            n.setBOcounter(r.nextInt(n.getCW() + 1));

            // start new round NOW
            scheduler.schedule(new StartListeningEvent(n, time + WSN.DIFS));

            // At the end of the StopTxEvent new CheckChannelEvents must be rescheduled for all the listening nodes (that have stopped the BO during the startTXEvent). However
            //  if a collision occurs the whole rescheduling has to happen during the stopTXEvent associated to the last collided node, in order to avoid duplicated events and unwanted behaviors.

            if (n.collidedNodes.isEmpty()) {
                System.out.println(" Problem... can't be empty!");
                System.exit(1);
            }

            boolean lastEvent = true;
            for (Node entry : n.collidedNodes) {
                if ((entry.getStatus() == WSN.NODE_STATUS.TRANSMITTING) && checkCollided(entry)) {
                    lastEvent = false;
                }
            }

            if (!lastEvent) {
                // if this is not the StopTxEvent of the last node that has collided its listening nodes are saved to be resumed in the last StopTxEvent
                listeningAtTX = filterListeningAtTX(listeningAtTX);
                this.n.resumingNodes.addAll(listeningAtTX);
                for (Node entry : listeningAtTX) {
                    if (WSN.debug) {
                        System.out.println("To be resumed, Node: " + entry.getId());
                    }
                }
                n.collidedNodes.clear();
            } else {
                // this is the StopTXEvent of the last collided Node

                listeningAtTX = filterListeningAtTX(listeningAtTX);
                for (Node entry : listeningAtTX) {
                    if (WSN.debug) {
                        System.out.println("listeningAtTX " + entry.getId());
                    }
                }



                ArrayList<Node> collidedNodeSave = new ArrayList<Node>();
                collidedNodeSave.addAll(n.collidedNodes);

                while (!n.collidedNodes.isEmpty()) {

                    ArrayList<Node> resumingNodes = n.collidedNodes.remove(0).resumingNodes;

                    while (!resumingNodes.isEmpty()) {
                        Node oldListeningNode = resumingNodes.remove(0);
                        listeningAtTX.add(oldListeningNode);
                        if (WSN.debug) {
                            System.out.println("Old listening Node: " + oldListeningNode.getId());
                        }
                    }
                }

                collidedNodeSave.add(this.n);
                listeningAtTX = removeDuplicate(listeningAtTX);
                listeningAtTX = removeOldCollided(listeningAtTX, collidedNodeSave);

                if (WSN.debug) {
                    System.out.println("Final listening nodes to be resumed: ");
                    for (Node entry : listeningAtTX) {
                        System.out.println(" Node " + entry.getId());
                    }
                    if (listeningAtTX.isEmpty()) {System.out.println(" None");}
                }

                for (Node listening : listeningAtTX) {

                    // check if the rescheduled is already happened.
                    if (!listening.freeChannel && listening.getStatus() == WSN.NODE_STATUS.LISTENING) {
                        reschedule(listening, scheduler, WSN.DIFS);

                    }
                }

            }


        } else {
            // sucessfull transmission
            if (WSN.debug) { System.out.println("->Tranmission successful!  \nDestination: Node "+this.n.getNextPacket().getDestination().getId()); }

            n.setCW(WSN.CWmin);
            n.setBOcounter(r.nextInt(n.getCW() + 1));
            // remove the packet from the queue
            n.dequeue();

            // save the overall packet transmission time (useful to throughput and delay)
            this.n.setTotalTime(time);

            // start new round after SIFS + tACK
            scheduler.schedule(new StartListeningEvent(n, time + WSN.tACK + WSN.SIFS));


            // reschedule CheckChannelStatus events for nodes that were listening when transmission begun

            for (Node listening : listeningAtTX) {

                // check if the rescheduled is already happened.  If the transmission succeeds only the last Node that stops the BO counter for this node can resume it.
                if (!listening.freeChannel && (listening.lastBOstopped.getId() == this.n.getId())) {
                    reschedule(listening, scheduler, WSN.SIFS + WSN.tACK + WSN.DIFS);
                }
            }

        }

        WSN.transmitting.remove(n);
    }


    private LinkedList<Node> removeDuplicate(LinkedList<Node> list) {
        // merging the previous listening nodes and the current listening nodes (that both need rescheduling), it is necessary to remove possible duplicates
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                if (list.get(j).getId() == list.get(i).getId()) {
                    if (WSN.debug) {
                        System.out.println("Removed from the list because duplicated, Node " + list.get(j).getId());
                    }
                    list.remove(j);
                }
            }
        }
        return list;
    }

    private LinkedList<Node> removeOldCollided(LinkedList<Node> list, ArrayList<Node> collidedNodes) {
        // remove from the whole listening nodes possible collided nodes for which it has already been scheduled a CheckChannelEvent.

        for (int i = 0; i < list.size(); i++) {
            for (int j = 0; j < collidedNodes.size(); j++) {
                if (list.get(i).getId() == collidedNodes.get(j).getId() || list.get(i).getId() == this.n.getId()) {
                    if (WSN.debug) {
                        System.out.println("Removed from the list because collided, Node " + list.get(i).getId());
                    }
                    list.remove(i);
                    break;
                }
            }
        }

        return list;

    }


    private void reschedule(Node node, Scheduler scheduler, double timeshift) {
        // schedule CheckChannelStatus event for the specified node
        scheduler.schedule(new CheckChannelStatus(node, time + timeshift, WSN.DIFS));
        node.freeChannel = true;

        if (WSN.debug) {
            System.out.println("->CheckChStatus rescheduled for Node " + node.getId());
        }
    }

    private LinkedList<Node> filterListeningAtTX (LinkedList<Node> listeningAtTX ){

        LinkedList<Node> newListeningAtTX= new LinkedList<Node>();
        for (Node entry : listeningAtTX){
            if (entry.lastBOstopped.getId() == this.n.getId()){
                newListeningAtTX.add(entry);
            }
        }
        return newListeningAtTX;
    }

    private boolean checkCollided(Node collided) {
        // check if the other collision node still have this node in the collided node, because due to channel update can happen
        //  that the other collision node has started again a transmission and the remaining listening nodes will never be resumed
        ArrayList<Node> otherCollidedNodes = collided.collidedNodes;
        boolean found = false;
        for (Node entry : otherCollidedNodes) {
            if (entry.getId() == this.n.getId()){
                found = true;
            }
        }
        return found;
    }
}

