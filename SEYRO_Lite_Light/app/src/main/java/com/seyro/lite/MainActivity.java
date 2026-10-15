package com.seyro.lite;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
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
    private final int bg=0xfff5f7fc, white=0xffffffff, ink=0xff17243a, muted=0xff718096,
        line=0xffe4eaf3, blue=0xff3267ee, paleBlue=0xffedf2ff, green=0xff138b68;
    private TextView connect,status,hint,profileLinkValue,subscriptionState;
    private SharedPreferences prefs;
    private String profileLink="";
    private boolean demoOn=false;

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
        w.setStatusBarColor(bg); w.setNavigationBarColor(white);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(bg);
        LinearLayout root=column(); padding(root,18,14,18,26); scroll.addView(root);

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView mark=text("S",22,white,true); mark.setGravity(Gravity.CENTER); mark.setBackground(shape(blue,15,0));
        header.addView(mark,new LinearLayout.LayoutParams(46,46));
        LinearLayout brand=column(); TextView name=text("SEYRO",23,ink,true); name.setLetterSpacing(.07f); brand.addView(name);
        TextView tagline=text("SECURE ACCESS",9,muted,true); tagline.setLetterSpacing(.16f); brand.addView(tagline);
        LinearLayout.LayoutParams brandParams=new LinearLayout.LayoutParams(0,-2,1); brandParams.leftMargin=12;
        header.addView(brand,brandParams);
        TextView avatar=pill("P",blue,paleBlue); header.addView(avatar,new LinearLayout.LayoutParams(40,38));
        root.addView(header);

        add(root,text("ЛИЧНЫЙ КАБИНЕТ",11,blue,true),25);
        add(root,text("Добро пожаловать",29,ink,true),5);
        add(root,text("Защищённый доступ — под вашим контролем.",13,muted,false),5);

        LinearLayout connection=card(); connection.setBackground(shape(white,22,0xffdce5f5)); add(root,connection,20);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        status=text("●  НЕ ПОДКЛЮЧЕНО",10,muted,true); top.addView(status);
        View spacer=new View(this); top.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        top.addView(pill("VLESS · REALITY",blue,paleBlue)); connection.addView(top);
        add(connection,text("Автоматический выбор",20,ink,true),16);
        add(connection,text("Оптимальный маршрут · защищённое соединение",12,muted,false),4);
        LinearLayout buttons=new LinearLayout(this); buttons.setGravity(Gravity.CENTER_VERTICAL);
        connect=text("Подключить",14,white,true); connect.setGravity(Gravity.CENTER);
        padding(connect,14,14,14,14); connect.setBackground(shape(blue,13,0));
        buttons.addView(connect,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=text("⚙  Настройки",12,blue,true); settings.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams settingsParams=new LinearLayout.LayoutParams(-2,-2); settingsParams.leftMargin=12;
        buttons.addView(settings,settingsParams); add(connection,buttons,17);
        hint=text(profileLink.isEmpty()?"Добавьте ссылку подписки в профиле, чтобы подготовить подключение.":"Ссылка подписки сохранена в профиле.",11,muted,false);
        add(connection,hint,12);
        connect.setOnClickListener(v->onConnect());
        settings.setOnClickListener(v->showSettings());

        LinearLayout stats=new LinearLayout(this); LinearLayout.LayoutParams statsP=new LinearLayout.LayoutParams(-1,-2);
        statsP.topMargin=14; root.addView(stats,statsP);
        LinearLayout traffic=card(); LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-1,1);
        half.rightMargin=6; stats.addView(traffic,half);
        traffic.addView(text("ТРАФИК",10,muted,true));
        add(traffic,text("— ГБ",25,ink,true),9);
        add(traffic,text("данные из API",10,muted,false),3);
        LinearLayout sub=card(); LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,-1,1);
        half2.leftMargin=6; stats.addView(sub,half2);
        sub.addView(text("ПОДПИСКА",10,muted,true));
        subscriptionState=text(profileLink.isEmpty()?"Не настроена":"Ссылка добавлена",17,ink,true);
        add(sub,subscriptionState,10);
        add(sub,text("синхронизация API",10,muted,false),4);

        LinearLayout quick=card(); add(root,quick,14);
        quick.addView(text("Ваш SEYRO",17,ink,true));
        add(quick,text("Настройки и управление",11,muted,false),3);
        add(quick,menuRow("Профиль и безопасность","Ссылка подписки и параметры аккаунта","↗",()->showProfile()),15);
        divider(quick);
        add(quick,menuRow("Моя подписка","Тариф, срок и статус","›",()->showSubscription()),0);
        divider(quick);
        add(quick,menuRow("Помощь","Ответы и связь с поддержкой","›",()->showHelp()),0);

        TextView footer=text("SEYRO LITE  •  LIGHT 0.3",9,0xff98a3b4,true);
        footer.setGravity(Gravity.CENTER); add(root,footer,20);
        setContentView(scroll);
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
        if(subscriptionState!=null) subscriptionState.setText(profileLink.isEmpty()?"Не настроена":"Ссылка добавлена");
        if(hint!=null) hint.setText(profileLink.isEmpty()?"Добавьте ссылку подписки в профиле, чтобы подготовить подключение.":"Ссылка подписки сохранена в профиле.");
        if(status!=null) { status.setText("●  НЕ ПОДКЛЮЧЕНО"); status.setTextColor(muted); }
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
            .setMessage("Добавьте постоянную ссылку из личного кабинета SEYRO. В этой сборке сохранение ссылки и оформление интерфейса готовы; VPN-движок и синхронизация с API ещё не подключены.")
            .setPositiveButton("Понятно",null).show();
    }
    private void onConnect() {
        if(profileLink.isEmpty()) { showProfile(); return; }
        new AlertDialog.Builder(this).setTitle("Профиль сохранён")
            .setMessage("Ссылка сохранена. Чтобы подключиться к VPN, нужно интегрировать VLESS + REALITY движок и разрешение Android VPNService. Сейчас приложение не создаёт VPN-туннель.")
            .setPositiveButton("Настройки", (d,w)->showSettings())
            .setNegativeButton("Закрыть",null).show();
    }
}
