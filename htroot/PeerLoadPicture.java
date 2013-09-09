import java.awt.Color;
import java.awt.Image;
import java.util.HashMap;
import java.util.Iterator;

import net.yacy.cora.protocol.RequestHeader;
import net.yacy.kelondro.workflow.BusyThread;
import net.yacy.peers.graphics.NetworkGraph;
import net.yacy.peers.graphics.NetworkGraph.CircleThreadPiece;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class PeerLoadPicture {

    public static Image respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {

        int width = 800;
        int height = 600;
        boolean showidle = true;

        if (post != null) {
            width = post.getInt("width", 800);
            height = post.getInt("height", 600);
            showidle = post.getBoolean("showidle");
        }

        final CircleThreadPiece idle = new CircleThreadPiece("Idle", new Color(170, 255, 170));
        final CircleThreadPiece misc = new CircleThreadPiece("Misc.", new Color(190,  50, 180));
        final HashMap<String, CircleThreadPiece> pieces = new HashMap<String, CircleThreadPiece>();
        pieces.put(null, idle);
        pieces.put(SwitchboardConstants.INDEX_DIST, new CircleThreadPiece("DHT-Distribution", new Color(119, 136, 153)));
        pieces.put(SwitchboardConstants.PEER_PING,  new CircleThreadPiece("YaCy Core",        new Color(255, 230, 160)));

        final Iterator<String> threads = env.threadNames();
        String threadname;
        BusyThread thread;

        long busy_time = 0;

        //Iterate over threads
        while (threads.hasNext()) {
            threadname = threads.next();
            thread = env.getThread(threadname);

            //count total times
            busy_time += thread.getBlockTime();
            busy_time += thread.getExecTime();
            if (showidle) idle.addExecTime(thread.getSleepTime());

            //count threadgroup-specific times
            final CircleThreadPiece piece = pieces.get(threadname);
            if (piece == null) {
                misc.addExecTime(thread.getBlockTime()+thread.getExecTime());
            } else {
                piece.addExecTime(thread.getBlockTime()+thread.getExecTime());
            }
        }
        busy_time += idle.getExecTime();

        // set respective angles
        final Iterator<CircleThreadPiece> it = pieces.values().iterator();
        CircleThreadPiece current;
        while (it.hasNext()) {
            current = it.next();
            current.setFraction(busy_time);
            //remove unneccessary elements
            if(current.getAngle() == 0) it.remove();
        }
        misc.setFraction(busy_time);

        // too small values lead to an error, too big to huge CPU/memory consumption,
        // resulting in possible DOS.
        if (width < 40) width = 40;
        if (width > 1920) width = 1920;
        if (height < 30) height = 30;
        if (height > 1440) height = 1440;
        return NetworkGraph.getPeerLoadPicture(
                5000,
                width,
                height,
                pieces.values().toArray(new CircleThreadPiece[pieces.size()]),
                misc
        );
    }
}
