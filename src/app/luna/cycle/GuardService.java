package app.luna.cycle;

import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.widget.Toast;

public final class GuardService extends Service {
    static volatile GuardService live;
    private String active;
    private SessionMonitor monitor;
    private long cursor, beganWall;
    private final java.util.Set<String> consumed=new java.util.HashSet<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final BroadcastReceiver screen=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) { lock(c); }
    };
    static boolean allowed(Context c) {
        AppOpsManager ops=c.getSystemService(AppOpsManager.class);
        int mode=ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),c.getPackageName());
        return mode==AppOpsManager.MODE_ALLOWED || (mode==AppOpsManager.MODE_DEFAULT && c.checkSelfPermission("android.permission.PACKAGE_USAGE_STATS")==android.content.pm.PackageManager.PERMISSION_GRANTED);
    }
    @Override public void onCreate() {
        super.onCreate();live=this;
        Profile.seal(this);
        registerReceiver(screen,new IntentFilter(Intent.ACTION_SCREEN_OFF));
        NotificationManager nm=getSystemService(NotificationManager.class);
        NotificationChannel channel=new NotificationChannel("luna-session","Луна — локальный сеанс",NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null,null);channel.enableVibration(false);channel.setShowBadge(false);nm.createNotificationChannel(channel);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,GuardService.class).setAction("lock"),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"luna-session").setSmallIcon(R.drawable.ic_luna).setContentTitle("Луна").setContentText("Локальный сеанс активен").setOngoing(true).setVisibility(Notification.VISIBILITY_SECRET).addAction(new Notification.Action.Builder(null,"Завершить",stop).build()).build();
        if(android.os.Build.VERSION.SDK_INT>=34)startForeground(7,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7,notification);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) {
        if(intent==null || "lock".equals(intent.getAction()) || !Profile.owner(this) || !allowed(this)) {lock(this);return START_NOT_STICKY;}
        active=intent.getStringExtra("package");monitor=new SessionMonitor(active,SystemClock.elapsedRealtime());beganWall=System.currentTimeMillis();cursor=beganWall;consumed.clear();
        try {Profile.openNow(this,active);handler.post(tick);}catch(RuntimeException ex){lock(this);Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();}
        return START_NOT_STICKY;
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(active==null)return;
        if(!allowed(GuardService.this) || !getSystemService(PowerManager.class).isInteractive() || getSystemService(KeyguardManager.class).isKeyguardLocked()){lock(GuardService.this);return;}
        long now=System.currentTimeMillis();
        UsageEvents events=getSystemService(UsageStatsManager.class).queryEvents(Math.max(beganWall,cursor-1000),now);
        if(events==null){lock(GuardService.this);return;}
        UsageEvents.Event event=new UsageEvents.Event();
        while(events.hasNextEvent()){
            events.getNextEvent(event);
            String key=event.getTimeStamp()+":"+event.getEventType()+":"+event.getPackageName()+":"+event.getClassName();
            if(!consumed.add(key))continue;
            cursor=Math.max(cursor,event.getTimeStamp());
            if(event.getEventType()==UsageEvents.Event.ACTIVITY_RESUMED)monitor.resumed(event.getPackageName(),event.getClassName(),SystemClock.elapsedRealtime());
            else if(event.getEventType()==UsageEvents.Event.ACTIVITY_PAUSED || event.getEventType()==UsageEvents.Event.ACTIVITY_STOPPED)monitor.paused(event.getPackageName(),event.getClassName(),SystemClock.elapsedRealtime());
        }
        if(monitor.mustLock(SystemClock.elapsedRealtime())){lock(GuardService.this);return;}
        if(consumed.size()>2048){long cutoff=cursor-1000;consumed.removeIf(key->Long.parseLong(key.substring(0,key.indexOf(':')))<cutoff);}
        handler.postDelayed(this,250);
    }};
    static void lock(Context c) {
        if(live!=null){live.active=null;live.handler.removeCallbacksAndMessages(null);live.stopForeground(STOP_FOREGROUND_REMOVE);live.stopSelf();}
        try{java.util.List<String> failures=Profile.seal(c);if(!failures.isEmpty())Toast.makeText(c,"Не все приложения скрыты. Проверьте профиль в Луне.",Toast.LENGTH_LONG).show();}
        catch(RuntimeException ex){Toast.makeText(c,"Не удалось заблокировать профиль. Проверьте Луну.",Toast.LENGTH_LONG).show();}
    }
    @Override public void onTaskRemoved(Intent intent){lock(this);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);active=null;try{Profile.seal(this);}catch(RuntimeException ignored){}live=null;try{unregisterReceiver(screen);}catch(RuntimeException ignored){}super.onDestroy();}
}
