/*
 * Copyright (c) 2016 Jahir Fiquitiva
 *
 * Licensed under the CreativeCommons Attribution-ShareAlike
 * 4.0 International License. You may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://creativecommons.org/licenses/by-sa/4.0/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Special thanks to the project contributors and collaborators
 * 	https://github.com/jahirfiquitiva/IconShowcase#special-thanks
 */

package jahirfiquitiva.iconshowcase.activities;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.StyleRes;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Timer;
import java.util.TimerTask;

import jahirfiquitiva.iconshowcase.R;
import jahirfiquitiva.iconshowcase.dialogs.ISDialogs;
import jahirfiquitiva.iconshowcase.models.WallpaperItem;
import jahirfiquitiva.iconshowcase.tasks.ApplyWallpaper;
import jahirfiquitiva.iconshowcase.tasks.WallpaperToCrop;
import jahirfiquitiva.iconshowcase.utilities.Preferences;
import jahirfiquitiva.iconshowcase.utilities.color.ColorUtils;
import jahirfiquitiva.iconshowcase.utilities.color.ToolbarColorizer;
import jahirfiquitiva.iconshowcase.utilities.utils.PermissionsUtils;
import jahirfiquitiva.iconshowcase.utilities.utils.ThemeUtils;
import jahirfiquitiva.iconshowcase.utilities.utils.Utils;
import jahirfiquitiva.iconshowcase.views.DebouncedClickListener;
import jahirfiquitiva.iconshowcase.views.TouchImageView;

public class AltWallpaperViewerActivity extends AppCompatActivity {

    private boolean fabOpened = false;
    private WallpaperItem item;
    private CoordinatorLayout layout;
    private Preferences mPrefs;
    private File downloadsFolder;
    private MaterialDialog dialogApply;
    private MaterialDialog downloadDialog;
    private FloatingActionButton fab, applyFab, saveFab, infoFab;

    //TODO clean up context; I removed the variable and a lot of it isn't needed. This itself is
    // already an activity

    @SuppressWarnings("ResourceAsColor")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        ThemeUtils.onActivityCreateSetTheme(this);

        setupFullScreen();

        super.onCreate(savedInstanceState);

        mPrefs = new Preferences(this);

        Intent intent = getIntent();
        String transitionName = intent.getStringExtra("transitionName");

        item = intent.getParcelableExtra("item");

        setContentView(R.layout.alt_wallpaper_viewer_activity);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        applyFab = (FloatingActionButton) findViewById(R.id.applyFab);
        saveFab = (FloatingActionButton) findViewById(R.id.saveFab);
        infoFab = (FloatingActionButton) findViewById(R.id.infoFab);

        hideFab(applyFab);
        hideFab(saveFab);
        hideFab(infoFab);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(item.getWallName());
            getSupportActionBar().setSubtitle(item.getWallAuthor());
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_back_with_shadow);
            changeToolbarTextAppearance(toolbar);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        if (Build.VERSION.SDK_INT < 19) {
            ToolbarColorizer.colorizeToolbar(toolbar, ContextCompat.getColor(this, android.R
                    .color.white));
        }

        fab.setOnClickListener(new DebouncedClickListener() {
            @Override
            public void onDebouncedClick(View v) {
                if (fabOpened) {
                    closeMenu();
                } else {
                    openMenu();
                }
                fabOpened = !fabOpened;
            }
        });

        applyFab.setOnClickListener(new DebouncedClickListener() {
            @Override
            public void onDebouncedClick(View v) {
                showApplyWallpaperDialog(AltWallpaperViewerActivity.this, item.getWallURL());
            }
        });

        if (item.isDownloadable()) {
            saveFab.setOnClickListener(new DebouncedClickListener() {
                @Override
                public void onDebouncedClick(View v) {
                    PermissionsUtils.checkPermission(AltWallpaperViewerActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            new PermissionsUtils.PermissionRequestListener() {
                                @Override
                                public void onPermissionRequest() {
                                    PermissionsUtils.setViewerActivityAction("save");
                                    PermissionsUtils.requestStoragePermission
                                            (AltWallpaperViewerActivity.this);
                                }

                                @Override
                                public void onPermissionDenied() {
                                    ISDialogs.showPermissionNotGrantedDialog
                                            (AltWallpaperViewerActivity.this);
                                }

                                @Override
                                public void onPermissionCompletelyDenied() {
                                    ISDialogs.showPermissionNotGrantedDialog
                                            (AltWallpaperViewerActivity.this);
                                }

                                @Override
                                public void onPermissionGranted() {
                                    runWallpaperSave();
                                }
                            });
                }
            });
        } else {
            saveFab.setVisibility(View.GONE);
        }

        infoFab.setOnClickListener(new DebouncedClickListener() {
            @Override
            public void onDebouncedClick(View v) {
                ISDialogs.showWallpaperDetailsDialog(AltWallpaperViewerActivity.this,
                        item.getWallName(), item.getWallAuthor(), item.getWallDimensions(),
                        item.getWallCopyright(), new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialogInterface) {
                                reshowFab(fab);
                                setupFullScreen();
                            }
                        });
            }
        });

        TouchImageView mPhoto = (TouchImageView) findViewById(R.id.big_wallpaper);
        ViewCompat.setTransitionName(mPhoto, transitionName);

        layout = (CoordinatorLayout) findViewById(R.id.viewerLayout);

        Bitmap bmp = null;
        String filename = getIntent().getStringExtra("image");
        try {
            if (filename != null) {
                FileInputStream is = openFileInput(filename);
                bmp = BitmapFactory.decodeStream(is);
                is.close();
            } else {
                bmp = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        int colorFromCachedPic;

        if (bmp != null) {
            colorFromCachedPic = ColorUtils.getPaletteSwatch(bmp).getBodyTextColor();
        } else {
            colorFromCachedPic = ThemeUtils.darkOrLight(this, R.color.drawable_tint_dark, R.color
                    .drawable_base_tint);
        }

        final ProgressBar spinner = (ProgressBar) findViewById(R.id.progress);
        spinner.getIndeterminateDrawable()
                .setColorFilter(colorFromCachedPic, PorterDuff.Mode.SRC_IN);

        Drawable d;
        if (bmp != null) {
            d = new GlideBitmapDrawable(getResources(), bmp);
        } else {
            d = new ColorDrawable(ContextCompat.getColor(this, android.R.color.transparent));
        }

        if (mPrefs.getAnimationsEnabled()) {
            Glide.with(this)
                    .load(item.getWallURL())
                    .placeholder(d)
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .fitCenter()
                    .listener(new RequestListener<String, GlideDrawable>() {
                        @Override
                        public boolean onException(Exception e, String model,
                                                   Target<GlideDrawable> target, boolean
                                                           isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(GlideDrawable resource, String model,
                                                       Target<GlideDrawable> target, boolean
                                                               isFromMemoryCache, boolean
                                                               isFirstResource) {
                            spinner.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(mPhoto);
        } else {
            Glide.with(this)
                    .load(item.getWallURL())
                    .placeholder(d)
                    .dontAnimate()
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .fitCenter()
                    .listener(new RequestListener<String, GlideDrawable>() {
                        @Override
                        public boolean onException(Exception e, String model,
                                                   Target<GlideDrawable> target, boolean
                                                           isFirstResource) {
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(GlideDrawable resource, String model,
                                                       Target<GlideDrawable> target, boolean
                                                               isFromMemoryCache, boolean
                                                               isFirstResource) {
                            spinner.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(mPhoto);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ProgressBar spinner = (ProgressBar) findViewById(R.id.progress);
        if (spinner != null) spinner.setVisibility(View.GONE);
        reshowFab(fab);
        setupFullScreen();
    }

    @Override
    public void onDestroy() {
        if (dialogApply != null) {
            dialogApply.dismiss();
            dialogApply = null;
        }
        super.onDestroy();
    }

    public void setupFullScreen() {
        makeStatusBarIconsWhite();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            decorView.setSystemUiVisibility(decorView.getSystemUiVisibility()
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        closeViewer();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                closeViewer();
                break;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResult) {
        if (requestCode == PermissionsUtils.PERMISSION_REQUEST_CODE) {
            if (grantResult.length > 0 && grantResult[0] == PackageManager.PERMISSION_GRANTED) {
                if (PermissionsUtils.getViewerActivityAction().equals("crop")) {
                    cropWallpaper(item.getWallURL());
                } else if (PermissionsUtils.getViewerActivityAction().equals("save")) {
                    runWallpaperSave();
                }
            } else {
                ISDialogs.showPermissionNotGrantedDialog(this);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        reshowFab(fab);
        setupFullScreen();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void changeToolbarTextAppearance(Toolbar toolbar) {
        TextView title, subtitle;
        try {
            Field f = toolbar.getClass().getDeclaredField("mTitleTextView");
            f.setAccessible(true);
            title = (TextView) f.get(toolbar);
            setTextAppearance(title, R.style.ToolbarTitleWithShadow);
            try {
                Field f2 = toolbar.getClass().getDeclaredField("mSubtitleTextView");
                f2.setAccessible(true);
                subtitle = (TextView) f2.get(toolbar);
                setTextAppearance(subtitle, R.style.ToolbarSubtitleWithShadow);
            } catch (NoSuchFieldException | IllegalAccessException ex) {
                //Do nothing
            }
        } catch (NoSuchFieldException | IllegalAccessException ex) {
            //Do nothing
        }
    }

    @SuppressWarnings("deprecation")
    private void setTextAppearance(TextView text, @StyleRes int style) {
        if (Build.VERSION.SDK_INT < 23) {
            text.setTextAppearance(this, style);
        } else {
            text.setTextAppearance(style);
        }
    }

    private void closeViewer() {
        if (fab != null && fab.getVisibility() != View.VISIBLE) {
            reshowFab(fab);
            setupFullScreen();
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                supportFinishAfterTransition();
            } else {
                finish();
            }
        }
    }

    private void openMenu() {
        fab.animate().rotation(45.0f).withLayer().setDuration(300).setInterpolator(new
                OvershootInterpolator(10.0F)).start();
        showFab(applyFab);
        showFab(saveFab);
        showFab(infoFab);
    }

    private void closeMenu() {
        hideFab(infoFab);
        hideFab(saveFab);
        hideFab(applyFab);
        fab.animate().rotation(0.0f).withLayer().setDuration(300).setInterpolator(new
                OvershootInterpolator(10.0F)).start();
    }

    private void showFab(FloatingActionButton fab) {
        if (fab != null) {
            fab.show();
            fab.setVisibility(View.VISIBLE);
        }
    }

    private void hideFab(FloatingActionButton fab) {
        if (fab != null) {
            fab.hide();
            fab.setVisibility(View.GONE);
        }
    }

    private void reshowFab(FloatingActionButton fab) {
        if (fab != null) {
            fab.show(new FloatingActionButton.OnVisibilityChangedListener() {
                @Override
                public void onShown(FloatingActionButton fab) {
                    super.onShown(fab);
                    fab.animate().rotation(0.0f).withLayer().setDuration(300).setInterpolator(new
                            OvershootInterpolator(10.0F)).start();
                }
            });
            fab.setVisibility(View.VISIBLE);
        }
    }

    private void saveWallpaperAction(final String name, String url) {

        if (downloadDialog != null) {
            downloadDialog.dismiss();
        }

        if (fabOpened) {
            closeMenu();
            fabOpened = false;
        }

        hideFab(fab);

        final boolean[] enteredDownloadTask = {false};

        downloadDialog = new MaterialDialog.Builder(this)
                .content(R.string.downloading_wallpaper)
                .progress(true, 0)
                .cancelable(false)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction
                            which) {
                        if (downloadDialog != null) {
                            downloadDialog.dismiss();
                            reshowFab(fab);
                            setupFullScreen();
                        }
                    }
                })
                .show();

        Glide.with(this)
                .load(url)
                .asBitmap()
                .dontAnimate()
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(Bitmap resource, GlideAnimation<? super Bitmap>
                            glideAnimation) {
                        if (resource != null && downloadDialog.isShowing()) {
                            enteredDownloadTask[0] = true;
                            saveWallpaper(AltWallpaperViewerActivity.this, name, downloadDialog,
                                    resource);
                        }
                    }
                });

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUIThread(AltWallpaperViewerActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        if (!enteredDownloadTask[0]) {
                            String newContent = getString(R.string.downloading_wallpaper)
                                    + "\n"
                                    + getString(R.string.download_takes_longer);
                            downloadDialog.setContent(newContent);
                            downloadDialog.setActionButton(DialogAction.POSITIVE, android.R
                                    .string.cancel);
                        }
                    }
                });
            }
        }, 10000);
    }

    private void saveWallpaper(final Activity context, final String wallName,
                               final MaterialDialog downloadDialog, final Bitmap result) {
        downloadDialog.setContent(context.getString(R.string.saving_wallpaper));
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mPrefs.getDownloadsFolder() != null) {
                    downloadsFolder = new File(mPrefs.getDownloadsFolder());
                } else {
                    downloadsFolder = new File(context.getString(R.string.walls_save_location,
                            Environment.getExternalStorageDirectory().getAbsolutePath()));
                }
                //noinspection ResultOfMethodCallIgnored
                downloadsFolder.mkdirs();
                final File destFile = new File(downloadsFolder, wallName + ".png");
                String snackbarText;
                if (!destFile.exists()) {
                    try {
                        FileOutputStream fos = new FileOutputStream(destFile);
                        result.compress(Bitmap.CompressFormat.PNG, 100, fos);
                        snackbarText = context.getString(R.string.wallpaper_downloaded,
                                destFile.getAbsolutePath());
                        fos.close();
                    } catch (final Exception e) {
                        snackbarText = context.getString(R.string.error);
                    }
                } else {
                    snackbarText = context.getString(R.string.wallpaper_downloaded,
                            destFile.getAbsolutePath());
                }
                final String finalSnackbarText = snackbarText;
                context.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        downloadDialog.dismiss();
                        Snackbar longSnackbar = Utils.snackbar(AltWallpaperViewerActivity.this,
                                layout, finalSnackbarText,
                                Snackbar.LENGTH_LONG);
                        ViewGroup snackbarView = (ViewGroup) longSnackbar.getView();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            snackbarView.setPadding(snackbarView.getPaddingLeft(),
                                    snackbarView.getPaddingTop(), snackbarView.getPaddingRight(),
                                    Utils.getNavigationBarHeight(AltWallpaperViewerActivity.this));
                        }
                        longSnackbar.show();
                        longSnackbar.addCallback(new Snackbar.Callback() {
                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                super.onDismissed(snackbar, event);
                                reshowFab(fab);
                                setupFullScreen();
                            }
                        });
                    }
                });
            }
        }).start();
    }

    private void showApplyWallpaperDialog(final Activity context, final String wallUrl) {
        ISDialogs.showApplyWallpaperDialog(this,
                new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog, @NonNull
                            DialogAction dialogAction) {
                        if (dialogApply != null) {
                            dialogApply.dismiss();
                        }

                        if (fabOpened) {
                            closeMenu();
                            fabOpened = false;
                        }

                        hideFab(fab);

                        final ApplyWallpaper[] applyTask = new ApplyWallpaper[1];

                        final boolean[] enteredApplyTask = {false};

                        dialogApply = new MaterialDialog.Builder(AltWallpaperViewerActivity.this)
                                .content(R.string.downloading_wallpaper)
                                .progress(true, 0)
                                .cancelable(false)
                                .onPositive(new MaterialDialog.SingleButtonCallback() {
                                    @Override
                                    public void onClick(@NonNull MaterialDialog dialog, @NonNull
                                            DialogAction which) {
                                        if (applyTask[0] != null) {
                                            applyTask[0].cancel(true);
                                        }
                                        dialogApply.dismiss();
                                        reshowFab(fab);
                                        setupFullScreen();
                                    }
                                })
                                .show();

                        Glide.with(context)
                                .load(wallUrl)
                                .asBitmap()
                                .dontAnimate()
                                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                                .into(new SimpleTarget<Bitmap>() {
                                    @Override
                                    public void onResourceReady(
                                            final Bitmap resource,
                                            GlideAnimation<? super Bitmap> glideAnimation) {
                                        if (resource != null && dialogApply.isShowing()) {
                                            enteredApplyTask[0] = true;

                                            if (dialogApply != null) {
                                                dialogApply.dismiss();
                                            }

                                            dialogApply = new MaterialDialog.Builder(context)
                                                    .content(R.string.setting_wall_title)
                                                    .progress(true, 0)
                                                    .cancelable(false)
                                                    .show();

                                            applyTask[0] = new ApplyWallpaper(context, resource,
                                                    new ApplyWallpaper.ApplyCallback() {
                                                        @Override
                                                        public void afterApplied() {
                                                            runOnUIThread(context, new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    if (dialogApply != null) {
                                                                        dialogApply.dismiss();
                                                                    }
                                                                    dialogApply = new MaterialDialog
                                                                            .Builder(context)
                                                                            .content(R.string
                                                                                    .set_as_wall_done)
                                                                            .positiveText(android.R
                                                                                    .string.ok)
                                                                            .show();

                                                                    dialogApply
                                                                            .setOnDismissListener
                                                                                    (new DialogInterface.OnDismissListener() {
                                                                                        @Override
                                                                                        public void
                                                                                        onDismiss
                                                                                                (DialogInterface
                                                                                                         dialogInterface) {
                                                                                            reshowFab(fab);
                                                                                            setupFullScreen();
                                                                                        }
                                                                                    });
                                                                }
                                                            });
                                                        }
                                                    });
                                            applyTask[0].execute();
                                        }
                                    }
                                });

                        Timer timer = new Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                runOnUIThread(AltWallpaperViewerActivity.this, new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!enteredApplyTask[0]) {
                                            String newContent = context.getString(R.string
                                                    .downloading_wallpaper)
                                                    + "\n"
                                                    + context.getString(R.string
                                                    .download_takes_longer);
                                            dialogApply.setContent(newContent);
                                            dialogApply.setActionButton(DialogAction.POSITIVE,
                                                    android.R.string.cancel);
                                        }
                                    }
                                });
                            }
                        }, 10000);
                    }
                }, new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog materialDialog, @NonNull
                            DialogAction dialogAction) {
                        PermissionsUtils.checkPermission(AltWallpaperViewerActivity.this,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                new PermissionsUtils.PermissionRequestListener() {
                                    @Override
                                    public void onPermissionRequest() {
                                        PermissionsUtils.setViewerActivityAction("crop");
                                        PermissionsUtils.requestStoragePermission
                                                (AltWallpaperViewerActivity.this);
                                    }

                                    @Override
                                    public void onPermissionDenied() {
                                        ISDialogs.showPermissionNotGrantedDialog
                                                (AltWallpaperViewerActivity.this);
                                    }

                                    @Override
                                    public void onPermissionCompletelyDenied() {
                                        ISDialogs.showPermissionNotGrantedDialog
                                                (AltWallpaperViewerActivity.this);
                                    }

                                    @Override
                                    public void onPermissionGranted() {
                                        cropWallpaper(wallUrl);
                                    }
                                });
                    }
                },
                new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        reshowFab(fab);
                        setupFullScreen();
                    }
                });
    }

    private void showNotConnectedSnackBar() {
        Snackbar notConnectedSnackBar = Utils.snackbar(this, layout, getString(R.string
                        .no_conn_title),
                Snackbar.LENGTH_LONG);

        ViewGroup snackbarView = (ViewGroup) notConnectedSnackBar.getView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            snackbarView.setPadding(snackbarView.getPaddingLeft(),
                    snackbarView.getPaddingTop(), snackbarView.getPaddingRight(),
                    Utils.getNavigationBarHeight(this));
        }
        notConnectedSnackBar.show();
    }

    private void runWallpaperSave() {
        if (Utils.hasNetwork(this)) {
            saveWallpaperAction(item.getWallName(), item.getWallURL());
        } else {
            showNotConnectedSnackBar();
        }
    }

    private Handler handler(Context context) {
        return new Handler(context.getMainLooper());
    }

    private void runOnUIThread(Context context, Runnable r) {
        handler(this).post(r);
    }

    private void cropWallpaper(String wallUrl) {
        if (dialogApply != null) {
            dialogApply.dismiss();
        }

        final WallpaperToCrop[] cropTask = new WallpaperToCrop[1];

        final boolean[] enteredCropTask = {false};

        dialogApply = new MaterialDialog.Builder(this)
                .content(R.string.downloading_wallpaper)
                .progress(true, 0)
                .cancelable(false)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction
                            which) {
                        if (cropTask[0] != null) {
                            cropTask[0].cancel(true);
                        }
                        dialogApply.dismiss();
                        reshowFab(fab);
                        setupFullScreen();
                    }
                })
                .show();

        Glide.with(this)
                .load(wallUrl)
                .asBitmap()
                .dontAnimate()
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .into(new SimpleTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(final Bitmap resource,
                                                GlideAnimation<? super Bitmap> glideAnimation) {
                        if (resource != null && dialogApply.isShowing()) {
                            enteredCropTask[0] = true;
                            if (dialogApply != null) {
                                dialogApply.dismiss();
                            }
                            dialogApply = new MaterialDialog.Builder(AltWallpaperViewerActivity
                                    .this)
                                    .content(getString(R.string.preparing_wallpaper))
                                    .progress(true, 0)
                                    .cancelable(false)
                                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                                        @Override
                                        public void onClick(@NonNull MaterialDialog dialog,
                                                            @NonNull DialogAction which) {
                                            if (cropTask[0] != null) {
                                                cropTask[0].cancel(true);
                                            }
                                            dialogApply.dismiss();
                                            reshowFab(fab);
                                            setupFullScreen();
                                        }
                                    })
                                    .show();
                            if (fabOpened) {
                                closeMenu();
                                fabOpened = false;
                            }
                            hideFab(fab);
                            cropTask[0] = new WallpaperToCrop(AltWallpaperViewerActivity.this,
                                    dialogApply, resource,
                                    layout, item.getWallName());
                            cropTask[0].execute();
                            Timer timer = new Timer();
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    runOnUIThread(AltWallpaperViewerActivity.this, new Runnable() {
                                        @Override
                                        public void run() {
                                            String content = getString(R.string.preparing_wallpaper)
                                                    + "\n" + getString(R.string
                                                    .download_takes_longer);

                                            dialogApply.setContent(content);
                                            dialogApply.setActionButton(DialogAction.POSITIVE,
                                                    android.R.string.cancel);
                                        }
                                    });
                                }
                            }, 7000);
                        }
                    }
                });

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUIThread(AltWallpaperViewerActivity.this, new Runnable() {
                    @Override
                    public void run() {
                        if (!enteredCropTask[0]) {
                            String newContent = getString(R.string.downloading_wallpaper)
                                    + "\n"
                                    + getString(R.string.download_takes_longer);
                            dialogApply.setContent(newContent);
                            dialogApply.setActionButton(DialogAction.POSITIVE, android.R.string
                                    .cancel);
                        }
                    }
                });
            }
        }, 10000);
    }

    private void makeStatusBarIconsWhite() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = getWindow().getDecorView().getSystemUiVisibility();
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

}