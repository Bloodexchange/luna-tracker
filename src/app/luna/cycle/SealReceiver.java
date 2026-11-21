package app.luna.cycle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SealReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if(!Profile.owner(c)) return;
        if(Intent.ACTION_PACKAGE_REMOVED.equals(i.getAction())) {
            // Hiding also emits PACKAGE_REMOVED, but does not delete app data.
            if(i.getData()!=null && i.getBooleanExtra(Intent.EXTRA_DATA_REMOVED,false) && !i.getBooleanExtra(Intent.EXTRA_REPLACING,false)) new Store(c).forget(i.getData().getSchemeSpecificPart());
        } else if(Intent.ACTION_PACKAGE_ADDED.equals(i.getAction()) && i.getData()!=null && !i.getBooleanExtra(Intent.EXTRA_REPLACING,false)) {
            try { Profile.protect(c,i.getData().getSchemeSpecificPart()); } catch(RuntimeException ignored) { }
        } else { GuardService.lock(c); }
    }
}
