package br.com.mibolocal.receptor;



import android.accessibilityservice.*;

import android.content.*;

import android.graphics.*;

import android.media.AudioManager;

import android.os.*;

import android.view.*;

import android.view.accessibility.*;

import br.com.mibolocal.*;

import java.net.*;

import java.util.*;

import java.util.concurrent.*;



public class RemoteService extends AccessibilityService {

 public static volatile RemoteService instance;

 private final Handler main=new Handler(Looper.getMainLooper());

 private volatile ServerSocket server;

 private ThreadPoolExecutor pool;

 private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();

 private final Map<Socket,String> clients=new ConcurrentHashMap<>();

 private String code;

 private byte[] pairKey;

 private long expires,lockedUntil;

 private int failures;

 private volatile int generation;

 private volatile String status="Aguardando ativação";

 private AccessibilityNodeInfo selected;

 private Marker marker;

 private int pointerX=-1,pointerY=-1;

 public android.content.SharedPreferences prefs(){return getSharedPreferences("receiver",0);}

 @Override public void onServiceConnected(){instance=this;migrate();if(!prefs().getBoolean("paused",false))startServer();else status="Recepção pausada";}

 @Override public void onAccessibilityEvent(AccessibilityEvent e){if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&!getPackageName().contentEquals(e.getPackageName()==null?"":e.getPackageName())){clearSelected();hideMarker();}}

 @Override public void onInterrupt(){hideMarker();}

 @Override public void onDestroy(){stopServer();clearSelected();hideMarker();instance=null;super.onDestroy();}

 public synchronized String currentCode(){if(code==null||SystemClock.elapsedRealtime()>expires)return null;return code.substring(0,4)+" "+code.substring(4,8)+" "+code.substring(8);}

 public synchronized String status(){return status;}

 public synchronized void pair(){

  if(server==null){status="Ative a recepção antes de gerar o código";return;}

  try{code=SecureLink.newCode();pairKey=SecureLink.codeKey(code);expires=SystemClock.elapsedRealtime()+300000;failures=0;lockedUntil=0;status="Código válido por 5 minutos para adicionar um celular.";}catch(Exception e){status="Falha ao gerar código";}

 }

 public void pause(){prefs().edit().putBoolean("paused",true).apply();stopServer();status="Recepção pausada";}

 public void resume(){prefs().edit().putBoolean("paused",false).apply();startServer();}

 private synchronized void migrate(){

  String old=prefs().getString("token",null);if(old==null)return;

  try{String id=SecureLink.identity(Base64.getDecoder().decode(old));if(!prefs().contains("device."+id))prefs().edit().putString("device."+id,old).putString("name."+id,"Celular anterior").commit();}catch(Exception e){status="Falha ao migrar autorização";}

 }

 public synchronized Map<String,String> devices(){

  Map<String,String> result=new TreeMap<>();for(String key:prefs().getAll().keySet())if(key.startsWith("device.")){String id=key.substring(7);result.put(id,prefs().getString("name."+id,"Celular")+" · "+id.substring(0,8));}return result;

 }

 public synchronized void revoke(String id){

  android.content.SharedPreferences.Editor edit=prefs().edit().remove("device."+id).remove("name."+id);

  String old=prefs().getString("token",null);try{if(old!=null&&SecureLink.identity(Base64.getDecoder().decode(old)).equals(id))edit.remove("token");}catch(Exception ignored){}

  if(!edit.commit()){status="Falha ao remover autorização";return;}

  for(Map.Entry<Socket,String> entry:clients.entrySet())if(id.equals(entry.getValue()))try{entry.getKey().close();}catch(Exception ignored){}

  status="Celular removido. Os outros continuam autorizados.";

 }

 private synchronized boolean authorized(String id,int epoch){return id!=null&&epoch==generation&&server!=null&&prefs().contains("device."+id);}

 private synchronized byte[] secretForId(String id){

  if(SystemClock.elapsedRealtime()<lockedUntil)return null;

  String saved=prefs().getString("device."+id,null);return saved==null?null:Base64.getDecoder().decode(saved);

 }

 private synchronized void startServer(){

  if(server!=null)return;

  try{

   ServerSocket listening=new ServerSocket();listening.setReuseAddress(true);listening.bind(new InetSocketAddress(SecureLink.PORT));server=listening;

   pool=new ThreadPoolExecutor(0,8,60,TimeUnit.SECONDS,new SynchronousQueue<Runnable>());

   status="Recepção ativa · porta "+SecureLink.PORT;

   new Thread(()->{while(server==listening){try{

    Socket s=listening.accept();

    if(!s.getInetAddress().isSiteLocalAddress()){s.close();continue;}

    sockets.add(s);try{pool.execute(()->serve(s));}catch(Exception e){sockets.remove(s);s.close();}

   }catch(Exception e){if(server==listening)main.post(()->status="Falha na rede. Pause e ative a recepção novamente.");break;}}},"MiboLocal-listen").start();

  }catch(Exception e){status="Não foi possível abrir a porta "+SecureLink.PORT;server=null;}

 }

 private synchronized void stopServer(){

  generation++;ServerSocket old=server;server=null;code=null;pairKey=null;

  if(old!=null)try{old.close();}catch(Exception ignored){}

  for(Socket s:sockets)try{s.close();}catch(Exception ignored){}sockets.clear();

  if(pool!=null)pool.shutdownNow();hideMarker();

 }

 private synchronized byte[] secret(boolean pairing){

  long now=SystemClock.elapsedRealtime();if(now<lockedUntil)return null;

  if(pairing)return pairKey!=null&&now<expires?pairKey.clone():null;

  String saved=prefs().getString("token",null);return saved==null?null:Base64.getDecoder().decode(saved);

 }

 private synchronized byte[] paired(byte[] used)throws Exception{

  if(pairKey==null||SystemClock.elapsedRealtime()>=expires||!java.security.MessageDigest.isEqual(pairKey,used))throw new java.io.IOException("Código expirado");

  if(devices().size()>=16)throw new java.io.IOException("Limite de 16 celulares. Remova um no receptor.");

  byte[] token=SecureLink.random(32);String id=SecureLink.identity(token);

  int number=prefs().getInt("nextDevice",1);

  if(!prefs().edit().putString("device."+id,Base64.getEncoder().encodeToString(token)).putString("name."+id,"Celular "+number).putInt("nextDevice",number+1).commit())throw new java.io.IOException("Não foi possível salvar autorização");

  code=null;pairKey=null;failures=0;status="Celular pareado. Pode voltar aos seus aplicativos.";return token;

 }

 private void serve(Socket s){

  boolean authenticated=false;

  java.util.concurrent.atomic.AtomicInteger authorizedEpoch=new java.util.concurrent.atomic.AtomicInteger();

  final String[] clientId={null};

  try(SecureLink link=SecureLink.accept(s,new SecureLink.Authority(){

   private byte[] remember(byte[] key)throws Exception{authorizedEpoch.set(generation);if(key!=null){clientId[0]=SecureLink.identity(key);clients.put(s,clientId[0]);}return key;}

   public byte[] secret(boolean pair)throws Exception{synchronized(RemoteService.this){byte[] key=RemoteService.this.secret(pair);if(pair){authorizedEpoch.set(generation);return key;}return remember(key);}}

   public byte[] secretForId(String id)throws Exception{synchronized(RemoteService.this){return remember(RemoteService.this.secretForId(id));}}

   public byte[] completePair(byte[] key)throws Exception{synchronized(RemoteService.this){if(authorizedEpoch.get()!=generation)throw new java.io.IOException("Recepção reiniciada");return remember(paired(key));}}

  })){

   authenticated=true;int epoch=authorizedEpoch.get();

   while(authorized(clientId[0],epoch)){

    String command=link.receive();

    if(!authorized(clientId[0],epoch))break;

    if(command.equals("PING")){link.send("OK Receptor ativo");continue;}

    FutureTask<String> task=new FutureTask<>(()->authorized(clientId[0],epoch)?act(command):"ERR Autorização revogada");main.post(task);

    String response;

    try{response=task.get(1800,TimeUnit.MILLISECONDS);}catch(TimeoutException e){task.cancel(false);response="ERR O aplicativo da TV demorou a responder";}

    link.send(response);

   }

  }catch(Exception e){if(!authenticated)synchronized(this){if(++failures>=5){lockedUntil=SystemClock.elapsedRealtime()+60000;failures=0;}}}

  finally{clients.remove(s);sockets.remove(s);try{s.close();}catch(Exception ignored){}}

 }

 private String act(String c){

  try{

   switch(c){

    case "HOME":hideMarker();clearSelected();return result(performGlobalAction(GLOBAL_ACTION_HOME),"Início solicitado");

    case "BACK":hideMarker();clearSelected();return result(performGlobalAction(GLOBAL_ACTION_BACK),"Voltar solicitado");

    case "RECENTS":hideMarker();return result(performGlobalAction(GLOBAL_ACTION_RECENTS),"Recentes solicitado");

    case "UP":case "DOWN":case "LEFT":case "RIGHT":return navigate(c);

    case "OK":return clickSelected(false);

    case "LONG_OK":return clickSelected(true);

    case "POINTER_UP":case "POINTER_DOWN":case "POINTER_LEFT":case "POINTER_RIGHT":return movePointer(c);

    case "POINTER_CLICK":return pointerClick();

    case "HIDE_POINTER":hideMarker();return "OK Ponteiro oculto";

    case "VOL_UP":case "VOL_DOWN":case "MUTE":{

     AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);

     audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,c.equals("VOL_UP")?AudioManager.ADJUST_RAISE:c.equals("VOL_DOWN")?AudioManager.ADJUST_LOWER:AudioManager.ADJUST_TOGGLE_MUTE,AudioManager.FLAG_SHOW_UI);

     return "OK Volume solicitado";

    }

    case "PLAY_PAUSE":{

     AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);

     audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));

     audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));return "OK Reprodução solicitada";

    }

    default:return "ERR Comando não suportado";

   }

  }catch(Exception e){return "ERR Este aplicativo não aceitou o comando";}

 }

 private String result(boolean ok,String message){return ok?"OK "+message:"ERR O sistema não aceitou este comando";}

 private void clearSelected(){if(selected!=null){selected.recycle();selected=null;}}

 private AccessibilityNodeInfo current(AccessibilityNodeInfo root){

  if(selected!=null&&selected.refresh()&&selected.getWindowId()==root.getWindowId()&&selected.isVisibleToUser())return AccessibilityNodeInfo.obtain(selected);

  AccessibilityNodeInfo focused=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);

  return focused!=null?focused:root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);

 }

 private boolean select(AccessibilityNodeInfo n){

  if(n==null||!n.isVisibleToUser()||!n.isEnabled())return false;

  boolean focused=n.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

  if(!focused)focused=n.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);

  if(!focused&&!n.isClickable())return false;

  clearSelected();selected=AccessibilityNodeInfo.obtain(n);Rect r=new Rect();n.getBoundsInScreen(r);showMarker(r,false);return true;

 }

 private ArrayList<AccessibilityNodeInfo> nodes(AccessibilityNodeInfo root){

  ArrayList<AccessibilityNodeInfo> result=new ArrayList<>();ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();queue.add(AccessibilityNodeInfo.obtain(root));int visited=0;

  while(!queue.isEmpty()&&visited++<700){AccessibilityNodeInfo n=queue.remove();

   if(n.isVisibleToUser()&&n.isEnabled()&&(n.isFocusable()||n.isClickable()))result.add(AccessibilityNodeInfo.obtain(n));

   for(int i=0;i<n.getChildCount()&&queue.size()<700;i++){AccessibilityNodeInfo child=n.getChild(i);if(child!=null)queue.add(child);}n.recycle();

  }for(AccessibilityNodeInfo n:queue)n.recycle();return result;

 }

 private String navigate(String dir){

  AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return "ERR Tela indisponível. Tente o modo ponteiro.";

  AccessibilityNodeInfo from=current(root);ArrayList<AccessibilityNodeInfo> choices=null;

  try{

   int direction=dir.equals("UP")?View.FOCUS_UP:dir.equals("DOWN")?View.FOCUS_DOWN:dir.equals("LEFT")?View.FOCUS_LEFT:View.FOCUS_RIGHT;

   if(from!=null){AccessibilityNodeInfo next=from.focusSearch(direction);if(next!=null){try{if(!next.equals(from)&&select(next))return "OK Foco movido";}finally{next.recycle();}}}

   choices=nodes(root);if(choices.isEmpty())return "ERR Tela sem itens acessíveis. Use o modo ponteiro.";

   if(from==null){for(AccessibilityNodeInfo n:choices)if(select(n))return "OK Primeiro item selecionado";}

   else{

    Rect bounds=new Rect();from.getBoundsInScreen(bounds);ArrayList<int[]> rectangles=new ArrayList<>();

    for(AccessibilityNodeInfo n:choices){Rect r=new Rect();n.getBoundsInScreen(r);rectangles.add(new int[]{r.left,r.top,r.right,r.bottom});}

    int index=FocusGeometry.choose(new int[]{bounds.left,bounds.top,bounds.right,bounds.bottom},rectangles,dir);

    if(index>=0&&select(choices.get(index)))return "OK Item selecionado";

    if(dir.equals("UP")||dir.equals("DOWN")){

     AccessibilityNodeInfo parent=AccessibilityNodeInfo.obtain(from);

     for(int i=0;i<10&&parent!=null;i++){AccessibilityNodeInfo next=parent.getParent();boolean scrolled=parent.isScrollable()&&parent.performAction(dir.equals("UP")?AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);parent.recycle();parent=next;if(scrolled){if(parent!=null)parent.recycle();clearSelected();return "OK Rolagem solicitada";}}

     if(parent!=null)parent.recycle();

    }

   }

   return "ERR Sem item nessa direção. Tente o modo ponteiro.";

  }finally{if(from!=null)from.recycle();if(choices!=null)for(AccessibilityNodeInfo n:choices)n.recycle();root.recycle();}

 }

 private String clickSelected(boolean longClick){

  AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return "ERR Tela indisponível";

  AccessibilityNodeInfo n=current(root);root.recycle();if(n==null)return "ERR Selecione um item com as setas ou use o ponteiro";

  Rect bounds=new Rect();n.getBoundsInScreen(bounds);

  try{

   for(int i=0;i<6&&n!=null;i++){

    if(n.performAction(longClick?AccessibilityNodeInfo.ACTION_LONG_CLICK:AccessibilityNodeInfo.ACTION_CLICK)){hideMarker();return "OK Ação solicitada";}

    AccessibilityNodeInfo parent=n.getParent();n.recycle();n=parent;

   }

   return tap(bounds.centerX(),bounds.centerY(),longClick?650:80);

  }finally{if(n!=null)n.recycle();}

 }

 private Point screen(){Point p=new Point();((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealSize(p);return p;}

 private String movePointer(String command){Point p=screen();if(pointerX<0){pointerX=p.x/2;pointerY=p.y/2;}

  int step=Math.max(12,Math.min(p.x,p.y)/18);

  if(command.endsWith("UP"))pointerY-=step;if(command.endsWith("DOWN"))pointerY+=step;if(command.endsWith("LEFT"))pointerX-=step;if(command.endsWith("RIGHT"))pointerX+=step;

  pointerX=Math.max(2,Math.min(p.x-3,pointerX));pointerY=Math.max(2,Math.min(p.y-3,pointerY));showMarker(new Rect(pointerX-12,pointerY-12,pointerX+12,pointerY+12),true);return "OK Ponteiro movido";

 }

 private String pointerClick(){Point p=screen();if(pointerX<0){pointerX=p.x/2;pointerY=p.y/2;}return tap(pointerX,pointerY,80);}

 private String tap(int x,int y,int duration){Point size=screen();if(x<0||y<0||x>=size.x||y>=size.y)return "ERR Item fora da tela";

  hideMarker();Path path=new Path();path.moveTo(x,y);GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();

  return result(dispatchGesture(gesture,null,null),"Toque enviado; depende do aplicativo");

 }

 private void showMarker(Rect r,boolean pointer){

  try{if(marker==null){marker=new Marker(this);WindowManager.LayoutParams lp=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);lp.gravity=Gravity.TOP|Gravity.LEFT;((WindowManager)getSystemService(WINDOW_SERVICE)).addView(marker,lp);}marker.bounds=new Rect(r);marker.pointer=pointer;marker.invalidate();}catch(Exception ignored){}

 }

 private void hideMarker(){if(Looper.myLooper()!=Looper.getMainLooper()){main.post(this::hideMarker);return;}if(marker!=null){try{((WindowManager)getSystemService(WINDOW_SERVICE)).removeView(marker);}catch(Exception ignored){}marker=null;}}

 private static class Marker extends View{Rect bounds=new Rect();boolean pointer;Paint paint=new Paint(3);Marker(Context c){super(c);paint.setColor(0xff54d6c7);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(4);}@Override public void onDraw(Canvas c){if(pointer){c.drawCircle(bounds.centerX(),bounds.centerY(),12,paint);c.drawLine(bounds.centerX()-20,bounds.centerY(),bounds.centerX()+20,bounds.centerY(),paint);c.drawLine(bounds.centerX(),bounds.centerY()-20,bounds.centerX(),bounds.centerY()+20,paint);}else c.drawRect(bounds,paint);}}

}

