package app.msime.android;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** :ime 进程的唯一网络适配，由可见的前台服务维持连接与系统剪贴板监听。 */
public final class LanClipboardRuntime {
    private static LanClipboardRuntime instance;
    private final LanClipboardStore store;
    private final Context context;
    private final LanClipboardBridge bridge;
    private volatile String captureStatus="", pasteStatus="";
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(3,3,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"clipboard-tls");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor copies=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"clipboard-copy");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final Set<String> exchanging=ConcurrentHashMap.newKeySet();
    private final Map<String,Probe> probes=new HashMap<>();
    private final Map<String,Endpoint> endpoints=new HashMap<>();
    private final Map<String,Long> reverse=new HashMap<>();
    private DatagramSocket udp;
    private ServerSocket tcp;
    private volatile String networkStatus="";
    private record Probe(String nonce,String device,String secret,long expires) {}
    private record Endpoint(InetAddress address,int port,long expires) {}
    private LanClipboardRuntime(Context context) throws Exception {
        this.context=context.getApplicationContext();
        store=new LanClipboardStore(context);
        bridge=new LanClipboardBridge(context,this);
        store.onReceived(bridge::received);
        Thread worker=new Thread(this::run,"clipboard-lan");worker.setDaemon(true);worker.start();
    }
    public static synchronized LanClipboardRuntime get(Context context) throws Exception {
        if(instance==null)instance=new LanClipboardRuntime(context);return instance;
    }
    public static void start(Context context){try{get(context);}catch(Exception ignored){}}
    public static void capture(Context context,String text){try{get(context).store.captured(text);}catch(Exception ignored){}}
    public static void history(Context context,boolean enabled){try{get(context).store.history(enabled);}catch(Exception ignored){}}
    boolean active(){return store.active();}
    void captureStatus(String value){captureStatus=value;}
    void pasteStatus(String value){pasteStatus=value;}
    void captureSystem(String text,java.util.function.Consumer<Boolean> completed){
        try{copies.execute(()->{boolean success=false;try{success=store.captureSystem(text);captureStatus="";}catch(Exception error){captureStatus="无法保存或发送当前文字，请检查剪贴板历史开关";}finally{completed.accept(success);}});}
        catch(RejectedExecutionException error){captureStatus="发送任务繁忙，请重新复制或点立即同步";completed.accept(false);}
    }
    synchronized JSONObject command(String action,String text,String ip,boolean enabled) throws Exception {
        if(action.equals("capture")){bridge.captureNow();action="status";}
        JSONObject value=store.command(action,text,ip,enabled);
        if(action.equals("enable")&&!enabled){closeNetwork();context.stopService(new android.content.Intent(context,LanClipboardService.class));}
        else if(store.active())LanClipboardService.ensure(context);
        return value.put("network",networkStatus).put("captureStatus",captureStatus).put("pasteStatus",pasteStatus);
    }
    private synchronized void closeNetwork(){
        if(udp!=null){udp.close();udp=null;}if(tcp!=null){try{tcp.close();}catch(IOException ignored){}tcp=null;}
        for(Socket socket:sockets)try{socket.close();}catch(IOException ignored){}
        probes.clear();endpoints.clear();reverse.clear();networkStatus="";
    }
    private synchronized void openNetwork() throws IOException {
        if(udp!=null&&tcp!=null)return;
        try{
            udp=new DatagramSocket(null);udp.setReuseAddress(false);udp.setBroadcast(true);udp.bind(new InetSocketAddress(LanClipboardProtocol.UDP_PORT));udp.setSoTimeout(50);
            tcp=new ServerSocket();tcp.setReuseAddress(true);tcp.bind(new InetSocketAddress(LanClipboardProtocol.TCP_PORT),8);tcp.setSoTimeout(50);
            networkStatus="";
        }catch(IOException error){closeNetwork();networkStatus="网络监听失败，请检查端口占用后重试";throw error;}
    }
    private void run(){
        long refreshed=0,probed=0,exchanged=0;
        while(true){
            long now=System.currentTimeMillis();
            try{
                if(now-refreshed>=1000){store.refreshHistory();refreshed=now;}
                if(!store.active()){closeNetwork();Thread.sleep(100);continue;}
                openNetwork();
                if(now-probed>=3000){probeAll();probed=now;}
                pollDiscovery();pollAccept();
                if(now-exchanged>=1000){exchangeAll();exchanged=now;}
            }catch(Exception error){try{Thread.sleep(100);}catch(InterruptedException ignored){return;}}
        }
    }
    private synchronized void send(LanClipboardProtocol.Discovery packet,String secret,InetAddress address) throws IOException {
        if(udp==null || (!address.isSiteLocalAddress()&&!address.isLinkLocalAddress()&&!address.isLoopbackAddress()&&!address.getHostAddress().endsWith(".255")))return;
        byte[] bytes=LanClipboardProtocol.discovery(packet,secret);udp.send(new DatagramPacket(bytes,bytes.length,address,LanClipboardProtocol.UDP_PORT));
    }
    private synchronized void probe(JSONObject peer,InetAddress destination) throws IOException {
        String key=peer.optString("key"),secret=peer.optString("secret"),device=peer.optString("device"),nonce=LanClipboardProtocol.random(16);
        probes.put(key,new Probe(nonce,device,secret,System.currentTimeMillis()+8000));
        send(new LanClipboardProtocol.Discovery(1,nonce,key,LanClipboardProtocol.TCP_PORT,store.local(),device),secret,destination);
    }
    private void probeAll() throws IOException {
        List<InetAddress> broadcasts=new ArrayList<>();
        Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
        while(interfaces!=null&&interfaces.hasMoreElements()&&broadcasts.size()<16){
            NetworkInterface iface=interfaces.nextElement();if(!iface.isUp()||iface.isLoopback())continue;
            for(InterfaceAddress address:iface.getInterfaceAddresses())if(LanClipboardProtocol.privateAddress(address.getAddress())&&address.getBroadcast()!=null&&broadcasts.size()<16)broadcasts.add(address.getBroadcast());
        }
        synchronized(this){
            long now=System.currentTimeMillis();probes.entrySet().removeIf(e->e.getValue().expires<now);endpoints.entrySet().removeIf(e->e.getValue().expires<now);reverse.entrySet().removeIf(e->now-e.getValue()>8000);
            for(JSONObject peer:store.peers()){
                String key=peer.optString("key"),secret=peer.optString("secret"),device=peer.optString("device"),nonce=LanClipboardProtocol.random(16);
                probes.put(key,new Probe(nonce,device,secret,now+8000));
                var packet=new LanClipboardProtocol.Discovery(1,nonce,key,LanClipboardProtocol.TCP_PORT,store.local(),device);
                for(InetAddress broadcast:broadcasts)try{send(packet,secret,broadcast);}catch(IOException ignored){}
                String ip=peer.optString("ip");if(!ip.isEmpty())try{send(packet,secret,LanClipboardProtocol.address(ip));}catch(IOException ignored){}
            }
        }
    }
    private void pollDiscovery() throws IOException {
        DatagramSocket socket; synchronized(this){socket=udp;}if(socket==null)return;
        byte[] buffer=new byte[364];DatagramPacket datagram=new DatagramPacket(buffer,buffer.length);
        try{socket.receive(datagram);}catch(SocketTimeoutException error){return;}
        if(!LanClipboardProtocol.privateAddress(datagram.getAddress())||datagram.getLength()<109||datagram.getLength()>363)return;
        String key=new String(buffer,39,32,java.nio.charset.StandardCharsets.US_ASCII);String secret=store.secret(key);if(secret==null)return;
        try{
            var packet=LanClipboardProtocol.discovery(Arrays.copyOf(buffer,datagram.getLength()),secret);
            if(!packet.to().equals(store.local()))return;
            JSONObject peer=null;for(JSONObject p:store.peers())if(key.equals(p.optString("key")))peer=p;
            if(peer!=null&&!peer.optString("device").equals(packet.from()))return;
            synchronized(this){
                long now=System.currentTimeMillis();
                if(packet.type()==2){
                    Probe pending=probes.get(key);
                    if(peer==null||pending==null||pending.expires<now||!pending.nonce.equals(packet.nonce())||!pending.device.equals(packet.from())||!pending.secret.equals(secret))return;
                    probes.remove(key);endpoints.put(key,new Endpoint(datagram.getAddress(),packet.port(),now+30000));
                }else{
                    send(new LanClipboardProtocol.Discovery(2,packet.nonce(),key,LanClipboardProtocol.TCP_PORT,store.local(),packet.from()),secret,datagram.getAddress());
                    if(peer!=null&&now-reverse.getOrDefault(key,0L)>=1000){reverse.put(key,now);probe(peer,datagram.getAddress());}
                }
            }
        }catch(IOException ignored){}
    }
    private void pollAccept() throws IOException {
        ServerSocket listener;synchronized(this){listener=tcp;}if(listener==null)return;
        Socket socket;try{socket=listener.accept();}catch(SocketTimeoutException error){return;}
        if(!LanClipboardProtocol.privateAddress(socket.getInetAddress())){socket.close();return;}
        sockets.add(socket);
        try{io.execute(()->{try{LanClipboardTls.accept(socket,store);}catch(IOException error){store.connectionError(null,error);}finally{sockets.remove(socket);}});}
        catch(RejectedExecutionException error){sockets.remove(socket);socket.close();}
    }
    private synchronized void exchangeAll(){
        for(JSONObject peer:store.peers()){
            String key=peer.optString("key"),secret=peer.optString("secret"),device=peer.optString("device");
            Endpoint endpoint=endpoints.get(key);if(endpoint!=null&&endpoint.expires<System.currentTimeMillis())endpoint=null;
            if(endpoint==null&&!peer.optString("ip").isEmpty())try{endpoint=new Endpoint(LanClipboardProtocol.address(peer.optString("ip")),LanClipboardProtocol.TCP_PORT,0);}catch(IOException ignored){}
            if(endpoint==null||!exchanging.add(key))continue;
            Endpoint destination=endpoint;
            try{io.execute(()->{try{LanClipboardTls.connect(destination.address,destination.port,key,secret,device,store);}catch(IOException error){store.connectionError(key,error);}finally{exchanging.remove(key);}});}
            catch(RejectedExecutionException error){exchanging.remove(key);}
        }
    }
}
