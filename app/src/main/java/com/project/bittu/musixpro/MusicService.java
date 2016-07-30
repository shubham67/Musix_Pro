

package com.project.bittu.musixpro;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.media.MediaRouter;

import com.google.android.gms.cast.ApplicationMetadata;
import com.google.android.libraries.cast.companionlibrary.cast.VideoCastManager;
import com.google.android.libraries.cast.companionlibrary.cast.callbacks.VideoCastConsumerImpl;
import com.project.bittu.musixpro.model.MusicProvider;
import com.project.bittu.musixpro.playback.CastPlayback;
import com.project.bittu.musixpro.playback.LocalPlayback;
import com.project.bittu.musixpro.playback.Playback;
import com.project.bittu.musixpro.playback.PlaybackManager;
import com.project.bittu.musixpro.playback.QueueManager;
import com.project.bittu.musixpro.ui.NowPlayingActivity;
import com.project.bittu.musixpro.utils.CarHelper;
import com.project.bittu.musixpro.utils.LogHelper;
import com.project.bittu.musixpro.utils.WearHelper;
import com.project.bittu.musixpro.utils.MediaIDHelper;
import com.project.bittu.musixpro.widget.MusixProWidget;

import java.lang.ref.WeakReference;
import java.util.List;

import static com.project.bittu.musixpro.utils.MediaIDHelper.MEDIA_ID_ROOT;



 public class MusicService extends MediaBrowserServiceCompat implements
         PlaybackManager.PlaybackServiceCallback {

     private static final String TAG = LogHelper.makeLogTag(MusicService.class);

     // Extra on MediaSession that contains the Cast device name currently connected to
     public static final String EXTRA_CONNECTED_CAST = "com.project.bittu.musixpro.CAST_NAME";
     // The action of the incoming Intent indicating that it contains a command
     // to be executed (see {@link #onStartCommand})
     public static final String ACTION_CMD = "com.project.bittu.musixpro.ACTION_CMD";
     // The key in the extras of the incoming Intent indicating the command that
     // should be executed (see {@link #onStartCommand})
     public static final String CMD_NAME = "CMD_NAME";
     // A value of a CMD_NAME key in the extras of the incoming Intent that
     // indicates that the music playback should be paused (see {@link #onStartCommand})
     public static final String CMD_PAUSE = "CMD_PAUSE";
     // A value of a CMD_NAME key that indicates that the music playback should switch
     // to local playback from cast playback.
     public static final String CMD_STOP_CASTING = "CMD_STOP_CASTING";
     // Delay stopSelf by using a handler.
     private static final int STOP_DELAY = 30000;

     private MusicProvider mMusicProvider;
     private PlaybackManager mPlaybackManager;

     private MediaSessionCompat mSession;
     private MediaNotificationManager mMediaNotificationManager;
     private Bundle mSessionExtras;
     private final DelayedStopHandler mDelayedStopHandler = new DelayedStopHandler(this);
     private MediaRouter mMediaRouter;
     private PackageValidator mPackageValidator;

     private boolean mIsConnectedToCar;
     private BroadcastReceiver mCarConnectionReceiver;
     private BroadcastReceiver widgetActionsReceiver;
     private BroadcastReceiver widgetActionsPauseReceiver;

     /**
      * Consumer responsible for switching the Playback instances depending on whether
      * it is connected to a remote player.
      */
     private final VideoCastConsumerImpl mCastConsumer = new VideoCastConsumerImpl() {

         @Override
         public void onApplicationConnected(ApplicationMetadata appMetadata, String sessionId,
                                            boolean wasLaunched) {
             // In case we are casting, send the device name as an extra on MediaSession metadata.
             mSessionExtras.putString(EXTRA_CONNECTED_CAST,
                     VideoCastManager.getInstance().getDeviceName());
             mSession.setExtras(mSessionExtras);
             // Now we can switch to CastPlayback
             Playback playback = new CastPlayback(mMusicProvider);
             mMediaRouter.setMediaSessionCompat(mSession);
             mPlaybackManager.switchToPlayback(playback, true);
         }

         @Override
         public void onDisconnectionReason(int reason) {
             LogHelper.d(TAG, "onDisconnectionReason");
             // This is our final chance to update the underlying stream position
             // In onDisconnected(), the underlying CastPlayback#mVideoCastConsumer
             // is disconnected and hence we update our local value of stream position
             // to the latest position.
             mPlaybackManager.getPlayback().updateLastKnownStreamPosition();
         }

         @Override
         public void onDisconnected() {
             LogHelper.d(TAG, "onDisconnected");
             mSessionExtras.remove(EXTRA_CONNECTED_CAST);
             mSession.setExtras(mSessionExtras);
             Playback playback = new LocalPlayback(MusicService.this, mMusicProvider);
             mMediaRouter.setMediaSessionCompat(null);
             mPlaybackManager.switchToPlayback(playback, false);
         }
     };

     /*
      * (non-Javadoc)
      * @see android.app.Service#onCreate()
      */
     @Override
     public void onCreate() {
         super.onCreate();
         LogHelper.d(TAG, "onCreate");

         mMusicProvider = new MusicProvider(getContentResolver(), this);

         // To make the app more responsive, fetch and cache catalog information now.
         // This can help improve the response time in the method
         // {@link #onLoadChildren(String, Result<List<MediaItem>>) onLoadChildren()}.
         mMusicProvider.retrieveMediaAsync(null /* Callback */);

         mPackageValidator = new PackageValidator(this);

         QueueManager queueManager = new QueueManager(mMusicProvider, getResources(),
                 new QueueManager.MetadataUpdateListener() {
                     @Override
                     public void onMetadataChanged(MediaMetadataCompat metadata) {
                         mSession.setMetadata(metadata);
                     }

                     @Override
                     public void onMetadataRetrieveError() {
                         mPlaybackManager.updatePlaybackState(
                                 getString(R.string.error_no_metadata));
                     }

                     @Override
                     public void onCurrentQueueIndexUpdated(int queueIndex) {
                         mPlaybackManager.handlePlayRequest();
                     }

                     @Override
                     public void onQueueUpdated(String title,
                                                List<MediaSessionCompat.QueueItem> newQueue) {
                         mSession.setQueue(newQueue);
                         mSession.setQueueTitle(title);
                     }
                 });

         LocalPlayback playback = new LocalPlayback(this, mMusicProvider);
         mPlaybackManager = new PlaybackManager(this, getResources(), mMusicProvider, queueManager,
                 playback);

         // Start a new MediaSession
         mSession = new MediaSessionCompat(this, "MusicService");
         setSessionToken(mSession.getSessionToken());
         mSession.setCallback(mPlaybackManager.getMediaSessionCallback());
         mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                 MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

         Context context = getApplicationContext();
         Intent intent = new Intent(context, NowPlayingActivity.class);
         PendingIntent pi = PendingIntent.getActivity(context, 99 /*request code*/,
                 intent, PendingIntent.FLAG_UPDATE_CURRENT);
         mSession.setSessionActivity(pi);

         mSessionExtras = new Bundle();
         CarHelper.setSlotReservationFlags(mSessionExtras, true, true, true);
         WearHelper.setSlotReservationFlags(mSessionExtras, true, true);
         WearHelper.setUseBackgroundFromTheme(mSessionExtras, true);
         mSession.setExtras(mSessionExtras);

         mPlaybackManager.updatePlaybackState(null);

         try {
             mMediaNotificationManager = new MediaNotificationManager(this, context);
         } catch (RemoteException e) {
             throw new IllegalStateException("Could not create a MediaNotificationManager", e);
         }
         VideoCastManager.getInstance().addVideoCastConsumer(mCastConsumer);
         mMediaRouter = MediaRouter.getInstance(getApplicationContext());

         registerCarConnectionReceiver();
         registerWidgetActionsReceiver();
     }

     public void registerWidgetActionsReceiver(){
         IntentFilter filter = new IntentFilter(MusixProWidget.WIDGET_ACTIONS);
         widgetActionsReceiver = new BroadcastReceiver() {
             @Override
             public void onReceive(Context context, Intent intent) {
                 if(MusixProWidget.WIDGET_ACTIONS.equals(intent.getAction())){

                     mPlaybackManager.handlePlayRequest();

                 }
             }
         };
         registerReceiver(widgetActionsReceiver, filter);
         IntentFilter filterPause = new IntentFilter(MusixProWidget.WIDGET_ACTIONS_PAUSE);
         widgetActionsPauseReceiver = new BroadcastReceiver() {
             @Override
             public void onReceive(Context context, Intent intent) {
                 if(MusixProWidget.WIDGET_ACTIONS_PAUSE.equals(intent.getAction())){
                     mPlaybackManager.handlePauseRequest();


                 }
             }
         };
         registerReceiver(widgetActionsPauseReceiver, filterPause);
     }

     /**
      * (non-Javadoc)
      * @see android.app.Service#onStartCommand(Intent, int, int)
      */
     @Override
     public int onStartCommand(Intent startIntent, int flags, int startId) {
         if (startIntent != null) {
             String action = startIntent.getAction();
             String command = startIntent.getStringExtra(CMD_NAME);
             if (ACTION_CMD.equals(action)) {
                 if (CMD_PAUSE.equals(command)) {
                     mPlaybackManager.handlePauseRequest();
                 } else if (CMD_STOP_CASTING.equals(command)) {
                     VideoCastManager.getInstance().disconnect();
                 }
             } else {
                 // Try to handle the intent as a media button event wrapped by MediaButtonReceiver
                 MediaButtonReceiver.handleIntent(mSession, startIntent);
             }
         }
         // Reset the delay handler to enqueue a message to stop the service if
         // nothing is playing.
         mDelayedStopHandler.removeCallbacksAndMessages(null);
         mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
         return START_STICKY;
     }

     /**
      * (non-Javadoc)
      * @see android.app.Service#onDestroy()
      */
     @Override
     public void onDestroy() {
         Intent intent = new Intent(MusixProWidget.FORCE_WIDGET_UPDATE);
         intent.putExtra("title", getString(R.string.welcome));
         intent.putExtra("subtitle", getString(R.string.by));
         getApplicationContext().sendBroadcast(intent);
         LogHelper.d(TAG, "onDestroy");
         unregisterReceiver(widgetActionsReceiver);
         unregisterReceiver(widgetActionsPauseReceiver);
         unregisterCarConnectionReceiver();
         // Service is being killed, so make sure we release our resources
         mPlaybackManager.handleStopRequest(null);
         mMediaNotificationManager.stopNotification();
         VideoCastManager.getInstance().removeVideoCastConsumer(mCastConsumer);
         mDelayedStopHandler.removeCallbacksAndMessages(null);
         mSession.release();
     }

     @Override
     public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid,
                                  Bundle rootHints) {
         LogHelper.d(TAG, "OnGetRoot: clientPackageName=" + clientPackageName,
                 "; clientUid=" + clientUid + " ; rootHints=", rootHints);
         // To ensure you are not allowing any arbitrary app to browse your app's contents, you
         // need to check the origin:
         if (!mPackageValidator.isCallerAllowed(this, clientPackageName, clientUid)) {
             // If the request comes from an untrusted package, return null. No further calls will
             // be made to other media browsing methods.
             LogHelper.w(TAG, "OnGetRoot: IGNORING request from untrusted package "
                     + clientPackageName);
             return null;
         }
         //noinspection StatementWithEmptyBody
         if (CarHelper.isValidCarPackage(clientPackageName)) {
             // Optional: if your app needs to adapt the music library to show a different subset
             // when connected to the car, this is where you should handle it.
             // If you want to adapt other runtime behaviors, like tweak ads or change some behavior
             // that should be different on cars, you should instead use the boolean flag
             // set by the BroadcastReceiver mCarConnectionReceiver (mIsConnectedToCar).
         }
         //noinspection StatementWithEmptyBody
         if (WearHelper.isValidWearCompanionPackage(clientPackageName)) {
             // Optional: if your app needs to adapt the music library for when browsing from a
             // Wear device, you should return a different MEDIA ROOT here, and then,
             // on onLoadChildren, handle it accordingly.
         }

         return new BrowserRoot(MEDIA_ID_ROOT, null);
     }

     @Override
     public void onLoadChildren(@NonNull final String parentMediaId,
                                @NonNull final Result<List<MediaItem>> result) {
         LogHelper.d(TAG, "OnLoadChildren: parentMediaId=", parentMediaId);
         if (mMusicProvider.isInitialized()) {
             // if music library is ready, return immediately
             result.sendResult(mMusicProvider.getChildren(parentMediaId, getResources()));
         } else {
             // otherwise, only return results when the music library is retrieved
             result.detach();
             mMusicProvider.retrieveMediaAsync(new MusicProvider.Callback() {
                 @Override
                 public void onMusicCatalogReady(boolean success) {
                     result.sendResult(mMusicProvider.getChildren(parentMediaId, getResources()));
                 }
             });
         }
     }

     /**
      * Callback method called from PlaybackManager whenever the music is about to play.
      */
     @Override
     public void onPlaybackStart() {
         if (!mSession.isActive()) {
             mSession.setActive(true);
         }

         mDelayedStopHandler.removeCallbacksAndMessages(null);

         // The service needs to continue running even after the bound client (usually a
         // MediaController) disconnects, otherwise the music playback will stop.
         // Calling startService(Intent) will keep the service running until it is explicitly killed.
         startService(new Intent(getApplicationContext(), MusicService.class));
     }


     /**
      * Callback method called from PlaybackManager whenever the music stops playing.
      */
     @Override
     public void onPlaybackStop() {
         // Reset the delayed stop handler, so after STOP_DELAY it will be executed again,
         // potentially stopping the service.
         mDelayedStopHandler.removeCallbacksAndMessages(null);
         mDelayedStopHandler.sendEmptyMessageDelayed(0, STOP_DELAY);
         stopForeground(true);
     }

     @Override
     public void onNotificationRequired() {
         mMediaNotificationManager.startNotification();
     }

     @Override
     public void onPlaybackStateUpdated(PlaybackStateCompat newState) {
         mSession.setPlaybackState(newState);
     }

     private void registerCarConnectionReceiver() {
         IntentFilter filter = new IntentFilter(CarHelper.ACTION_MEDIA_STATUS);
         mCarConnectionReceiver = new BroadcastReceiver() {
             @Override
             public void onReceive(Context context, Intent intent) {
                 String connectionEvent = intent.getStringExtra(CarHelper.MEDIA_CONNECTION_STATUS);
                 mIsConnectedToCar = CarHelper.MEDIA_CONNECTED.equals(connectionEvent);
                 LogHelper.i(TAG, "Connection event to Android Auto: ", connectionEvent,
                         " isConnectedToCar=", mIsConnectedToCar);
             }
         };
         registerReceiver(mCarConnectionReceiver, filter);
     }

     private void unregisterCarConnectionReceiver() {
         unregisterReceiver(mCarConnectionReceiver);
     }

     /**
      * A simple handler that stops the service if playback is not active (playing)
      */
     private static class DelayedStopHandler extends Handler {
         private final WeakReference<MusicService> mWeakReference;

         private DelayedStopHandler(MusicService service) {
             mWeakReference = new WeakReference<>(service);
         }

         @Override
         public void handleMessage(Message msg) {
             MusicService service = mWeakReference.get();
             if (service != null && service.mPlaybackManager.getPlayback() != null) {
                 if (service.mPlaybackManager.getPlayback().isPlaying()) {
                     LogHelper.d(TAG, "Ignoring delayed stop since the media player is in use.");
                     return;
                 }
                 LogHelper.d(TAG, "Stopping service with delay handler.");
                 service.stopSelf();
             }
         }
     }
 }
