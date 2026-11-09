import br.com.mibolocal.*;
import java.net.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public class ProtocolTest {
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 static class Authority implements SecureLink.Authority{
  byte[] pairing,token;
  Authority()throws Exception{pairing=SecureLink.codeKey(SecureLink.newCode());}
  public synchronized byte[] secretForId(String id)throws Exception{return token!=null&&SecureLink.identity(token).equals(id)?token:null;}
  public synchronized byte[] secret(boolean pair){return pair?pairing:token;}
  public synchronized byte[] completePair(byte[] key)throws Exception{check(Arrays.equals(key,pairing),"pair key");pairing=null;token=SecureLink.random(32);return token;}
 }
 static void run(String mode)throws Exception{
  Authority auth=new Authority();byte[] code=auth.pairing.clone();if(!mode.equals("pair"))auth.token=SecureLink.random(32);
  ExecutorService worker=Executors.newSingleThreadExecutor();
  try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getLoopbackAddress())){
   java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();
   Future<?> server=worker.submit(()->{try(Socket s=listener.accept()){
    try(SecureLink remote=SecureLink.accept(s,auth)){
     if(mode.equals("wrong"))throw new AssertionError("Wrong key authenticated");
     if(mode.equals("tamper")||mode.equals("oversize")){boolean denied=false;try{remote.receive();}catch(Exception expected){denied=true;}check(denied,"invalid frame rejected");return;}
     for(int i=0;i<(mode.equals("replay")?1:100);i++){check(remote.receive().equals("HOME"),"command ordering");count.incrementAndGet();remote.send("OK");}
     if(mode.equals("replay")){boolean denied=false;try{remote.receive();}catch(Exception expected){denied=true;}check(denied,"replay rejected");}
    }
   }catch(Exception e){if(!mode.equals("wrong"))throw new RuntimeException(e);}});
   if(mode.equals("wrong")){
    boolean denied=false;try(SecureLink ignored=SecureLink.connect("127.0.0.1",listener.getLocalPort(),false,SecureLink.random(32))){}catch(Exception expected){denied=true;}check(denied,"wrong credential rejected");
   }else try(SecureLink client=SecureLink.connect("127.0.0.1",listener.getLocalPort(),mode.equals("pair"),mode.equals("pair")?code:auth.token)){
    if(mode.equals("pair")){check(Arrays.equals(auth.token,client.pairedToken),"token delivered");check(auth.pairing==null,"pairing window consumed");}
    if(mode.equals("tamper")||mode.equals("oversize")){
     Field out=SecureLink.class.getDeclaredField("out");out.setAccessible(true);DataOutputStream raw=(DataOutputStream)out.get(client);raw.writeInt(mode.equals("oversize")?Integer.MAX_VALUE:32);if(!mode.equals("oversize"))raw.write(new byte[32]);raw.flush();
    }else{
     for(int i=0;i<(mode.equals("replay")?1:100);i++)check(client.request("HOME").equals("OK"),"encrypted round trip");
     if(mode.equals("replay")){Field counter=SecureLink.class.getDeclaredField("tx");counter.setAccessible(true);counter.setLong(client,0);client.send("HOME");}
    }
   }
   server.get(10,TimeUnit.SECONDS);check(count.get()==(mode.equals("wrong")||mode.equals("tamper")||mode.equals("oversize")?0:mode.equals("replay")?1:100),"executed only authenticated fresh commands");
  }finally{worker.shutdownNow();}
  System.out.println("PASS "+mode);
 }
 public static void main(String[] args)throws Exception{
  for(int i=0;i<100;i++)check(SecureLink.codeKey(SecureLink.newCode()).length==32,"code format");
  run("pair");run("saved");run("wrong");run("replay");run("tamper");run("oversize");
  int[] center={100,100,150,150};List<int[]> targets=Arrays.asList(new int[]{100,0,150,50},new int[]{200,100,250,150},new int[]{100,200,150,250},new int[]{0,100,50,150});
  check(FocusGeometry.choose(center,targets,"UP")==0,"up");check(FocusGeometry.choose(center,targets,"RIGHT")==1,"right");check(FocusGeometry.choose(center,targets,"DOWN")==2,"down");check(FocusGeometry.choose(center,targets,"LEFT")==3,"left");
  check(FocusGeometry.choose(center,Collections.singletonList(center),"LEFT")==-1,"no target");
  System.out.println("PASS directional focus geometry");
 }
}
