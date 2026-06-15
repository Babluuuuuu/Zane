package com.babluu.zane;
import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.babluu.zane.databinding.ActivityMainBinding;
import org.json.*;
import java.util.*;
import okhttp3.*;
public class MainActivity extends AppCompatActivity {
    private static final String API_KEY="YOUR_API_KEY_HERE";
    private ActivityMainBinding b;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private OkHttpClient http;
    private ZaneActionExecutor ax;
    private JSONArray history=new JSONArray();
    private Handler h=new Handler(Looper.getMainLooper());
    private boolean listening=false,ttsOk=false,wakeSvc=false;
    private String state="IDLE";
    private BroadcastReceiver recv=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            if("ZANE_STATE_UPDATE".equals(i.getAction())){
                String s=i.getStringExtra("state"),t=i.getStringExtra("text");
                h.post(()->{
                    if("AWAKE".equals(s)){setState("LISTENING");b.tvResponse.setText(t);b.tvResponse.setVisibility(View.VISIBLE);}
                    else if("THINKING".equals(s)){setState("THINKING");b.tvTranscript.setText(t);b.tvTranscript.setVisibility(View.VISIBLE);}
                    else if("SPEAKING".equals(s)){setState("SPEAKING");b.tvResponse.setText(t);b.tvResponse.setVisibility(View.VISIBLE);}
                });
            }
        }
    };
    private static final String SYS="You are ZANE, Babluu personal AI assistant. For phone actions respond: ACTION:{type:OPEN_APP,app:FreeFire} then reply. Keep replies under 2 sentences.";
    protected void onCreate(Bundle s){
        super.onCreate(s);
        b=ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        http=new OkHttpClient();
        ax=new ZaneActionExecutor(this);
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS){
                tts.setLanguage(Locale.US);
                tts.setPitch(0.85f);
                tts.setSpeechRate(1.05f);
                ttsOk=true;
            }
        });
        b.orbContainer.setOnClickListener(v->{
            if(wakeSvc){Toast.makeText(this,"Say Hey ZANE",Toast.LENGTH_SHORT).show();return;}
            if("IDLE".equals(state))startListen();
            else if("LISTENING".equals(state))stopListen();
        });
        b.wakeToggle.setOnCheckedChangeListener((btn,on)->{
            if(on)startWake();
            else stopWake();
        });
        IntentFilter f=new IntentFilter("ZANE_STATE_UPDATE");
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU){
            registerReceiver(recv,f,RECEIVER_NOT_EXPORTED);
        }else{
            registerReceiver(recv,f);
        }
        checkPerms();
        setState("IDLE");
    }
    private void startWake(){
        startForegroundService(new Intent(this,WakeWordService.class));
        wakeSvc=true;
        b.tvWakeLabel.setText("Hey ZANE Mode: ON");
        b.tvWakeLabel.setTextColor(getColor(R.color.accent_green));
        b.tvStatus.setText("Say Hey ZANE");
    }
    private void stopWake(){
        stopService(new Intent(this,WakeWordService.class));
        wakeSvc=false;
        b.tvWakeLabel.setText("Hey ZANE Mode: OFF");
        b.tvWakeLabel.setTextColor(getColor(R.color.text_muted));
        setState("IDLE");
    }
    private void startListen(){
        if(listening)return;
        if(sr!=null)sr.destroy();
        sr=SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle p){setState("LISTENING");}
            public void onResults(Bundle r){
                List<String> m=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(m!=null&&!m.isEmpty()){
                    String q=m.get(0);
                    h.post(()->{b.tvTranscript.setText(q);b.tvTranscript.setVisibility(View.VISIBLE);});
                    setState("THINKING");
                    callAPI(q);
                }else setState("IDLE");
                listening=false;
            }
            public void onError(int e){
                listening=false;
                h.post(()->{b.tvStatus.setText("Try again");h.postDelayed(()->setState("IDLE"),2000);});
            }
            public void onPartialResults(Bundle r){
                List<String> p=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(p!=null&&!p.isEmpty())h.post(()->{b.tvTranscript.setText(p.get(0));b.tvTranscript.setVisibility(View.VISIBLE);});
            }
            public void onBeginningOfSpeech(){}
            public void onRmsChanged(float r){}
            public void onBufferReceived(byte[] d){}
            public void onEndOfSpeech(){setState("THINKING");}
            public void onEvent(int e,Bundle p){}
        });
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault());
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        listening=true;
        sr.startListening(i);
    }
    private void stopListen(){if(sr!=null)sr.stopListening();listening=false;}
    private void callAPI(String q){
        try{
            JSONObject u=new JSONObject();
            u.put("role","user");
            u.put("content",q);
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
                public void onFailure(Call c,java.io.IOException e){
                    h.post(()->handleResp("Network error.",null));
                }
                public void onResponse(Call c,Response r)throws java.io.IOException{
                    try{
                        JSONObject j=new JSONObject(r.body().string());
                        String raw=j.getJSONArray("content").getJSONObject(0).getString("text");
                        JSONObject a=null;
                        String reply=raw;
                        if(raw.startsWith("ACTION:")){
                            String[]lines=raw.split(System.lineSeparator(),2);if(lines.length<2)lines=new String[]{raw,""};
                            try{
                                a=new JSONObject(lines[0].replace("ACTION:","").trim());
                                reply=lines.length>1?lines[1].trim():"Done.";
                            }catch(Exception ex){}
                        }
                        final String fr=reply;
                        final JSONObject fa=a;
                        h.post(()->handleResp(fr,fa));
                    }catch(Exception e){
                        h.post(()->handleResp("Error.",null));
                    }
                }
            });
        }catch(Exception e){
            Log.e("ZANE",e.getMessage());
        }
    }
    private void handleResp(String reply,JSONObject action){
        b.tvResponse.setText(reply);
        b.tvResponse.setVisibility(View.VISIBLE);
        if(action!=null){showBadge(action);ax.execute(action);}
        setState("SPEAKING");
        speak(reply);
    }
    private void speak(String t){
        if(!ttsOk)return;
        tts.stop();
        Bundle p=new Bundle();
        p.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,"r");
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener(){
            public void onStart(String id){}
            public void onDone(String id){h.post(()->setState("IDLE"));}
            public void onError(String id){h.post(()->setState("IDLE"));}
        });
        tts.speak(t,TextToSpeech.QUEUE_FLUSH,p,"r");
    }
    private void setState(String s){
        state=s;
        switch(s){
            case "IDLE":
                b.tvStatus.setText(wakeSvc?"Say Hey ZANE":"Tap orb to speak");
                b.tvTranscript.setVisibility(View.GONE);
                b.tvResponse.setVisibility(View.GONE);
                b.actionBadge.setVisibility(View.GONE);
                b.waveContainer.setVisibility(View.GONE);
                setOrb("#0F172A");
                break;
            case "LISTENING":
                b.tvStatus.setText("Listening...");
                b.waveContainer.setVisibility(View.VISIBLE);
                setOrb("#00F5A0");
                break;
            case "THINKING":
                b.tvStatus.setText("Processing...");
                b.waveContainer.setVisibility(View.GONE);
                setOrb("#2563EB");
                break;
            case "SPEAKING":
                b.tvStatus.setText("ZANE speaking");
                setOrb("#7C3AED");
                break;
        }
    }
    private void showBadge(JSONObject a){
        try{
            String t=a.getString("type");
            if(a.has("app"))t+=" -> "+a.getString("app");
            else if(a.has("contact"))t+=" -> "+a.getString("contact");
            b.tvActionType.setText(t);
            b.actionBadge.setVisibility(View.VISIBLE);
        }catch(Exception e){}
    }
    private void setOrb(String hex){
        b.orbInner.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor(hex)));
    }
    private void checkPerms(){
        String[]perms={Manifest.permission.RECORD_AUDIO,Manifest.permission.CALL_PHONE,Manifest.permission.READ_CONTACTS,Manifest.permission.READ_PHONE_STATE,Manifest.permission.ANSWER_PHONE_CALLS,Manifest.permission.CAMERA};
        boolean ok=true;
        for(String p:perms)if(ContextCompat.checkSelfPermission(this,p)!=PackageManager.PERMISSION_GRANTED){ok=false;break;}
        if(!ok)ActivityCompat.requestPermissions(this,perms,100);
    }
    protected void onDestroy(){
        super.onDestroy();
        try{unregisterReceiver(recv);}catch(Exception e){}
        if(sr!=null)sr.destroy();
        if(tts!=null){tts.stop();tts.shutdown();}
        if(ax!=null)ax.cleanup();
    }
}