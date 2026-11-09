package br.com.mibolocal.receptor;



import android.app.*;

import android.os.*;

import android.content.*;

import android.graphics.Color;

import android.provider.Settings;

import android.view.*;

import android.widget.*;

import java.net.*;

import java.util.*;



public class MainActivity extends Activity {

 private final Handler handler=new Handler(Looper.getMainLooper());

 private TextView state,code,ip;

 private Button pair,pause;

 private final Runnable refresh=new Runnable(){public void run(){update();handler.postDelayed(this,1000);}};

 @Override public void onCreate(Bundle b){super.onCreate(b);

  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(0xff111722);

  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(32,24,32,24);scroll.addView(root);setContentView(scroll);

  root.addView(label("Mibo Receptor Local · teste 0.2",27,Color.WHITE));

  root.addView(label("Controle pela rede, sem ADB e sem modo desenvolvedor",17,0xff54d6c7));

  root.addView(label("1. Ative Mibo Receptor Local em Acessibilidade → Serviços.\n2. Gere um código aqui e digite no app Mibo Controle Local do celular.",16,0xffc7d2df));

  state=label("",17,0xff54d6c7);root.addView(state);

  ip=label("",21,Color.WHITE);root.addView(ip);

  code=label("",30,Color.WHITE);code.setTextIsSelectable(true);root.addView(code);

  LinearLayout row=new LinearLayout(this);root.addView(row);

  button(row,"Abrir Acessibilidade",v->{try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception e){new AlertDialog.Builder(this).setMessage("Abra Configurações → Acessibilidade → Serviços no Mibo.").setPositiveButton("OK",null).show();}});

  pair=button(row,"Gerar código",v->{if(RemoteService.instance!=null){RemoteService.instance.pair();update();}});

  pause=button(row,"Pausar recepção",v->{RemoteService s=RemoteService.instance;if(s!=null){if(s.prefs().getBoolean("paused",false))s.resume();else s.pause();update();}});

  LinearLayout bottom=new LinearLayout(this);root.addView(bottom);

  button(bottom,"Celulares autorizados",v->showDevices());

  root.addView(label("A permissão de Acessibilidade permite consultar itens da tela e executar ações. Textos e imagens da TV não são enviados ao celular. Somente celulares pareados podem comandar o receptor. Use sua rede de confiança.",14,0xffa8b6c9));

  root.addView(label("Android 10: setas e OK dependem dos aplicativos. Se necessário, experimente o modo Ponteiro no celular. Mantenha o controle anterior até concluir seus testes.",14,0xffa8b6c9));

 }

 private void showDevices(){

  RemoteService service=RemoteService.instance;

  if(service==null){new AlertDialog.Builder(this).setMessage("Ative o serviço de Acessibilidade para gerenciar celulares.").setPositiveButton("OK",null).show();return;}

  Map<String,String> devices=service.devices();

  if(devices.isEmpty()){new AlertDialog.Builder(this).setMessage("Nenhum celular autorizado.").setPositiveButton("OK",null).show();return;}

  String[] ids=devices.keySet().toArray(new String[0]),names=devices.values().toArray(new String[0]);

  new AlertDialog.Builder(this).setTitle("Selecione o celular para remover").setItems(names,(d,index)->new AlertDialog.Builder(this).setMessage("Remover "+names[index]+"? Os outros celulares continuarão autorizados.").setNegativeButton("Cancelar",null).setPositiveButton("Remover",(dialog,w)->{service.revoke(ids[index]);update();}).show()).setNegativeButton("Fechar",null).show();

 }

 private TextView label(String value,int size,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,8,0,8);return t;}

 private Button button(LinearLayout parent,String label,View.OnClickListener action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setOnClickListener(action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(4,4,4,4);parent.addView(b,lp);return b;}

 private void update(){RemoteService s=RemoteService.instance;state.setText(s==null?"Serviço ainda desativado. Abra Acessibilidade para ativá-lo.":s.status());pair.setEnabled(s!=null);pause.setEnabled(s!=null);pause.setText(s!=null&&s.prefs().getBoolean("paused",false)?"Ativar recepção":"Pausar recepção");

  String active=s==null?null:s.currentCode();code.setText(active==null?"Código: toque em Gerar código":active);

  ip.setText("IP do Mibo: "+addresses());

 }

 private String addresses(){try{ArrayList<String> a=new ArrayList<>();Enumeration<NetworkInterface> all=NetworkInterface.getNetworkInterfaces();while(all.hasMoreElements()){NetworkInterface n=all.nextElement();if(!n.isUp()||n.isLoopback())continue;Enumeration<InetAddress> values=n.getInetAddresses();while(values.hasMoreElements()){InetAddress v=values.nextElement();if(v instanceof Inet4Address&&v.isSiteLocalAddress())a.add(v.getHostAddress());}}return a.isEmpty()?"conecte à rede":android.text.TextUtils.join(" / ",a);}catch(Exception e){return "veja em Configurações → Rede";}}

 @Override protected void onResume(){super.onResume();handler.post(refresh);}

 @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}

}

