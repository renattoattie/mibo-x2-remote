package br.com.mibolocal.controle;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import br.com.mibolocal.SecureLink;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Handler main=new Handler(Looper.getMainLooper());
 private volatile SecureLink link;
 private volatile int epoch;
 private boolean connected,busy,visible;
 private int pending;
 private EditText ip,code;
 private Button connect,pair,confirm;
 private Switch pointer;
 private TextView status;
 private final ArrayList<Button> controls=new ArrayList<>();
 private static final int BG=0xff111722,PANEL=0xff263348,ACCENT=0xff54d6c7;
 private final Runnable heartbeat=new Runnable(){public void run(){if(visible){if(connected&&pending==0&&!busy)send("PING",true);main.postDelayed(this,30000);}}};
 @Override public void onCreate(Bundle b){super.onCreate(b);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(18),dp(20),dp(24));scroll.addView(root);setContentView(scroll);
  scroll.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
  root.addView(label("MIBO LOCAL",30,Color.WHITE));root.addView(label("Sem depuração · versão de teste 0.2",14,ACCENT));
  ip=entry("IP exibido no app receptor",false);ip.setInputType(android.text.InputType.TYPE_CLASS_PHONE);ip.setText(getPreferences(0).getString("ip",""));root.addView(ip);
  code=entry("Código da TV (só no primeiro pareamento)",true);root.addView(code);
  LinearLayout row=row(root);pair=button("Parear",false);connect=button("Conectar",true);row.addView(pair,cell(48));row.addView(connect,cell(48));pair.setOnClickListener(v->open(true));connect.setOnClickListener(v->open(false));
  status=label("Instale e ative Mibo Receptor Local na TV.",14,0xffc5d0df);status.setMinHeight(dp(72));root.addView(status);
  pointer=new Switch(this);pointer.setText("Modo ponteiro (alternativa às setas)");pointer.setTextColor(Color.WHITE);pointer.setPadding(0,dp(8),0,dp(8));root.addView(pointer);
  pointer.setOnCheckedChangeListener((v,on)->{confirm.setText(on?"Tocar":"OK");if(connected&&!on)send("HIDE_POINTER",false);});
  LinearLayout up=row(root);blank(up);key(up,"▲","UP");blank(up);
  LinearLayout mid=row(root);key(mid,"◀","LEFT");confirm=key(mid,"OK","OK");key(mid,"▶","RIGHT");
  LinearLayout down=row(root);blank(down);key(down,"▼","DOWN");blank(down);
  LinearLayout nav=row(root);key(nav,"Voltar","BACK");key(nav,"Início","HOME");key(nav,"Recentes","RECENTS");
  LinearLayout volume=row(root);key(volume,"Vol −","VOL_DOWN");key(volume,"Mudo","MUTE");key(volume,"Vol +","VOL_UP");
  LinearLayout media=row(root);key(media,"Reproduzir / Pausar","PLAY_PAUSE");
  root.addView(label("Se as setas não funcionarem em uma tela, ligue o modo ponteiro: mova a mira na TV e toque em Tocar. Compatibilidade varia por aplicativo.",12,0xffa8b6c9));
  root.addView(label("Pareamento e comandos criptografados na rede local. Sem conta, anúncios, ADB ou acesso à tela do Mibo pelo celular.",12,0xffa8b6c9));
  update();
 }
 private void open(boolean pairing){
  if(busy||pending>0)return;String host=ip.getText().toString().trim();
  if(!host.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")){status.setText("Digite o IPv4 mostrado no receptor.");return;}
  try{for(String p:host.split("\\."))if(Integer.parseInt(p)>255)throw new Exception();if(!java.net.InetAddress.getByName(host).isSiteLocalAddress())throw new Exception();}catch(Exception e){status.setText("Use o IP privado da sua rede local.");return;}
  byte[] secret;
  try{if(pairing)secret=SecureLink.codeKey(code.getText().toString());else{String saved=getPreferences(0).getString("token",null);if(saved==null)throw new Exception("Gere um código na TV e toque em Parear primeiro.");secret=Base64.getDecoder().decode(saved);}}catch(Exception e){status.setText(e.getMessage());return;}
  disconnect();final int attempt=epoch;busy=true;status.setText(pairing?"Pareando com o receptor…":"Conectando…");update();
  ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(ip.getWindowToken(),0);
  worker.execute(()->{try{
   SecureLink next=SecureLink.connect(host,SecureLink.PORT,pairing,secret);
   if(epoch!=attempt){next.close();return;}link=next;
   String test=next.request("PING");if(!test.startsWith("OK "))throw new Exception("Receptor não confirmou a conexão");
   runOnUiThread(()->{if(attempt!=epoch)return;
    android.content.SharedPreferences.Editor prefs=getPreferences(0).edit().putString("ip",host);
    if(pairing)prefs.putString("token",Base64.getEncoder().encodeToString(next.pairedToken));prefs.apply();
    connected=true;busy=false;code.setText("");status.setText("● Conectado · ID "+deviceId(pairing?next.pairedToken:secret));update();
   });
  }catch(Exception e){failure(attempt,e);}});
 }
 private String deviceId(byte[] token){try{return SecureLink.identity(token).substring(0,8);}catch(Exception e){return "";}}
 private void send(String command,boolean quiet){
  if(!connected||busy||pending>=6)return;
  final int attempt=epoch;final long started=System.nanoTime();pending++;update();
  worker.execute(()->{if(attempt!=epoch)return;try{
   SecureLink current=link;if(current==null)throw new Exception("Conexão encerrada");String response=current.request(command);
   long ms=(System.nanoTime()-started)/1000000;
   runOnUiThread(()->{if(attempt!=epoch)return;pending--;if(!quiet){if(response.startsWith("OK "))status.setText(response.substring(3)+" · "+ms+" ms");else status.setText(response.startsWith("ERR ")?response.substring(4):"Resposta desconhecida");}update();});
  }catch(Exception e){failure(attempt,e);}});
 }
 private void failure(int attempt,Exception e){runOnUiThread(()->{if(epoch!=attempt)return;disconnect();String reason=e.getMessage();if(reason==null||reason.isEmpty())reason="A conexão terminou";if(reason.length()>170)reason=reason.substring(0,170);status.setText(reason+". Confira o receptor e toque em Conectar.");update();});}
 private void disconnect(){epoch++;connected=false;busy=false;pending=0;SecureLink old=link;link=null;if(old!=null)old.close();}
 private void update(){connect.setEnabled(!busy&&pending==0);pair.setEnabled(!busy&&pending==0);ip.setEnabled(!busy&&pending==0);code.setEnabled(!busy&&pending==0);pointer.setEnabled(!busy&&pending==0);for(Button b:controls){b.setEnabled(connected&&!busy&&pending<6);b.setAlpha(connected?1:.4f);}}
 private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
 private TextView label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(5));return t;}
 private EditText entry(String hint,boolean password){EditText e=new EditText(this);e.setSingleLine();e.setHint(hint);e.setTextSize(15);e.setTextColor(Color.WHITE);e.setHintTextColor(0xff9aabbd);if(password){e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);}return e;}
 private LinearLayout row(LinearLayout root){LinearLayout r=new LinearLayout(this);r.setOrientation(0);root.addView(r);return r;}
 private LinearLayout.LayoutParams cell(int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),1);p.setMargins(dp(3),dp(4),dp(3),dp(4));return p;}
 private void blank(LinearLayout row){row.addView(new View(this),cell(60));}
 private Button button(String name,boolean accent){Button b=new Button(this);b.setText(name);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(accent?BG:Color.WHITE);b.setPadding(dp(2),0,dp(2),0);b.setMinWidth(0);b.setMinimumWidth(0);GradientDrawable g=new GradientDrawable();g.setColor(accent?ACCENT:PANEL);g.setCornerRadius(dp(16));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44ffffff),g,null));return b;}
 private Button key(LinearLayout row,String name,String command){Button b=button(name,command.equals("OK"));row.addView(b,cell(60));controls.add(b);b.setContentDescription(command.equals("UP")?"Para cima":command.equals("DOWN")?"Para baixo":command.equals("LEFT")?"Para esquerda":command.equals("RIGHT")?"Para direita":name);
  b.setOnClickListener(v->{String c=command;if(pointer.isChecked()){if(c.equals("OK"))c="POINTER_CLICK";else if(Arrays.asList("UP","DOWN","LEFT","RIGHT").contains(c))c="POINTER_"+c;}v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);send(c,false);});return b;
 }
 @Override protected void onResume(){super.onResume();visible=true;main.postDelayed(heartbeat,30000);}
 @Override protected void onStop(){visible=false;main.removeCallbacks(heartbeat);disconnect();status.setText("Toque em Conectar para retomar.");update();super.onStop();}
 @Override protected void onDestroy(){disconnect();worker.shutdownNow();super.onDestroy();}
}
