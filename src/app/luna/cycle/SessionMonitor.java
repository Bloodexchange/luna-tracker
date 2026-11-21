package app.luna.cycle;

import java.util.HashSet;
import java.util.Set;

/** Pure state machine; no Android permissions or screen contents involved. */
final class SessionMonitor {
    private final String target;
    private final long started;
    private final Set<String> resumed=new HashSet<>();
    private boolean seen, exited;
    private long pausedAt=-1;
    SessionMonitor(String target,long now){this.target=target;started=now;}
    void resumed(String pkg,String instance,long now){
        if(target.equals(pkg)){seen=true;resumed.add(instance);pausedAt=-1;}
        else if(seen)exited=true;
    }
    void paused(String pkg,String instance,long now){
        if(target.equals(pkg)){
            resumed.remove(instance);
            if(seen && resumed.isEmpty() && pausedAt<0)pausedAt=now;
        }
    }
    boolean mustLock(long now){return exited || (!seen && now-started>=8000) || (seen && resumed.isEmpty() && pausedAt>=0 && now-pausedAt>=400);}
}
