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
    private java.util.ArrayList<ProfileOption> availableProfiles=new java.util.ArrayList<>();
    private static class ProfileOption {
        final String label, transport, config;
        ProfileOption(String label,String transport,String config) { this.label=label; this.transport=transport; this.config=config; }
    }
    private static final int VPN_PERMISSION_REQUEST=71;
    private static final String APP_VERSION="0.7-light";
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
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
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
        DebugLog.i(this,"APP","Запуск приложения; VPN state="+SeyroVpnService.vpnState);
        Window w=getWindow();
        w.setStatusBarColor(0xfff3f7ff); w.setNavigationBarColor(0xfff3f7ff);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setBackgroundColor(bg);
        LinearLayout root=column(); padding(root,18,dp(92),18,28); scroll.addView(root);

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=text("S",25,white,true); mark.setGravity(Gravity.CENTER); mark.setBackground(shape(blue,17,0));
        mark.setElevation(3f); header.addView(mark,new LinearLayout.LayoutParams(48,48));
        LinearLayout brand=column(); TextView name=text("SEYRO",23,ink,true); name.setLetterSpacing(.07f); brand.addView(name);
        TextView tagline=text("LITE VPN",9,blue,true); tagline.setLetterSpacing(.18f); brand.addView(tagline);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,-2,1); bp.leftMargin=11; header.addView(brand,bp);
        TextView settings=text("⚙",28,blue,true); settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("Настройки");
        settings.setBackground(shape(paleBlue,16,blue));
        settings.setElevation(2f);
        LinearLayout.LayoutParams settingsP=new LinearLayout.LayoutParams(dp(48),dp(48));
        settingsP.leftMargin=dp(8);
        header.addView(settings,settingsP); settings.setOnClickListener(v->showSettings());
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

        TextView footer=text("SEYRO LITE  ·  ВЕРСИЯ "+APP_VERSION,10,0xff93a0b5,true);
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
                profileLink=value; selectedConfig=""; availableProfiles.clear(); prefs.edit().putString("subscription_link",profileLink).apply();
                selectedConfig=""; DebugLog.i(this,"PROFILE","Ссылка подписки "+(profileLink.isEmpty()?"удалена":"сохранена; значение скрыто"));
                refreshProfileState();
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
        String[] choices={"Выбрать профиль подключения","Автоматический выбор","VLESS + REALITY","Обновлять профиль вручную","Журнал отладки"};
        new AlertDialog.Builder(this).setTitle("Настройки подключения")
            .setItems(choices,(dialog,which)->{
                if(which==4) { showDebugLog(); return; }
                if(which==0) { showProfileChoices(); return; }
                String message;
                if(which==1) message="Маршрут будет выбран автоматически сервером SEYRO.";
                else if(which==2) message="Используется профиль VLESS + REALITY, если он указан в вашей подписке.";
                else message="Ссылка сохранена локально. Обновление с сервера появится после подключения API.";
                new AlertDialog.Builder(this).setTitle(choices[which]).setMessage(message).setPositiveButton("Понятно",null).show();
            }).setNegativeButton("Закрыть",null).setPositiveButton("Ссылка профиля",(d,w)->showProfile()).show();
    }
    private void showProfileChoices() {
        if(!"disconnected".equals(SeyroVpnService.vpnState)) {
            new AlertDialog.Builder(this).setTitle("Сначала отключите VPN")
                .setMessage("Выбор транспорта доступен после отключения.")
                .setPositiveButton("Понятно",null).show();
            return;
        }
        if(!availableProfiles.isEmpty()) {
            String[] labels=new String[availableProfiles.size()];
            for(int i=0;i<labels.length;i++) labels[i]=availableProfiles.get(i).label+" — "+availableProfiles.get(i).transport.toUpperCase(java.util.Locale.ROOT);
            new AlertDialog.Builder(this).setTitle("Выберите профиль")
                .setItems(labels,(d,which)->selectProfileWithoutStarting(availableProfiles.get(which)))
                .setNegativeButton("Отмена",null).show();
        } else {
            selectedConfig="";
            onConnect();
        }
    }

    private void selectProfileWithoutStarting(ProfileOption option) {
        selectedConfig=option.config;
        DebugLog.i(this,"PROFILE","Выбран профиль в настройках: "+option.label+"; transport="+option.transport);
        hint.setText("Выбран профиль: "+option.label+" ("+option.transport.toUpperCase(java.util.Locale.ROOT)+")");
        new AlertDialog.Builder(this).setTitle("Профиль выбран")
            .setMessage(option.label+"\nТранспорт: "+option.transport.toUpperCase(java.util.Locale.ROOT)+"\nНажмите «Подключить» для запуска.")
            .setPositiveButton("Понятно",null).show();
    }

    private void showDebugLog() {
        String log=DebugLog.read(this);
        if(log.isEmpty()) log="Журнал пока пуст. Повторите подключение, затем откройте этот экран.";
        TextView view=text(log,11,ink,false); view.setTextIsSelectable(true); view.setPadding(18,12,18,12);
        ScrollView scroll=new ScrollView(this); scroll.addView(view);
        new AlertDialog.Builder(this).setTitle("Журнал отладки")
            .setView(scroll).setNeutralButton("Поделиться",(d,w)->{
                Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"SEYRO Lite — журнал отладки").putExtra(Intent.EXTRA_TEXT,DebugLog.read(this));
                startActivity(Intent.createChooser(share,"Отправить журнал"));
            }).setNegativeButton("Очистить",(d,w)->DebugLog.clear(this))
            .setPositiveButton("Закрыть",null).show();
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
        DebugLog.i(this,"UI","Нажата кнопка подключения; state="+SeyroVpnService.vpnState);
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
            String error=null;
            java.util.ArrayList<ProfileOption> configs=null;
            try { configs=fetchAndBuildConfigs(profileLink); }
            catch(Exception e) { error=e.getMessage()==null?"Не удалось прочитать подписку":e.getMessage(); DebugLog.e(this,"SUBSCRIPTION","Ошибка загрузки/разбора: "+e.getClass().getSimpleName()+": "+error); }
            final java.util.ArrayList<ProfileOption> result=configs;
            final String problem=error;
            runOnUiThread(() -> {
                connect.setEnabled(true); connect.setText("Подключить");
                if(problem!=null) {
                    new AlertDialog.Builder(this).setTitle("Не удалось загрузить подписку")
                        .setMessage(problem).setPositiveButton("Изменить ссылку",(d,w)->showProfile())
                        .setNegativeButton("Закрыть",null).show();
                    return;
                }
                availableProfiles=result;
                if(result.size()>1) {
                    String[] labels=new String[result.size()];
                    for(int i=0;i<result.size();i++) labels[i]=result.get(i).label+" — "+result.get(i).transport.toUpperCase(java.util.Locale.ROOT);
                    new AlertDialog.Builder(this).setTitle("Выберите профиль подключения")
                        .setMessage("Проверьте TCP первым. Если сайты не откроются, отключитесь и попробуйте другой транспорт.")
                        .setItems(labels,(d,which)->selectProfile(result.get(which)))
                        .setNegativeButton("Отмена",null).show();
                } else selectProfile(result.get(0));
            });
        }).start();
    }

    private void selectProfile(ProfileOption option) {
        selectedConfig=option.config;
        DebugLog.i(this,"PROFILE","Выбран профиль: "+option.label+"; transport="+option.transport);
        hint.setText("Профиль: "+option.label+" ("+option.transport.toUpperCase(java.util.Locale.ROOT)+")");
        requestVpnPermissionAndStart();
    }

    private String detectTransport(String config) {
        try {
            JSONObject root=new JSONObject(config);
            JSONArray outbounds=root.optJSONArray("outbounds");
            if(outbounds!=null) for(int i=0;i<outbounds.length();i++) {
                JSONObject o=outbounds.optJSONObject(i);
                if(o!=null && "vless".equalsIgnoreCase(o.optString("protocol"))) {
                    JSONObject stream=o.optJSONObject("streamSettings");
                    return stream==null?"tcp":stream.optString("network","tcp");
                }
            }
        } catch(Exception ignored) {}
        return "unknown";
    }

    private java.util.ArrayList<ProfileOption> fetchAndBuildConfigs(String link) throws Exception {
        DebugLog.i(this,"SUBSCRIPTION","Начата загрузка профиля; ссылка скрыта");
        URL url=new URL(link);
        if(!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol()))
            throw new IllegalArgumentException("Для подписки нужна ссылка HTTPS или HTTP.");
        java.net.HttpURLConnection conn=(java.net.HttpURLConnection)url.openConnection();
        conn.setConnectTimeout(12000); conn.setReadTimeout(15000); conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent","SEYRO-Lite/"+APP_VERSION);
        try {
            int code=conn.getResponseCode();
            DebugLog.i(this,"SUBSCRIPTION","HTTP статус="+code+"; тип ответа="+conn.getContentType());
            if(code<200||code>=300) throw new IllegalStateException("Сервер подписки ответил HTTP "+code);
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=conn.getInputStream()) {
                byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1) { out.write(buf,0,n); if(out.size()>2_000_000) throw new IllegalStateException("Ответ подписки слишком большой"); }
            }
            String body=new String(out.toByteArray(),StandardCharsets.UTF_8).trim();
            DebugLog.i(this,"SUBSCRIPTION","Ответ получен; байт="+out.size()+"; начало формата="+(body.startsWith("[")?"JSON-массив":body.startsWith("{")?"JSON-объект":body.startsWith("vless://")?"VLESS":"другой/Base64"));
            // Some providers return a JSON array of complete Xray profiles instead of vless:// links.
            if(body.startsWith("[") || body.startsWith("{")) {
                try {
                    JSONArray profiles = body.startsWith("[") ? new JSONArray(body) : new JSONArray().put(new JSONObject(body));
                    java.util.ArrayList<ProfileOption> parsed=new java.util.ArrayList<>();
                    for(int i=0;i<profiles.length();i++) {
                        JSONObject p=profiles.optJSONObject(i); if(p==null) continue;
                        try {
                            String cfg=buildJsonProfileConfig(p);
                            String label=p.optString("remarks",p.optString("name","Профиль "+(i+1)));
                            String transport=detectTransport(cfg);
                            parsed.add(new ProfileOption(label,transport,cfg));
                            DebugLog.i(this,"PROFILE","Найден профиль #"+(i+1)+": "+label+"; transport="+transport);
                        } catch(Exception ex) { DebugLog.w(this,"PROFILE","Пропущен профиль #"+(i+1)+": "+ex.getMessage()); }
                    }
                    if(!parsed.isEmpty()) return parsed;
                } catch(org.json.JSONException ignored) { /* Continue with Base64/link parsing. */ }
            }
            // Subscription providers may return plain links, standard Base64, or URL-safe Base64.
            // Try decoding more than once, because some gateways wrap an already encoded subscription.
            for(int attempt=0;attempt<3 && !body.contains("vless://");attempt++) {
                String compact=body.replaceAll("\\s+","");
                String decoded=null;
                int[] flags={Base64.DEFAULT,Base64.URL_SAFE|Base64.NO_WRAP,Base64.NO_WRAP};
                for(int flag:flags) {
                    try {
                        String candidate=new String(Base64.decode(compact,flag),StandardCharsets.UTF_8).trim();
                        if(!candidate.isEmpty() && !candidate.equals(body)) { decoded=candidate; break; }
                    } catch(Exception ignored) {}
                }
                if(decoded==null) break;
                body=decoded;
            }
            // A Base64 subscription may decode to JSON rather than to vless:// links.
            if(body.startsWith("[") || body.startsWith("{")) {
                try {
                    JSONArray profiles = body.startsWith("[") ? new JSONArray(body) : new JSONArray().put(new JSONObject(body));
                    java.util.ArrayList<ProfileOption> parsed=new java.util.ArrayList<>();
                    for(int i=0;i<profiles.length();i++) {
                        JSONObject p=profiles.optJSONObject(i); if(p==null) continue;
                        try {
                            String cfg=buildJsonProfileConfig(p);
                            String label=p.optString("remarks",p.optString("name","Профиль "+(i+1)));
                            String transport=detectTransport(cfg);
                            parsed.add(new ProfileOption(label,transport,cfg));
                            DebugLog.i(this,"PROFILE","Найден профиль #"+(i+1)+": "+label+"; transport="+transport);
                        } catch(Exception ex) { DebugLog.w(this,"PROFILE","Пропущен профиль #"+(i+1)+": "+ex.getMessage()); }
                    }
                    if(!parsed.isEmpty()) return parsed;
                } catch(org.json.JSONException ignored) { /* Continue with link parsing. */ }
            }
            for(String line:body.split("\\r?\\n")) {
                String candidate=line.trim();
                int linkStart=candidate.indexOf("vless://");
                if(linkStart>=0) {
                    int linkEnd=candidate.indexOf(' ',linkStart);
                    if(linkEnd>linkStart) candidate=candidate.substring(linkStart,linkEnd);
                    else candidate=candidate.substring(linkStart);
                    try {
                        String cfg=buildVlessConfig(candidate);
                        java.util.ArrayList<ProfileOption> one=new java.util.ArrayList<>();
                        String label="VLESS-профиль";
                        int hash=candidate.lastIndexOf('#'); if(hash>=0 && hash<candidate.length()-1) label=URLDecoder.decode(candidate.substring(hash+1),"UTF-8");
                        one.add(new ProfileOption(label,detectTransport(cfg),cfg));
                        return one;
                    } catch(Exception ignored) { /* Try the next VLESS entry, if any. */ }
                }
            }
            throw new IllegalStateException("Ссылка открылась, но формат ответа подписки не распознан. Нужен список ссылок VLESS + REALITY; сервер мог вернуть другой формат или сообщение об ошибке.");
        } finally { conn.disconnect(); }
    }

    private String buildJsonProfileConfig(JSONObject profile) throws Exception {
        JSONArray sourceOutbounds=profile.optJSONArray("outbounds");
        if(sourceOutbounds==null) throw new IllegalArgumentException("В JSON-профиле нет outbounds.");
        JSONObject sourceProxy=null;
        for(int i=0;i<sourceOutbounds.length();i++) {
            JSONObject candidate=sourceOutbounds.optJSONObject(i);
            if(candidate!=null && "vless".equalsIgnoreCase(candidate.optString("protocol"))) {
                sourceProxy=candidate; break;
            }
        }
        if(sourceProxy==null) throw new IllegalArgumentException("В JSON-профиле нет VLESS outbound.");
        JSONObject sourceSettings=sourceProxy.optJSONObject("settings");
        JSONArray vnexts=sourceSettings==null?null:sourceSettings.optJSONArray("vnext");
        if(vnexts==null||vnexts.length()==0) throw new IllegalArgumentException("В JSON-профиле не указан сервер VLESS.");
        JSONObject sourceVnext=vnexts.getJSONObject(0);
        JSONArray sourceUsers=sourceVnext.optJSONArray("users");
        if(sourceUsers==null||sourceUsers.length()==0) throw new IllegalArgumentException("В JSON-профиле не указан пользователь VLESS.");
        JSONObject sourceUser=sourceUsers.getJSONObject(0);
        JSONObject sourceStream=sourceProxy.optJSONObject("streamSettings");
        if(sourceStream==null||!"reality".equalsIgnoreCase(sourceStream.optString("security")))
            throw new IllegalArgumentException("Профиль VLESS не использует REALITY.");
        JSONObject sourceReality=sourceStream.optJSONObject("realitySettings");
        if(sourceReality==null||sourceReality.optString("publicKey").isEmpty())
            throw new IllegalArgumentException("В профиле REALITY отсутствует публичный ключ.");

        // Preserve the provider's complete VLESS outbound. Rebuilding only a subset of
        // streamSettings silently drops transport-specific fields (sockopt, tcp/raw,
        // xhttp mode/host/path, mux, packetEncoding, and future Xray options).
        // Only normalize the tag because the app's routing rule targets "proxy".
        JSONObject outbound=new JSONObject(sourceProxy.toString()).put("tag","proxy");
        String network=sourceStream.optString("network","tcp");
        JSONObject direct=new JSONObject().put("tag","direct").put("protocol","freedom");
        JSONObject block=new JSONObject().put("tag","block").put("protocol","blackhole");
        JSONArray outbounds=new JSONArray().put(outbound).put(direct).put(block);
        JSONObject routing=new JSONObject().put("domainStrategy","AsIs")
            .put("rules",new JSONArray().put(new JSONObject().put("type","field").put("inboundTag",new JSONArray().put("tun-in")).put("outboundTag","proxy")));
        JSONObject inbound=new JSONObject().put("tag","tun-in").put("port",0)
            .put("protocol","tun").put("settings",new JSONObject().put("name","seyro").put("MTU",1400));
        return new JSONObject().put("log",new JSONObject().put("loglevel","debug"))
            .put("stats",new JSONObject())
            .put("policy",new JSONObject()
                .put("levels",new JSONObject().put("0",new JSONObject().put("statsUserUplink",true).put("statsUserDownlink",true)))
                .put("system",new JSONObject().put("statsOutboundUplink",true).put("statsOutboundDownlink",true)))
            .put("inbounds",new JSONArray().put(inbound)).put("outbounds",outbounds).put("routing",routing).toString();
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
        JSONObject inbound=new JSONObject().put("tag","tun-in").put("port",0)
            .put("protocol","tun").put("settings",new JSONObject().put("name","seyro").put("MTU",1400));
        // Xray's native TUN inbound consumes the VpnService file descriptor passed to StartLoop.
        return new JSONObject().put("log",new JSONObject().put("loglevel","warning"))
            .put("stats",new JSONObject())
            .put("policy",new JSONObject()
                .put("levels",new JSONObject().put("0",new JSONObject().put("statsUserUplink",true).put("statsUserDownlink",true)))
                .put("system",new JSONObject().put("statsOutboundUplink",true).put("statsOutboundDownlink",true)))
            .put("inbounds",new JSONArray().put(inbound)).put("outbounds",outbounds).put("routing",routing).toString();
    }

    private void requestVpnPermissionAndStart() {
        DebugLog.i(this,"VPN","Проверка разрешения Android VPN");
        Intent permission=VpnService.prepare(this);
        if(permission!=null) { DebugLog.i(this,"VPN","Запрошено разрешение Android VPN"); startActivityForResult(permission,VPN_PERMISSION_REQUEST); return; }
        startVpnService();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==VPN_PERMISSION_REQUEST) {
            if(resultCode==RESULT_OK) { DebugLog.i(this,"VPN","Разрешение Android VPN получено"); startVpnService(); }
            else { DebugLog.w(this,"VPN","Пользователь отклонил разрешение Android VPN"); new AlertDialog.Builder(this).setTitle("Нужно разрешение VPN")
                .setMessage("Android не разрешил создать VPN-туннель. Подключение не запущено.")
                .setPositiveButton("Понятно",null).show(); }
        }
    }
    private void startVpnService() {
        DebugLog.i(this,"VPN","Отправлена команда запуска службы; конфигурация не записывается");
        status.setText("●  ПОДКЛЮЧАЕМСЯ"); status.setTextColor(blue);
        connect.setText("Подождите…"); connect.setEnabled(false);
        hint.setText("Запускаем защищённый туннель…");
        Intent intent=new Intent(this,SeyroVpnService.class);
        intent.setAction(SeyroVpnService.ACTION_START);
        intent.putExtra(SeyroVpnService.EXTRA_CONFIG,selectedConfig);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(intent); else startService(intent);
    }
}
