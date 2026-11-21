package app.luna.cycle;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.PersistableBundle;
import java.util.ArrayList;
import java.util.List;

final class Profile {
    static ComponentName admin(Context c) { return new ComponentName(c, ProfileAdmin.class); }
    static DevicePolicyManager policy(Context c) { return c.getSystemService(DevicePolicyManager.class); }
    static boolean owner(Context c) { return policy(c).isProfileOwnerApp(c.getPackageName()); }
    static List<String> seal(Context c) {
        List<String> failures = new ArrayList<>();
        if (!owner(c)) return failures;
        for (String pkg : new Store(c).protectedApps()) {
            try {
                if (!policy(c).setApplicationHidden(admin(c), pkg, true) || !policy(c).isApplicationHidden(admin(c), pkg)) failures.add(pkg);
            } catch (RuntimeException ex) { failures.add(pkg); }
        }
        // Only this app's tasks are owned here. Android manages other apps' recent tasks.
        return failures;
    }
    static void protect(Context c, String pkg) {
        if (!owner(c) || pkg.equals(c.getPackageName())) throw new IllegalStateException("Нужен отдельный профиль");
        try {
            ApplicationInfo info=c.getPackageManager().getApplicationInfo(pkg, android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES);
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) throw new IllegalStateException("Системные приложения не скрываются");
            if ((info.flags & ApplicationInfo.FLAG_INSTALLED) == 0) throw new IllegalStateException("Приложение не установлено в этом профиле");
        } catch (android.content.pm.PackageManager.NameNotFoundException ex) { throw new IllegalStateException("Приложение не найдено"); }
        new Store(c).protect(pkg);
        if (!policy(c).setApplicationHidden(admin(c),pkg,true) || !policy(c).isApplicationHidden(admin(c),pkg)) throw new IllegalStateException("Android не подтвердил скрытие");
    }
    static Intent provision(Context c) {
        DevicePolicyManager d=policy(c);
        if (!d.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)) throw new IllegalStateException("Телефон не разрешает новый рабочий профиль. Возможно, профиль уже создан или функция не поддерживается.");
        Intent i=new Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
        i.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,admin(c));
        PersistableBundle bundle=new PersistableBundle();
        bundle.putString("luna.setup",new Store(c).read().toString());
        i.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,bundle);
        return i;
    }
    static void complete(Context c, Intent intent) {
        if (!owner(c)) return;
        PersistableBundle extras=intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE);
        Store store=new Store(c);
        if(extras!=null && !store.read().has("normalHash")) {
            try { org.json.JSONObject d=new org.json.JSONObject(extras.getString("luna.setup","{}")); d.remove("apps"); d.remove("notes"); store.write(d); }
            catch (org.json.JSONException ex) { throw new IllegalStateException(ex); }
        }
        DevicePolicyManager d=policy(c);
        d.setProfileName(admin(c),"Луна");
        d.setProfileEnabled(admin(c));
        d.setScreenCaptureDisabled(admin(c),true);
        d.clearUserRestriction(admin(c),android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
        try { d.enableSystemApp(admin(c),"com.android.vending"); } catch (RuntimeException ignored) { }
        seal(c);
    }
    static void open(Context c,String pkg) {
        if (!owner(c)) throw new IllegalStateException("Откройте Луну со значком портфеля");
        if (!GuardService.allowed(c)) throw new IllegalStateException("Сначала разрешите доступ к статистике использования для Луны в рабочем профиле");
        if (Build.VERSION.SDK_INT < 33) throw new IllegalStateException("Для запуска APK с обязательным запретом уведомлений нужен Android 13+. На этой версии доступны веб-сервисы и хранение скрытых APK.");
        c.startForegroundService(new Intent(c,GuardService.class).putExtra("package",pkg));
    }
    static void openNow(Context c,String pkg) {
        if(pkg==null)throw new IllegalStateException("Приложение не выбрано");
        if (!new Store(c).protectedApps().contains(pkg)) throw new IllegalStateException("Приложение не защищено");
        if (!seal(c).isEmpty()) throw new IllegalStateException("Не удалось скрыть все приложения. Проверьте профиль.");
        DevicePolicyManager d=policy(c);
        if (!d.setApplicationHidden(admin(c),pkg,false) || d.isApplicationHidden(admin(c),pkg)) throw new IllegalStateException("Android не разрешил запуск");
        try {
            boolean denied=d.setPermissionGrantState(admin(c),pkg,"android.permission.POST_NOTIFICATIONS",DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED);
            if (!denied) {
                // An app that does not request this permission cannot post notifications on Android 13+.
                android.content.pm.PackageInfo info=c.getPackageManager().getPackageInfo(pkg,android.content.pm.PackageManager.GET_PERMISSIONS);
                if(info.requestedPermissions!=null) for(String p:info.requestedPermissions) if(p.equals("android.permission.POST_NOTIFICATIONS")) throw new IllegalStateException("Не удалось запретить уведомления");
            }
            Intent i=c.getPackageManager().getLaunchIntentForPackage(pkg);
            if(i==null) throw new IllegalStateException("У приложения нет экрана запуска");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            c.startActivity(i);
        } catch (Exception ex) { GuardService.lock(c); throw new IllegalStateException(ex.getMessage(),ex); }
    }
}
