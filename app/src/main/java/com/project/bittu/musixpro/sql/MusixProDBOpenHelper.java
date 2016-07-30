package com.project.bittu.musixpro.sql;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class MusixProDBOpenHelper extends SQLiteOpenHelper {


    public static final String DATABASE_NAME = "MusixPro.db";
    public static final String DATABASE_TABLE = "SongsMetadata";
    public static final int DATABASE_VERSION = 1;

    private static final String DATABASE_CREATE = "create table " + DATABASE_TABLE + " ("
            + MusixProContract.MUSIC_ID + " integer primary key, " + MusixProContract.SOURCE_LOCATION + " text not null, "
            + MusixProContract.ALBUM + " text, " + MusixProContract.ARTIST + " text, "
            + MusixProContract.DURATION + " integer, " + MusixProContract.TITLE + " text not null, "
            + MusixProContract.TRACK_NUMBER + " integer, " + MusixProContract.IS_MUSIC + " integer);";

    public MusixProDBOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF IT EXISTS " + DATABASE_TABLE);
        onCreate(db);
    }
}
