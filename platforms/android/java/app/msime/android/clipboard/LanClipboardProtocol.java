package app.msime.android;

import java.io.*;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.*;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Windows engine/contracts/clipboard_{sync,lan} v1 的安卓字节适配，不包含输入会话状态。 */
public final class LanClipboardProtocol {
    public static final int TCP_PORT = 49876, UDP_PORT = 49877;
    public static final int MAX_FRAME = 12316;
    private static final SecureRandom RANDOM = new SecureRandom();
    private LanClipboardProtocol() {}
    public record Pairing(String device, String key, String secret) {}
    public record Event(String id, String origin, long created, String text, byte[] digest) {}
    public record Discovery(int type, String nonce, String key, int port, String from, String to) {}

    public static boolean identity(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,128}");
    }
    public static boolean hex(String value, int bytes) {
        return value != null && value.matches("[0-9a-f]{" + bytes * 2 + "}");
    }
    public static Pairing pairing(String code) throws IOException {
        if (code == null || code.length() > 233) throw new IOException("配对码无效");
        String[] fields = code.split(":", -1);
        if (fields.length != 4 || !fields[0].equals("msime1") || !identity(fields[1])
                || !hex(fields[2], 16) || !hex(fields[3], 32)) throw new IOException("配对码无效");
        return new Pairing(fields[1], fields[2], fields[3]);
    }
    public static byte[] unhex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i*2, i*2+2), 16);
        return bytes;
    }
    public static String hexBytes(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(Character.forDigit((b & 255) >> 4, 16)).append(Character.forDigit(b & 15, 16));
        return out.toString();
    }
    public static String random(int length) {
        byte[] bytes = new byte[length]; RANDOM.nextBytes(bytes); return hexBytes(bytes);
    }
    public static byte[] digest(byte[] bytes) {
        try { return MessageDigest.getInstance("SHA-256").digest(bytes); }
        catch (GeneralSecurityException error) { throw new IllegalStateException(error); }
    }
    public static boolean privateAddress(InetAddress address) {
        byte[] b = address.getAddress();
        if (b.length != 4) return false;
        int a = b[0]&255, c = b[1]&255;
        return a == 10 || a == 127 || (a == 172 && c >= 16 && c <= 31)
            || (a == 192 && c == 168) || (a == 169 && c == 254);
    }
    public static InetAddress address(String value) throws IOException {
        if (value == null || !value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) throw new IOException("请填写局域网 IPv4");
        byte[] bytes = new byte[4]; String[] parts = value.split("\\.");
        for (int i = 0; i < 4; i++) { int n = Integer.parseInt(parts[i]); if (n > 255) throw new IOException("IP 无效"); bytes[i]=(byte)n; }
        InetAddress result = InetAddress.getByAddress(bytes);
        if (!privateAddress(result)) throw new IOException("只允许局域网地址");
        return result;
    }
    private static byte[] text(String value) throws IOException {
        if (value == null || value.isEmpty() || value.length() > 4000 || value.indexOf('\0') >= 0) throw new IOException("文字长度无效");
        try {
            ByteBuffer buffer = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
            byte[] result = new byte[buffer.remaining()]; buffer.get(result);
            if (result.length > 12000) throw new IOException("文字过长");
            return result;
        } catch (CharacterCodingException error) { throw new IOException("UTF8 无效", error); }
    }
    public static Event capture(String local, String value) throws IOException {
        if (value.contains("msime1:")) throw new IOException("不分享配对凭据");
        byte[] bytes = text(value);
        return new Event(random(16), local, System.currentTimeMillis()/1000, value, digest(bytes));
    }
    public static byte[] encode(Event e) throws IOException {
        if (!identity(e.id) || !identity(e.origin) || e.created < 0) throw new IOException("事件身份无效");
        byte[] t=text(e.text), id=e.id.getBytes(StandardCharsets.US_ASCII), origin=e.origin.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(digest(t), e.digest)) throw new IOException("摘要无效");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(56+id.length+origin.length+t.length); out.writeInt(0x4d534342); out.writeShort(1); out.writeShort(1);
        out.writeLong(e.created); out.writeShort(id.length); out.writeShort(origin.length); out.writeInt(t.length);
        out.write(e.digest); out.write(id); out.write(origin); out.write(t); return bytes.toByteArray();
    }
    public static Event read(DataInputStream in, String peer, String local) throws IOException {
        int length=in.readInt(); if (length < 59 || length > MAX_FRAME-4) throw new IOException("事件长度无效");
        byte[] bytes=new byte[length]; in.readFully(bytes); DataInputStream body=new DataInputStream(new ByteArrayInputStream(bytes));
        if (body.readInt()!=0x4d534342 || body.readUnsignedShort()!=1 || body.readUnsignedShort()!=1) throw new IOException("事件版本无效");
        long time=body.readLong(); int il=body.readUnsignedShort(), ol=body.readUnsignedShort(), tl=body.readInt();
        if (time<0 || il<1 || il>128 || ol<1 || ol>128 || tl<1 || tl>12000 || length!=56+il+ol+tl) throw new IOException("事件字段无效");
        byte[] hash=new byte[32], id=new byte[il], origin=new byte[ol], txt=new byte[tl];
        body.readFully(hash); body.readFully(id); body.readFully(origin); body.readFully(txt);
        String i=new String(id,StandardCharsets.US_ASCII), o=new String(origin,StandardCharsets.US_ASCII), t;
        try { t=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(txt)).toString(); }
        catch (CharacterCodingException error) { throw new IOException("UTF8 无效",error); }
        text(t);
        if (!identity(i) || !identity(o) || !o.equals(peer) || o.equals(local) || !MessageDigest.isEqual(hash,digest(txt))) throw new IOException("事件来源或摘要无效");
        return new Event(i,o,time,t,hash);
    }
    public static void hello(DataOutputStream out, String local) throws IOException {
        if (!identity(local)) throw new IOException("身份无效");
        out.write(new byte[]{77,83,67,76,0,1}); out.writeByte(local.length()); out.write(local.getBytes(StandardCharsets.US_ASCII)); out.flush();
    }
    public static String hello(DataInputStream in) throws IOException {
        byte[] header=new byte[6]; in.readFully(header);
        if (!Arrays.equals(header,new byte[]{77,83,67,76,0,1})) throw new IOException("身份版本无效");
        int size=in.readUnsignedByte(); if(size<1 || size>128) throw new IOException("身份长度无效");
        byte[] bytes=new byte[size]; in.readFully(bytes); String result=new String(bytes,StandardCharsets.US_ASCII);
        if (!identity(result)) throw new IOException("身份无效"); return result;
    }
    private static byte[] mac(byte[] body,String secret) throws IOException {
        if (!hex(secret,32)) throw new IOException("密钥无效");
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(unhex(secret),"HmacSHA256")); return mac.doFinal(body); }
        catch (GeneralSecurityException error) { throw new IOException("认证不可用",error); }
    }
    public static byte[] discovery(Discovery d,String secret) throws IOException {
        if ((d.type!=1 && d.type!=2) || !hex(d.nonce,16) || !hex(d.key,16) || !identity(d.from) || !identity(d.to)
                || d.from.equals(d.to) || d.port<1 || d.port>65535) throw new IOException("发现字段无效");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        out.write(new byte[]{77,83,67,68,0,1}); out.writeByte(d.type); out.write(d.nonce.getBytes(StandardCharsets.US_ASCII));
        out.write(d.key.getBytes(StandardCharsets.US_ASCII)); out.writeShort(d.port); out.writeByte(d.from.length()); out.writeByte(d.to.length());
        out.write(d.from.getBytes(StandardCharsets.US_ASCII)); out.write(d.to.getBytes(StandardCharsets.US_ASCII)); out.write(mac(bytes.toByteArray(),secret));
        return bytes.toByteArray();
    }
    public static Discovery discovery(byte[] bytes,String secret) throws IOException {
        if (bytes.length<109 || bytes.length>363) throw new IOException("发现长度无效");
        byte[] body=Arrays.copyOf(bytes,bytes.length-32);
        if (!MessageDigest.isEqual(mac(body,secret),Arrays.copyOfRange(bytes,body.length,bytes.length))) throw new IOException("发现认证失败");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(body)); byte[] header=new byte[6],nonce=new byte[32],key=new byte[32]; in.readFully(header);
        if (!Arrays.equals(header,new byte[]{77,83,67,68,0,1})) throw new IOException("发现版本无效");
        int type=in.readUnsignedByte(); in.readFully(nonce); in.readFully(key); int port=in.readUnsignedShort(),fl=in.readUnsignedByte(),tl=in.readUnsignedByte();
        if(fl<1 || fl>128 || tl<1 || tl>128 || body.length!=75+fl+tl) throw new IOException("发现身份长度无效");
        byte[] from=new byte[fl],to=new byte[tl]; in.readFully(from);in.readFully(to);
        Discovery d=new Discovery(type,new String(nonce,StandardCharsets.US_ASCII),new String(key,StandardCharsets.US_ASCII),port,new String(from,StandardCharsets.US_ASCII),new String(to,StandardCharsets.US_ASCII));
        discovery(d,secret); return d;
    }
}
