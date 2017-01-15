package com.example.xyzreader.ui;

import android.annotation.TargetApi;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager;
import android.app.SharedElementCallback;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.example.xyzreader.R;
import com.example.xyzreader.data.ArticleLoader;
import com.example.xyzreader.data.ItemsContract;

import java.util.List;
import java.util.Map;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * An activity representing a single Article detail screen, letting you swipe between articles.
 */
public class ArticleDetailActivity extends AppCompatActivity
        implements LoaderManager.LoaderCallbacks<Cursor> {

    public static final String STARTING_POSITION = "startingPosition";
    public static final String CURRENT_POSITION = "currentPosition";

    private Cursor mCursor;
    private long mStartId;

    private ViewPager mPager;
    private MyPagerAdapter mPagerAdapter;
    private View mUpButton;

    private int mStartingPosition;
    private int mCurrentPosition;
    private boolean mIsReturning;

    private ArticleDetailFragment mCurrentFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SDK_INT >= LOLLIPOP) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

            postponeEnterTransition();
        }
        setContentView(R.layout.activity_article_detail);

        getLoaderManager().initLoader(0, null, this);

        mPagerAdapter = new MyPagerAdapter(getFragmentManager());
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mPagerAdapter);
        mPager.setPageMargin((int) TypedValue
                .applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics()));
        mPager.setPageMarginDrawable(new ColorDrawable(0x22000000));

        mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {

            @Override
            public void onPageSelected(int position) {
                if (mCursor != null) {
                    mCursor.moveToPosition(position);
                    mCurrentPosition = position;
                }
            }
        });

        mUpButton = findViewById(R.id.action_up);
        mUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ArticleDetailActivity.super.onBackPressed();
            }
        });

        mStartingPosition = getIntent().getIntExtra(STARTING_POSITION, 0);

        if (savedInstanceState == null) {
            if (getIntent() != null && getIntent().getData() != null) {
                mStartId = ItemsContract.Items.getItemId(getIntent().getData());
            }
        }

        if (SDK_INT >= LOLLIPOP) {
            setSharedElementCallback();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void setSharedElementCallback() {
        SharedElementCallback sharedElementCallback = new SharedElementCallback() {
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                ImageView sharedElement = mCurrentFragment.getPhoto();
                if (sharedElement == null) {
                    names.clear();
                    sharedElements.clear();
                } else {
                    names.clear();
                    names.add(sharedElement.getTransitionName());
                    sharedElements.clear();
                    sharedElements.put(sharedElement.getTransitionName(), sharedElement);
                }
            }
        };

        setEnterSharedElementCallback(sharedElementCallback);
    }

    @Override
    public void finishAfterTransition() {
        mIsReturning = true;
        Intent data = new Intent();
        data.putExtra(STARTING_POSITION, mStartingPosition);
        data.putExtra(CURRENT_POSITION, mCurrentPosition);
        setResult(RESULT_OK, data);
        super.finishAfterTransition();

    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return ArticleLoader.newAllArticlesInstance(this);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        mCursor = cursor;
        mPagerAdapter.notifyDataSetChanged();

        // Select the start ID
        if (mStartId > 0) {
            mCursor.moveToFirst();
            // TODO: optimize
            while (!mCursor.isAfterLast()) {
                if (mCursor.getLong(ArticleLoader.Query._ID) == mStartId) {
                    final int position = mCursor.getPosition();
                    mPager.setCurrentItem(position, false);
                    break;
                }
                mCursor.moveToNext();
            }
            mStartId = 0;
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mCursor = null;
        mPagerAdapter.notifyDataSetChanged();
    }

    private class MyPagerAdapter extends FragmentStatePagerAdapter {
        public MyPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            mCurrentFragment = (ArticleDetailFragment) object;
        }

        @Override
        public Fragment getItem(int position) {
            mCursor.moveToPosition(position);
            return ArticleDetailFragment.newInstance(mCursor.getLong(ArticleLoader.Query._ID));
        }

        @Override
        public int getCount() {
            return (mCursor != null) ? mCursor.getCount() : 0;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_POSITION, mCurrentPosition);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mCurrentPosition = savedInstanceState.getInt(CURRENT_POSITION);
    }
}
