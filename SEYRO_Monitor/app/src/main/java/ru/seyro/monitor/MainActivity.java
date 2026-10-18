package ru.seyro.monitor;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(11,16,32), CARD = Color.rgb(22,29,49);
    private static final int MUTED = Color.rgb(153,166,190), WHITE = Color.rgb(241,245,249);
    private LinearLayout cards;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20),dp(24),dp(20),dp(28));
        page.setBackgroundColor(BG);
        scroll.addView(page);

        TextView brand = text("SEYRO  /  INFRASTRUCTURE",12,MUTED,true);
        brand.setLetterSpacing(.12f); page.addView(brand);
        TextView title = text("Monitor",32,WHITE,true);
        LinearLayout.LayoutParams t = p(-2,-2); t.topMargin=dp(6); page.addView(title,t);
        TextView sub = text("Состояние серверов в одном месте",14,MUTED,false);
        LinearLayout.LayoutParams s=p(-2,-2); s.topMargin=dp(3); page.addView(sub,s);

        LinearLayout overview = card(); overview.setPadding(dp(18),dp(18),dp(18),dp(18));
        LinearLayout.LayoutParams o=p(-1,-2); o.topMargin=dp(22); page.addView(overview,o);
        overview.addView(text("ОБЩИЙ СТАТУС",11,MUTED,true));
        TextView status=text("Ожидает подключения",20,WHITE,true);
        LinearLayout.LayoutParams st=p(-1,-2); st.topMargin=dp(9); overview.addView(status,st);
        TextView help=text("Добавьте SSH-подключения, чтобы получать реальные показатели.",13,MUTED,false);
        LinearLayout.LayoutParams hl=p(-1,-2); hl.topMargin=dp(7); overview.addView(help,hl);

        LinearLayout heading=new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams h=p(-1,-2); h.topMargin=dp(26); page.addView(heading,h);
        heading.addView(text("Серверы",20,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        Button add=new Button(this); add.setText("+ Добавить"); add.setAllCaps(false);
        add.setTextColor(WHITE); add.setTextSize(13); add.setBackground(bg(Color.rgb(109,40,217),12));
        heading.addView(add);
        add.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
            .setTitle("SSH-подключение")
            .setMessage("В следующем этапе добавим форму адреса, порта, пользователя и SSH-ключа с обязательной проверкой host key. Сейчас приложение показывает макет интерфейса; реальные серверы ещё не подключены.")
            .setPositiveButton("Понятно",null).show());

        cards=new LinearLayout(this); cards.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp=p(-1,-2); cp.topMargin=dp(12); page.addView(cards,cp);
        addServer("IN","Входящий узел","Не настроен");
        addServer("OUT","Хельсинки","Не настроен");
        addServer("OUT","Франкфурт","Не настроен");

        TextView foot=text("SEYRO MONITOR  •  MVP 0.1.0",11,MUTED,false);
        foot.setGravity(Gravity.CENTER); LinearLayout.LayoutParams f=p(-1,-2); f.topMargin=dp(22); page.addView(foot,f);
        setContentView(scroll);
    }

    private void addServer(String tag,String name,String state) {
        LinearLayout c=card(); c.setPadding(dp(16),dp(16),dp(16),dp(16));
        LinearLayout.LayoutParams cp=p(-1,-2); cp.bottomMargin=dp(10); cards.addView(c,cp);
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); c.addView(row);
        TextView badge=text(tag,11,Color.rgb(196,181,253),true); badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10),dp(8),dp(10),dp(8)); badge.setBackground(bg(Color.rgb(48,39,83),10)); row.addView(badge);
        LinearLayout labels=new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.leftMargin=dp(12); row.addView(labels,lp);
        labels.addView(text(name,16,WHITE,true));
        TextView detail=text(state+"  ·  CPU —  RAM —  Диск —",12,MUTED,false);
        LinearLayout.LayoutParams dp=p(-1,-2); dp.topMargin=dp(5); labels.addView(detail,dp);
        TextView dot=text("●",15,Color.rgb(245,158,11),true); row.addView(dot);
    }

    private LinearLayout card(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setBackground(bg(CARD,18)); return l; }
    private TextView text(String value,int size,int color,boolean bold){ TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private GradientDrawable bg(int color,int radius){ GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout.LayoutParams p(int w,int h){return new LinearLayout.LayoutParams(w,h);}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
}
