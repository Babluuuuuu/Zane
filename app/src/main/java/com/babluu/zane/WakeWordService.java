package com.babluu.zane;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import org.json.*;
import java.util.*;
import okhttp3.*;
public class WakeWordService extends Service {
    private static final String CHANNEL_ID="zane_wake";
    private static final String API_KEY="YOUR_API_KEY_HERE";
    private static final String[] WAKE={"hey zane","hey jane","zane","ok zane","hi zane"};
    private static final String[] GREETS={"Yes Babluu, what is going on?","Hey Babluu, I am here. What do you need?","What is up Babluu?","Yes boss, what can I do for you?","ZANE online. Talk to me Babluu."};
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private OkHttpClient http;
    private Handler h;
    private boolean running=false,ttsOk=false;
    private JSONArray history=new JSONArray();
    private String state="SLEEP";
    private static final String SYS="You are ZANE, Babluu personal AI assistant. Sharp, calm, direct. For phone actions respond EXACTLY: ACTION:{type:OPEN_APP,app:FreeFire} then your reply. Actions: OPEN_APP,MAKE_CALL,END_CALL,SEND_WHATSAPP,SET_ALARM,PLAY_MUSIC,OPEN_URL,SEARCH,TORCH_ON,TORCH_OFF,VOLUME_UP,VOLUME_DOWN. Keep replies under 2 sentences. Address Babluu by name.";
    public int onStartCommand(Intent i,int f,int s){running=true;return START_STICKY;}
    public IBinder onBind(Intent i){return null;}
    public void onCreate(){
        super.onCreate();
        h=new Handler(Looper.getMainLooper());
        http=new OkHttpClient();
        createChannel();
        startForeground(2001,buildNotif("Say Hey ZANE"));
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"ZANE::Wake").acquire();
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS){
                tts.setLanguage(java.util.Locale.US);
                tts.setPitch(0.85f);
                tts.setSpeechRate(1.0f);
                ttsOk=true;
                h.postDelayed(this::listenWake,1000);
            }
        });
    }
    private void listenWake(){
        if(!running)return;
        state="SLEEP";
        updateNotif("Sleeping... Say Hey ZANE");
        if(sr!=null)sr.destroy();
        sr=SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener(){
            public void onResults(Bundle b){
                List<String> m=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(m!=null&&!m.isEmpty()){String t=m.get(0).toLowerCase();if(isWake(t))onWake(t);else h.postDelayed(()->listenWake(),200);}
                else h.postDelayed(()->listenWake(),200);
            }
            public void onError(int e){h.postDelayed(()->listenWake(),300);}
            public void onPartialResults(Bundle b){
                List<String> p=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(p!=null&&!p.isEmpty()&&isWake(p.get(0).toLowerCase())&&state.equals("SLEEP"))sr.stopListening();
            }
            public void onReadyForSpeech(Bundle p){}
            public void onBeginningOfSpeech(){}
            public void onRmsChanged(float r){}
            public void onBufferReceived(byte[] b){}
            public void onEndOfSpeech(){}
            public void onEvent(int e,Bundle p){}
        });
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,1500);
        sr.startListening(i);
    }
    private boolean isWake(String t){for(String w:WAKE)if(t.contains(w))return true;return false;}
    private void onWake(String t){
        if(!state.equals("SLEEP"))return;
        state="AWAKE";
        updateNotif("Awake!");
        String g=GREETS[new Random().nextInt(GREETS.length)];
        broadcast("AWAKE",g);
        if(ttsOk){
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                public void onStart(String id){}
                public void onDone(String id){h.post(()->listenCmd());}
                public void onError(String id){h.post(()->listenCmd());}
            });
            tts.speak(g,TextToSpeech.QUEUE_FLUSH,null,"g");
        }else listenCmd();
    }
    private void listenCmd(){
        state="LISTEN";
        updateNotif("Listening for command...");
        if(sr!=null)sr.destroy();
        sr=SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener(){
            public void onResults(Bundle b){
                List<String> m=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(m!=null&&!m.isEmpty()){broadcast("THINKING",m.get(0));process(m.get(0));}
                else{speak("Didn't catch that Babluu.");h.postDelayed(()->listenWake(),3000);}
            }
            public void onError(int e){speak("Call me when you need me Babluu.");h.postDelayed(()->listenWake(),3000);}
            public void onReadyForSpeech(Bundle p){}
            public void onBeginningOfSpeech(){}
            public void onRmsChanged(float r){}
            public void onBufferReceived(byte[] b){}
            public void onEndOfSpeech(){}
            public void onPartialResults(Bundle p){}
            public void onEvent(int e,Bundle p){}
        });
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,3000);
        sr.startListening(i);
    }
    private void process(String cmd){
        state="THINK";
        updateNotif("Thinking...");
        try{
            JSONObject u=new JSONObject();
            u.put("role","user");
            u.put("content",cmd);
            history.put(u);
            while(history.length()>10)history.remove(0);
            JSONObject body=new JSONObject();
            body.put("model","claude-sonnet-4-6");
            body.put("max_tokens",1000);
            body.put("system",SYS);
            body.put("messages",history);
            Request req=new Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .addHeader("x-api-key",API_KEY)
                .addHeader("anthropic-version","2023-06-01")
                .addHeader("Content-Type","application/json")
                .post(RequestBody.create(body.toString(),MediaType.parse("application/json")))
                .build();
            http.newCall(req).enqueue(new Callback(){
                public void onFailure(Call c,java.io.IOException e){h.post(()->respond("Network error Babluu.",null));}
                public void onResponse(Call c,Response r)throws java.io.IOException{
                    try{
                        JSONObject j=new JSONObject(r.body().string());
                        String raw=j.getJSONArray("content").getJSONObject(0).getString("text");
                        JSONObject a=null;String reply=raw;
                        if(raw.startsWith("ACTION:")){
                            String[]lines=raw.split("\n",2);
                            try{a=new JSONObject(lines[0].replace("ACTION:","").trim());reply=lines.length>1?lines[1].trim():"Done.";}catch(Exception ex){}
                        }
                        final String fr=reply;final JSONObject fa=a;
                        h.post(()->respond(fr,fa));
                    }catch(Exception e){h.post(()->respond("Error Babluu.",null));}
                }
            });
        }catch(Exception e){respond("Error processing Babluu.",null);}
    }
    private void respond(String reply,JSONObject action){
        state="SPEAK";
        updateNotif("Speaking...");
        if(action!=null){new ZaneActionExecutor(this).execute(action);broadcast("ACTION",action.toString());}
        broadcast("SPEAKING",reply);
        if(ttsOk){
            tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
                public void onStart(String id){}
                public void onDone(String id){h.postDelayed(()->listenWake(),1000);}
                public void onError(String id){h.postDelayed(()->listenWake(),1000);}
            });
            tts.speak(reply,TextToSpeech.QUEUE_FLUSH,null,"r");
        }else h.postDelayed(()->listenWake(),2000);
    }
    private void speak(String t){if(ttsOk)tts.speak(t,TextToSpeech.QUEUE_FLUSH,null,null);}
    private void broadcast(String s,String t){Intent i=new Intent("ZANE_STATE_UPDATE");i.putExtra("state",s);i.putExtra("text",t);sendBroadcast(i);}
    private void createChannel(){NotificationChannel c=new NotificationChannel(CHANNEL_ID,"ZANE Wake",NotificationManager.IMPORTANCE_LOW);c.setSound(null,null);((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);}
    private Notification buildNotif(String s){return new NotificationCompat.Builder(this,CHANNEL_ID).setContentTitle("ZANE").setContentText(s).setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).setSilent(true).build();}
    private void updateNotif(String s){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(2001,buildNotif(s));}
    public void onDestroy(){running=false;if(sr!=null)sr.destroy();if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}