package app.msime.android.home;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import app.msime.android.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.*;
import java.io.*;
import org.json.*;

/** 局域网设备配对页：扫码仅填表，保存时才建立信任。 */
public final class LanClipboardPage extends DetailPage {
    private EditText code,ip;
    private TextView status,device;
    private GroupCard.Row toggle;
    private LinearLayout devices;
    private AlertDialog invitation;
    private boolean busy;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String> notifications=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{});
    private final Runnable poll=new Runnable(){public void run(){if(isAdded()&&!isHidden()&&getView()!=null){refresh();handler.postDelayed(this,3000);}}};
    private final ActivityResultLauncher<ScanOptions> camera=registerForActivityResult(new ScanContract(),result->{if(result.getContents()!=null)fill(result.getContents());});
    private final ActivityResultLauncher<String[]> image=registerForActivityResult(new ActivityResultContracts.OpenDocument(),uri->{
        if(uri==null)return;
        Context context=requireContext().getApplicationContext();
        AboutPage.network(this,()->{
            byte[] bytes;
            try(InputStream input=context.getContentResolver().openInputStream(uri)){
                if(input==null)throw new IOException("图片不可读");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                while((n=input.read(buffer))!=-1){if(out.size()+n>8_000_000)throw new IOException("图片过大，请裁剪后重试");out.write(buffer,0,n);}bytes=out.toByteArray();
            }
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(options.outWidth<1||options.outHeight<1||options.outWidth>30000||options.outHeight>30000)throw new IOException("图片尺寸无效");
            options.inJustDecodeBounds=false;options.inSampleSize=1;while(options.outWidth/options.inSampleSize>2048||options.outHeight/options.inSampleSize>2048)options.inSampleSize*=2;
            Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)throw new IOException("图片格式不支持");
            try{int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);
                return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(w,h,pixels)))).getText();
            }finally{bitmap.recycle();}
        },outcome->{if(outcome.error()!=null)MsToast.show(requireContext(),"未识别到配对二维码，请裁剪图片后重试");else fill(outcome.value());});
    });
    @Override protected void buildContent(LinearLayout column,Bundle args){
        Context context=requireContext();
        GroupCard intro=GroupCard.add(column,null);
        intro.note("与 zhuoyiwan 改版 Windows 输入法互通。同一局域网内分享新复制的文字，配对一次即可双向同步；换 IP 会自动发现重连。无需账号。");
        intro.note("开启后会同时启用本地剪贴板历史。收到的新文字可直接粘贴，也会出现在键盘「最近复制」提示中。保持通知中的同步服务开启，并将水杉设为默认输入法。");
        toggle=intro.toggle("同步系统剪贴板","在手机与电脑之间复制、粘贴文字",false,value->{Bundle b=new Bundle();b.putBoolean("enabled",value);call("enable",b,result->{
            if(value && Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
                notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        });});
        device=intro.note("正在读取设备身份…");status=intro.note("正在读取…");
        intro.button("立即读取并同步", "重新读取当前系统剪贴板，发送给已配对设备", "立即同步", () -> call("capture",null,value -> handler.postDelayed(this::refresh,500)));
        intro.nav("剪贴板历史", "查看收到和复制过的文字", null, () -> SettingsNavigator.open(requireContext(),PageId.CLIPBOARD_SEARCH,null));
        GroupCard pairing=GroupCard.add(column,"设备配对");
        code=field(pairing,"配对码",false);ip=field(pairing,"对方 IP（可选，例如 192.168.1.8）",true);
        pairing.button("扫码配对", "扫描电脑或另一台手机的配对二维码", "扫码",()->camera.launch(new ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt("扫描局域网剪贴板配对二维码").setBeepEnabled(false).setOrientationLocked(false)));
        pairing.button("从图片识别", "选择含配对二维码的图片", "选图片",()->image.launch(new String[]{"image/*"}));
        pairing.button("保存并连接", "确认对方设备后建立双向配对", "保存并连接",()->{
            try{var p=LanClipboardProtocol.pairing(code.getText().toString().trim());
                new AlertDialog.Builder(context).setTitle("配对这台设备？").setMessage(p.device()).setNegativeButton("取消",null)
                    .setPositiveButton("保存并连接",(dialog,which)->{Bundle b=new Bundle();b.putString("text",code.getText().toString().trim());b.putString("ip",ip.getText().toString().trim());call("pair",b,value->{code.setText("");ip.setText("");MsToast.show(context,"已保存，正在发现并连接设备");});}).show();
            }catch(IOException error){MsToast.show(context,error.getMessage());}
        });
        pairing.button("生成配对二维码", "让另一台设备扫描，5 分钟有效", "生成",()->call("invite",null,this::showInvitation));
        devices=new LinearLayout(context);devices.setOrientation(LinearLayout.VERTICAL);column.addView(devices);
        refresh();
    }
    private EditText field(GroupCard card,String hint,boolean address){
        EditText field=new EditText(requireContext());field.setHint(hint);field.setTextSize(14);field.setSingleLine(address);
        field.setInputType(address?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(address?15:233)});
        int padding=(int)(16*getResources().getDisplayMetrics().density);field.setPadding(padding,padding,padding,padding);
        card.card().addView(field,new LinearLayout.LayoutParams(-1,-2));return field;
    }
    private void fill(String text){
        try{var pairing=LanClipboardProtocol.pairing(text);if(code!=null){code.setText(text);MsToast.show(requireContext(),"识别到设备 "+pairing.device()+"，请点保存并连接确认");}}
        catch(IOException error){MsToast.show(requireContext(),"这不是有效的局域网剪贴板配对码");}
    }
    private void call(String method,Bundle args,java.util.function.Consumer<JSONObject> done){
        if(busy||getView()==null)return;busy=true;Context context=requireContext().getApplicationContext();
        AboutPage.network(this,()->{
            Bundle response=LanClipboardProvider.request(context,method,args);
            if(response==null)throw new IOException("输入法进程未响应");
            if(response.containsKey("error"))throw new IOException(response.getString("error"));
            return new JSONObject(response.getString("value"));
        },outcome->{busy=false;if(outcome.error()!=null){MsToast.show(requireContext(),outcome.error().getMessage());return;}
            if(done!=null)done.accept(outcome.value());if(!method.equals("invite"))render(outcome.value());});
    }
    private void refresh(){if(getView()==null&&status==null)return;call("status",null,null);}
    private void render(JSONObject value){
        if(status==null||devices==null)return;
        toggle.setChecked(value.optBoolean("enabled"));device.setText("本机设备 ID："+value.optString("device"));
        String text=!value.optBoolean("enabled")?"同步已关闭":value.optBoolean("private")?"隐私模式已开启，暂停同步":!value.optBoolean("history")?"请到本机 → 隐私，打开剪贴板历史":value.optString("network");
        if(text.isEmpty())text="正在发现或连接已配对设备";
        status.setText(text+"\n已发送 "+value.optLong("sent")+" 条 · 已接收 "+value.optLong("received")+" 条"
            +(value.optString("captureStatus").isEmpty()?"":"\n"+value.optString("captureStatus"))
            +(value.optString("pasteStatus").isEmpty()?"":"\n"+value.optString("pasteStatus")));
        devices.removeAllViews();GroupCard list=GroupCard.add(devices,"已配对设备");JSONArray peers=value.optJSONArray("peers");
        if(peers==null||peers.length()==0){list.note("还没有配对设备。扫码后，两端会自动互相配对。");return;}
        for(int i=0;i<peers.length();i++){
            JSONObject peer=peers.optJSONObject(i);if(peer==null)continue;String id=peer.optString("device");
            list.button(id,(peer.optBoolean("connected")?"已连接":peer.optString("error").isEmpty()?"等待连接":peer.optString("error"))+(peer.optString("ip").isEmpty()?"":" · "+peer.optString("ip")),"移除",()->
                new AlertDialog.Builder(requireContext()).setTitle("移除配对设备？").setMessage(id+"\n如需完全解除配对，请在对方设备上也移除本机。")
                    .setNegativeButton("取消",null).setPositiveButton("移除",(dialog,which)->{Bundle b=new Bundle();b.putString("text",id);call("remove",b,null);}).show());
        }
    }
    private void showInvitation(JSONObject value){
        try{
            String payload=value.getString("code");var matrix=new QRCodeWriter().encode(payload,BarcodeFormat.QR_CODE,600,600);
            Bitmap bitmap=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888);for(int y=0;y<600;y++)for(int x=0;x<600;x++)bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
            LinearLayout body=new LinearLayout(requireContext());body.setOrientation(LinearLayout.VERTICAL);ImageView qr=new ImageView(requireContext());qr.setImageBitmap(bitmap);qr.setContentDescription("配对二维码，含临时密钥");body.addView(qr,new LinearLayout.LayoutParams(-1,(int)(280*getResources().getDisplayMetrics().density)));
            TextView manual=new TextView(requireContext());manual.setText(payload);manual.setTextIsSelectable(true);manual.setTextSize(12);body.addView(manual);
            invitation=new AlertDialog.Builder(requireContext()).setTitle("让另一台设备扫描").setMessage("5 分钟有效，请只分享给你信任的设备。").setView(body).setNegativeButton("关闭",null).create();
            invitation.setOnDismissListener(dialog->{bitmap.recycle();invitation=null;Context context=getContext();if(context!=null){Context app=context.getApplicationContext();java.util.concurrent.CompletableFuture.runAsync(()->{try{LanClipboardProvider.request(app,"cancel",null);}catch(RuntimeException ignored){}});}});
            invitation.show();if(invitation.getWindow()!=null)invitation.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            AlertDialog current=invitation;handler.postDelayed(()->{if(current.isShowing())current.dismiss();},Math.max(1,value.optLong("expires")-System.currentTimeMillis()));
        }catch(Exception error){MsToast.show(requireContext(),"无法显示配对二维码");}
    }
    @Override protected void onBecameVisible(){refresh();}
    @Override public void onResume(){super.onResume();handler.removeCallbacks(poll);handler.postDelayed(poll,3000);}
    @Override public void onPause(){handler.removeCallbacks(poll);if(invitation!=null)invitation.dismiss();super.onPause();}
    @Override public void onHiddenChanged(boolean hidden){super.onHiddenChanged(hidden);if(hidden){handler.removeCallbacks(poll);if(invitation!=null)invitation.dismiss();}else handler.postDelayed(poll,3000);}
    @Override public void onDestroyView(){handler.removeCallbacksAndMessages(null);if(invitation!=null)invitation.dismiss();status=null;device=null;devices=null;code=null;ip=null;busy=false;super.onDestroyView();}
}
