package ru.student.attendance;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private DB db; private LinearLayout body; private String date;
    private static final int ACCENT=Color.rgb(89,72,169);
    public void onCreate(Bundle state){super.onCreate(state);db=new DB(this); date=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date()); showJournal();}
    private class DB extends SQLiteOpenHelper {
        DB(Context c){super(c,"attendance.db",null,1);}
        public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE students (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");d.execSQL("CREATE TABLE marks (student_id INTEGER NOT NULL, day TEXT NOT NULL, status INTEGER NOT NULL, PRIMARY KEY(student_id,day), FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE)");}
        public void onConfigure(SQLiteDatabase d){d.setForeignKeyConstraintsEnabled(true);}
        public void onUpgrade(SQLiteDatabase d,int oldVersion,int newVersion){}
    }
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(Color.rgb(37,32,49));return v;}
    private Button button(String text,Runnable click){Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->click.run());return b;}
    private void frame(String title){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(Color.rgb(249,248,252));root.setPadding(24,32,24,12);setContentView(root);
        TextView heading=label(title,24);heading.setTextColor(ACCENT);root.addView(heading);
        LinearLayout nav=new LinearLayout(this);root.addView(nav);nav.addView(button("Журнал",()->showJournal()),new LinearLayout.LayoutParams(0,-2,1));nav.addView(button("Студенты",()->showStudents()),new LinearLayout.LayoutParams(0,-2,1));nav.addView(button("Итоги",()->showStats()),new LinearLayout.LayoutParams(0,-2,1));
        ScrollView scroll=new ScrollView(this);root.addView(scroll);body=new LinearLayout(this);body.setOrientation(1);scroll.addView(body);
    }
    private void chooseDate(){Calendar c=Calendar.getInstance();try{c.setTime(new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).parse(date));}catch(Exception ignored){} new DatePickerDialog(this,(view,y,m,d)->{date=String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d);showJournal();},c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    private void showJournal(){frame("Журнал посещаемости");body.addView(button("Дата: "+date+"  •  изменить",()->chooseDate()));body.addView(label("Нажмите на статус, чтобы изменить отметку",16));
        try(Cursor c=db.getReadableDatabase().rawQuery("SELECT s.id,s.name,COALESCE(m.status,0) FROM students s LEFT JOIN marks m ON m.student_id=s.id AND m.day=? ORDER BY s.name COLLATE NOCASE",new String[]{date})){
            if(c.getCount()==0)body.addView(label("Сначала добавьте студентов во вкладке «Студенты»",16));
            while(c.moveToNext()){long id=c.getLong(0);String name=c.getString(1);int status=c.getInt(2);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView n=label(name,17);row.addView(n,new LinearLayout.LayoutParams(0,-2,1));String[] names={"Не отмечен","Присутствовал","Отсутствовал","Опоздал"};row.addView(button(names[status],()->{ContentValues values=new ContentValues();values.put("student_id",id);values.put("day",date);values.put("status",(status+1)%4);db.getWritableDatabase().insertWithOnConflict("marks",null,values,SQLiteDatabase.CONFLICT_REPLACE);showJournal();}));body.addView(row);}
        }}
    private void showStudents(){frame("Студенты группы");body.addView(button("+ Добавить студента",()->addStudent()));try(Cursor c=db.getReadableDatabase().rawQuery("SELECT id,name FROM students ORDER BY name COLLATE NOCASE",null)){if(c.getCount()==0)body.addView(label("Список пока пуст",16));while(c.moveToNext()){long id=c.getLong(0);String name=c.getString(1);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(label(name,17),new LinearLayout.LayoutParams(0,-2,1));row.addView(button("Удалить",()->new AlertDialog.Builder(this).setMessage("Удалить студента «"+name+"» и все его отметки?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{db.getWritableDatabase().delete("students","id=?",new String[]{String.valueOf(id)});showStudents();}).show()));body.addView(row);}}}
    private void addStudent(){EditText input=new EditText(this);input.setHint("Фамилия Имя");new AlertDialog.Builder(this).setTitle("Новый студент").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Добавить",(dialog,which)->{String name=input.getText().toString().trim();if(name.isEmpty()){Toast.makeText(this,"Введите имя студента",Toast.LENGTH_SHORT).show();return;}ContentValues v=new ContentValues();v.put("name",name);db.getWritableDatabase().insert("students",null,v);showStudents();}).show();}
    private void showStats(){frame("Итоги посещаемости");body.addView(label("Всего за все отмеченные даты",16));try(Cursor c=db.getReadableDatabase().rawQuery("SELECT s.name, SUM(CASE WHEN m.status=1 THEN 1 ELSE 0 END), SUM(CASE WHEN m.status=2 THEN 1 ELSE 0 END), SUM(CASE WHEN m.status=3 THEN 1 ELSE 0 END) FROM students s LEFT JOIN marks m ON s.id=m.student_id GROUP BY s.id ORDER BY s.name COLLATE NOCASE",null)){if(c.getCount()==0)body.addView(label("Добавьте студентов для просмотра итогов",16));while(c.moveToNext()){TextView t=label(c.getString(0)+"\nПрисутствовал: "+c.getInt(1)+"   Отсутствовал: "+c.getInt(2)+"   Опоздал: "+c.getInt(3),16);t.setPadding(0,18,0,18);body.addView(t);}}}
}
