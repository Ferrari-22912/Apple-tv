package com.example.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.tv.*;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.view.animation.OvershootInterpolator;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    int cols;
    TextView greet, appName, wifiPill;
    LinearLayout dock, pills;
    RecyclerView grid;
    FrameLayout root;
    View glass, glow, lastDock;
    final List<ResolveInfo> apps = new ArrayList<>();
    final Map<View, Integer> glowColors = new HashMap<>();
    BroadcastReceiver pkgReceiver;

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(r)); return g;
    }

    TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color); return t;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        cols = getResources().getInteger(R.integer.columns);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(24), dp(48), dp(8));

        RelativeLayout top = new RelativeLayout(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        greet = text("", 18, 0xFF9A9AA0);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm"); clock.setFormat24Hour("HH:mm");
        clock.setTextSize(56); clock.setTextColor(Color.WHITE);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM"); date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(18); date.setTextColor(0xFF9A9AA0);
        left.addView(greet); left.addView(clock); left.addView(date);
        top.addView(left);
        pills = new LinearLayout(this);
        View inputs = pill("Inputs", v -> showInputs());
        wifiPill = (TextView) pill("Wi-Fi", v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        View sett = pill("Settings", v -> open(new Intent(Settings.ACTION_SETTINGS)));
        pills.addView(inputs); pills.addView(wifiPill); pills.addView(sett);
        inputs.setNextFocusLeftId(inputs.getId());
        sett.setNextFocusRightId(sett.getId());
        RelativeLayout.LayoutParams pl = new RelativeLayout.LayoutParams(-2, -2);
        pl.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        top.addView(pills, pl);
        col.addView(top);

        FrameLayout nameBox = new FrameLayout(this);
        glow = new View(this);
        nameBox.addView(glow, new FrameLayout.LayoutParams(-1, -1));
        appName = text("", 36, Color.WHITE);
        appName.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        appName.setPadding(dp(24), 0, 0, dp(8));
        nameBox.addView(appName, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START));
        col.addView(nameBox, new LinearLayout.LayoutParams(-1, dp(96)));

        dock = new LinearLayout(this);
        dock.setBackground(round(0xFF1C1C1E, 24));
        dock.setPadding(dp(16), dp(12), dp(16), dp(12));
        col.addView(dock, new LinearLayout.LayoutParams(-1, -2));

        grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, cols));
        grid.setClipToPadding(false);
        grid.setAdapter(new Adapter());
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                View f = getCurrentFocus();
                if (f != null && f.getTag() instanceof String) moveGlass(f, false);
            }
        });
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, 0, 1f);
        gl.topMargin = dp(16);
        col.addView(grid, gl);
        root.addView(col, new FrameLayout.LayoutParams(-1, -1));

        glass = new View(this);
        GradientDrawable gd = round(0x30FFFFFF, 18);
        gd.setStroke(dp(2), 0x77FFFFFF);
        glass.setBackground(gd);
        glass.setVisibility(View.INVISIBLE);
        root.addView(glass, new FrameLayout.LayoutParams(0, 0));
        setContentView(root);

        root.getViewTreeObserver().addOnGlobalFocusChangeListener((o, n) -> onFocus(n));

        pkgReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { load(); }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
        load();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) {}
    }

    View pill(String label, View.OnClickListener l) {
        TextView t = text(label, 16, Color.WHITE);
        t.setPadding(dp(20), dp(10), dp(20), dp(10));
        t.setFocusable(true); t.setClickable(true);
        t.setId(View.generateViewId());
        t.setTag("pill");
        t.setBackground(round(0x33FFFFFF, 20));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = dp(12); t.setLayoutParams(p);
        return t;
    }

    void open(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); } catch (Exception ignored) {}
    }

    void showInputs() {
        TvInputManager m = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        final List<String> ids = new ArrayList<>(); List<String> names = new ArrayList<>();
        try {
            for (TvInputInfo i : m.getTvInputList()) {
                ids.add(i.getId());
                CharSequence n = i.loadLabel(this);
                names.add(n == null ? i.getId() : n.toString());
            }
        } catch (Exception ignored) {}
        if (ids.isEmpty()) { Toast.makeText(this, "No inputs found", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("Inputs")
            .setItems(names.toArray(new String[0]), (d, w) ->
                open(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(ids.get(w)))))
            .show();
    }

    @Override protected void onResume() {
        super.onResume();
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greet.setText(h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening");
        String w = "Wi-Fi";
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            String s = wm.getConnectionInfo().getSSID();
            if (s != null && !s.contains("unknown")) w = s.replace("\"", "");
        } catch (Exception ignored) {}
        wifiPill.setText(w);
    }

    void load() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(q, 0);
        List<String> dockPk = Arrays.asList(getResources().getStringArray(R.array.dock_apps));
        dock.removeAllViews(); apps.clear(); glowColors.clear(); lastDock = null;
        for (String pk : dockPk) {
            for (ResolveInfo r : all) if (r.activityInfo.packageName.equals(pk)) {
                View t = tile(r, pm, true);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
                p.setMargins(dp(6), 0, dp(6), 0);
                dock.addView(t, p); break;
            }
        }
        int n = dock.getChildCount();
        for (int i = 0; i < n; i++) {
            View t = dock.getChildAt(i);
            t.setNextFocusUpId(wifiPill.getId());
            if (i == 0) t.setNextFocusLeftId(t.getId());
            if (i == n - 1) t.setNextFocusRightId(t.getId());
        }
        if (n > 0) for (int i = 0; i < pills.getChildCount(); i++)
            pills.getChildAt(i).setNextFocusDownId(dock.getChildAt(0).getId());
        for (ResolveInfo r : all) {
            String pk = r.activityInfo.packageName;
            if (!dockPk.contains(pk) && !pk.equals(getPackageName())) apps.add(r);
        }
        Collections.sort(apps, new ResolveInfo.DisplayNameComparator(pm));
        grid.getAdapter().notifyDataSetChanged();
        if (n > 0) dock.getChildAt(0).post(() -> { if (getCurrentFocus() == null) dock.getChildAt(0).requestFocus(); });
    }

    int sample(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 8, 8); d.draw(c);
            long r = 0, g = 0, bl = 0;
            for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) {
                int p = b.getPixel(x, y);
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p);
            }
            int col = Color.rgb((int) (r / 64), (int) (g / 64), (int) (bl / 64));
            b.recycle();
            return col;
        } catch (Exception e) { return 0; }
    }

    View tile(ResolveInfo r, PackageManager pm, boolean inDock) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        t.setPadding(dp(8), dp(8), dp(8), dp(8));
        t.setFocusable(true); t.setClickable(true);
        t.setId(View.generateViewId());
        final String pk = r.activityInfo.packageName;
        t.setTag(pk);
        String label = r.loadLabel(pm).toString();
        t.setContentDescription(label);
        Drawable d = r.activityInfo.loadBanner(pm);
        if (d == null) d = r.loadIcon(pm);
        if (inDock) glowColors.put(t, sample(d));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageDrawable(d);
        t.addView(iv, new LinearLayout.LayoutParams(-1, dp(inDock ? 90 : 100)));
        TextView n = text(label, 14, Color.WHITE);
        n.setGravity(Gravity.CENTER); n.setSingleLine(true);
        n.setVisibility(inDock ? View.GONE : View.INVISIBLE);
        t.addView(n);
        t.setOnClickListener(v -> {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pk);
            if (i != null) open(i);
        });
        t.setOnFocusChangeListener((v, f) -> {
            if (!inDock) n.setVisibility(f ? View.VISIBLE : View.INVISIBLE);
            v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(150).start();
        });
        return t;
    }

    void onFocus(View n) {
        if (n == null || !(n.getTag() instanceof String)) return;
        boolean pill = "pill".equals(n.getTag());
        float s = pill ? 1f : 1.08f;
        glass.setScaleX(s); glass.setScaleY(s);
        moveGlass(n, true);
        if (n.getParent() == dock) {
            lastDock = n;
            appName.setText(n.getContentDescription());
            Integer c = glowColors.get(n);
            setGlow(c == null ? 0 : c);
            for (int i = 0; i < pills.getChildCount(); i++) pills.getChildAt(i).setNextFocusDownId(n.getId());
            updateUp();
        } else {
            appName.setText(""); setGlow(0);
        }
    }

    void setGlow(int c) {
        if (c == 0) { glow.setBackground(null); return; }
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientRadius(dp(420));
        g.setGradientCenter(0.5f, 1f);
        int rgb = c & 0xFFFFFF;
        g.setColors(new int[]{rgb | 0x70000000, rgb});
        glow.setBackground(g);
    }

    void updateUp() {
        if (lastDock == null) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View w = grid.getChildAt(i);
            if (grid.getChildAdapterPosition(w) < cols) ((ViewGroup) w).getChildAt(0).setNextFocusUpId(lastDock.getId());
        }
    }

    void moveGlass(View n, boolean anim) {
        float x = 0, y = 0;
        View v = n;
        while (v != root) {
            x += v.getLeft(); y += v.getTop();
            if (!(v.getParent() instanceof View)) break;
            v = (View) v.getParent();
        }
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) glass.getLayoutParams();
        if (p.width != n.getWidth() || p.height != n.getHeight()) {
            p.width = n.getWidth(); p.height = n.getHeight();
            glass.setLayoutParams(p);
        }
        boolean first = glass.getVisibility() != View.VISIBLE;
        glass.setVisibility(View.VISIBLE);
        glass.animate().cancel();
        if (anim && !first) {
            glass.animate().translationX(x).translationY(y)
                .setInterpolator(new OvershootInterpolator(1.1f)).setDuration(280).start();
        } else {
            glass.setTranslationX(x); glass.setTranslationY(y);
        }
    }

    class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int type) {
            FrameLayout f = new FrameLayout(MainActivity.this);
            f.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerView.ViewHolder(f) {};
        }
        public void onBindViewHolder(RecyclerView.ViewHolder h, int i) {
            FrameLayout f = (FrameLayout) h.itemView;
            f.removeAllViews();
            View t = tile(apps.get(i), getPackageManager(), false);
            if (i < cols && lastDock != null) t.setNextFocusUpId(lastDock.getId());
            f.addView(t);
        }
        public int getItemCount() { return apps.size(); }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        String pk = null;
        switch (e.getScanCode()) {
            case 242: pk = "com.netflix.ninja"; break;
            case 752: pk = "com.amazon.amazonvideo.livingroom"; break;
            case 245: pk = "com.google.android.youtube.tv"; break;
        }
        if (pk != null) {
            if (e.getRepeatCount() == 0) {
                Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pk);
                if (i != null) open(i);
            }
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override public void onBackPressed() { /* stay on Home */ }
}
