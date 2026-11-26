package cn.club.welcome;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

public final class Crypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    public static String random(int bytes) {
        byte[] value = new byte[bytes]; RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }
    public static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
    public static String digest(String secret, String value) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch(Exception e) { throw new IllegalStateException("摘要计算失败",e); }
    }
    private static String derive(char[] password, String salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(password,salt.getBytes(StandardCharsets.UTF_8),600000,256);
        try { return HexFormat.of().formatHex(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded()); }
        finally { spec.clearPassword(); }
    }
    public static String hash(char[] password) throws Exception {
        String salt=random(16);
        return "pbkdf2_sha256$600000$"+salt+"$"+derive(password,salt);
    }
    public static boolean verify(String password,String encoded) {
        try {
            String[] parts=encoded.split("\\$");
            return parts.length==4 && parts[0].equals("pbkdf2_sha256") && parts[1].equals("600000") && same(derive(password.toCharArray(),parts[2]),parts[3]);
        } catch(Exception e) { return false; }
    }
    public static boolean validNewPassword(char[] password) {
        if(password==null || password.length<8 || password.length>64)return false;
        boolean upper=false,lower=false,digit=false;
        for(char c:password) {
            if(c>='A'&&c<='Z')upper=true;
            else if(c>='a'&&c<='z')lower=true;
            else if(c>='0'&&c<='9')digit=true;
            else return false;
        }
        return upper&&lower&&digit;
    }
    private Crypto() {}
}
