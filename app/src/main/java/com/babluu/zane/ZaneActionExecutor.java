package com.babluu.zane;
import android.content.*;
import android.hardware.camera2.*;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.AlarmClock;
import android.telecom.TelecomManager;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONObject;
public class ZaneActionExecutor {
    private final Context ctx;
    private CameraManager cam;
    private String camId;
    public ZaneActionExecutor(Context c){
        this.ctx=c;
        try{cam=(CameraManager)c.getSystemService(Context.CAMERA_SERVICE);camId=cam.getCameraIdList()[0];}catch(Exception e){}
    }
    public void execute(JSONObject a){
        try{
            switch(a.getString("type")){
                case "OPEN_APP": openApp(a.optString("app","")); break;
                case "MAKE_CALL": makeCall(a.optString("contact","")); break;
                case "END_CALL": endCall(); break;
                case "SEND_WHATSAPP": sendWA(a.optString("contact",""),a.optString("message","")); break;
                case "SET_ALARM": setAlarm(a.optString("time","8:00"),a.optString("label","ZANE Alarm")); break;
                case "PLAY_MUSIC": playMusic(a.optString("query","")); break;
                case "OPEN_URL": openUrl(a.optString("url","")); break;
                case "SEARCH": searchWeb(a.optString("query","")); break;
                case "TORCH_ON": setTorch(true); break;
                case "TORCH_OFF": setTorch(false); break;
                case "VOLUME_UP": vol(AudioManager.ADJUST_RAISE); break;
                case "VOLUME_DOWN": vol(AudioManager.ADJUST_LOWER); break;
                case "VOLUME_MUTE": vol(AudioManager.ADJUST_MUTE); break;
            }
        }catch(Exception e){Log.e("ZANE",e.getMessage());}
    }
    private void openApp(String n){
        String pkg=getPkg(n);
        if(pkg!=null){Intent i=ctx.getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i);return;}}
        try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("market://search?q="+n));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i);}catch(Exception e){}
    }
    private String getPkg(String n){
        switch(n.toLowerCase().trim()){
            case "freefire": case "free fire": return "com.dts.freefireth";
            case "whatsapp": return "com.whatsapp";
            case "instagram": return "com.instagram.android";
            case "youtube": return "com.google.android.youtube";
            case "chrome": return "com.android.chrome";
            case "spotify": return "com.spotify.music";
            case "telegram": return "org.telegram.messenger";
            case "settings": return "com.android.settings";
            case "camera": return "com.android.camera2";
            case "pubg": case "bgmi": return "com.pubg.imobile";
            case "snapchat": return "com.snapchat.android";
            case "maps": case "google maps": return "com.google.android.apps.maps";
            case "gmail": return "com.google.android.gm";
            case "netflix": return "com.netflix.mediaclient";
            case "phonepe": return "com.phonepe.app";
            case "gpay": return "com.google.android.apps.nbu.paisa.user";
            case "paytm": return "net.one97.paytm";
            case "swiggy": return "in.swiggy.android";
            case "zomato": return "com.application.zomato";
            default: return null;
        }
    }
    private void makeCall(String c){
        try{Intent i=new Intent(Intent.ACTION_CALL,Uri.parse("tel:"+c));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i);}catch(Exception e){toast("Call permission needed");}
    }
    private void endCall(){
        try{if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P){TelecomManager t=(TelecomManager)ctx.getSystemService(Context.TELECOM_SERVICE);if(t!=null)t.endCall();}}catch(Exception e){}
    }
    private void sendWA(String c,String m){
        try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://api.whatsapp.com/send?phone="+c+"&text="+Uri.encode(m)));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i);}catch(Exception e){toast("WhatsApp not installed");}
    }
    private void setAlarm(String t,String l){
        try{String[]p=t.split(":");Intent i=new Intent(AlarmClock.ACTION_SET_ALARM);i.putExtra(AlarmClock.EXTRA_HOUR,Integer.parseInt(p[0].trim()));i.putExtra(AlarmClock.EXTRA_MINUTES,p.length>1?Integer.parseInt(p[1].trim()):0);i.putExtra(AlarmClock.EXTRA_MESSAGE,l);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i);}catch(Exception e){}
    }
    private void playMusic(String q){
        try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("spotify:search:"+q));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i);}catch(Exception e){openUrl("https://music.youtube.com/search?q="+Uri.encode(q));}
    }
    private void openUrl(String u){
        try{if(!u.startsWith("http"))u="https://"+u;Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(u));i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i);}catch(Exception e){}
    }
    private void searchWeb(String q){
        try{Intent i=new Intent(Intent.ACTION_WEB_SEARCH);i.putExtra("query",q);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);ctx.startActivity(i);}catch(Exception e){openUrl("https://www.google.com/search?q="+Uri.encode(q));}
    }
    private void setTorch(boolean on){
        try{if(cam!=null&&camId!=null)cam.setTorchMode(camId,on);}catch(Exception e){}
    }
    private void vol(int d){
        try{AudioManager a=(AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);if(a!=null)a.adjustStreamVolume(AudioManager.STREAM_MUSIC,d,AudioManager.FLAG_SHOW_UI);}catch(Exception e){}
    }
    private void toast(String m){Toast.makeText(ctx,m,Toast.LENGTH_SHORT).show();}
    public void cleanup(){}
}