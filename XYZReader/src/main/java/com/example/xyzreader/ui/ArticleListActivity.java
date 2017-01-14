package com.example.xyzreader.ui;

import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.app.LoaderManager;
import android.app.SharedElementCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;
import com.example.xyzreader.data.UpdaterService;

import java.util.List;
import java.util.Map;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.example.xyzreader.data.UpdaterService.BROADCAST_ACTION_STATE_CHANGE;
import static com.example.xyzreader.data.UpdaterService.EXTRA_REFRESHING;

/**
 * An activity representing a list of Articles. This activity has different presentations for
 * handset and tablet-size devices. On handsets, the activity presents a list of items, which when
 * touched, lead to a {@link ArticleDetailActivity} representing item details. On tablets, the
 * activity presents a grid of items as cards.
 */
public class ArticleListActivity extends ActionBarActivity implements
        LoaderManager.LoaderCallbacks<Cursor>, SwipeRefreshLayout.OnRefreshListener {

    private Toolbar mToolbar;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView;

    private Bundle mReenterState;

    private boolean isLoading;

    private Cursor mCursorObject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_list);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);

        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        getLoaderManager().initLoader(0, null, this);
        isLoading = true;

        if (savedInstanceState == null) {
            refresh();
        }

        if (SDK_INT >= LOLLIPOP) {
            setSharedElementCallback();
        }
    }

    @Override
    public void onRefresh() {
        Intent refreshIntent = new Intent(this, UpdaterService.class);
        startService(refreshIntent);
    }

    private void refresh() {
        startService(new Intent(this, UpdaterService.class));
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mRefreshingReceiver,
                new IntentFilter(BROADCAST_ACTION_STATE_CHANGE));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mRefreshingReceiver);
    }

    private boolean mIsRefreshing = false;

    private BroadcastReceiver mRefreshingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BROADCAST_ACTION_STATE_CHANGE.equals(intent.getAction())) {
                mIsRefreshing = intent.getBooleanExtra(EXTRA_REFRESHING, false);
                updateRefreshingUI();
            }
        }
    };

    private void updateRefreshingUI() {
        mSwipeRefreshLayout.setRefreshing(mIsRefreshing);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        isLoading = false;
        Adapter adapter = new Adapter(cursor);
        adapter.setHasStableIds(true);
        mRecyclerView.setAdapter(adapter);
        int columnCount = getResources().getInteger(R.integer.list_column_count);
        StaggeredGridLayoutManager sglm =
                new StaggeredGridLayoutManager(columnCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(sglm);

        scrollToNewPosition();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mRecyclerView.setAdapter(null);
    }

    private class Adapter extends RecyclerView.Adapter<ViewHolder> {
        private Cursor mCursor;

        public Adapter(Cursor cursor) {
            mCursorObject = cursor;
            mCursor = cursor;
        }

        @Override
        public long getItemId(int position) {
            mCursor.moveToPosition(position);
            return mCursor.getLong(ArticleLoader.Query._ID);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.list_item_article, parent, false);
            final ViewHolder vh = new ViewHolder(view);
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Bundle bundle = new Bundle();
                    if (SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        mCursor.moveToPosition(vh.getAdapterPosition());
                        String transitionName = mCursor.getString(ArticleLoader.Query.TITLE);
                        bundle = ActivityOptions.makeSceneTransitionAnimation(
                                ArticleListActivity.this,
                                vh.thumbnailView,
                                transitionName)
                                .toBundle();
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW,
                            ItemsContract.Items.buildItemUri(getItemId(vh.getAdapterPosition())));
                    intent.putExtra(ArticleDetailActivity.STARTING_POSITION, vh.getAdapterPosition());
                    startActivity(intent, bundle);
                }
            });
            return vh;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            mCursor.moveToPosition(position);
            holder.titleView.setText(mCursor.getString(ArticleLoader.Query.TITLE));
            holder.subtitleView.setText(
                    DateUtils.getRelativeTimeSpanString(
                            mCursor.getLong(ArticleLoader.Query.PUBLISHED_DATE),
                            System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL).toString());
            holder.thumbnailView.setImageUrl(
                    mCursor.getString(ArticleLoader.Query.THUMB_URL),
                    ImageLoaderHelper.getInstance(ArticleListActivity.this).getImageLoader());
            holder.thumbnailView.setAspectRatio(mCursor.getFloat(ArticleLoader.Query.ASPECT_RATIO));

            // to create unique transition ids
            if (SDK_INT >= LOLLIPOP) {
                String transitionName = mCursor.getString(ArticleLoader.Query.TITLE);
                holder.thumbnailView.setTransitionName(transitionName);
                holder.thumbnailView.setTag(transitionName);
            }
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public DynamicHeightNetworkImageView thumbnailView;
        public TextView titleView;
        public TextView subtitleView;

        public ViewHolder(View view) {
            super(view);
            thumbnailView = (DynamicHeightNetworkImageView) view.findViewById(R.id.thumbnail);
            titleView = (TextView) view.findViewById(R.id.article_title);
            subtitleView = (TextView) view.findViewById(R.id.article_subtitle);
        }
    }

    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);
        mReenterState = new Bundle(data.getExtras());
        if (SDK_INT >= LOLLIPOP) {
            postponeEnterTransition();
        }

        if (!isLoading) {
            scrollToNewPosition();
        }
    }

    private void scrollToNewPosition() {
        if (mReenterState != null) {
            int startingPosition = mReenterState.getInt(ArticleDetailActivity.STARTING_POSITION);
            int currentPosition = mReenterState.getInt(ArticleDetailActivity.CURRENT_POSITION);

            if (startingPosition != currentPosition) {
                mRecyclerView.scrollToPosition(currentPosition);
            }

            postponedTransitionStart();
        }
    }


    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void postponedTransitionStart() {
        mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                mRecyclerView.requestLayout();
                startPostponedEnterTransition();
                return true;
            }
        });
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setSharedElementCallback() {
        SharedElementCallback sharedElementCallback = new SharedElementCallback() {
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                if (mReenterState != null) {
                    int startingPosition = mReenterState.getInt(ArticleDetailActivity.STARTING_POSITION);
                    int currentPosition = mReenterState.getInt(ArticleDetailActivity.CURRENT_POSITION);
                    if (startingPosition != currentPosition) {
                        mCursorObject.moveToPosition(currentPosition);
                        String newTransitionName = mCursorObject.getString(ArticleLoader.Query.TITLE);
                        View view = mRecyclerView.findViewWithTag(newTransitionName);
                        if (newTransitionName != null) {
                            names.clear();
                            names.add(newTransitionName);
                            sharedElements.clear();
                            sharedElements.put(newTransitionName, view);
                        }
                    }

                    mReenterState = null;
                }
            }
        };

        setExitSharedElementCallback(sharedElementCallback);
    }
}
