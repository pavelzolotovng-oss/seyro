package com.seyro.lite;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    int bg=0xfff7f9fc, white=0xffffffff, ink=0xff182438, muted=0xff708095, line=0xffe2e8f0, blue=0xff3068ee, green=0xff149d71;
    TextView connect,status,hint;
    GradientDrawable shape(int c,int r,int s) {
        GradientDrawable d=new GradientDrawable(); d.setColor(c); d.setCornerRadius(r);
        if(s!=0)d.setStroke(1,s); return d;
    }
    TextView t(String s,int z,int c,boolean b) {
        TextView v=new TextView(this); v.setText(s); v.setTextSize(z); v.setTextColor(c);
        if(b)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v;
    }
    LinearLayout col(){ LinearLayout l=new LinearLayout(this); l.setOrientation(1); return l; }
    void pad(View v,int a,int b,int c,int d){v.setPadding(a,b,c,d);}
    LinearLayout card(){LinearLayout l=col();pad(l,17,16,17,16);l.setBackground(shape(white,18,line));return l;}
    void add(LinearLayout p,View v,int top){LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(-1,-2);x.topMargin=top;p.addView(v,x);}
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(white);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout page=col();page.setBackgroundColor(bg);
        ScrollView scroll=new ScrollView(this);LinearLayout root=col();pad(root,20,18,20,24);scroll.addView(root);page.addView(scroll);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=t("S",21,white,true);logo.setGravity(Gravity.CENTER);logo.setBackground(shape(blue,14,0));header.addView(logo,new LinearLayout.LayoutParams(43,43));
        LinearLayout names=col();TextView brand=t("SEYRO",22,ink,true);brand.setLetterSpacing(.07f);names.addView(brand);
        TextView sub=t("SECURE ACCESS",9,muted,true);sub.setLetterSpacing(.14f);names.addView(sub);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=12;header.addView(names,np);
        TextView avatar=t("P",14,blue,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(shape(0xffe7efff,50,0xffd6e2ff));header.addView(avatar,new LinearLayout.LayoutParams(40,40));root.addView(header);
        add(root,t("ЛИЧНЫЙ КАБИНЕТ",10,blue,true),29);add(root,t("Добро пожаловать",28,ink,true),7);
        add(root,t("Всё для защищённого доступа — в одном месте.",13,muted,false),7);
        LinearLayout conn=card();add(root,conn,22);
        LinearLayout sr=new LinearLayout(this);sr.setGravity(Gravity.CENTER_VERTICAL);
        status=t("●  НЕ ПОДКЛЮЧЕНО",10,muted,true);sr.addView(status);
        View gap=new View(this);sr.addView(gap,new LinearLayout.LayoutParams(0,1,1));
        TextView demo=t("DEMO",10,blue,true);pad(demo,10,6,10,6);demo.setBackground(shape(0xffebf2ff,30,0));sr.addView(demo);conn.addView(sr);
        add(conn,t("Автоматический выбор",18,ink,true),16);
        add(conn,t("Оптимальный маршрут · VLESS + REALITY",11,muted,false),5);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);
        connect=t("Подключить",14,white,true);connect.setGravity(Gravity.CENTER);pad(connect,16,13,16,13);connect.setBackground(shape(blue,12,0));
        actions.addView(connect,new LinearLayout.LayoutParams(0,-2,1));TextView settings=t("Настройки  ›",12,blue,true);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.leftMargin=12;actions.addView(settings,sp);add(conn,actions,18);
        hint=t("Демонстрационный режим: VPN ещё не подключён.",11,muted,false);add(conn,hint,12);
        connect.setOnClickListener(v->{boolean on=connect.getText().toString().equals("Подключить");
            connect.setText(on?"Отключить":"Подключить");connect.setBackground(shape(on?green:blue,12,0));
            status.setText(on?"●  ДЕМО-РЕЖИМ":"●  НЕ ПОДКЛЮЧЕНО");status.setTextColor(on?green:muted);
            hint.setText(on?"Это только демонстрация — VPN-туннель не создан.":"Демонстрационный режим: VPN ещё не подключён.");});
        LinearLayout stats=new LinearLayout(this);LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(-1,-2);stp.topMargin=14;root.addView(stats,stp);
        LinearLayout a=card();LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);half.rightMargin=7;stats.addView(a,half);
        a.addView(t("ИСПОЛЬЗОВАНО",9,muted,true));add(a,t("— ГБ",23,ink,true),10);add(a,t("из лимита",11,muted,false),4);
        LinearLayout c=card();LinearLayout.LayoutParams half2=new LinearLayout.LayoutParams(0,-2,1);half2.leftMargin=7;stats.addView(c,half2);
        c.addView(t("ПОДПИСКА",9,muted,true));add(c,t("Пробная",20,ink,true),10);add(c,t("ожидает API",11,muted,false),4);
        LinearLayout quick=card();add(root,quick,14);quick.addView(t("Быстрый доступ",15,ink,true));
        String[][] rows={{"Профиль и безопасность","Управление аккаунтом"},{"Моя подписка","Тариф, срок и статус"},{"Помощь","Ответы и связь с поддержкой"}};
        for(int i=0;i<rows.length;i++){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);pad(row,0,13,0,13);
            LinearLayout words=col();words.addView(t(rows[i][0],13,ink,true));add(words,t(rows[i][1],11,muted,false),4);
            row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.addView(t("›",24,muted,false));add(quick,row,i==0?9:0);
            if(i<2){View div=new View(this);div.setBackgroundColor(line);quick.addView(div,new LinearLayout.LayoutParams(-1,1));}
        }
        TextView foot=t("SEYRO LITE  •  CONCEPT 0.2",9,0xff919daf,true);foot.setGravity(Gravity.CENTER);add(root,foot,22);
        setContentView(page);
    }
}
