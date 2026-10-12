package app.msime.android.test;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** 在隔离模拟器内检查加密存储、跨进程配对和真实 Windows 端收发。 */
public final class LanClipboardDeviceSmoke extends DeviceSmoke {
    private String shell(String command) throws Exception {
        try(ParcelFileDescriptor fd=automation.executeShellCommand(command);InputStream input=new FileInputStream(fd.getFileDescriptor())){
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            while((n=input.read(buffer))!=-1){if(out.size()+n>65536)throw new AssertionError("Shell response exceeded bound");out.write(buffer,0,n);}
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private JSONObject request(String action,Bundle args) throws Exception {
        Bundle answer=getTargetContext().getContentResolver().call(Uri.parse("content://app.msime.android.clipboardlan"),action,null,args);
        if(answer==null||answer.containsKey("error"))throw new AssertionError("LAN operation refused: "+action);
        return new JSONObject(answer.getString("value"));
    }
    @Override protected String successDescription(){return "Windows TLS to Android system paste and recent chip, Android copy to Windows, direct history, 123/456/789 grid and revoke";}
    @Override protected void runChecks() throws Exception {
        if(!shell("getprop ro.kernel.qemu").trim().equals("1"))throw new AssertionError("Only dedicated emulator");
        Context context=getTargetContext();
        Class<?> host=Class.forName("app.msime.android.home.HostStore",true,context.getClassLoader());
        Method put=host.getMethod("putPreference",Context.class,String.class,Object.class);
        stage="enable clipboard history";
        if(put.invoke(null,context,"clipboard_history",true)==null)throw new AssertionError("History setting refused");
        put.invoke(null,context,"usage_reporting",false);
        put.invoke(null,context,"touch_incognito",false);
        shell("am start -W -n app.msime.android/app.msime.android.home.HomeActivity");
        stage="enable LAN";Bundle enabled=new Bundle();enabled.putBoolean("enabled",true);JSONObject status=request("enable",enabled);
        String device=status.getString("device");
        stage="invitation storage";JSONObject invite=request("invite",null);String invitation=invite.getString("code");
        File encrypted=new File(context.getFilesDir(),"clipboard-lan.enc");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(encrypted)){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>2_000_000)throw new AssertionError("Encrypted file exceeded bound");bytes.write(buffer,0,n);}}
        if(new String(bytes.toByteArray(),StandardCharsets.ISO_8859_1).contains(invitation.split(":")[3]))throw new AssertionError("Invitation persisted plaintext");
        request("cancel",null);
        stage="invalid QR rejection";Bundle invalid=new Bundle();invalid.putString("text","https://example.com");
        Bundle refused=context.getContentResolver().call(Uri.parse("content://app.msime.android.clipboardlan"),"pair",null,invalid);
        if(refused==null||!refused.containsKey("error"))throw new AssertionError("URL accepted as pairing");
        stage="save Windows pair";Bundle removePrevious=new Bundle();removePrevious.putString("text","host");request("remove",removePrevious);
        Bundle pair=new Bundle();pair.putString("text","msime1:host:"+"1".repeat(32)+":"+"22".repeat(32));request("pair",pair);
        shell("am start -W -n app.msime.android.test/app.msime.android.test.EditorActivity");tap(field("msime-test-plain"));
        Bundle ready=new Bundle();ready.putString("lan_device",device);sendStatus(0,ready);
        stage="connect Windows";boolean connected=false;
        for(int i=0;i<300;i++){JSONObject current=request("status",null);if(!current.getString("device").equals(device))throw new AssertionError("Device identity changed");JSONArray peers=current.getJSONArray("peers");if(peers.length()==1&&peers.getJSONObject(0).optBoolean("connected")&&current.optLong("received")>0){connected=true;break;}SystemClock.sleep(200);}
        if(!connected)throw new AssertionError("Windows TLS endpoint did not connect: "+request("status",null));
        stage="Windows event reached shared history";
        Class<?> nativeClient=Class.forName("app.msime.android.NativeClient",true,context.getClassLoader());
        Method history=nativeClient.getMethod("mobileClipboardHistory",String.class);
        JSONObject query=new JSONObject().put("directory",context.getFilesDir().getAbsolutePath()).put("action",new JSONObject().put("operation","load"));
        String response=(String)history.invoke(null,query.toString());if(!response.contains("Windows中文"))throw new AssertionError("Remote event absent from authority");
        stage="remote system clipboard";
        ClipboardManager clipboard=context.getSystemService(ClipboardManager.class);
        ClipData primary=clipboard.getPrimaryClip();
        if(primary==null||!"Windows中文 😀".contentEquals(primary.getItemAt(0).getText()))throw new AssertionError("System clipboard did not receive remote text");
        stage="remote recent clipboard suggestion";
        tap(imeTextContains("Windows中文").and(AccessibilityNodeInfo::isClickable));
        await(field("msime-test-plain").and(node->equalsText("Windows中文 😀",node.getText())));
        stage="ordinary system paste";
        clearEditor();
        if(!await(field("msime-test-plain")).performAction(AccessibilityNodeInfo.ACTION_PASTE))throw new AssertionError("Editor paste refused");
        await(field("msime-test-plain").and(node->equalsText("Windows中文 😀",node.getText())));
        stage="direct clipboard history shortcut";
        tap(described("剪贴板").and(AccessibilityNodeInfo::isClickable));
        await(imeTextContains("Windows中文"));
        tap(described("剪贴板").and(AccessibilityNodeInfo::isClickable));
        stage="compact toolbar keeps clipboard access";
        Method putLocal=Class.forName("app.msime.android.AndroidLocalSettings",true,context.getClassLoader()).getMethod("put",Context.class,String.class,Object.class);
        putLocal.invoke(null,context,"platform.android.toolbar_hidden",true);
        long compactDeadline=SystemClock.uptimeMillis()+10000;boolean compact=false;
        while(SystemClock.uptimeMillis()<compactDeadline){
            compact=true;for(var window:automation.getWindows())if(find(window.getRoot(),described("表情"))!=null)compact=false;
            if(compact)break;SystemClock.sleep(100);
        }
        if(!compact)throw new AssertionError("Compact toolbar did not hide optional buttons");
        tap(described("剪贴板").and(AccessibilityNodeInfo::isClickable));
        await(imeTextContains("Windows中文"));
        tap(described("剪贴板").and(AccessibilityNodeInfo::isClickable));
        putLocal.invoke(null,context,"platform.android.toolbar_hidden",false);
        await(described("表情"));
        stage="number grid geometry";
        clearEditor();tap(key("123"));
        Rect[] cells=new Rect[9];
        for(int i=0;i<9;i++){cells[i]=new Rect();await(key(Integer.toString(i+1)).and(AccessibilityNodeInfo::isClickable)).getBoundsInScreen(cells[i]);}
        for(int row=0;row<3;row++)for(int col=0;col<3;col++){
            Rect cell=cells[row*3+col];
            if(cell.centerX()!=cells[col].centerX()||cell.centerY()!=cells[row*3].centerY())throw new AssertionError("Number grid column or row shifted");
        }
        if(!(cells[0].centerX()<cells[1].centerX()&&cells[1].centerX()<cells[2].centerX()&&cells[0].centerY()<cells[3].centerY()&&cells[3].centerY()<cells[6].centerY()))throw new AssertionError("Numbers not arranged 123/456/789");
        for(String digit:new String[]{"1","2","3","4","5","6","7","8","9","0"})tap(key(digit));
        await(field("msime-test-plain").and(node->equalsText("1234567890",node.getText())));
        tap(described("切换到字母键盘"));
        stage="fresh local clipboard capture";
        clipboard.setPrimaryClip(ClipData.newPlainText("synthetic LAN fixture","安卓中文 😀"));
        boolean sent=false;for(int i=0;i<100;i++){if(request("status",null).optLong("sent")>0){sent=true;break;}SystemClock.sleep(200);}
        if(!sent)throw new AssertionError("Local system copy did not receive Windows acknowledgement");
        stage="revoke peer";Bundle remove=new Bundle();remove.putString("text","host");if(request("remove",remove).getJSONArray("peers").length()!=0)throw new AssertionError("Peer not removed");
        enabled.putBoolean("enabled",false);if(request("enable",enabled).getBoolean("active"))throw new AssertionError("Disabled LAN still active");
        stage="reload encrypted pairing after process restart";
        android.app.ActivityManager manager=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        for(android.app.ActivityManager.RunningAppProcessInfo process:manager.getRunningAppProcesses()){
            if(process.processName.equals(context.getPackageName()+":ime")&&process.pid!=android.os.Process.myPid())android.os.Process.killProcess(process.pid);
        }
        SystemClock.sleep(500);
        JSONObject restarted=request("status",null);
        if(!restarted.getString("device").equals(device)||restarted.getBoolean("enabled")||restarted.getJSONArray("peers").length()!=0)throw new AssertionError("Encrypted state not retained");
    }
    private void clearEditor(){
        Bundle text=new Bundle();text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"");
        if(!await(field("msime-test-plain")).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,text))throw new AssertionError("Editor clear refused");
        await(field("msime-test-plain").and(node->node.isShowingHintText()||node.getText()==null||node.getText().length()==0));
    }
}
