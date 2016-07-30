package com.project.bittu.musixpro.sql;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.text.TextUtils;



public class MusixProProvider extends ContentProvider {

    public static final Uri CONTENT_URI = Uri.parse("content://com.project.bittu.musixpro/songs");

    private static final int ALLROWS = 1;
    private static final int SINGLE_ROW = 2;

    private static final UriMatcher uriMatcher;

    static{
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        uriMatcher.addURI("com.project.bittu.musixpro", "songs", ALLROWS);
        uriMatcher.addURI("com.project.bittu.musixpro", "songs/#", SINGLE_ROW);
    }

    private MusixProDBOpenHelper musixProDBOpenHelper;

    @Override
    public boolean onCreate() {
        musixProDBOpenHelper = new MusixProDBOpenHelper(getContext(), MusixProDBOpenHelper.DATABASE_NAME, null,
                MusixProDBOpenHelper.DATABASE_VERSION);
        return true;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = musixProDBOpenHelper.getWritableDatabase();

        String groupBy = null;
        String having = null;

        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        queryBuilder.setTables(MusixProDBOpenHelper.DATABASE_TABLE);

        switch(uriMatcher.match(uri)){
            case SINGLE_ROW:
                String musicID = uri.getPathSegments().get(1);
                queryBuilder.appendWhere(MusixProContract.MUSIC_ID + "=" + musicID);
                break;
            default: break;
        }

        Cursor cursor = queryBuilder.query(db, projection, selection, selectionArgs, groupBy, having, sortOrder);
        return cursor;
    }

    @Nullable
    @Override
    public String getType(Uri uri) {

        switch(uriMatcher.match(uri)){
            case ALLROWS:
                return "vnd.android.cursor.dir/vnd.bittu.songs";
            case SINGLE_ROW:
                return "vnd.android.cursor.item/vnd.bittu.songs";
            default:
                throw new IllegalArgumentException("Unsupported URI: " + uri);
        }
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = musixProDBOpenHelper.getWritableDatabase();

        String nullColumnHack = null;

        long id = db.insert(MusixProDBOpenHelper.DATABASE_TABLE, nullColumnHack, values);

        if(id > -1){
            Uri insertedId = ContentUris.withAppendedId(CONTENT_URI, id);
            getContext().getContentResolver().notifyChange(insertedId, null);
            return insertedId;
        }else
            return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = musixProDBOpenHelper.getWritableDatabase();

        switch(uriMatcher.match(uri)){
            case SINGLE_ROW:
                String musicId = uri.getPathSegments().get(1);
                selection = MusixProContract.MUSIC_ID + "=" + musicId + (!TextUtils.isEmpty(selection) ?
                        " AND (" + selection + ")" : "");
            default: break;
        }

        if(selection == null)
            selection = "1";

        int deleteCount = db.delete(MusixProDBOpenHelper.DATABASE_TABLE, selection, selectionArgs);

        getContext().getContentResolver().notifyChange(uri, null);

        return deleteCount;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        SQLiteDatabase db = musixProDBOpenHelper.getWritableDatabase();

        switch (uriMatcher.match(uri)){
            case SINGLE_ROW:
                String musicId = uri.getPathSegments().get(1);
                selection = MusixProContract.MUSIC_ID + "=" + musicId + (!TextUtils.isEmpty(selection) ?
                " AND (" + selection + ")" : "");
            default: break;
        }

        int updateCount = db.update(MusixProDBOpenHelper.DATABASE_TABLE, values, selection, selectionArgs);

        getContext().getContentResolver().notifyChange(uri, null);

        return updateCount;
    }


}
