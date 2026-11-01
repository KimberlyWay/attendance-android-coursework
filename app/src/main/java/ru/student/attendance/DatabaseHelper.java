package ru.student.attendance;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** Локальное хранилище одной учебной группы. */
public final class DatabaseHelper extends SQLiteOpenHelper {
    public static final String[] STATUSES = {"Не отмечен", "Присутствовал", "Отсутствовал", "Опоздал"};
    public DatabaseHelper(Context context) { super(context, "attendance.db", null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE students (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL)");
        db.execSQL("CREATE TABLE marks (student_id INTEGER NOT NULL, day TEXT NOT NULL, status INTEGER NOT NULL, PRIMARY KEY(student_id,day), FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int from, int to) {
        throw new IllegalStateException("Не предусмотрено обновление схемы " + from + " -> " + to);
    }
    public long addStudent(String name) {
        ContentValues values = new ContentValues(); values.put("name", validName(name));
        return getWritableDatabase().insertOrThrow("students", null, values);
    }
    public void renameStudent(long id, String name) {
        ContentValues values = new ContentValues(); values.put("name", validName(name));
        getWritableDatabase().update("students", values, "id=?", new String[]{String.valueOf(id)});
    }
    public static String validName(String name) {
        String value = name.trim().replaceAll("\\s+", " ");
        if (value.isEmpty() || value.length() > 100) throw new IllegalArgumentException("Введите имя от 1 до 100 символов");
        return value;
    }
    public void deleteStudent(long id) { getWritableDatabase().delete("students", "id=?", new String[]{String.valueOf(id)}); }
    public void setMark(long id, String day, int status) {
        if (status < 0 || status >= STATUSES.length) throw new IllegalArgumentException("Неизвестный статус");
        if (status == 0) {
            getWritableDatabase().delete("marks", "student_id=? AND day=?", new String[]{String.valueOf(id), day});
        } else {
            ContentValues values = new ContentValues(); values.put("student_id", id); values.put("day", day); values.put("status", status);
            getWritableDatabase().insertWithOnConflict("marks", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
    public Cursor journal(String day) {
        return getReadableDatabase().rawQuery("SELECT s.id,s.name,COALESCE(m.status,0) FROM students s LEFT JOIN marks m ON m.student_id=s.id AND m.day=? ORDER BY s.name COLLATE NOCASE", new String[]{day});
    }
    public Cursor stats() {
        return getReadableDatabase().rawQuery("SELECT s.id,s.name,SUM(CASE WHEN m.status=1 THEN 1 ELSE 0 END),SUM(CASE WHEN m.status=2 THEN 1 ELSE 0 END),SUM(CASE WHEN m.status=3 THEN 1 ELSE 0 END) FROM students s LEFT JOIN marks m ON s.id=m.student_id GROUP BY s.id ORDER BY s.name COLLATE NOCASE", null);
    }
    private static String csvCell(String s) {
        // Не позволяем табличному редактору исполнять формулы из имени.
        if (s.matches("^[=+@\\-\\t\\r].*")) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    public String exportCsv() {
        StringBuilder result = new StringBuilder("\uFEFFСтудент;Дата;Статус\r\n");
        try (Cursor c = getReadableDatabase().rawQuery("SELECT s.name,m.day,m.status FROM marks m JOIN students s ON s.id=m.student_id ORDER BY m.day,s.name", null)) {
            while (c.moveToNext()) result.append(csvCell(c.getString(0))).append(';').append(csvCell(c.getString(1))).append(';').append(csvCell(STATUSES[c.getInt(2)])).append("\r\n");
        }
        return result.toString();
    }
}
