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
    private TextView connect,status,hint,profileLinkValue,subscriptionState,protocolBadge,userGreeting,subscriptionExpiryText,trafficSummary,trafficPercent;
    private View trafficProgressFill;
    private LinearLayout trafficBarTrack;
    private volatile String subscriptionTitle="", subscriptionAnnounce="";
    private volatile long trafficUpload=-1, trafficDownload=-1, trafficTotal=-1, subscriptionExpire=-1;
    private String activeTransport="";
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
    private static final String APP_VERSION=BuildConfig.VERSION_NAME;
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
        activeTransport=prefs.getString("selected_transport","");
        loadCachedSubscriptionMetadata();
        DebugLog.i(this,"APP","Запуск приложения; VPN state="+SeyroVpnService.vpnState);
        Window w=getWindow();
        w.setStatusBarColor(0xfff3f7ff); w.setNavigationBarColor(0xfff3f7ff);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xffeaf4ff,0xfff8fbff,0xffe6f0ff}));
        LinearLayout root=column(); padding(root,dp(18),dp(38),dp(18),dp(28)); scroll.addView(root);

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=text("S",27,white,true); mark.setGravity(Gravity.CENTER); mark.setBackground(shape(0xff087cff,dp(14),0));
        mark.setElevation(dp(3)); header.addView(mark,new LinearLayout.LayoutParams(dp(48),dp(48)));
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
        userGreeting=text(greetingText(),25,ink,true); userGreeting.setLineSpacing(0f,1.02f); add(root,userGreeting,6);
        subscriptionExpiryText=text("Подписка активна до: проверяем…",13,green,true);
        add(root,subscriptionExpiryText,dp(7));
        add(root,text("Безопасный доступ — в одно касание.",14,muted,false),8);

        LinearLayout hero=column(); padding(hero,20,22,20,20); hero.setBackground(shape(white,26,line)); hero.setElevation(2f);
        add(root,hero,18);
        LinearLayout center=column(); center.setGravity(Gravity.CENTER);
        TextView shield=text("S",36,white,true); shield.setGravity(Gravity.CENTER);
        shield.setBackground(shape(0xff087cff,dp(100),0)); shield.setElevation(dp(5));
        LinearLayout.LayoutParams shieldP=new LinearLayout.LayoutParams(dp(76),dp(76)); shieldP.gravity=Gravity.CENTER;
        center.addView(shield,shieldP);
        status=text("●  НЕ ПОДКЛЮЧЕНО",12,muted,true); status.setGravity(Gravity.CENTER);
        add(center,status,dp(12)); hero.addView(center);
        protocolBadge=pill("ПРОТОКОЛ НЕ ВЫБРАН",blue,paleBlue);
        LinearLayout.LayoutParams protocolP=new LinearLayout.LayoutParams(-2,-2); protocolP.gravity=Gravity.CENTER; protocolP.topMargin=dp(7); center.addView(protocolBadge,protocolP);
        hint=text(profileLink.isEmpty()?"Добавьте ссылку профиля, чтобы подключиться.":"Профиль готов к подключению.",13,muted,false);
        hint.setGravity(Gravity.CENTER); add(hero,hint,8);
        connect=text("⏻    Подключить",16,white,true); connect.setGravity(Gravity.CENTER);
        padding(connect,16,16,16,16); connect.setBackground(shape(blue,17,0)); connect.setElevation(2f); add(hero,connect,18);
        connect.setOnClickListener(v->onConnect());

        LinearLayout trafficCard=column();
        padding(trafficCard,dp(14),dp(12),dp(14),dp(12));
        trafficCard.setBackground(shape(0xfff7faff,dp(16),line));
        add(hero,trafficCard,dp(12));
        LinearLayout trafficHeader=new LinearLayout(this);
        trafficHeader.setGravity(Gravity.CENTER_VERTICAL);
        trafficHeader.addView(text("ТРАФИК ПОДПИСКИ",11,muted,true),
            new LinearLayout.LayoutParams(0,-2,1));
        trafficPercent=text("—",13,blue,true);
        trafficHeader.addView(trafficPercent);
        trafficCard.addView(trafficHeader);
        trafficSummary=text("Загрузка данных о трафике…",12,muted,false);
        add(trafficCard,trafficSummary,dp(5));
        trafficBarTrack=new LinearLayout(this);
        trafficBarTrack.setBackground(shape(0xffe2eaf6,dp(20),0));
        trafficBarTrack.setClipToOutline(true);
        trafficProgressFill=new View(this);
        trafficProgressFill.setBackground(shape(blue,dp(20),0));
        trafficBarTrack.addView(trafficProgressFill,new LinearLayout.LayoutParams(0,dp(7)));
        LinearLayout.LayoutParams trackP=new LinearLayout.LayoutParams(-1,dp(7));
        trackP.topMargin=dp(9);
        trafficCard.addView(trafficBarTrack,trackP);
        updateSubscriptionWidgets();

        LinearLayout profile=card(); padding(profile,16,5,16,5); profile.setElevation(1f); add(root,profile,14);
        profile.addView(menuRow("Профиль","Ссылка и параметры аккаунта","↗",()->showProfile()));
        profile.setOnClickListener(v->showProfile());

        LinearLayout pair=new LinearLayout(this); LinearLayout.LayoutParams pairP=new LinearLayout.LayoutParams(-1,-2);
        pairP.topMargin=12; root.addView(pair,pairP);
        LinearLayout sub=card(); sub.setElevation(1f); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1); hp.rightMargin=6;
        pair.addView(sub,hp); sub.setOnClickListener(v->showSubscription());
        sub.addView(text("ПОДПИСКА",10,muted,true));
        subscriptionState=text(profileLink.isEmpty()?"Не настроена":(subscriptionExpire>0?"Активна до "+new java.text.SimpleDateFormat("dd.MM.yyyy",java.util.Locale.getDefault()).format(new java.util.Date(subscriptionExpire*1000L)):"Профиль добавлен"),14,ink,true);
        add(sub,subscriptionState,7);
        LinearLayout help=card(); help.setElevation(1f); LinearLayout.LayoutParams hp2=new LinearLayout.LayoutParams(0,-2,1); hp2.leftMargin=6;
        pair.addView(help,hp2); help.setOnClickListener(v->showHelp());
        help.addView(text("НУЖНА ПОМОЩЬ?",10,muted,true)); add(help,text("Поддержка  ›",14,blue,true),7);

        TextView footer=text("SEYRO LITE  ·  ВЕРСИЯ "+APP_VERSION,10,0xff93a0b5,true);
        footer.setGravity(Gravity.CENTER); add(root,footer,20);
        setContentView(scroll);
        renderVpnState();
        if(!profileLink.isEmpty()) refreshSubscriptionMetadataInBackground();
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
            updateProtocolBadge();
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
            updateProtocolBadge();
        }
        updateProfileSubscriptionState();
        updateSubscriptionWidgets();
    }

    private void updateProtocolBadge() {
        if(protocolBadge==null) return;
        String state=SeyroVpnService.vpnState;
        String mode=prefs.getString("selection_mode","auto");
        String modeLabel="auto".equals(mode)?"АВТОВЫБОР":"РУЧНОЙ ВЫБОР";
        String transport=activeTransport;
        if(transport.isEmpty() && !selectedConfig.isEmpty()) transport=detectTransport(selectedConfig);
        String protocol=transport.isEmpty()?"ПРОТОКОЛ НЕ ВЫБРАН":"VLESS + REALITY  ·  "+transport.toUpperCase(java.util.Locale.ROOT);
        if("connecting".equals(state)) protocolBadge.setText(modeLabel+"  ·  ПОДКЛЮЧЕНИЕ…");
        else protocolBadge.setText(modeLabel+"  ·  "+protocol);
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
        final boolean hasProfile=!profileLink.trim().isEmpty();
        LinearLayout box=column();
        padding(box,dp(20),dp(18),dp(20),dp(18));
        box.setBackground(shape(white,dp(26),0));
        TextView icon=text(hasProfile?"✓":"＋",26,white,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(shape(hasProfile?green:blue,dp(18),0));
        LinearLayout.LayoutParams iconP=new LinearLayout.LayoutParams(dp(56),dp(56));
        iconP.gravity=Gravity.CENTER_HORIZONTAL;
        box.addView(icon,iconP);

        TextView title=text(hasProfile?"Профиль добавлен":"Добавьте профиль SEYRO",18,ink,true);
        title.setGravity(Gravity.CENTER);
        add(box,title,dp(12));
        TextView description=text(hasProfile
            ?"Ссылка подписки сохранена на этом устройстве. При необходимости её можно заменить."
            :"Вставьте постоянную ссылку подписки из личного кабинета SEYRO. Ссылка хранится только на этом устройстве.",12,muted,false);
        description.setGravity(Gravity.CENTER);
        add(box,description,dp(6));

        EditText input=new EditText(this);
        input.setSingleLine(true);
        input.setText(profileLink);
        input.setHint("https://… или vless://…");
        input.setTextSize(14);
        input.setSelectAllOnFocus(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(dp(14),dp(12),dp(14),dp(12));
        input.setBackground(shape(0xfff5f8ff,dp(14),line));
        add(box,input,dp(18));

        TextView paste=text("▣   Вставить из буфера обмена",14,blue,true);
        paste.setGravity(Gravity.CENTER);
        paste.setPadding(dp(12),dp(12),dp(12),dp(12));
        paste.setBackground(shape(paleBlue,dp(14),0));
        add(box,paste,dp(10));
        paste.setOnClickListener(v->{
            android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(clipboard!=null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount()>0) {
                CharSequence clip=clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                if(clip!=null && clip.length()>0) {
                    input.setText(clip.toString().trim());
                    input.setSelection(input.length());
                    Toast.makeText(this,"Ссылка вставлена",Toast.LENGTH_SHORT).show();
                } else Toast.makeText(this,"Буфер обмена пуст",Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this,"Буфер обмена пуст",Toast.LENGTH_SHORT).show();
        });

        if(hasProfile) {
            TextView remove=text("Удалить сохранённую ссылку",13,0xffc13e4b,true);
            remove.setGravity(Gravity.CENTER);
            remove.setPadding(dp(12),dp(13),dp(12),dp(10));
            add(box,remove,dp(8));
            remove.setOnClickListener(v->new AlertDialog.Builder(this)
                .setTitle("Удалить профиль?")
                .setMessage("Сохранённая ссылка будет удалена с этого устройства. Для подключения потребуется добавить её снова.")
                .setNegativeButton("Оставить",null)
                .setPositiveButton("Удалить",(dialog,which)->{
                    profileLink="";
                    clearCachedSubscriptionMetadata();
                    selectedConfig="";
                    activeTransport="";
                    availableProfiles.clear();
                    prefs.edit().remove("subscription_link").remove("selected_transport").apply();
                    DebugLog.i(this,"PROFILE","Ссылка подписки удалена");
                    refreshProfileState();
                }).show());
        }

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionsP=new LinearLayout.LayoutParams(-1,-2);
        actionsP.topMargin=dp(12);
        box.addView(actions,actionsP);

        TextView cancel=text("Отмена",14,blue,true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(10),dp(12),dp(10),dp(12));
        cancel.setBackground(shape(paleBlue,dp(14),line));
        LinearLayout.LayoutParams cancelP=new LinearLayout.LayoutParams(0,-2,1);
        cancelP.rightMargin=dp(6);
        actions.addView(cancel,cancelP);

        TextView save=text("✓   Сохранить",14,blue,true);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(10),dp(12),dp(10),dp(12));
        save.setBackground(shape(paleBlue,dp(14),0));
        LinearLayout.LayoutParams saveP=new LinearLayout.LayoutParams(0,-2,1);
        saveP.leftMargin=dp(6);
        actions.addView(save,saveP);

        AlertDialog dialog=new AlertDialog.Builder(this)
            .setView(box)
            .create();
        dialog.setOnShowListener(ignored->{
            Window window=dialog.getWindow();
            if(window!=null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams params=window.getAttributes();
                params.width=getResources().getDisplayMetrics().widthPixels-dp(32);
                params.height=WindowManager.LayoutParams.WRAP_CONTENT;
                params.gravity=Gravity.CENTER;
                params.dimAmount=0.58f;
                window.setAttributes(params);
            }
        });
        cancel.setOnClickListener(v->dialog.dismiss());
        save.setOnClickListener(v->{
            String value=input.getText().toString().trim();
            if(value.isEmpty()) {
                input.setError("Вставьте ссылку подписки");
                return;
            }
            if(!validLink(value)) {
                input.setError("Нужна ссылка HTTPS, HTTP или VLESS");
                return;
            }
            boolean changed=!value.equals(profileLink);
            profileLink=value;
            prefs.edit().putString("subscription_link",profileLink).apply();
            if(changed) {
                clearCachedSubscriptionMetadata();
                selectedConfig="";
                activeTransport="";
                availableProfiles.clear();
                prefs.edit().remove("selected_transport").apply();
            }
            DebugLog.i(this,"PROFILE","Ссылка подписки сохранена; значение скрыто");
            refreshProfileState();
            dialog.dismiss();
            Toast.makeText(this,changed?"Профиль сохранён":"Ссылка не изменилась",Toast.LENGTH_SHORT).show();
        });
        dialog.show();
    }

    private boolean validLink(String value) {
        String s=value.toLowerCase(java.util.Locale.ROOT);
        return s.startsWith("https://")||s.startsWith("http://")||s.startsWith("vless://");
    }
    private void refreshProfileState() {
        if(userGreeting!=null) userGreeting.setText(greetingText());
        updateProfileSubscriptionState();
        updateSubscriptionWidgets();
        renderVpnState();
        if(!profileLink.isEmpty()) refreshSubscriptionMetadataInBackground();
    }
    private void showSettings() {
        String mode=prefs.getString("selection_mode","auto");
        boolean manual="manual".equals(mode);
        java.util.ArrayList<String> items=new java.util.ArrayList<>();
        java.util.ArrayList<String> actions=new java.util.ArrayList<>();

        items.add(("auto".equals(mode)?"✓  ":"")+"Автоматический выбор");
        actions.add("auto");
        items.add(("manual".equals(mode)?"✓  ":"")+"Ручной выбор");
        actions.add("manual");

        // A specific transport can be selected only in manual mode.
        if(manual) {
            items.add("↳  Выбрать профиль подключения");
            actions.add("choose_profile");
        }
        items.add("Обновить подписку");
        actions.add("refresh");
        items.add("Журнал отладки");
        actions.add("debug");

        new AlertDialog.Builder(this)
            .setTitle("Настройки подключения")
            .setMessage(manual
                ?"Ручной режим позволяет самостоятельно выбрать транспорт подключения."
                :"В автоматическом режиме используется первый доступный профиль подписки.")
            .setItems(items.toArray(new String[0]),(dialog,which)->{
                String action=actions.get(which);
                if("debug".equals(action)) { showDebugLog(); return; }
                if("choose_profile".equals(action)) { showProfileChoices(); return; }
                if("auto".equals(action) || "manual".equals(action)) {
                    String next=action;
                    boolean changed=!next.equals(prefs.getString("selection_mode","auto"));
                    prefs.edit().putString("selection_mode",next).apply();
                    updateProtocolBadge();
                    if(changed) {
                        Toast.makeText(this,next.equals("auto")
                            ?"Включён автоматический выбор"
                            :"Включён ручной выбор профиля",Toast.LENGTH_SHORT).show();
                    }
                    // Reopen after the current dialog closes so the menu updates immediately.
                    dialog.dismiss();
                    new android.os.Handler(getMainLooper()).postDelayed(this::showSettings,180);
                    return;
                }
                if("refresh".equals(action)) {
                    refreshSubscriptionMetadataInBackground();
                    new AlertDialog.Builder(this).setTitle("Обновление подписки")
                        .setMessage("Запрошено обновление данных подписки. Список профилей будет обновлён при следующем подключении.")
                        .setPositiveButton("Понятно",null).show();
                }
            })
            .setNegativeButton("Закрыть",null)
            .setPositiveButton("Ссылка профиля",(d,w)->showProfile())
            .show();
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
        activeTransport=option.transport;
        prefs.edit().putString("selected_transport",activeTransport).apply();
        DebugLog.i(this,"PROFILE","Выбран профиль в настройках: "+option.label+"; transport="+option.transport);
        updateProtocolBadge();
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
        StringBuilder info=new StringBuilder();
        if(profileLink.isEmpty()) info.append("Ссылка подписки ещё не добавлена. Откройте «Профиль и безопасность».");
        else {
            if(!subscriptionTitle.isEmpty()) info.append(subscriptionTitle).append("\n\n");
            if(trafficUpload>=0 || trafficDownload>=0) {
                info.append("Отправлено: ").append(trafficUpload<0?"—":formatBytes(trafficUpload)).append("\n");
                info.append("Получено: ").append(trafficDownload<0?"—":formatBytes(trafficDownload)).append("\n");
                if(trafficTotal>0) {
                    long used=Math.max(0,trafficUpload)+Math.max(0,trafficDownload);
                    info.append("Использовано: ").append(formatBytes(used)).append(" из ").append(formatBytes(trafficTotal)).append("\n");
                    info.append("Осталось: ").append(formatBytes(Math.max(0,trafficTotal-used))).append("\n");
                }
            } else info.append("Данные о трафике сервером не переданы.\n");
            if(subscriptionExpire>0) info.append("Действует до: ").append(new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.getDefault()).format(new java.util.Date(subscriptionExpire*1000L))).append("\n");
            if(!subscriptionAnnounce.isEmpty()) info.append("\n").append(subscriptionAnnounce);
        }
        new AlertDialog.Builder(this).setTitle("Моя подписка")
            .setMessage(info.toString())
            .setPositiveButton(profileLink.isEmpty()?"Добавить ссылку":"ОК",(d,w)->{if(profileLink.isEmpty())showProfile();}).show();
    }

    private void updateSubscriptionWidgets() {
        if(subscriptionExpiryText!=null) {
            if(profileLink.isEmpty()) {
                subscriptionExpiryText.setText("Добавьте ссылку, чтобы проверить подписку");
                subscriptionExpiryText.setTextColor(muted);
            } else if(subscriptionExpire>0) {
                java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("dd.MM.yyyy 'в' HH:mm",java.util.Locale.getDefault());
                subscriptionExpiryText.setText("Подписка активна до "+fmt.format(new java.util.Date(subscriptionExpire*1000L)));
                subscriptionExpiryText.setTextColor(green);
            } else {
                subscriptionExpiryText.setText("Срок подписки появится после проверки");
                subscriptionExpiryText.setTextColor(muted);
            }
        }
        if(trafficSummary==null || trafficPercent==null || trafficProgressFill==null || trafficBarTrack==null) return;
        if(profileLink.isEmpty()) {
            trafficPercent.setText("—");
            trafficSummary.setText("Добавьте ссылку подписки");
            trafficProgressFill.setVisibility(View.GONE);
            return;
        }
        if(trafficTotal<=0 || (trafficUpload<0 && trafficDownload<0)) {
            trafficPercent.setText("—");
            trafficSummary.setText("Нет данных о лимите трафика от сервера");
            trafficProgressFill.setVisibility(View.GONE);
            return;
        }
        long used=Math.max(0,trafficUpload)+Math.max(0,trafficDownload);
        int percent=(int)Math.min(100,Math.round(used*100.0/trafficTotal));
        trafficPercent.setText(percent+"%");
        trafficSummary.setText(formatBytes(used)+" из "+formatBytes(trafficTotal)+" · осталось "+formatBytes(Math.max(0,trafficTotal-used)));
        trafficProgressFill.setVisibility(View.VISIBLE);
        trafficProgressFill.setBackground(shape(percent>=90?0xffd94b55:blue,dp(20),0));
        trafficBarTrack.post(()->{
            int width=trafficBarTrack.getWidth();
            if(width>0) {
                LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)trafficProgressFill.getLayoutParams();
                p.width=(int)(width*percent/100.0);
                p.height=dp(7);
                trafficProgressFill.setLayoutParams(p);
            }
        });
    }

    private void clearCachedSubscriptionMetadata() {
        subscriptionTitle="";
        subscriptionAnnounce="";
        trafficUpload=-1;
        trafficDownload=-1;
        trafficTotal=-1;
        subscriptionExpire=-1;
        prefs.edit()
            .remove("cached_subscription_title")
            .remove("cached_subscription_announce")
            .remove("cached_traffic_upload")
            .remove("cached_traffic_download")
            .remove("cached_traffic_total")
            .remove("cached_subscription_expire")
            .remove("cached_subscription_updated_at")
            .apply();
    }

    private void loadCachedSubscriptionMetadata() {
        subscriptionTitle=prefs.getString("cached_subscription_title","");
        subscriptionAnnounce=prefs.getString("cached_subscription_announce","");
        trafficUpload=prefs.getLong("cached_traffic_upload",-1);
        trafficDownload=prefs.getLong("cached_traffic_download",-1);
        trafficTotal=prefs.getLong("cached_traffic_total",-1);
        subscriptionExpire=prefs.getLong("cached_subscription_expire",-1);
    }

    private void refreshSubscriptionMetadataInBackground() {
        final String link=profileLink;
        new Thread(() -> {
            java.net.HttpURLConnection conn=null;
            try {
                URL url=new URL(link);
                if(!"https".equalsIgnoreCase(url.getProtocol()) && !"http".equalsIgnoreCase(url.getProtocol())) return;
                conn=(java.net.HttpURLConnection)url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent","SEYRO-Lite/"+APP_VERSION);
                int code=conn.getResponseCode();
                if(code>=200 && code<300) {
                    readSubscriptionMetadata(conn);
                    runOnUiThread(() -> {
                        if(!link.equals(profileLink)) return;
                        if(userGreeting!=null) userGreeting.setText(greetingText());
                        updateSubscriptionWidgets();
                        updateProfileSubscriptionState();
                    });
                    DebugLog.i(this,"SUBSCRIPTION","Кэш подписки обновлён при запуске; HTTP "+code);
                } else DebugLog.w(this,"SUBSCRIPTION","Обновление метаданных при запуске: HTTP "+code);
            } catch(Exception e) {
                DebugLog.w(this,"SUBSCRIPTION","Не удалось обновить метаданные при запуске: "+e.getClass().getSimpleName());
            } finally {
                if(conn!=null) conn.disconnect();
            }
        }).start();
    }

    private void updateProfileSubscriptionState() {
        if(subscriptionState==null) return;
        if(profileLink.isEmpty()) {
            subscriptionState.setText("Не настроена");
            return;
        }
        if(subscriptionExpire>0) {
            String date=new java.text.SimpleDateFormat("dd.MM.yyyy",java.util.Locale.getDefault())
                .format(new java.util.Date(subscriptionExpire*1000L));
            subscriptionState.setText("Активна до "+date);
        } else {
            subscriptionState.setText("Профиль добавлен");
        }
    }

    private String greetingText() {
        if(!subscriptionAnnounce.isEmpty()) return subscriptionAnnounce;
        if(!subscriptionTitle.isEmpty()) return "Добро пожаловать, "+subscriptionTitle;
        return "Ваш интернет.\nПод защитой.";
    }

    private String decodeMetadataHeader(String value) {
        if(value==null) return "";
        String v=value.trim();
        if(v.regionMatches(true,0,"base64:",0,7)) {
            try { return new String(Base64.decode(v.substring(7),Base64.DEFAULT),StandardCharsets.UTF_8).trim(); }
            catch(Exception ignored) {}
        }
        return v;
    }

    private String formatBytes(long bytes) {
        if(bytes<1024) return bytes+" Б";
        double value=bytes; String[] units={"Б","КБ","МБ","ГБ","ТБ"}; int i=0;
        while(value>=1024 && i<units.length-1) { value/=1024.0; i++; }
        return String.format(java.util.Locale.getDefault(),"%.2f %s",value,units[i]);
    }

    private void readSubscriptionMetadata(java.net.HttpURLConnection conn) {
        String title=decodeMetadataHeader(conn.getHeaderField("profile-title"));
        String announce=decodeMetadataHeader(conn.getHeaderField("announce"));
        if(!title.isEmpty()) subscriptionTitle=title;
        if(!announce.isEmpty()) subscriptionAnnounce=announce;
        String userInfo=conn.getHeaderField("subscription-userinfo");
        if(userInfo!=null) for(String part:userInfo.split(";")) {
            String[] kv=part.trim().split("=",2);
            if(kv.length!=2) continue;
            try {
                long n=Long.parseLong(kv[1].trim());
                switch(kv[0].trim().toLowerCase(java.util.Locale.ROOT)) {
                    case "upload": trafficUpload=n; break;
                    case "download": trafficDownload=n; break;
                    case "total": trafficTotal=n; break;
                    case "expire": subscriptionExpire=n; break;
                }
            } catch(NumberFormatException ignored) {}
        }
        prefs.edit()
            .putString("cached_subscription_title",subscriptionTitle)
            .putString("cached_subscription_announce",subscriptionAnnounce)
            .putLong("cached_traffic_upload",trafficUpload)
            .putLong("cached_traffic_download",trafficDownload)
            .putLong("cached_traffic_total",trafficTotal)
            .putLong("cached_subscription_expire",subscriptionExpire)
            .putLong("cached_subscription_updated_at",System.currentTimeMillis())
            .apply();
        DebugLog.i(this,"SUBSCRIPTION","Метаданные сохранены в кэш: title="+(!subscriptionTitle.isEmpty())+
            "; announce="+(!subscriptionAnnounce.isEmpty())+"; userinfo="+(userInfo!=null));
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
                if(result.size()>1 && "manual".equals(prefs.getString("selection_mode","auto"))) {
                    String[] labels=new String[result.size()];
                    for(int i=0;i<result.size();i++) labels[i]=result.get(i).label+" — "+result.get(i).transport.toUpperCase(java.util.Locale.ROOT);
                    new AlertDialog.Builder(this).setTitle("Выберите профиль подключения")
                        .setMessage("Выберите транспорт TCP или XHTTP.")
                        .setItems(labels,(d,which)->selectProfile(result.get(which)))
                        .setNegativeButton("Отмена",null).show();
                } else {
                    ProfileOption chosen=result.get(0);
                    DebugLog.i(this,"PROFILE","Автоматический выбор профиля: "+chosen.label+"; transport="+chosen.transport);
                    selectProfile(chosen);
                }
            });
        }).start();
    }

    private void selectProfile(ProfileOption option) {
        selectedConfig=option.config;
        activeTransport=option.transport;
        prefs.edit().putString("selected_transport",activeTransport).apply();
        DebugLog.i(this,"PROFILE","Выбран профиль: "+option.label+"; transport="+option.transport);
        updateProtocolBadge();
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
            readSubscriptionMetadata(conn);
            runOnUiThread(() -> {
                if(userGreeting!=null) userGreeting.setText(greetingText());
                updateSubscriptionWidgets();
            });
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
            for(String line:body.split("\\r?\n")) {
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
        JSONObject direct=new JSONObject().put("tag","direct").put("protocol","freedom");
        JSONObject block=new JSONObject().put("tag","block").put("protocol","blackhole");
        JSONArray outbounds=new JSONArray().put(outbound).put(direct).put(block);
        JSONObject routing=new JSONObject().put("domainStrategy","AsIs")
            .put("rules",new JSONArray().put(new JSONObject().put("type","field").put("inboundTag",new JSONArray().put("socks-in")).put("outboundTag","proxy")));
        // HEV tun2socks feeds packets into this local SOCKS inbound; Xray itself does not own the TUN fd.
        JSONObject inbound=new JSONObject().put("tag","socks-in").put("listen","127.0.0.1").put("port",10808)
            .put("protocol","socks").put("settings",new JSONObject().put("auth","noauth").put("udp",true).put("ip","127.0.0.1"));
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
            .put("rules",new JSONArray().put(new JSONObject().put("type","field").put("inboundTag",new JSONArray().put("socks-in")).put("outboundTag","proxy")));
        // HEV tun2socks feeds packets into this local SOCKS inbound; Xray itself does not own the TUN fd.
        JSONObject inbound=new JSONObject().put("tag","socks-in").put("listen","127.0.0.1").put("port",10808)
            .put("protocol","socks").put("settings",new JSONObject().put("auth","noauth").put("udp",true).put("ip","127.0.0.1"));
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
