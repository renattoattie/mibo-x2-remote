import br.com.mibolocal.SecureLink;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Exercises independent credentials and simultaneous encrypted sessions over real sockets. */
public class MultiClientTest {
 public static void main(String[] args)throws Exception {
  Map<String,byte[]> tokens=new ConcurrentHashMap<>();
  byte[][] keys=new byte[8][];
  for(int i=0;i<keys.length;i++){keys[i]=SecureLink.random(32);tokens.put(SecureLink.identity(keys[i]),keys[i]);}
  SecureLink.Authority auth=new SecureLink.Authority(){
   public byte[] secret(boolean pair){return pair?null:keys[0];}
   public byte[] secretForId(String id){return tokens.get(id);}
   public byte[] completePair(byte[] key){throw new AssertionError();}
  };
  ExecutorService workers=Executors.newCachedThreadPool();
  List<Future<?>> tasks=new ArrayList<>();List<SecureLink> clients=new ArrayList<>();
  try(ServerSocket listener=new ServerSocket(0,10,InetAddress.getLoopbackAddress())){
   for(int i=0;i<8;i++){
    tasks.add(workers.submit(()->{try(Socket socket=listener.accept();SecureLink link=SecureLink.accept(socket,auth)){
     while(true){String command=link.receive();if(command.equals("CLOSE"))break;link.send("OK "+command);}
    }catch(java.io.EOFException expected){}catch(Exception e){throw new RuntimeException(e);}}));
    clients.add(SecureLink.connect("127.0.0.1",listener.getLocalPort(),false,keys[i]));
   }
   for(int i=0;i<8;i++)if(!clients.get(i).request("PHONE "+i).equals("OK PHONE "+i))throw new AssertionError("crossed sessions");
   tokens.remove(SecureLink.identity(keys[0]));
   Future<?> refused=workers.submit(()->{try(Socket socket=listener.accept()){
    try(SecureLink ignored=SecureLink.accept(socket,auth)){throw new AssertionError("revoked accepted");}catch(java.io.IOException expected){}
   }catch(Exception e){throw new RuntimeException(e);}});
   try(SecureLink ignored=SecureLink.connect("127.0.0.1",listener.getLocalPort(),false,keys[0])){throw new AssertionError("revoked connected");}catch(java.io.IOException expected){}
   refused.get(10,TimeUnit.SECONDS);
   for(int i=1;i<8;i++)if(!clients.get(i).request("PING").equals("OK PING"))throw new AssertionError("other phone interrupted");
   for(SecureLink client:clients)client.send("CLOSE");
   for(Future<?> task:tasks)task.get(10,TimeUnit.SECONDS);
   System.out.println("PASS eight concurrent identities; revoked identity rejected; other sessions continue");
  }finally{for(SecureLink client:clients)client.close();workers.shutdownNow();}
 }
}
