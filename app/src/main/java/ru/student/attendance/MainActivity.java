package ru.student.attendance;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private DatabaseHelper db;
    private LinearLayout body;
    private String date;
    private int screen = 0;
    private static final int EXPORT = 10;
    private static final int ACCENT = Color.rgb(86, 73, 165);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new DatabaseHelper(this);
        date = state == null ? new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date()) : state.getString("date");
        screen = state == null ? 0 : state.getInt("screen");
        showScreen();
    }
    @Override public void onSaveInstanceState(Bundle state) {
        state.putString("date", date); state.putInt("screen", screen); super.onSaveInstanceState(state);
    }
    @Override public void onDestroy() { db.close(); super.onDestroy(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(Color.rgb(38, 35, 48));
        return v;
    }
    private Button button(String text, Runnable action) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); b.setMinHeight(dp(48));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void showScreen() { if (screen == 1) showStudents(); else if (screen == 2) showStats(); else showJournal(); }
    private void frame(String title, int current) {
        screen = current;
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 247, 252)); root.setPadding(dp(20), dp(16), dp(20), dp(8));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(dp(20), dp(16) + insets.getSystemWindowInsetTop(), dp(20), dp(8) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root); root.requestApplyInsets();
        TextView heading = label(title, 26); heading.setTypeface(null, Typeface.BOLD); heading.setTextColor(ACCENT);
        heading.setPadding(0, dp(12), 0, dp(16)); root.addView(heading);
        LinearLayout nav = new LinearLayout(this); root.addView(nav);
        String[] names = {"Журнал", "Студенты", "Итоги"};
        for (int i = 0; i < names.length; i++) {
            final int index = i; Button b = button(names[i], () -> { screen = index; showScreen(); });
            b.setTextColor(i == current ? ACCENT : Color.DKGRAY);
            nav.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
        ScrollView scroll = new ScrollView(this); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); scroll.addView(body);
    }
    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16),dp(12),dp(16),dp(12));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE); background.setCornerRadius(dp(16));
        card.setBackground(background);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,dp(10),0,0); body.addView(card,lp); return card;
    }
    private String displayDate() {
        return date.substring(8,10) + "." + date.substring(5,7) + "." + date.substring(0,4);
    }
    private void chooseDate() {
        Calendar c = Calendar.getInstance();
        c.set(Integer.parseInt(date.substring(0,4)),Integer.parseInt(date.substring(5,7))-1,Integer.parseInt(date.substring(8,10)));
        new DatePickerDialog(this, (v,y,m,d) -> {
            date = String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d); showJournal();
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void showJournal() {
        frame("Посещаемость группы",0);
        body.addView(button("Дата: " + displayDate(), this::chooseDate));
        body.addView(label("Выберите отметку для каждого студента",15));
        int total=0, done=0;
        try (Cursor c = db.journal(date)) {
            total=c.getCount();
            if (total==0) body.addView(label("Список пуст. Добавьте студентов во вкладке «Студенты».",17));
            while(c.moveToNext()) {
                long id=c.getLong(0); String name=c.getString(1); int status=c.getInt(2); if(status!=0)done++;
                LinearLayout row=card(); row.addView(label(name,18));
                Button mark=button(DatabaseHelper.STATUSES[status], () -> new AlertDialog.Builder(this)
                    .setTitle(name).setSingleChoiceItems(DatabaseHelper.STATUSES,status,(d,w)->{db.setMark(id,date,w);d.dismiss();showJournal();})
                    .setNegativeButton("Отмена",null).show());
                int[] colors={Color.DKGRAY,Color.rgb(30,110,69),Color.rgb(170,45,50),Color.rgb(145,93,15)};
                mark.setTextColor(colors[status]); row.addView(mark);
            }
        }
        TextView summary=label("Отмечено: "+done+" из "+total,16); summary.setPadding(0,dp(16),0,dp(12)); body.addView(summary);
    }
    private void showStudents() {
        frame("Студенты группы",1); body.addView(button("Добавить студента",()->editStudent(0,"")));
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name FROM students ORDER BY name COLLATE NOCASE",null)) {
            if(c.getCount()==0)body.addView(label("Добавьте фамилию и имя первого студента",17));
            while(c.moveToNext()) {
                long id=c.getLong(0); String name=c.getString(1); LinearLayout row=card();row.addView(label(name,18));
                LinearLayout actions=new LinearLayout(this);row.addView(actions);
                actions.addView(button("Изменить",()->editStudent(id,name)),new LinearLayout.LayoutParams(0,-2,1));
                actions.addView(button("Удалить",()->new AlertDialog.Builder(this).setTitle("Удалить студента?")
                    .setMessage(name+"\nВсе отметки студента также будут удалены. Восстановление недоступно.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{db.deleteStudent(id);showStudents();}).show()),new LinearLayout.LayoutParams(0,-2,1));
            }
        }
    }
    private void editStudent(long id,String name) {
        EditText input=new EditText(this); input.setSingleLine(true);input.setHint("Фамилия Имя");input.setText(name);
        input.setPadding(dp(24),dp(12),dp(24),dp(12));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(id==0?"Новый студент":"Изменение имени").setView(input)
            .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try { if(id==0)db.addStudent(input.getText().toString());else db.renameStudent(id,input.getText().toString());dialog.dismiss();showStudents(); }
            catch(IllegalArgumentException e){input.setError(e.getMessage());}
            catch(android.database.SQLException e){Toast.makeText(this,"Не удалось сохранить запись",Toast.LENGTH_LONG).show();}
        })); dialog.show();
    }
    private void showStats() {
        frame("Итоги посещаемости",2);body.addView(label("За все сохранённые даты. Одна отметка в день.",15));
        body.addView(button("Экспорт отметок в CSV",()->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/csv");intent.putExtra(Intent.EXTRA_TITLE,"attendance.csv");startActivityForResult(intent,EXPORT);
        }));
        try(Cursor c=db.stats()) {
            if(c.getCount()==0)body.addView(label("Итоги появятся после добавления студентов",17));
            while(c.moveToNext()) {
                LinearLayout row=card();row.addView(label(c.getString(1),18));
                row.addView(label("Присутствовал: "+c.getInt(2)+"\nОтсутствовал: "+c.getInt(3)+"\nОпоздал: "+c.getInt(4),16));
            }
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==EXPORT && result==RESULT_OK && data!=null && data.getData()!=null) {
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())) {
                if(out==null)throw new java.io.IOException("Нет потока");
                out.write(db.exportCsv().getBytes(StandardCharsets.UTF_8));Toast.makeText(this,"CSV сохранён",Toast.LENGTH_SHORT).show();
            }catch(Exception e){Toast.makeText(this,"Не удалось сохранить CSV",Toast.LENGTH_LONG).show();}
        }
    }
}
