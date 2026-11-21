package app.luna.cycle;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Core {
    private Core() {}
    public static String credential(char[] password) {
        byte[] salt = new byte[24]; new SecureRandom().nextBytes(salt);
        return "210000:" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(derive(password, salt, 210000));
    }
    public static boolean verify(char[] password, String encoded) {
        try {
            String[] p = encoded.split(":");
            int iterations = Integer.parseInt(p[0]);
            if (iterations < 100000 || iterations > 1000000) return false;
            return MessageDigest.isEqual(Base64.getDecoder().decode(p[2]), derive(password, Base64.getDecoder().decode(p[1]), iterations));
        } catch (RuntimeException ex) { return false; }
        finally { Arrays.fill(password, '\0'); }
    }
    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch (Exception ex) { throw new IllegalStateException("Password derivation failed", ex); }
        finally { spec.clearPassword(); Arrays.fill(password, '\0'); }
    }
    public static int cycleDay(LocalDate start, LocalDate today, int length) {
        if (length < 15 || length > 90) throw new IllegalArgumentException("Cycle length");
        long days = ChronoUnit.DAYS.between(start, today);
        return days < 0 ? 0 : (int) (days % length) + 1;
    }
    public static LocalDate nextPeriod(LocalDate start, LocalDate today, int length) {
        cycleDay(start, today, length);
        long days = ChronoUnit.DAYS.between(start, today);
        return start.plusDays(days < 0 ? 0 : (days / length + 1) * length);
    }
}
