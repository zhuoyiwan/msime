package app.msime.android;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** 不导出的进程内入口，让设置进程访问 :ime 的单一信任存储。 */
public final class LanClipboardProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    public static Bundle request(Context context,String action,Bundle args){
        return context.getContentResolver().call(Uri.parse("content://"+context.getPackageName()+".clipboardlan"),action,null,args);
    }
    @Override public Bundle call(String method,String arg,Bundle extras){
        Bundle response=new Bundle();
        try{
            if(extras==null)extras=new Bundle();String code=extras.getString("text",""),ip=extras.getString("ip","");
            if(code.length()>233||ip.length()>15)throw new java.io.IOException("配对参数过长");
            response.putString("value",LanClipboardRuntime.get(getContext()).command(method,code,ip,extras.getBoolean("enabled")).toString());
        }catch(Exception error){response.putString("error",error instanceof java.io.IOException?error.getMessage():"局域网配对存储不可用，请重试");}
        return response;
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){throw new UnsupportedOperationException();}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
