package com.katorabian.zoomysample;


import static com.katorabian.zoomy.ZoomableTouchListenerKt.DEF_MAX_SCALE_FACTOR;
import static com.katorabian.zoomy.ZoomableTouchListenerKt.DEF_MIN_SCALE_FACTOR;

import android.graphics.ImageDecoder;
import android.graphics.Movie;
import android.graphics.Rect;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.katorabian.R;
import com.katorabian.zoomy.DoubleTapListener;
import com.katorabian.zoomy.LongPressListener;
import com.katorabian.zoomy.TapListener;
import com.katorabian.zoomy.Zoomy;
import com.katorabian.zoomy.ZoomyConfig;

import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private List<Integer> mImages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mImages.addAll(
            Arrays.asList(
                R.drawable.img1, R.drawable.img2,
                R.drawable.img3, R.drawable.img4,
                R.drawable.img5, R.drawable.img6,
                R.drawable.img7, R.drawable.img8,
                R.drawable.img9, R.drawable.img10
            )
        );

        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.rv);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.addItemDecoration(new CommonItemSpaceDecoration(5));
        recyclerView.setAdapter(new Adapter(mImages));

    }

    class Adapter extends RecyclerView.Adapter<ImageViewHolder> {

        private List<Integer> images;

        Adapter(List<Integer> images) {
            this.images = images;
        }

        @NonNull
        @Override
        public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView imageView = new SquareImageView(MainActivity.this);
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            return new ImageViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull final ImageViewHolder holder, int position) {
            int imageRes = images.get(position);
            ((ImageView) holder.itemView).setImageResource(imageRes);
            holder.itemView.setTag(holder.getAdapterPosition());

            ZoomyConfig config = new ZoomyConfig();
            config.setImmersiveModeEnabled(false);
            Zoomy.setDefaultConfig(config);
            Zoomy.setLoginEnabled(true);

            Zoomy.Builder builder = new Zoomy.Builder(MainActivity.this)
                .target(holder.itemView)
                .interpolator(new AccelerateDecelerateInterpolator())
                .supportAnimatedView( checkIfGif(imageRes), 30 )
                .bgDimmingIntensity(0.5F)
                .customScaleLimiters(DEF_MIN_SCALE_FACTOR, DEF_MAX_SCALE_FACTOR)
                .tapListener(new TapListener() {
                    @Override
                    public void onTap(View v) {
                        Toast.makeText(MainActivity.this, "Tap on "
                                + v.getTag(), Toast.LENGTH_SHORT).show();
                    }
                })
                .longPressListener(new LongPressListener() {
                    @Override
                    public void onLongPress(View v) {
                        Toast.makeText(MainActivity.this, "Long press on "
                                + v.getTag(), Toast.LENGTH_SHORT).show();
                    }
                }).doubleTapListener(new DoubleTapListener() {
                    @Override
                    public void onDoubleTap(View v) {
                        Toast.makeText(MainActivity.this, "Double tap on "
                                + v.getTag(), Toast.LENGTH_SHORT).show();
                    }
                });

            builder.register();
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        private boolean checkIfGif(int imgResId) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.Source source = ImageDecoder.createSource(getResources(), imgResId);
                    Drawable drawable = ImageDecoder.decodeDrawable(source);
                    if (drawable instanceof AnimatedImageDrawable) {
                        return true;
                    }
                } else {
                    BufferedInputStream resIO = new BufferedInputStream(
                        getResources().openRawResource(imgResId)
                    );
                    Movie movie = Movie.decodeStream(resIO);
                    return movie != null;
                }
            } catch (Throwable e) {
                // not handled
            }
            return false;
        }
    }

    private class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageViewHolder(View itemView) {
            super(itemView);
        }
    }

    public class CommonItemSpaceDecoration extends RecyclerView.ItemDecoration {
        private int mSpace;

        CommonItemSpaceDecoration(int space) {
            this.mSpace = space;
        }


        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            outRect.set(mSpace, mSpace, mSpace, mSpace);
        }

    }
}
