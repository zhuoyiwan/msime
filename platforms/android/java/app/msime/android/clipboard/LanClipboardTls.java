package app.msime.android;

import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

/** 仅允许新鲜 TLS 1.3 外部 PSK+DHE 会话，禁用证书退路、票据和恢复。 */
public final class LanClipboardTls {
    public interface Store {
        String local();
        String secret(String key);
        boolean bind(String key, String secret, String peer);
        List<LanClipboardProtocol.Event> pending(String key, String peer);
        void imported(String key, String secret, String peer, LanClipboardProtocol.Event event) throws IOException;
        void delivered(String key, String secret, String peer, List<LanClipboardProtocol.Event> events, String ip);
    }
    private static final ScheduledExecutorService DEADLINES=Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t=new Thread(r,"clipboard-deadlines"); t.setDaemon(true); return t;
    });
    private LanClipboardTls() {}
    public static void connect(InetAddress address,int port,String key,String secret,String peer,Store store) throws IOException {
        if (!LanClipboardProtocol.privateAddress(address)) throw new IOException("非局域网地址");
        try (Socket socket=new Socket()) {
            ScheduledFuture<?> timeout=deadline(socket);
            try {
                socket.connect(new InetSocketAddress(address,port),1500);socket.setSoTimeout(3000);
                BcTlsCrypto crypto=new BcTlsCrypto(new SecureRandom());
                TlsClientProtocol protocol=new TlsClientProtocol(socket.getInputStream(),socket.getOutputStream());
                protocol.connect(new DefaultTlsClient(crypto) {
                    protected ProtocolVersion[] getSupportedVersions() {return ProtocolVersion.TLSv13.only();}
                    protected int[] getSupportedCipherSuites() {return new int[]{CipherSuite.TLS_AES_128_GCM_SHA256};}
                    public short[] getPskKeyExchangeModes(){return new short[]{PskKeyExchangeMode.psk_dhe_ke};}
                    protected Vector<Integer> getSupportedGroups(Vector roles){return new Vector<>(Arrays.asList(NamedGroup.x25519,NamedGroup.secp256r1));}
                    public Vector<TlsPSKExternal> getExternalPSKs(){return new Vector<>(Collections.singletonList(psk(crypto,key,secret)));}
                    public TlsAuthentication getAuthentication() throws IOException {throw new IOException("禁止证书认证退路");}
                    public TlsSession getSessionToResume(){return null;}
                    public void notifyNewSessionTicket(NewSessionTicket ticket) throws IOException {throw new IOException("禁止会话票据");}
                });
                DataInputStream in=new DataInputStream(protocol.getInputStream());DataOutputStream out=new DataOutputStream(protocol.getOutputStream());
                LanClipboardProtocol.hello(out,store.local());
                if(!LanClipboardProtocol.hello(in).equals(peer) || !store.bind(key,secret,peer)) throw new IOException("设备身份不匹配");
                List<LanClipboardProtocol.Event> events=store.pending(key,peer);send(out,events);receive(in,key,secret,peer,store);
                out.writeByte(1);out.flush();store.delivered(key,secret,peer,events,address.getHostAddress());
            } finally {timeout.cancel(false);}
        }
    }
    public static void accept(Socket socket,Store store) throws IOException {
        if(!LanClipboardProtocol.privateAddress(socket.getInetAddress())) {socket.close();throw new IOException("非局域网来源");}
        try(socket) {
            ScheduledFuture<?> timeout=deadline(socket);
            try {
                socket.setSoTimeout(3000);BcTlsCrypto crypto=new BcTlsCrypto(new SecureRandom());String[] selected=new String[2];
                TlsServerProtocol protocol=new TlsServerProtocol(socket.getInputStream(),socket.getOutputStream());
                protocol.accept(new DefaultTlsServer(crypto) {
                    protected ProtocolVersion[] getSupportedVersions(){return ProtocolVersion.TLSv13.only();}
                    protected int[] getSupportedCipherSuites(){return new int[]{CipherSuite.TLS_AES_128_GCM_SHA256};}
                    public short[] getPskKeyExchangeModes(){return new short[]{PskKeyExchangeMode.psk_dhe_ke};}
                    public int[] getSupportedGroups(){return new int[]{NamedGroup.x25519,NamedGroup.secp256r1};}
                    public TlsSession getSessionToResume(byte[] id){return null;}
                    public NewSessionTicket getNewSessionTicket(){return null;}
                    public TlsCredentials getCredentials() throws IOException {throw new IOException("禁止证书认证退路");}
                    public TlsPSKExternal getExternalPSK(Vector identities) {
                        for(Object object:identities) {
                            String key=new String(((PskIdentity)object).getIdentity(),java.nio.charset.StandardCharsets.US_ASCII);
                            if(!LanClipboardProtocol.hex(key,16)) continue;
                            String secret=store.secret(key);
                            if(secret!=null) {selected[0]=key;selected[1]=secret;return psk(crypto,key,secret);}
                        }
                        return null;
                    }
                });
                if(selected[0]==null) throw new IOException("没有认证密钥");
                DataInputStream in=new DataInputStream(protocol.getInputStream());DataOutputStream out=new DataOutputStream(protocol.getOutputStream());
                String peer=LanClipboardProtocol.hello(in);
                if(!store.bind(selected[0],selected[1],peer)) throw new IOException("设备身份不匹配");
                LanClipboardProtocol.hello(out,store.local());receive(in,selected[0],selected[1],peer,store);
                List<LanClipboardProtocol.Event> events=store.pending(selected[0],peer);send(out,events);
                if(in.readUnsignedByte()!=1) throw new IOException("缺少确认");
                store.delivered(selected[0],selected[1],peer,events,socket.getInetAddress().getHostAddress());
            } finally {timeout.cancel(false);}
        }
    }
    private static BasicTlsPSKExternal psk(BcTlsCrypto crypto,String key,String secret) {
        return new BasicTlsPSKExternal(key.getBytes(java.nio.charset.StandardCharsets.US_ASCII),crypto.createSecret(LanClipboardProtocol.unhex(secret)));
    }
    private static ScheduledFuture<?> deadline(Socket socket) {
        return DEADLINES.schedule(() -> {try{socket.close();}catch(IOException ignored){}},3,TimeUnit.SECONDS);
    }
    private static void send(DataOutputStream out,List<LanClipboardProtocol.Event> events) throws IOException {
        if(events.size()>50) throw new IOException("发送队列超限");
        out.writeByte(events.size());for(var event:events) out.write(LanClipboardProtocol.encode(event));out.flush();
    }
    private static void receive(DataInputStream in,String key,String secret,String peer,Store store) throws IOException {
        int count=in.readUnsignedByte();if(count>50)throw new IOException("批次超限");
        for(int i=0;i<count;i++) store.imported(key,secret,peer,LanClipboardProtocol.read(in,peer,store.local()));
    }
}
