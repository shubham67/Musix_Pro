package com.project.bittu.musixpro.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import com.project.bittu.musixpro.R;
import com.project.bittu.musixpro.ui.FullScreenPlayerActivity;
import com.project.bittu.musixpro.ui.MusicPlayerActivity;

public class MusixProWidget extends AppWidgetProvider {

    public static String FORCE_WIDGET_UPDATE = "com.project.bittu.musixpro.FORCE_WIDGET_UPDATE";
    public static String WIDGET_ACTIONS = "com.project.bittu.musixpro.WIDGET_ACTIONS";
    public static String WIDGET_ACTIONS_PAUSE = "com.project.bittu.musixpro.WIDGET_ACTIONS_PAUSE";


    String title;
    String subtitle;

    static boolean laidOut = false;


    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {


        if(!laidOut) {
            title = context.getString(R.string.welcome);
            subtitle = context.getString(R.string.by);
            laidOut = true;
        }

        final int N = appWidgetIds.length;

        for(int i = 0; i < N; i++){

            int appWidgetId = appWidgetIds[i];



            RemoteViews view = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            view.setTextViewText(R.id.title, title);
            view.setTextViewText(R.id.artist, subtitle);

            if(title != null && title.equals(context.getString(R.string.welcome))){
                Intent intent = new Intent(context, MusicPlayerActivity.class);
                PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

                view.setContentDescription(R.id.container, title + subtitle);

                view.setOnClickPendingIntent(R.id.container, pendingIntent);
                view.setViewVisibility(R.id.play, View.GONE);
                view.setViewVisibility(R.id.pause, View.GONE);
            }else{
                Intent intentContainer = new Intent(context, FullScreenPlayerActivity.class);
                PendingIntent pi = PendingIntent.getActivity(context, 0, intentContainer, 0);
                view.setOnClickPendingIntent(R.id.container, pi);

                view.setContentDescription(R.id.container, String.format(context.getString(R.string.currently_playing), title, subtitle));

                Intent intent = new Intent();
                intent.setAction(WIDGET_ACTIONS);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context.getApplicationContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                view.setOnClickPendingIntent(R.id.play, pendingIntent);

                Intent intentPause = new Intent();
                intentPause.setAction(WIDGET_ACTIONS_PAUSE);
                PendingIntent pendingIntentPause = PendingIntent.getBroadcast(context.getApplicationContext(), 0, intentPause, PendingIntent.FLAG_CANCEL_CURRENT);
                view.setOnClickPendingIntent(R.id.pause, pendingIntentPause);

                view.setViewVisibility(R.id.play, View.VISIBLE);
                view.setViewVisibility(R.id.pause, View.VISIBLE);

            }



            appWidgetManager.updateAppWidget(appWidgetId, view);
        }
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if(FORCE_WIDGET_UPDATE.equals(intent.getAction())){
            title = intent.getStringExtra("title");
            subtitle = intent.getStringExtra("subtitle");
            ComponentName thisWidget = new ComponentName(context, MusixProWidget.class);
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);
            onUpdate(context, appWidgetManager, appWidgetIds);
        }
    }
}
