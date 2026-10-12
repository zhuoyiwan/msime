package app.msime.android;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;
import app.msime.android.home.HostStore;
import java.io.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** 只由 :ime 进程访问；信任文档损坏时保留原文件并拒绝启动。 */
final class LanClipboardStore implements LanClipboardTls.Store {
    private final Context context;
    private final AtomicFile file;
    private final SecretKey encryption;
    private JSONObject state;
    private boolean historyEnabled;
    private boolean privateMode;
    private java.util.function.Consumer<LanClipboardProtocol.Event> receiver;
    private final Map<String,String> errors=new HashMap<>();
    private long sentCount,receivedCount,lastSent,lastReceived;
    private final Map<String,ArrayDeque<LanClipboardProtocol.Event>> queues=new HashMap<>();
    private final Map<String,Long> connected=new HashMap<>();

    LanClipboardStore(Context context) throws Exception {
        this.context=context.getApplicationContext(); file=new AtomicFile(new File(context.getFilesDir(),"clipboard-lan.enc"));
        KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
        String alias=context.getPackageName()+".clipboard-lan.v1";
        if(!keys.containsAlias(alias)) {
            if(file.getBaseFile().exists())throw new IOException("配对密钥已丢失，请重置局域网配对");
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            generator.generateKey();
        }
        encryption=(SecretKey)keys.getKey(alias,null);
        if(file.getBaseFile().exists()) {
            try(InputStream in=file.openRead()) {
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
                while((count=in.read(buffer))!=-1){if(out.size()+count>2_000_000)throw new IOException("配对存储超限");out.write(buffer,0,count);}
                byte[] bytes=out.toByteArray();if(bytes.length<29)throw new IOException("配对存储损坏");
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,encryption,new GCMParameterSpec(128,Arrays.copyOf(bytes,12)));
                cipher.updateAAD(alias.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                state=new JSONObject(new String(cipher.doFinal(bytes,12,bytes.length-12),java.nio.charset.StandardCharsets.UTF_8));
                validate();
            }
        } else {
            state=new JSONObject().put("version",1).put("device","android-"+LanClipboardProtocol.random(16)).put("enabled",false)
                .put("peers",new JSONArray()).put("seen",new JSONArray());save();
        }
    }
    private void validate() throws Exception {
        if(state.getInt("version")!=1 || !LanClipboardProtocol.identity(state.getString("device")))throw new IOException("配对文档无效");
        state.getBoolean("enabled");JSONArray peers=state.getJSONArray("peers"),seen=state.getJSONArray("seen");
        if(peers.length()>8 || seen.length()>50)throw new IOException("配对文档超限");
        Set<String> ids=new HashSet<>(),keys=new HashSet<>();
        for(int i=0;i<peers.length();i++) {
            JSONObject p=peers.getJSONObject(i);String device=p.getString("device"),key=p.getString("key"),secret=p.getString("secret"),ip=p.optString("ip");
            if(!LanClipboardProtocol.identity(device)||device.equals(local())||!LanClipboardProtocol.hex(key,16)||!LanClipboardProtocol.hex(secret,32)
                    || !ids.add(device)||!keys.add(key))throw new IOException("配对身份无效");
            if(!ip.isEmpty())LanClipboardProtocol.address(ip);
        }
        for(int i=0;i<seen.length();i++) {
            byte[] frame=Base64.decode(seen.getString(i),Base64.NO_WRAP);
            if(frame.length>LanClipboardProtocol.MAX_FRAME)throw new IOException("事件超限");
        }
        JSONObject invite=state.optJSONObject("invitation");
        if(invite!=null && (!LanClipboardProtocol.hex(invite.getString("key"),16)||!LanClipboardProtocol.hex(invite.getString("secret"),32)))throw new IOException("邀请无效");
    }
    private void save() throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,encryption);
        cipher.updateAAD((context.getPackageName()+".clipboard-lan.v1").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] body=cipher.doFinal(state.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        FileOutputStream out=file.startWrite();
        try{out.write(cipher.getIV());out.write(body);file.finishWrite(out);}catch(Exception error){file.failWrite(out);throw error;}
    }
    /** 写盘成功才替换当前信任，失败不能让内存悄悄放行新设备。 */
    private void mutate(JSONObject next) throws Exception {
        JSONObject previous=state;state=next;try{save();}catch(Exception error){state=previous;throw error;}
    }
    synchronized void history(boolean enabled){historyEnabled=enabled;if(!enabled){queues.clear();connected.clear();}}
    synchronized boolean active(){return historyEnabled && !privateMode && state.optBoolean("enabled");}
    synchronized void onReceived(java.util.function.Consumer<LanClipboardProtocol.Event> receiver){this.receiver=receiver;}
    synchronized void connectionError(String key,IOException error){
        if(key==null)return;
        errors.put(key,error instanceof java.net.ConnectException?"电脑未响应，请检查电脑同步开关、防火墙及局域网":error instanceof java.net.SocketTimeoutException?"连接超时，请检查两端是否在同一局域网":"连接中断或认证未完成，正在重试");
    }
    synchronized void refreshHistory(){
        try{JSONObject snapshot=HostStore.loadPreferences(context);JSONObject p=snapshot==null?null:snapshot.optJSONObject("preferences");history(p!=null&&p.optBoolean("clipboard_history",false));privateMode=p==null||p.optBoolean("touch_incognito",false);}
        catch(RuntimeException|LinkageError error){history(false);}
    }
    public synchronized String local(){return state.optString("device");}
    synchronized List<JSONObject> peers(){
        List<JSONObject> result=new ArrayList<>();JSONArray peers=state.optJSONArray("peers");
        for(int i=0;i<peers.length();i++)try{result.add(new JSONObject(peers.getJSONObject(i).toString()));}catch(JSONException ignored){}
        return result;
    }
    private JSONObject peer(String key){JSONArray peers=state.optJSONArray("peers");for(int i=0;i<peers.length();i++){JSONObject p=peers.optJSONObject(i);if(p!=null&&key.equals(p.optString("key")))return p;}return null;}
    private boolean allowed(String key,String secret,String device){JSONObject p=peer(key);return active()&&p!=null&&p.optString("secret").equals(secret)&&p.optString("device").equals(device);}
    public synchronized String secret(String key){
        if(!active())return null;JSONObject p=peer(key);if(p!=null)return p.optString("secret");
        JSONObject invite=state.optJSONObject("invitation");return invite!=null && key.equals(invite.optString("key")) && invite.optLong("expires")>System.currentTimeMillis()?invite.optString("secret"):null;
    }
    public synchronized boolean bind(String key,String secret,String device){
        if(!active()||!LanClipboardProtocol.identity(device)||device.equals(local()))return false;
        JSONObject p=peer(key);if(p!=null)return allowed(key,secret,device);
        if(!secret.equals(secret(key)))return false;
        try{
            for(JSONObject old:peers())if(device.equals(old.optString("device")))return false;
            JSONObject next=new JSONObject(state.toString());JSONArray peers=next.getJSONArray("peers");if(peers.length()>=8)return false;
            peers.put(new JSONObject().put("device",device).put("key",key).put("secret",secret).put("ip",""));next.remove("invitation");mutate(next);return true;
        }catch(Exception error){return false;}
    }
    synchronized JSONObject command(String action,String text,String ip,boolean enabled) throws Exception {
        refreshHistory();JSONObject next=new JSONObject(state.toString());
        switch(action){
            case "enable":
                if(enabled){if(HostStore.putPreference(context,"clipboard_history",true)==null)throw new IOException("无法开启剪贴板历史，请等待词库准备完成");refreshHistory();}
                next.put("enabled",enabled);mutate(next);if(!enabled){queues.clear();connected.clear();}break;
            case "invite":
                if(!active())throw new IOException("请先打开剪贴板历史和局域网同步");
                if(peers().size()>=8)throw new IOException("最多配对 8 台设备");
                JSONObject invite=new JSONObject().put("key",LanClipboardProtocol.random(16)).put("secret",LanClipboardProtocol.random(32)).put("expires",System.currentTimeMillis()+300_000);
                next.put("invitation",invite);mutate(next);
                return new JSONObject().put("code","msime1:"+local()+":"+invite.getString("key")+":"+invite.getString("secret")).put("expires",invite.getLong("expires"));
            case "cancel": next.remove("invitation");mutate(next);break;
            case "pair":
                if(!active())throw new IOException("请先打开剪贴板历史和局域网同步");
                var code=LanClipboardProtocol.pairing(text);
                if(code.device().equals(local()))throw new IOException("不能配对自己");
                if(!ip.isEmpty())LanClipboardProtocol.address(ip);
                JSONArray peers=next.getJSONArray("peers");if(peers.length()>=8)throw new IOException("最多配对 8 台设备");
                for(JSONObject p:peers())if(p.optString("device").equals(code.device())||p.optString("key").equals(code.key()))throw new IOException("设备已配对，一次扫码即可双向同步；如两端互扫后无法连接，请在两端移除配对，再只扫描一次");
                peers.put(new JSONObject().put("device",code.device()).put("key",code.key()).put("secret",code.secret()).put("ip",ip));mutate(next);break;
            case "remove":
                JSONArray remaining=new JSONArray();for(JSONObject p:peers())if(!p.optString("device").equals(text))remaining.put(p);else{queues.remove(p.optString("key"));connected.remove(p.optString("key"));}
                next.put("peers",remaining);mutate(next);break;
            case "status": break;
            default: throw new IOException("操作无效");
        }
        JSONObject status=new JSONObject().put("device",local()).put("enabled",state.getBoolean("enabled")).put("history",historyEnabled).put("private",privateMode).put("active",active())
            .put("sent",sentCount).put("received",receivedCount).put("lastSent",lastSent).put("lastReceived",lastReceived);
        JSONArray peers=new JSONArray();for(JSONObject p:peers())peers.put(new JSONObject().put("device",p.getString("device")).put("ip",p.optString("ip"))
            .put("connected",System.currentTimeMillis()-connected.getOrDefault(p.getString("key"),0L)<10_000)
            .put("error",errors.getOrDefault(p.getString("key"),"")));
        return status.put("peers",peers);
    }
    synchronized void captured(String text){
        if(!active())return;
        try{var event=LanClipboardProtocol.capture(local(),text);for(JSONObject p:peers()){
            ArrayDeque<LanClipboardProtocol.Event> queue=queues.computeIfAbsent(p.optString("key"),k->new ArrayDeque<>());if(queue.size()>=50)queue.removeFirst();queue.addLast(event);
        }}catch(IOException ignored){}
    }
    synchronized boolean captureSystem(String text) throws IOException {
        refreshHistory();if(!active())return false;
        LanClipboardProtocol.capture(local(),text);
        String reason=new ClipboardHistoryStore(context.getFilesDir()).add(text);
        if(reason!=null&&!reason.equals("duplicate"))throw new IOException("历史存储未接收文字");
        captured(text);
        return true;
    }
    public synchronized List<LanClipboardProtocol.Event> pending(String key,String device){JSONObject p=peer(key);return p!=null&&allowed(key,p.optString("secret"),device)?new ArrayList<>(queues.getOrDefault(key,new ArrayDeque<>())):Collections.emptyList();}
    public synchronized void imported(String key,String secret,String device,LanClipboardProtocol.Event event) throws IOException {
        refreshHistory();if(!allowed(key,secret,device))throw new IOException("设备已撤销或共享已关闭");
        byte[] frame=LanClipboardProtocol.encode(event);JSONArray seen=state.optJSONArray("seen");
        try{
            for(int i=0;i<seen.length();i++) {
                byte[] old=Base64.decode(seen.getString(i),Base64.NO_WRAP);
                DataInputStream input=new DataInputStream(new ByteArrayInputStream(old));
                // 元数据解码时沿用记录自己的来源，跨设备转发只在网络接收入口拒绝。
                int ol=((old[22]&255)<<8)|(old[23]&255),il=((old[20]&255)<<8)|(old[21]&255);
                if(old.length<60+il+ol)throw new IOException("去重文档损坏");
                String origin=new String(old,60+il,ol,java.nio.charset.StandardCharsets.US_ASCII);
                var existing=LanClipboardProtocol.read(input,origin,local());
                if(existing.id().equals(event.id())){if(!Arrays.equals(old,frame))throw new IOException("事件身份冲突");return;}
            }
            String rejected=new ClipboardHistoryStore(context.getFilesDir()).add(event.text());
            if(rejected!=null && !rejected.equals("duplicate"))throw new IOException("历史存储未接收事件");
            JSONObject next=new JSONObject(state.toString());JSONArray updated=new JSONArray();updated.put(Base64.encodeToString(frame,Base64.NO_WRAP));
            for(int i=0;i<Math.min(seen.length(),49);i++)updated.put(seen.get(i));next.put("seen",updated);mutate(next);
            receivedCount++;lastReceived=System.currentTimeMillis();if(receiver!=null)receiver.accept(event);
        }catch(IOException error){throw error;}catch(Exception error){throw new IOException("无法导入历史",error);}
    }
    public synchronized void delivered(String key,String secret,String device,List<LanClipboardProtocol.Event> events,String ip){
        if(!allowed(key,secret,device))return;
        try{if(!peer(key).optString("ip").equals(ip)){LanClipboardProtocol.address(ip);JSONObject next=new JSONObject(state.toString());JSONArray peers=next.getJSONArray("peers");for(int i=0;i<peers.length();i++)if(peers.getJSONObject(i).getString("key").equals(key))peers.getJSONObject(i).put("ip",ip);mutate(next);}
            ArrayDeque<LanClipboardProtocol.Event> queue=queues.get(key);if(queue!=null)for(var event:events)if(queue.removeIf(e->e.id().equals(event.id()))){sentCount++;lastSent=System.currentTimeMillis();}connected.put(key,System.currentTimeMillis());errors.remove(key);
        }catch(Exception ignored){}
    }
}
