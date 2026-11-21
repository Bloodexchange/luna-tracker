package app.luna.cycle;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

public final class ProfileAdmin extends DeviceAdminReceiver {
    @Override public void onProfileProvisioningComplete(Context context, Intent intent) { Profile.complete(context,intent); }
}
