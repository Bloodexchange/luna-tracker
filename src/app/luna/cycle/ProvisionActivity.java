package app.luna.cycle;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.os.Bundle;

public final class ProvisionActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (DevicePolicyManager.ACTION_GET_PROVISIONING_MODE.equals(getIntent().getAction())) {
            Intent result=new Intent();
            result.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE);
            setResult(RESULT_OK,result);
        } else { Profile.complete(this,getIntent()); setResult(RESULT_OK); }
        finish();
    }
}
