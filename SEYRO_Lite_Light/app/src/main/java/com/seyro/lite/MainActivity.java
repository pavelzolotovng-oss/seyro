package com.seyro.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.VpnService;
import android.os.Build;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

public class MainActivity extends Activity {
    private final int bg=0xfff3f7ff, white=0xffffffff, ink=0xff14233b, muted=0xff738198,
        line=0xffe2eaf6, blue=0xff2864e8, paleBlue=0xffeaf2ff, green=0xff118568;
    private TextView connect,status,hint,profileLinkValue,subscriptionState;
    private SharedPreferences prefs;
    private String profileLink="";
    private boolean demoOn=false;
    private String selectedConfig="";
    private static final int VPN_PERMISSION_REQUEST=71;
    private boolean vpnStateReceiverRegistered=false;
    private final BroadcastReceiver vpnStateReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) { renderVpnState(); }
    };

    private GradientDrawable shape(int color,int radius,int stroke) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        if(stroke!=0)d.setStroke(1,stroke); return d;
    }
    private TextView text(String s,int size,int color,boolean bold) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(size); v.setTextColor(color);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setIncludeFontPadding(true); return v;
    }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(1); return l; }
    private void padding(View v,int l,int t,int r,int b) { v.setPadding(l,t,r,b); }
    private LinearLayout card() {
        LinearLayout l=column(); padding(l,16,15,16,15);
        l.setBackground(shape(white,20,line)); return l;
    }
    private void add(LinearLayout parent,View child,int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=top; parent.addView(child,p);
    }
    private TextView pill(String label,int color,int fill) {
        TextView v=text(label,10,color,true); padding(v,10,6,10,6); v.setGravity(Gravity.CENTER);
        v.setBackground(shape(fill,40,0)); return v;
    }

    @Override public void onCreate(Bundle stateBundle) {
        super.onCreate(stateBundle);
        prefs=getSharedPreferences("seyro_profile",MODE_PRIVATE);
        profileLink=prefs.getString("subscription_link","");
        Window w=getWindow();
        w.setStatusBarColor(0xfff3f7ff); w.setNavigationBarColor(0xfff3f7ff);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setBackgroundColor(bg);
        LinearLayout root=column(); padding(root,18,76,18,28); scroll.addView(root);

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=text("S",25,white,true); mark.setGravity(Gravity.CENTER); mark.setBackground(shape(blue,17,0));
        mark.setElevation(3f); header.addView(mark,new LinearLayout.LayoutParams(48,48));
        LinearLayout brand=column(); TextView name=text("SEYRO",23,ink,true); name.setLetterSpacing(.07f); brand.addView(name);
        TextView tagline=text("LITE VPN",9,blue,true); tagline.setLetterSpacing(.18f); brand.addView(tagline);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1); bp.leftMargin=11; header.addView(brand,bp);
        TextView settings=text("⚙",22,ink,true); settings.setGravity(Gravity.CENTER);
        header.addView(settings,new LinearLayout.LayoutParams(42,42)); settings.setOnClickListener(v->showSettings());
        root.addView(header);

        add(root,text("ВАШ ЛИЧНЫЙ VPN",10,blue,true),28);
        TextView headline=text("Ваш интернет.\nПод защитой.",32,ink,true); headline.setLineSpacing(0f,1.02f); add(root,headline,6);
        add(root,text("Безопасный доступ — в одно касание.",14,muted,false),8);

        LinearLayout hero=column(); padding(hero,20,22,20,20); hero.setBackground(shape(white,26,line)); hero.setElevation(2f);
        add(root,hero,18);
        LinearLayout center=column(); center.setGravity(Gravity.CENTER);
        TextView shield=text("S",31,blue,true); shield.setGravity(Gravity.CENTER);
        shield.setBackground(shape(0xffe7f0ff,100,0));
        LinearLayout.LayoutParams shieldP=new LinearLayout.LayoutParams(76,76); shieldP.gravity=Gravity.CENTER;
        center.addView(shield,shieldP);
        status=text("●  НЕ ПОДКЛЮЧЕНО",12,muted,true); status.setGravity(Gravity.CENTER);
        add(center,status,16); hero.addView(center);
        hint=text(profileLink.isEmpty()?"Добавьте ссылку профиля, чтобы подключиться.":"Профиль готов к подключению.",13,muted,false);
        hint.setGravity(Gravity.CENTER); add(hero,hint,8);
        connect=text("⏻    Подключить",16,white,true); connect.setGravity(Gravity.CENTER);
        padding(connect,16,16,16,16); connect.setBackground(shape(blue,17,0)); connect.setElevation(2f); add(hero,connect,18);
        connect.setOnClickListener(v->onConnect());

        LinearLayout profile=card(); padding(profile,16,5,16,5); profile.setElevation(1f); add(root,profile,14);
        profile.addView(menuRow("Профиль","Ссылка и параметры аккаунта","↗",()->showProfile()));
        profile.setOnClickListener(v->showProfile());

        LinearLayout pair=new LinearLayout(this); LinearLayout.LayoutParams pairP=new LinearLayout.LayoutParams(-1,-2);
        pairP.topMargin=12; root.addView(pair,pairP);
        LinearLayout sub=card(); sub.setElevation(1f); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1); hp.rightMargin=6;
        pair.addView(sub,hp); sub.setOnClickListener(v->showSubscription());
        sub.addView(text("ПОДПИСКА",10,muted,true));
        subscriptionState=text(profileLink.isEmpty()?"Не настроена":"Профиль добавлен",14,ink,true);
        add(sub,subscriptionState,7);
        LinearLayout help=card(); help.setElevation(1f); LinearLayout.LayoutParams hp2=new LinearLayout.LayoutParams(0,-2,1); hp2.leftMargin=6;
        pair.addView(help,hp2); help.setOnClickListener(v->showHelp());
        help.addView(text("НУЖНА ПОМОЩЬ?",10,muted,true)); add(help,text("Поддержка  ›",14,blue,true),7);

        TextView footer=text("SEYRO LITE  ·  ВЕРСИЯ "+BuildConfig.VERSION_NAME,10,0xff93a0b5,true);
        footer.setGravity(Gravity.CENTER); add(root,footer,20);
        setContentView(scroll);
        renderVpnState();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter=new IntentFilter(SeyroVpnService.ACTION_STATE_CHANGED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(vpnStateReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(vpnStateReceiver,filter);
        vpnStateReceiverRegistered=true;
        renderVpnState();
    }

    @Override protected void onStop() {
        if(vpnStateReceiverRegistered) { unregisterReceiver(vpnStateReceiver); vpnStateReceiverRegistered=false; }
        super.onStop();
    }

    private void renderVpnState() {
        if(status==null||connect==null||hint==null) return;
        String state=SeyroVpnService.vpnState;
        if("connected".equals(state)) {
            status.setText("●  ПОДКЛЮЧЕНО"); status.setTextColor(green);
            connect.setText("⏻    Отключиться"); connect.setEnabled(true);
            hint.setText("Защищённое соединение активно.");
        } else if("connecting".equals(state)||"disconnecting".equals(state)) {
            status.setText("●  "+("connecting".equals(state)?"ПОДКЛЮЧАЕМСЯ":"ОТКЛЮЧАЕМСЯ")); status.setTextColor(blue);
            connect.setText("Подождите…"); connect.setEnabled(false);
            hint.setText("Запускаем защищённый туннель. Состояние обновится автоматически.");
        } else if("error".equals(state)) {
            status.setText("●  НЕ УДАЛОСЬ ПОДКЛЮЧИТЬ"); status.setTextColor(0xffc64747);
            connect.setText("Повторить"); connect.setEnabled(true);
            hint.setText(SeyroVpnService.lastError.isEmpty()?"Проверьте профиль и попробуйте снова.":SeyroVpnService.lastError);
        } else {
            status.setText("●  НЕ ПОДКЛЮЧЕНО"); status.setTextColor(muted);
            connect.setText("⏻    Подключить"); connect.setEnabled(true);
            hint.setText(profileLink.isEmpty()?"Добавьте ссылку профиля, чтобы подключиться.":"Профиль готов к подключению.");
        }
        if(subscriptionState!=null) subscriptionState.setText(profileLink.isEmpty()?"Не настроена":"Профиль добавлен");
    }

    private View menuRow(String title,String subtitle,String icon,Runnable action) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); padding(row,0,13,0,13);
        LinearLayout words=column(); words.addView(text(title,14,ink,true));
        add(words,text(subtitle,11,muted,false),3);
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text(icon,20,muted,true); arrow.setGravity(Gravity.CENTER);
        row.addView(arrow,new LinearLayout.LayoutParams(30,40));
        row.setOnClickListener(v->action.run()); return row;
    }
    private void divider(LinearLayout parent) {
        View d=new View(this); d.setBackgroundColor(line); parent.addView(d,new LinearLayout.LayoutParams(-1,1));
    }
    private void showProfile() {
        LinearLayout box=column(); padding(box,2,4,2,0);
        box.addView(text("Ссылка из личного кабинета SEYRO",13,ink,true));
        TextView description=text("Вставьте постоянную ссылку подписки, которую выдали в профиле. Она хранится только на этом устройстве.",11,muted,false);
        add(box,description,5);
        EditText input=new EditText(this); input.setSingleLine(true); input.setText(profileLink);
        input.setHint("https://seyro.ru/… или vless://…");
        input.setTextSize(13); input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        add(box,input,10);
        new AlertDialog.Builder(this).setTitle("Профиль и подключение").setView(box)
            .setNegativeButton("Отмена",null).setNeutralButton("Удалить ссылку",(d,w)->{
                profileLink=""; prefs.edit().remove("subscription_link").apply(); refreshProfileState();
            }).setPositiveButton("Сохранить",(d,w)->{
                String value=input.getText().toString().trim();
                if(!value.isEmpty()&&!validLink(value)) {
                    new AlertDialog.Builder(this).setTitle("Проверьте ссылку")
                        .setMessage("Поддерживаются ссылки https://, http:// и отдельные профили vless://.")
                        .setPositiveButton("OK",null).show(); return;
                }
                profileLink=value; prefs.edit().putString("subscription_link",profileLink).apply(); refreshProfileState();
            }).show();
    }
    private boolean validLink(String value) {
        String s=value.toLowerCase(java.util.Locale.ROOT);
        return s.startsWith("https://")||s.startsWith("http://")||s.startsWith("vless://");
    }
    private void refreshProfileState() {
        renderVpnState();
    }
    private void showSettings() {
        String[] choices={"Автоматический выбор","VLESS + REALITY","Обновлять профиль вручную"};
        new AlertDialog.Builder(this).setTitle("Настройки подключения")
            .setItems(choices,(dialog,which)->{
                String message;
                if(which==0) message="Маршрут будет выбран автоматически сервером SEYRO.";
                else if(which==1) message="Используется профиль VLESS + REALITY, если он указан в вашей подписке.";
                else message="Ссылка сохранена локально. Обновление с сервера появится после подключения API.";
                new AlertDialog.Builder(this).setTitle(choices[which]).setMessage(message).setPositiveButton("Понятно",null).show();
            }).setNegativeButton("Закрыть",null).setPositiveButton("Ссылка профиля",(d,w)->showProfile()).show();
    }
    private void showSubscription() {
        new AlertDialog.Builder(this).setTitle("Моя подписка")
            .setMessage(profileLink.isEmpty()?"Ссылка подписки ещё не добавлена. Откройте «Профиль и безопасность».":"Ссылка сохранена. Срок действия и трафик пока не загружаются: API SEYRO ещё не подключён.")
            .setPositiveButton(profileLink.isEmpty()?"Добавить ссылку":"ОК",(d,w)->{if(profileLink.isEmpty())showProfile();}).show();
    }
    private void showHelp() {
        new AlertDialog.Builder(this).setTitle("Помощь")
            .setMessage("Добавьте постоянную ссылку из личного кабинета SEYRO. Добавьте ссылку профиля VLESS + REALITY из личного кабинета SEYRO. Если подключение не запускается, проверьте ссылку и доступ к серверу.")
            .setPositiveButton("Понятно",null).show();
    }
    private void onConnect() {
        if("connected".equals(SeyroVpnService.vpnState)) {
            connect.setEnabled(false); connect.setText("Отключаемся…");
            Intent stop=new Intent(this,SeyroVpnService.class); stop.setAction(SeyroVpnService.ACTION_STOP); startService(stop);
            return;
        }
        if("connecting".equals(SeyroVpnService.vpnState)||"disconnecting".equals(SeyroVpnService.vpnState)) return;
        if(profileLink.isEmpty()) { showProfile(); return; }
        if(!selectedConfig.isEmpty()) { requestVpnPermissionAndStart(); return; }
        connect.setEnabled(false); connect.setText("Загрузка профиля…");
        new Thread(() -> {
            String error=null, config=null;
            try { config=fetchAndBuildConfig(profileLink); }
            catch(Exception e) { error=e.getMessage()==null?"Не удалось прочитать подписку":e.getMessage(); }
            final String result=config, problem=error;
            runOnUiThread(() -> {
                connect.setEnabled(true); connect.setText("Подключить");
                if(problem!=null) {
                    new AlertDialog.Builder(this).setTitle("Не удалось загрузить подписку")
                        .setMessage(problem).setPositiveButton("Изменить ссылку",(d,w)->showProfile())
                        .setNegativeButton("Закрыть",null).show();
                    return;
                }
                selectedConfig=result;
                hint.setText("Профиль загружен. Нажмите «Подключить» для запуска VPN.");
                requestVpnPermissionAndStart();
            });
        }).start();
    }

    private String fetchAndBuildConfig(String link) throws Exception {
        URL url=new URL(link);
        if(!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol()))
            throw new IllegalArgumentException("Для подписки нужна ссылка HTTPS или HTTP.");
        java.net.HttpURLConnection conn=(java.net.HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(12000); conn.setReadTimeout(15000); conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent","SEYRO-Lite/"+BuildConfig.VERSION_NAME);
        try {
            int code=conn.getResponseCode();
            if(code<200||code>=300) throw new IllegalStateException("Сервер подписки ответил HTTP "+code);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()) {
                byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1) { out.write(buf,0,n); if(out.size()>2_000_000) throw new IllegalStateException("Ответ подписки слишком большой"); }
            }
            String body=new String(out.toByteArray(),StandardCharsets.UTF_8).trim();
            if(!body.contains("://")) {
                String compact=body.replaceAll("\\s+","");
                try { body=new String(Base64.decode(compact,Base64.DEFAULT),StandardCharsets.UTF_8).trim(); }
                catch(Exception ignored) {}
            }
            for(String line:body.split("\\r?\\n")) {
                String candidate=line.trim();
                if(candidate.startsWith("vless://")) return buildVlessConfig(candidate);
            }
            throw new IllegalStateException("В подписке не найден профиль VLESS. Поддерживается список VLESS + REALITY.");
        } finally { conn.disconnect(); }
    }

    private String decode(String value) throws Exception {
        return URLDecoder.decode(value, "UTF-8");
    }
    private String buildVlessConfig(String raw) throws Exception {
        URI uri=new URI(raw);
        String authority=uri.getRawAuthority();
        if(authority==null||!authority.contains("@")) throw new IllegalArgumentException("Некорректная VLESS-ссылка.");
        String[] auth=authority.split("@",2);
        String id=auth[0]; String hostPort=auth[1];
        int colon=hostPort.lastIndexOf(':');
        if(colon<1) throw new IllegalArgumentException("В профиле не указан порт сервера.");
        String host=hostPort.substring(0,colon); int port=Integer.parseInt(hostPort.substring(colon+1));
        java.util.Map<String,String> q=new java.util.HashMap<>();
        if(uri.getRawQuery()!=null) for(String pair:uri.getRawQuery().split("&")) {
            String[] kv=pair.split("=",2); q.put(decode(kv[0]),kv.length>1?decode(kv[1]):"");
        }
        String security=q.getOrDefault("security","none");
        String network=q.getOrDefault("type","tcp");
        if(!"reality".equalsIgnoreCase(security)) throw new IllegalArgumentException("Первый профиль не использует REALITY. Сейчас включена проверка VLESS + REALITY.");
        JSONObject user=new JSONObject().put("id",id).put("encryption","none");
        if(!q.getOrDefault("flow","").isEmpty()) user.put("flow",q.get("flow"));
        JSONArray users=new JSONArray().put(user);
        JSONObject vnext=new JSONObject().put("address",host).put("port",port).put("users",users);
        JSONObject settings=new JSONObject().put("vnext",new JSONArray().put(vnext));
        JSONObject reality=new JSONObject().put("serverName",q.getOrDefault("sni",host))
            .put("fingerprint",q.getOrDefault("fp","chrome"))
            .put("publicKey",q.getOrDefault("pbk",q.getOrDefault("publicKey","")))
            .put("shortId",q.getOrDefault("sid",""))
            .put("spiderX",q.getOrDefault("spx","/"));
        if(reality.optString("publicKey").isEmpty()) throw new IllegalArgumentException("В профиле REALITY отсутствует публичный ключ.");
        JSONObject stream=new JSONObject().put("network",network).put("security","reality").put("realitySettings",reality);
        JSONObject outbound=new JSONObject().put("tag","proxy").put("protocol","vless").put("settings",settings).put("streamSettings",stream);
        JSONObject direct=new JSONObject().put("tag","direct").put("protocol","freedom");
        JSONObject block=new JSONObject().put("tag","block").put("protocol","blackhole");
        JSONArray outbounds=new JSONArray().put(outbound).put(direct).put(block);
        JSONObject routing=new JSONObject().put("domainStrategy","AsIs")
            .put("rules",new JSONArray().put(new JSONObject().put("type","field").put("inboundTag",new JSONArray().put("tun-in")).put("outboundTag","proxy")));
        JSONObject inbound=new JSONObject().put("tag","tun-in").put("listen","127.0.0.1").put("port",10808)
            .put("protocol","socks").put("settings",new JSONObject().put("auth","noauth").put("udp",true));
        // AndroidLibXrayLite uses the TUN file descriptor passed to StartLoop; this config routes its traffic to the proxy outbound.
        return new JSONObject().put("log",new JSONObject().put("loglevel","warning"))
            .put("inbounds",new JSONArray().put(inbound)).put("outbounds",outbounds).put("routing",routing).toString();
    }

    private void requestVpnPermissionAndStart() {
        Intent permission=VpnService.prepare(this);
        if(permission!=null) { startActivityForResult(permission,VPN_PERMISSION_REQUEST); return; }
        startVpnService();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==VPN_PERMISSION_REQUEST) {
            if(resultCode==RESULT_OK) startVpnService();
            else new AlertDialog.Builder(this).setTitle("Нужно разрешение VPN")
                .setMessage("Android не разрешил создать VPN-туннель. Подключение не запущено.")
                .setPositiveButton("Понятно",null).show();
        }
    }
    private void startVpnService() {
        status.setText("●  ПОДКЛЮЧАЕМСЯ"); status.setTextColor(blue);
        connect.setText("Подождите…"); connect.setEnabled(false);
        hint.setText("Запускаем защищённый туннель…");
        Intent intent=new Intent(this,SeyroVpnService.class);
        intent.setAction(SeyroVpnService.ACTION_START);
        intent.putExtra(SeyroVpnService.EXTRA_CONFIG,selectedConfig);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(intent); else startService(intent);
    }
}
