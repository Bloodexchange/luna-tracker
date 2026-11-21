package app.luna.cycle;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

final class Store {
    private final Context context;
    Store(Context c) { context = c.getApplicationContext(); }
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias("luna.local.v1")) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder("luna.local.v1", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey)ks.getKey("luna.local.v1", null);
    }
    synchronized JSONObject read() {
        String value = context.getSharedPreferences("luna", 0).getString("data", null);
        if (value == null) return new JSONObject();
        try {
            String[] parts = value.split(":");
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new JSONObject(new String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ex) { throw new IllegalStateException("Не удалось открыть локальные данные. Не удаляйте приложение: это удалит данные.", ex); }
    }
    synchronized void write(JSONObject data) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key());
            String value = Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)), Base64.NO_WRAP);
            if (!context.getSharedPreferences("luna", 0).edit().putString("data", value).commit()) throw new IllegalStateException("Не удалось сохранить данные");
        } catch (Exception ex) { throw new IllegalStateException("Не удалось сохранить данные", ex); }
    }
    void put(String name, Object value) {
        synchronized (Store.class) { try { JSONObject d = read(); d.put(name, value); write(d); } catch (org.json.JSONException ex) { throw new IllegalStateException(ex); } }
    }
}
