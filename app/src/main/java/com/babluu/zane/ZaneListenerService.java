package com.babluu.zane;
import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
public class ZaneListenerService extends Service {
    private static final String CHANNEL_ID="zane_service";
    public int onStartCommand(Intent i,int f,int s){return START_STICKY;}
    public IBinder onBind(Intent i){return null;}
    public void onCreate(){
        super.onCreate();
        NotificationChannel c=new NotificationChannel(CHANNEL_ID,"ZANE",NotificationManager.IMPORTANCE_LOW);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        startForeground(1001,new NotificationCompat.Builder(this,CHANNEL_ID).setContentTitle("ZANE Active").setContentText("Ready").setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build());
    }
}