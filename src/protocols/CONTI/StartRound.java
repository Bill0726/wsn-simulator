package protocols.CONTI;

import protocols.Event;
import WSN.Scheduler;
import WSN.Node;
import WSN.WSN;
import java.util.*;


/**
 * Created by Gianluca on 23/08/2017.
 */
public class StartRound extends Event {

    public StartRound(Node n, double time){ super(n, time, WSN.normColor); }

    public void run(){

        super.run();
        n.CONTIslotNumber = 0;
        Scheduler scheduler = Scheduler.getInstance();
        scheduler.schedule(new StartContentionSlot(n, time));
    }

    @Override
    public String toString(){
        return "[" + time + "][StartRound] from node " +  this.n;
    }
}
