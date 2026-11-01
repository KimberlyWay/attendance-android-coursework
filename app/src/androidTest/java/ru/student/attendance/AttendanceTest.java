package ru.student.attendance;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AttendanceTest {
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    @Test public void databaseScenarios() {
        context().deleteDatabase("attendance.db");
        try(DatabaseHelper db=new DatabaseHelper(context())) {
            long a=db.addStudent("  Иванов   Иван  "); long b=db.addStudent("Петрова Анна");
            try(Cursor c=db.journal("2026-11-01")){assertEquals(2,c.getCount());c.moveToFirst();assertEquals("Иванов Иван",c.getString(1));assertEquals(0,c.getInt(2));}
            try{db.addStudent("  ");fail("Пустое имя");}catch(IllegalArgumentException expected){}
            try{db.addStudent(new String(new char[101]).replace('\0','А'));fail("Длинное имя");}catch(IllegalArgumentException expected){}
            db.setMark(a,"2026-11-01",1); db.setMark(a,"2026-11-01",2);
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM marks",null)){c.moveToFirst();assertEquals(1,c.getInt(0));}
            db.setMark(a,"2026-11-02",3);db.setMark(b,"2026-11-01",1);
            try(Cursor c=db.stats()){c.moveToFirst();assertEquals(0,c.getInt(2));assertEquals(1,c.getInt(3));assertEquals(1,c.getInt(4));}
            db.renameStudent(a,"Иванов Илья");
            assertTrue(db.exportCsv().contains("Иванов Илья"));assertTrue(db.exportCsv().contains("Опоздал"));
            db.setMark(a,"2026-11-01",0);
            db.deleteStudent(a);
            try(Cursor c=db.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM marks WHERE student_id=?",new String[]{""+a})){c.moveToFirst();assertEquals(0,c.getInt(0));}
            long formula=db.addStudent("=1+1");db.setMark(formula,"2026-11-02",1);assertTrue(db.exportCsv().contains("'=1+1"));
        }
        try(DatabaseHelper reopened=new DatabaseHelper(context());Cursor c=reopened.journal("2026-11-01")){assertEquals(2,c.getCount());}
    }
    private View find(View v,String text) {
        if(v instanceof TextView && ((TextView)v).getText().toString().equals(text))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private void screenshot(String name)throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        File dir=new File(context().getExternalFilesDir(null),"screenshots");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    @Test public void interfaceAndScreenshots()throws Exception {
        context().deleteDatabase("attendance.db");
        String day=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(new java.util.Date());
        try(DatabaseHelper db=new DatabaseHelper(context())){
            long a=db.addStudent("Иванов Артём");long b=db.addStudent("Петрова Анна");long c=db.addStudent("Смирнов Максим");
            db.setMark(a,day,1);db.setMark(b,day,3);db.setMark(c,day,2);
        }
        android.app.Instrumentation ins=InstrumentationRegistry.getInstrumentation();
        Activity activity=ins.startActivitySync(new Intent(context(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ins.waitForIdleSync();screenshot("01-journal");
        ins.runOnMainSync(()->{View button=find(activity.getWindow().getDecorView(),"Студенты");assertNotNull(button);button.performClick();});
        screenshot("02-students");
        ins.runOnMainSync(()->{View button=find(activity.getWindow().getDecorView(),"Итоги");assertNotNull(button);button.performClick();});
        screenshot("03-stats");
        ins.runOnMainSync(()->activity.finish());
    }
}
