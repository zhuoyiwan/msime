package app.msime.android;

import java.io.*;
import java.util.Arrays;

/** 用 Windows 黄金向量验证字节兼容，异常路径必须拒绝后再继续运行。 */
public final class LanClipboardProtocolSmoke {
    private static int checks;
    private static void require(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private interface Work{void run() throws Exception;}
    private static void rejects(Work work) throws Exception{checks++;try{work.run();}catch(IOException error){return;}throw new AssertionError("expected rejection "+checks);}
    private static DataInputStream input(byte[] bytes){return new DataInputStream(new ByteArrayInputStream(bytes));}
    public static void main(String[] args) throws Exception {
        String golden="000000434d53434200010001000000000000002a0002000400000005"+
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"+"69647065657268656c6c6f";
        var event=new LanClipboardProtocol.Event("id","peer",42,"hello",LanClipboardProtocol.digest("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        byte[] encoded=LanClipboardProtocol.encode(event);require(LanClipboardProtocol.hexBytes(encoded).equals(golden));
        require(LanClipboardProtocol.read(input(encoded),"peer","local").text().equals("hello"));
        rejects(()->LanClipboardProtocol.read(input(encoded),"other","local"));
        rejects(()->LanClipboardProtocol.read(input(encoded),"peer","peer"));
        byte[] bad=encoded.clone();bad[28]^=1;rejects(()->LanClipboardProtocol.read(input(bad),"peer","local"));
        byte[] malformed=encoded.clone();malformed[malformed.length-1]=(byte)255;rejects(()->LanClipboardProtocol.read(input(malformed),"peer","local"));
        byte[] huge=encoded.clone();Arrays.fill(huge,0,4,(byte)127);rejects(()->LanClipboardProtocol.read(input(huge),"peer","local"));
        rejects(()->LanClipboardProtocol.capture("local","\ud800"));
        rejects(()->LanClipboardProtocol.capture("local","a\0b"));
        rejects(()->LanClipboardProtocol.capture("local","a".repeat(4001)));
        var emoji=LanClipboardProtocol.capture("local","😀".repeat(2000));require(LanClipboardProtocol.encode(emoji).length<LanClipboardProtocol.MAX_FRAME);
        rejects(()->LanClipboardProtocol.capture("local","😀".repeat(2001)));
        rejects(()->LanClipboardProtocol.capture("local","msime1:secret"));
        String secret="22".repeat(32),key="1".repeat(32),nonce="0".repeat(32);
        byte[] packet=LanClipboardProtocol.discovery(new LanClipboardProtocol.Discovery(1,nonce,key,49876,"client","host"),secret);
        require(LanClipboardProtocol.hexBytes(Arrays.copyOfRange(packet,packet.length-32,packet.length)).equals("f99ca891dfc6738a2337dab2ee346c8db8d504682bf4ae75b96dbeceff146fd8"));
        require(LanClipboardProtocol.discovery(packet,secret).from().equals("client"));
        rejects(()->LanClipboardProtocol.discovery(packet,"33".repeat(32)));
        byte[] corrupted=packet.clone();corrupted[corrupted.length-1]^=1;rejects(()->LanClipboardProtocol.discovery(corrupted,secret));
        String code="msime1:host:"+key+":"+secret;require(LanClipboardProtocol.pairing(code).device().equals("host"));
        rejects(()->LanClipboardProtocol.pairing("https://example.com"));
        rejects(()->LanClipboardProtocol.pairing(code.toUpperCase()));
        rejects(()->LanClipboardProtocol.pairing(code+":extra"));
        require(LanClipboardProtocol.privateAddress(LanClipboardProtocol.address("192.168.1.1")));
        rejects(()->LanClipboardProtocol.address("8.8.8.8"));
        rejects(()->LanClipboardProtocol.address("example.com"));
        rejects(()->LanClipboardProtocol.address("192.168.1.999"));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();LanClipboardProtocol.hello(new DataOutputStream(bytes),"local");require(LanClipboardProtocol.hello(input(bytes.toByteArray())).equals("local"));
        System.out.println("局域网协议黄金向量与拒绝路径通过："+checks+" 项");
    }
}
