
package com.project.bittu.musixpro.ui.tv;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;

import com.project.bittu.musixpro.MusicService;
import com.project.bittu.musixpro.R;
import com.project.bittu.musixpro.utils.LogHelper;


public class TvVerticalGridActivity extends FragmentActivity
        implements TvVerticalGridFragment.MediaFragmentListener {

    private static final String TAG = LogHelper.makeLogTag(TvVerticalGridActivity.class);
    public static final String SHARED_ELEMENT_NAME = "hero";
    private MediaBrowserCompat mMediaBrowser;
    private String mMediaId;
    private String mTitle;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tv_vertical_grid);

        mMediaId = getIntent().getStringExtra(TvBrowseActivity.SAVED_MEDIA_ID);
        mTitle = getIntent().getStringExtra(TvBrowseActivity.BROWSE_TITLE);

        getWindow().setBackgroundDrawableResource(R.drawable.bg);

        mMediaBrowser = new MediaBrowserCompat(this,
                new ComponentName(this, MusicService.class),
                mConnectionCallback, null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogHelper.d(TAG, "Activity onStart: mMediaBrowser connect");
        mMediaBrowser.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mMediaBrowser.disconnect();
    }

    protected void browse() {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=" + mMediaId);
        TvVerticalGridFragment fragment = (TvVerticalGridFragment) getSupportFragmentManager()
                .findFragmentById(R.id.vertical_grid_fragment);
        fragment.setMediaId(mMediaId);
        fragment.setTitle(mTitle);
    }

    @Override
    public MediaBrowserCompat getMediaBrowser() {
        return mMediaBrowser;
    }

    private final MediaBrowserCompat.ConnectionCallback mConnectionCallback =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    LogHelper.d(TAG, "onConnected: session token ",
                            mMediaBrowser.getSessionToken());

                    try {
                        MediaControllerCompat mediaController = new MediaControllerCompat(
                                TvVerticalGridActivity.this, mMediaBrowser.getSessionToken());
                        setSupportMediaController(mediaController);
                        browse();
                    } catch (RemoteException e) {
                        LogHelper.e(TAG, e, "could not connect media controller");
                    }
                }

                @Override
                public void onConnectionFailed() {
                    LogHelper.d(TAG, "onConnectionFailed");
                }

                @Override
                public void onConnectionSuspended() {
                    LogHelper.d(TAG, "onConnectionSuspended");
                    setSupportMediaController(null);
                }
            };

}
