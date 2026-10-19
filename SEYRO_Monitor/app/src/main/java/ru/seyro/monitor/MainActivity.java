package ru.seyro.monitor;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.graphics.drawable.GradientDrawable;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(8,12,25), CARD=Color.rgb(18,25,43), LINE=Color.rgb(39,50,73);
    private static final int WHITE=Color.rgb(239,244,255), MUTED=Color.rgb(139,154,180), PURPLE=Color.rgb(167,139,250), GREEN=Color.rgb(52,211,153);
    private LinearLayout root, content, nav;
    private int activeTab=0;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(20),dp(18),dp(20),dp(14));
        header.addView(label("SEYRO  /  INFRASTRUCTURE",11,PURPLE,true));
        LinearLayout titleRow=new LinearLayout(this); titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Monitor",30,WHITE,true); titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView live=label("● DEMO",11,Color.rgb(251,191,36),true); live.setPadding(dp(10),dp(7),dp(10),dp(7)); live.setBackground(shape(Color.rgb(62,48,24),30)); titleRow.addView(live);
        header.addView(titleRow); header.addView(label("Серверы · метрики · SSH-терминал",13,MUTED,false));
        root.addView(header);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        nav=new LinearLayout(this); nav.setPadding(dp(12),dp(8),dp(12),dp(8)); nav.setBackgroundColor(Color.rgb(13,19,34));
        Button overview=navButton("◫  Обзор"), terminal=navButton(">_  Терминал");
        nav.addView(overview,new LinearLayout.LayoutParams(0,dp(48),1)); nav.addView(terminal,new LinearLayout.LayoutParams(0,dp(48),1));
        overview.setOnClickListener(v->showTab(0)); terminal.setOnClickListener(v->showTab(1));
        root.addView(nav); setContentView(root); showTab(0);
    }

    private void showTab(int tab) {
        activeTab=tab; content.removeAllViews();
        if(tab==0) buildDashboard(); else buildTerminal();
    }
    private void buildDashboard() {
        content.setPadding(dp(18),dp(6),dp(18),dp(24));
        LinearLayout status=card(); status.setPadding(dp(16),dp(16),dp(16),dp(16)); content.addView(status,margin(-1,-2,0,0,0,16));
        status.addView(label("СОСТОЯНИЕ ИНФРАСТРУКТУРЫ",10,MUTED,true));
        status.addView(label("Ожидает SSH-подключения",19,WHITE,true),margin(-1,-2,0,8,0,0));
        status.addView(label("Графики показывают демонстрационные данные до подключения серверов.",12,MUTED,false));
        LinearLayout metrics=new LinearLayout(this); metrics.setOrientation(LinearLayout.HORIZONTAL); content.addView(metrics,margin(-1,-2,0,0,0,14));
        metric(metrics,"CPU","24%","+2.1%"); metric(metrics,"RAM","61%","4.9 / 8 GB"); metric(metrics,"ДИСК","38%","76 / 200 GB");
        addSectionTitle("Нагрузка CPU","ПОСЛЕДНИЕ 60 МИНУТ");
        addChart(new float[]{22,28,24,31,27,35,30,24,21,26,32,24},PURPLE,"CPU", "Средняя загрузка");
        addSectionTitle("Память","ИСПОЛЬЗОВАНИЕ RAM");
        addChart(new float[]{48,49,51,52,55,54,57,58,60,59,61,61},GREEN,"RAM","Использовано");
        addSectionTitle("Узлы","3 СЕРВЕРА");
        server("IN","Входящий узел","Не подключён","10.0.0.1"); server("OUT","Helsinki","Не подключён","hel-seyro"); server("OUT","Frankfurt","Не подключён","fra-seyro");
        Button add=button("+ Добавить сервер"); content.addView(add,margin(-1,dp(48),0,12,0,0));
        add.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("SSH-сервер").setMessage("Форма подключения будет добавлена следующим шагом. Перед реальным подключением необходимо настроить адрес, пользователя и проверенный SSH host key.").setPositiveButton("Понятно",null).show());
    }
    private void buildTerminal() {
        content.setPadding(dp(18),dp(6),dp(18),dp(20));
        LinearLayout panel=card(); panel.setPadding(dp(14),dp(14),dp(14),dp(14)); content.addView(panel,margin(-1,-1,0,0,0,0));
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("SSH TERMINAL",13,GREEN,true); row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView disconnected=label("● OFFLINE",10,Color.rgb(251,191,36),true); row.addView(disconnected); panel.addView(row);
        TextView host=label("Выберите сервер для подключения",12,MUTED,false); panel.addView(host,margin(-1,-2,0,8,0,12));
        Spinner servers=new Spinner(this); String[] choices={"Входящий узел (IN)","Helsinki (OUT)","Frankfurt (OUT)"}; servers.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,choices)); panel.addView(servers,margin(-1,dp(48),0,0,0,10));
        TextView console=new TextView(this); console.setText("SEYRO Monitor terminal\n────────────────────────\nSSH session: disconnected\n\nПодключение не настроено.\nДобавьте адрес сервера и проверьте его host key.\n\n$ _"); console.setTextColor(Color.rgb(185,214,202)); console.setTextSize(13); console.setTypeface(Typeface.MONOSPACE); console.setPadding(dp(12),dp(14),dp(12),dp(14)); console.setBackground(shape(Color.rgb(3,8,17),12)); console.setMinHeight(dp(300)); console.setGravity(Gravity.TOP|Gravity.START); panel.addView(console,margin(-1,-2,0,0,0,12));
        EditText command=new EditText(this); command.setSingleLine(true); command.setTextColor(WHITE); command.setHintTextColor(MUTED); command.setHint("Команда (после SSH-подключения)"); command.setTextSize(13); command.setTypeface(Typeface.MONOSPACE); command.setBackground(shape(Color.rgb(29,39,59),10)); command.setPadding(dp(12),0,dp(12),0); command.setEnabled(false); panel.addView(command,margin(-1,dp(48),0,0,0,10));
        Button connect=button("Настроить SSH-подключение"); panel.addView(connect,margin(-1,dp(48),0,0,0,0));
        connect.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("SSH-подключение").setMessage("Терминальная панель восстановлена. Реальная интерактивная SSH-сессия пока не подключена: сначала нужно реализовать добавление сервера и строгую проверку host key. В целях безопасности подключение без проверки ключа не используется.").setPositiveButton("ОК",null).show());
    }
    private void addSectionTitle(String title,String meta) {
        LinearLayout row=new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(title,17,WHITE,true),new LinearLayout.LayoutParams(0,-2,1)); row.addView(label(meta,9,MUTED,true));
        content.addView(row,margin(-1,-2,0,8,0,8));
    }
    private void addChart(float[] values,int color,String tag,String caption) {
        LinearLayout c=card(); c.setPadding(dp(14),dp(12),dp(14),dp(10)); content.addView(c,margin(-1,-2,0,0,0,16));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.addView(label(tag,12,color,true),new LinearLayout.LayoutParams(0,-2,1)); top.addView(label(caption,11,MUTED,false)); c.addView(top);
        Chart chart=new Chart(this,values,color); c.addView(chart,new LinearLayout.LayoutParams(-1,dp(112)));
        LinearLayout axis=new LinearLayout(this); axis.addView(label("60 min",10,MUTED,false),new LinearLayout.LayoutParams(0,-2,1)); TextView now=label("СЕЙЧАС",10,MUTED,false); now.setGravity(Gravity.RIGHT); axis.addView(now); c.addView(axis);
    }
    private void metric(LinearLayout parent,String name,String value,String sub) {
        LinearLayout c=card(); c.setPadding(dp(11),dp(12),dp(8),dp(12)); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1); cp.setMargins(dp(3),0,dp(3),0); parent.addView(c,cp);
        c.addView(label(name,10,MUTED,true)); c.addView(label(value,21,WHITE,true),margin(-1,-2,0,5,0,2)); c.addView(label(sub,9,MUTED,false));
    }
    private void server(String tag,String name,String state,String host) {
        LinearLayout c=card(); c.setPadding(dp(13),dp(13),dp(13),dp(13)); content.addView(c,margin(-1,-2,0,0,0,8));
        LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); c.addView(r);
        TextView badge=label(tag,10,PURPLE,true); badge.setGravity(Gravity.CENTER); badge.setBackground(shape(Color.rgb(44,37,74),8)); badge.setPadding(dp(10),dp(8),dp(10),dp(8)); r.addView(badge);
        LinearLayout labels=new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); r.addView(labels,new LinearLayout.LayoutParams(0,-2,1)); ((LinearLayout.LayoutParams)labels.getLayoutParams()).leftMargin=dp(12);
        labels.addView(label(name,14,WHITE,true)); labels.addView(label(host+"  ·  "+state,11,MUTED,false));
        r.addView(label("●",13,Color.rgb(251,191,36),true));
    }
    private class Chart extends View {
        private final float[] values; private final int color; private final Paint p=new Paint(3);
        Chart(Activity a,float[] v,int c){super(a);values=v;color=c;}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas); float w=getWidth(),h=getHeight(); p.setStrokeWidth(dp(1)); p.setColor(LINE); for(int i=1;i<=3;i++) canvas.drawLine(0,h*i/4,w,h*i/4,p);
            Path path=new Path(); Path fill=new Path(); float dx=w/(values.length-1);
            for(int i=0;i<values.length;i++){float x=i*dx,y=h-dp(8)-(values[i]/100f)*(h-dp(16)); if(i==0){path.moveTo(x,y);fill.moveTo(x,h);fill.lineTo(x,y);}else{path.lineTo(x,y);fill.lineTo(x,y);}}
            fill.lineTo(w,h);fill.close(); p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(30,Color.red(color),Color.green(color),Color.blue(color))); canvas.drawPath(fill,p);
            p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));canvas.drawPath(path,p);
            float x=w-dp(1),y=h-dp(8)-(values[values.length-1]/100f)*(h-dp(16));p.setStyle(Paint.Style.FILL);canvas.drawCircle(x,y,dp(4),p);
        }
    }
    private Button navButton(String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(WHITE);b.setBackground(shape(Color.rgb(27,37,57),12));return b;}
    private Button button(String text){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(WHITE);b.setTextSize(13);b.setBackground(shape(Color.rgb(91,55,183),12));return b;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setBackground(shape(CARD,16));return l;}
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private LinearLayout.LayoutParams margin(int w,int h,int left,int top,int right,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.setMargins(dp(left),dp(top),dp(right),dp(bottom));return p;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
}