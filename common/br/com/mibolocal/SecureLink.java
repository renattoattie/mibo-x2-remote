package br.com.mibolocal;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Local PSK protocol. Pairing uses a random 60-bit code; saved identity is 256 bits. */
public final class SecureLink implements Closeable {
    public static final int PORT=8765;
    private static final SecureRandom RANDOM=new SecureRandom();
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final byte[] txKey,rxKey;
    private long tx=0,rx=0;
    private final int txDirection,rxDirection;
    public byte[] pairedToken;
    private SecureLink(Socket s,byte[] base,boolean client)throws Exception {
        socket=s;in=new DataInputStream(s.getInputStream());out=new DataOutputStream(s.getOutputStream());
        txKey=hmac(base,utf(client?"c2s":"s2c"));rxKey=hmac(base,utf(client?"s2c":"c2s"));
        txDirection=client?1:2;rxDirection=client?2:1;
    }
    public static byte[] random(int size){byte[] b=new byte[size];RANDOM.nextBytes(b);return b;}
    public static String newCode(){String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";StringBuilder b=new StringBuilder();for(int i=0;i<12;i++)b.append(alphabet.charAt(RANDOM.nextInt(32)));return b.toString();}
    public static byte[] codeKey(String code)throws Exception {
        String normalized=code.toUpperCase(java.util.Locale.ROOT).replace(" ","").replace("-","");
        if(!normalized.matches("[A-HJ-NP-Z2-9]{12}"))throw new IOException("Digite os 12 caracteres do código mostrado na TV.");
        return MessageDigest.getInstance("SHA-256").digest(utf("MIBO-PAIR-1:"+normalized));
    }
    private static byte[] utf(String s){return s.getBytes(StandardCharsets.UTF_8);}
    private static byte[] hmac(byte[] key,byte[]... parts)throws Exception{
        Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));for(byte[] p:parts)m.update(p);return m.doFinal();
    }
    private static byte[] proof(byte[] key,String side,byte mode,byte[] a,byte[] b)throws Exception{return hmac(key,utf("MIBO-LOCAL-1:"+side),new byte[]{mode},a,b);}
    public interface Authority {
        byte[] secret(boolean pairing)throws Exception;
        default byte[] secretForId(String id)throws Exception{return null;}
        byte[] completePair(byte[] usedKey)throws Exception;
    }
    public static String identity(byte[] token)throws Exception{
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(token);
        StringBuilder id=new StringBuilder();for(byte v:hash)id.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return id.toString();
    }
    public static SecureLink connect(String host,int port,boolean pair,byte[] secret)throws Exception{
        Socket s=new Socket();
        try{
            s.connect(new InetSocketAddress(host,port),4000);s.setSoTimeout(6000);s.setTcpNoDelay(true);
            DataOutputStream out=new DataOutputStream(s.getOutputStream());DataInputStream in=new DataInputStream(s.getInputStream());
            byte mode=(byte)(pair?0:2);byte[] a=random(32),b=new byte[32],remoteProof=new byte[32];
            out.write(utf("MBL1"));out.writeByte(mode);if(!pair)out.write(utf(identity(secret)));out.write(a);out.flush();
            if(in.readByte()!=1)throw new IOException("Pareamento fechado ou receptor temporariamente bloqueado. Confira a TV.");
            in.readFully(b);in.readFully(remoteProof);
            if(!MessageDigest.isEqual(proof(secret,"server",mode,a,b),remoteProof))throw new IOException(pair?"Código incorreto ou expirado. Gere outro na TV.":"Este celular não está autorizado. Faça o pareamento novamente.");
            out.write(proof(secret,"client",mode,a,b));out.flush();
            SecureLink link=new SecureLink(s,proof(secret,"session",mode,a,b),true);
            String response=link.receive();
            if(pair){
                if(!response.startsWith("PAIRED "))throw new IOException("Pareamento não confirmado");
                link.pairedToken=java.util.Base64.getDecoder().decode(response.substring(7));
                if(link.pairedToken.length!=32)throw new IOException("Identidade inválida");
            }else if(!response.equals("READY"))throw new IOException("Receptor indisponível");
            return link;
        }catch(Exception e){try{s.close();}catch(Exception ignored){}throw e;}
    }
    public static SecureLink accept(Socket s,Authority authority)throws Exception{
        s.setSoTimeout(6000);s.setTcpNoDelay(true);
        DataInputStream in=new DataInputStream(s.getInputStream());DataOutputStream out=new DataOutputStream(s.getOutputStream());
        byte[] magic=new byte[4];in.readFully(magic);byte mode=in.readByte();
        if(!Arrays.equals(magic,utf("MBL1"))||(mode!=0&&mode!=1&&mode!=2))throw new IOException("Protocolo inválido");
        String id=null;if(mode==2){byte[] raw=new byte[64];in.readFully(raw);id=new String(raw,StandardCharsets.US_ASCII);if(!id.matches("[0-9a-f]{64}"))throw new IOException("Identidade inválida");}
        byte[] a=new byte[32],b=random(32),clientProof=new byte[32];in.readFully(a);
        byte[] secret=mode==2?authority.secretForId(id):authority.secret(mode==0);
        if(secret==null){out.writeByte(0);out.flush();throw new IOException("Autorização indisponível");}
        out.writeByte(1);out.write(b);out.write(proof(secret,"server",mode,a,b));out.flush();
        in.readFully(clientProof);
        if(!MessageDigest.isEqual(proof(secret,"client",mode,a,b),clientProof))throw new IOException("Autenticação recusada");
        SecureLink link=new SecureLink(s,proof(secret,"session",mode,a,b),false);
        if(mode==0){byte[] token=authority.completePair(secret);link.send("PAIRED "+java.util.Base64.getEncoder().encodeToString(token));}
        else link.send("READY");
        s.setSoTimeout(120000);
        return link;
    }
    private static byte[] crypt(int mode,byte[] key,int direction,long counter,byte[] input)throws Exception{
        byte[] nonce=ByteBuffer.allocate(12).putInt(direction).putLong(counter).array();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        cipher.updateAAD(utf("MIBO-LOCAL-1"));return cipher.doFinal(input);
    }
    public void send(String value)throws Exception{
        byte[] plain=utf(value);if(plain.length>2048)throw new IOException("Mensagem muito grande");
        byte[] encrypted=crypt(Cipher.ENCRYPT_MODE,txKey,txDirection,tx++,plain);
        out.writeInt(encrypted.length);out.write(encrypted);out.flush();
    }
    public String receive()throws Exception{
        int size=in.readInt();if(size<16||size>2064)throw new IOException("Tamanho de mensagem inválido");
        byte[] encrypted=new byte[size];in.readFully(encrypted);
        byte[] result=crypt(Cipher.DECRYPT_MODE,rxKey,rxDirection,rx,encrypted);rx++;
        return new String(result,StandardCharsets.UTF_8);
    }
    public String request(String command)throws Exception{send(command);return receive();}
    @Override public void close(){try{socket.close();}catch(IOException ignored){}}
}
