

package com.project.bittu.musixpro.model;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.media.MediaMetadataCompat;
import android.util.Log;


import com.project.bittu.musixpro.sql.MusixProContract;
import com.project.bittu.musixpro.sql.MusixProProvider;
import com.project.bittu.musixpro.utils.LogHelper;

import java.util.ArrayList;
import java.util.Iterator;


public class LocalSource implements MusicProviderSource, Loader.OnLoadCompleteListener<Cursor> {

    private static final String TAG = LogHelper.makeLogTag(LocalSource.class);


    ContentResolver cr;
    static Context context;
    ArrayList<MediaMetadataCompat> tracks = new ArrayList<>();
    static CursorLoader cursorLoader;


    boolean loaded = false;

    public LocalSource(ContentResolver cr, Context context) {
        this.cr = cr;
        this.context = context;
    }



    @Override
    public Iterator<MediaMetadataCompat> iterator() {



        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(
                new Runnable() {
                    @Override
                    public void run() {

                        String selection = MusixProContract.IS_MUSIC + " != 0";

                        String[] projection = {
                                MusixProContract.MUSIC_ID,
                                MusixProContract.ARTIST,
                                MusixProContract.TITLE,
                                MusixProContract.SOURCE_LOCATION,
                                MusixProContract.DURATION,
                                MusixProContract.ALBUM,
                                MusixProContract.TRACK_NUMBER,
                        };

                        Uri queryUri = MusixProProvider.CONTENT_URI;


                        cursorLoader = new CursorLoader(context, queryUri, projection, selection, null, null);
                        cursorLoader.registerListener(1, LocalSource.this);

                        for (; ; ) {
                            try {

                                cr.query(MusixProProvider.CONTENT_URI, null, null, null, null);

                                break;
                            } catch (Exception e) {

                            }
                        }


                        cursorLoader.startLoading();

                    }
                }
        );



        for(;;) {
            Log.d("AppInfo", "Infinite");

           if(loaded) {
               break;
           }
        }

        return tracks.iterator();
    }



    @Override
    public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
        while(cursor.moveToNext()) {

            //noinspection ResourceType
            tracks.add(new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, cursor.getString(0))
                    .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, cursor.getString(3))
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, cursor.getString(5))
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, cursor.getString(1))
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, cursor.getLong(4))
                    .putString(MediaMetadataCompat.METADATA_KEY_GENRE, "All")
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, null)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, cursor.getString(2))
                    .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, cursor.getLong(6))
                    .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1)
                    .build());


            if(tracks.size() == cr.query(MusixProProvider.CONTENT_URI, new String[]{
                MusixProContract.MUSIC_ID}, null, null, null).getCount() && tracks.size() != 0) {
                loaded = true;

            }


        }
    }
}
