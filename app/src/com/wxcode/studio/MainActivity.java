package com.wxcode.studio;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_LOGO = 11;
    private static final int REQ_PERM = 12;
    private static final int REQ_DECODE = 13;
    static final String REPO = "https://github.com/Taocrypt/wxcode-studio";
    private static final int GREEN = 0xFF07C160;
    private static final int INK = 0xFF1F2329;
    private static final int MUTED = 0xFF7A7F87;
    private static final int BG = 0xFFF5F6F8;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private WxEngine engine;

    private EditText urlInput;
    private EditText tokenInput, appIdInput, pathInput, secretInput;
    private Button mpButton;
    private TextView mpStatus;
    private TextView decodeType, decodeText;
    private LinearLayout decodeResult;
    private Button decodeOpen;
    private String decoded;
    private android.content.SharedPreferences prefs;
    private ImageView logoThumb;
    private TextView logoLabel;
    private Button logoClear;
    private Button generate;
    private ProgressBar progress;
    private TextView status;
    private LinearLayout resultCard;
    private ImageView preview;
    private TextView payloadText;
    private TextView verifyText;

    private Bitmap logo;
    private Bitmap result;
    private String resultPayload;
    private Runnable pendingAfterPermission;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        engine = new WxEngine(this);
        prefs = getSharedPreferences("wxcode", MODE_PRIVATE);
        getWindow().setStatusBarColor(BG);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildUi());
        handleShare(getIntent());
        worker.execute(() -> {
            String err = WxEngine.ensureLoaded();
            if (err != null) ui.post(() -> showError(err));
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShare(intent);
    }

    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null) return;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("#?小程序://[^\\s]+|https?://[^\\s]+").matcher(text);
        urlInput.setText(m.find() ? m.group() : text.trim());
    }

    // ---------- UI ----------

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private GradientDrawable round(int color, float radius, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if (strokeColor != 0) d.setStroke(dp(1), strokeColor);
        return d;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(primary ? Color.WHITE : INK);
        b.setBackground(round(primary ? GREEN : Color.WHITE, 12, primary ? 0 : 0xFFDADDE2));
        b.setStateListAnimator(null);
        b.setMinHeight(dp(48));
        b.setPadding(dp(14), 0, dp(14), 0);
        return b;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(Color.WHITE, 16, 0));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(14);
        c.setLayoutParams(lp);
        return c;
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(root);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(head);
        head.addView(text("葵花码工坊", 24, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        ImageView gh = new ImageView(this);
        gh.setImageResource(R.drawable.ic_github);
        gh.setContentDescription("GitHub 项目主页");
        gh.setPadding(dp(8), dp(8), dp(8), dp(8));
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) gh.setBackgroundResource(tv.resourceId);
        head.addView(gh, new LinearLayout.LayoutParams(dp(40), dp(40)));
        gh.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPO))); } catch (Exception e) { toast("没有可用的浏览器"); }
        });
        TextView sub = text("输入网址、选一个 Logo，在手机上离线生成带 Logo 的葵花码，也能解析葵花码图片。只有小程序转链会调用 C1N 接口。", 13.5f, MUTED, false);
        sub.setPadding(0, dp(6), 0, 0);
        sub.setLineSpacing(0, 1.2f);
        root.addView(sub);

        // input card
        LinearLayout in = card();
        root.addView(in);
        in.addView(text("链接或文字", 14, INK, true));
        urlInput = new EditText(this);
        urlInput.setHint("网址，或任意文字（非网址会按原文生成）");
        urlInput.setSingleLine(false);
        urlInput.setMaxLines(5);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        urlInput.setTextColor(INK);
        urlInput.setBackground(round(BG, 10, 0));
        urlInput.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(-1, -2);
        ulp.topMargin = dp(8);
        in.addView(urlInput, ulp);

        Button paste = button("粘贴剪贴板链接", false);
        paste.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        paste.setMinHeight(dp(38));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, dp(38));
        plp.topMargin = dp(8);
        in.addView(paste, plp);
        paste.setOnClickListener(v -> pasteClipboard());

        TextView logoTitle = text("Logo（可选）", 14, INK, true);
        logoTitle.setPadding(0, dp(16), 0, 0);
        in.addView(logoTitle);
        LinearLayout logoRow = new LinearLayout(this);
        logoRow.setGravity(Gravity.CENTER_VERTICAL);
        logoRow.setPadding(0, dp(8), 0, 0);
        in.addView(logoRow);
        logoThumb = new ImageView(this);
        logoThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logoThumb.setBackground(round(BG, 28, 0));
        logoThumb.setClipToOutline(true);
        logoRow.addView(logoThumb, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout logoCol = new LinearLayout(this);
        logoCol.setOrientation(LinearLayout.VERTICAL);
        logoCol.setPadding(dp(12), 0, 0, 0);
        logoRow.addView(logoCol, new LinearLayout.LayoutParams(0, -2, 1));
        logoLabel = text("未选择，中间留白", 13, MUTED, false);
        logoCol.addView(logoLabel);
        LinearLayout logoBtns = new LinearLayout(this);
        logoBtns.setPadding(0, dp(6), 0, 0);
        logoCol.addView(logoBtns);
        Button pick = button("选择图片", false);
        pick.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        logoBtns.addView(pick, new LinearLayout.LayoutParams(-2, dp(38)));
        logoClear = button("移除", false);
        logoClear.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(38));
        clp.leftMargin = dp(8);
        logoBtns.addView(logoClear, clp);
        logoClear.setVisibility(View.GONE);
        pick.setOnClickListener(v -> pickLogo());
        logoClear.setOnClickListener(v -> setLogo(null));

        FrameLayout genWrap = new FrameLayout(this);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, dp(52));
        glp.topMargin = dp(18);
        in.addView(genWrap, glp);
        generate = button("生成葵花码", true);
        generate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        genWrap.addView(generate, new FrameLayout.LayoutParams(-1, -1));
        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        FrameLayout.LayoutParams prlp = new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER_VERTICAL | Gravity.END);
        prlp.rightMargin = dp(16);
        progress.setElevation(dp(8));
        genWrap.addView(progress, prlp);
        generate.setOnClickListener(v -> doGenerate());

        status = text("", 13, 0xFFD54941, false);
        status.setPadding(0, dp(10), 0, 0);
        status.setVisibility(View.GONE);
        in.addView(status);

        // result card
        resultCard = card();
        resultCard.setVisibility(View.GONE);
        root.addView(resultCard);
        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        resultCard.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        TextView pl = text("实际编码内容", 12, MUTED, false);
        pl.setPadding(0, dp(12), 0, 0);
        resultCard.addView(pl);
        payloadText = text("", 13, INK, false);
        payloadText.setTextIsSelectable(true);
        payloadText.setPadding(0, dp(2), 0, 0);
        resultCard.addView(payloadText);
        verifyText = text("", 12.5f, GREEN, false);
        verifyText.setPadding(0, dp(8), 0, 0);
        resultCard.addView(verifyText);
        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(0, dp(14), 0, 0);
        resultCard.addView(actions);
        Button save = button("保存到相册", true);
        Button share = button("分享", false);
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(48), 1);
        slp.leftMargin = dp(10);
        actions.addView(share, slp);
        save.setOnClickListener(v -> withStorage(() -> saveResult(false)));
        share.setOnClickListener(v -> withStorage(() -> saveResult(true)));

        // mini program -> c1n
        LinearLayout mp = card();
        root.addView(mp);
        mp.addView(text("小程序转链（C1N）", 14, INK, true));
        TextView mpSub = text("微信扫码不认 #小程序:// 文字。用你的 C1N token 把小程序转成 https 短链，再生成葵花码。上面直接粘贴 #小程序:// 链接也会自动尝试转换。", 12.5f, MUTED, false);
        mpSub.setLineSpacing(0, 1.2f);
        mpSub.setPadding(0, dp(4), 0, 0);
        mp.addView(mpSub);
        tokenInput = field(mp, "C1N token（控制台 - 个人中心 - 短链配置）", true);
        tokenInput.setText(prefs.getString("token", ""));
        appIdInput = field(mp, "appId，例如 wxedee7313076bc641", false);
        appIdInput.setText(prefs.getString("appId", ""));
        pathInput = field(mp, "页面路径，例如 pages/index/index", false);
        pathInput.setText(prefs.getString("path", "pages/index/index"));
        secretInput = field(mp, "小程序密钥（可选，微信外打开才需要）", true);
        mpButton = button("转成短链并生成葵花码", true);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-1, dp(48));
        mlp.topMargin = dp(12);
        mp.addView(mpButton, mlp);
        mpButton.setOnClickListener(v -> doMiniProgram());
        mpStatus = text("", 12.5f, MUTED, false);
        mpStatus.setPadding(0, dp(8), 0, 0);
        mpStatus.setVisibility(View.GONE);
        mp.addView(mpStatus);

        // decode
        LinearLayout dc = card();
        root.addView(dc);
        dc.addView(text("解析葵花码", 14, INK, true));
        TextView dcSub = text("从相册选一张葵花码或二维码图片，读出里面的链接。", 12.5f, MUTED, false);
        dcSub.setPadding(0, dp(4), 0, 0);
        dc.addView(dcSub);
        Button pickDecode = button("选择图片解析", false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, dp(48));
        dlp.topMargin = dp(12);
        dc.addView(pickDecode, dlp);
        pickDecode.setOnClickListener(v -> pickImage(REQ_DECODE, "选择要解析的图片"));
        decodeResult = new LinearLayout(this);
        decodeResult.setOrientation(LinearLayout.VERTICAL);
        decodeResult.setVisibility(View.GONE);
        decodeResult.setPadding(0, dp(12), 0, 0);
        dc.addView(decodeResult);
        decodeType = text("", 12, MUTED, false);
        decodeResult.addView(decodeType);
        decodeText = text("", 14, INK, false);
        decodeText.setTextIsSelectable(true);
        decodeText.setPadding(0, dp(4), 0, 0);
        decodeResult.addView(decodeText);
        LinearLayout dActs = new LinearLayout(this);
        dActs.setPadding(0, dp(10), 0, 0);
        decodeResult.addView(dActs);
        Button dCopy = button("复制", false);
        Button dUse = button("用它生成", false);
        decodeOpen = button("打开", false);
        for (Button b : new Button[] {dCopy, dUse, decodeOpen}) {
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, dp(42), 1);
            if (b != dCopy) blp.leftMargin = dp(8);
            dActs.addView(b, blp);
        }
        dCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && decoded != null) { cm.setPrimaryClip(ClipData.newPlainText("wxcode", decoded)); toast("已复制"); }
        });
        dUse.setOnClickListener(v -> {
            if (decoded == null) return;
            urlInput.setText(decoded);
            ((ScrollView) root.getParent()).smoothScrollTo(0, 0);
        });
        decodeOpen.setOnClickListener(v -> {
            if (decoded == null) return;
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(decoded))); } catch (Exception e) { toast("没有能打开这个链接的应用"); }
        });

        TextView foot = text("提示：生成后会用本机原生解码器回读校验，正式使用前请用微信实扫确认。仅支持 64 位 ARM 手机。", 11.5f, MUTED, false);
        foot.setLineSpacing(0, 1.2f);
        foot.setPadding(dp(4), dp(16), dp(4), 0);
        root.addView(foot);
        return scroll;
    }

    private void pasteClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip() || cm.getPrimaryClip().getItemCount() == 0) {
            toast("剪贴板是空的");
            return;
        }
        CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (t == null) return;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("#?小程序://[^\\s]+|https?://[^\\s]+").matcher(t);
        urlInput.setText(m.find() ? m.group() : t.toString().trim());
        urlInput.setSelection(urlInput.getText().length());
    }

    private void pickLogo() { pickImage(REQ_LOGO, "选择 Logo"); }

    private void pickImage(int req, String title) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/png", "image/jpeg", "image/webp"});
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, title), req);
        } catch (Exception e) {
            toast("找不到可用的图片选择器");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (req == REQ_DECODE) { doDecode(uri); return; }
        if (req != REQ_LOGO) return;
        worker.execute(() -> {
            try {
                Bitmap b = loadBitmap(uri);
                ui.post(() -> setLogo(b));
            } catch (Exception e) {
                ui.post(() -> showError(e.getMessage()));
            }
        });
    }

    private Bitmap loadBitmap(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
        if (o.outWidth <= 0) throw new IllegalArgumentException("无法读取这张图片，请换 PNG、JPEG 或 WebP");
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 1024) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b;
        try (InputStream in = getContentResolver().openInputStream(uri)) { b = BitmapFactory.decodeStream(in, null, o2); }
        if (b == null) throw new IllegalArgumentException("无法读取这张图片");
        // respect EXIF rotation for JPEGs
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            android.media.ExifInterface ex = new android.media.ExifInterface(in);
            int ori = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            int deg = ori == 6 ? 90 : ori == 3 ? 180 : ori == 8 ? 270 : 0;
            if (deg != 0) {
                android.graphics.Matrix mx = new android.graphics.Matrix();
                mx.postRotate(deg);
                b = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), mx, true);
            }
        } catch (Exception ignored) { }
        WxEngine.prepareLogo(b); // validates not fully transparent
        return b;
    }

    private void setLogo(Bitmap b) {
        logo = b;
        logoThumb.setImageBitmap(b);
        logoLabel.setText(b == null ? "未选择，中间留白" : "已选择 " + b.getWidth() + "×" + b.getHeight());
        logoClear.setVisibility(b == null ? View.GONE : View.VISIBLE);
    }

    private static boolean looksLikeUrl(String s) {
        if (s.matches("(?is)^https?://\\S+$")) return true;
        return s.matches("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}(:\\d+)?([/?#]\\S*)?$");
    }

    private void doGenerate() {
        String raw = urlInput.getText().toString();
        String url = raw.trim();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        if (url.isEmpty()) { showError("先填一个链接或一段文字"); return; }
        final String tok = tokenInput.getText().toString().trim();
        final boolean convertMini = UrlNormalizer.isMiniProgram(url) && !url.matches(".*\\s.*") && !tok.isEmpty();
        final boolean asText = !convertMini && !looksLikeUrl(url);
        if (!asText && !convertMini && !url.matches("(?i)^https?://.*")) url = "https://" + url;
        final String u = url;
        final Bitmap l = logo;
        setBusy(true);
        status.setVisibility(View.GONE);
        worker.execute(() -> {
            try {
                String target = u;
                if (convertMini) {
                    prefs.edit().putString("token", tok).apply();
                    try {
                        target = C1nClient.shortUrl(tok, u);
                    } catch (Exception e) {
                        throw new IllegalStateException(e.getMessage() + "。C1N 接口可能不收 #小程序:// 链接，请改用下方 appId + 页面路径（把小程序链接发给公众号「C1N短链接服务」可查到 appId）。");
                    }
                    final String t2 = target;
                    ui.post(() -> urlInput.setText(t2));
                }
                WxEngine.Result r = engine.generate(target, l, 1200, asText);
                ui.post(() -> { setBusy(false); showResult(r, asText); });
            } catch (Throwable t) {
                String msg = t.getMessage() != null ? t.getMessage() : t.toString();
                ui.post(() -> { setBusy(false); showError(msg); });
            }
        });
    }

    private EditText field(LinearLayout parent, String hint, boolean secret) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(secret ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD) : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI));
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setTextColor(INK);
        e.setBackground(round(BG, 10, 0));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        parent.addView(e, lp);
        return e;
    }

    private void doMiniProgram() {
        final String tok = tokenInput.getText().toString().trim();
        final String appId = appIdInput.getText().toString().trim();
        String p = pathInput.getText().toString().trim();
        while (p.startsWith("/")) p = p.substring(1);
        final String path = p;
        final String secret = secretInput.getText().toString().trim();
        if (tok.isEmpty()) { mpMsg("请先填 C1N token", true); return; }
        if (!appId.matches("wx[0-9a-fA-F]{16}")) { mpMsg("appId 格式不对，应为 wx 开头的 18 位", true); return; }
        if (path.isEmpty()) { mpMsg("页面路径不能为空", true); return; }
        prefs.edit().putString("token", tok).putString("appId", appId).putString("path", path).apply();
        mpButton.setEnabled(false);
        mpMsg("正在向 C1N 申请短链…", false);
        worker.execute(() -> {
            try {
                String url = C1nClient.miniProgram(tok, appId, path, secret);
                ui.post(() -> {
                    mpButton.setEnabled(true);
                    mpMsg("已转成 " + url, false);
                    urlInput.setText(url);
                    doGenerate();
                });
            } catch (Exception e) {
                String m = e.getMessage() != null ? e.getMessage() : e.toString();
                ui.post(() -> { mpButton.setEnabled(true); mpMsg(m, true); });
            }
        });
    }

    private void mpMsg(String m, boolean err) {
        mpStatus.setText(m);
        mpStatus.setTextColor(err ? 0xFFD54941 : MUTED);
        mpStatus.setVisibility(View.VISIBLE);
    }

    private void doDecode(Uri uri) {
        decodeResult.setVisibility(View.VISIBLE);
        decodeType.setText("正在解析…");
        decodeText.setText("");
        decoded = null;
        worker.execute(() -> {
            String[] r = null;
            String err = WxEngine.ensureLoaded();
            try {
                if (err == null) {
                    Bitmap b = loadPlainBitmap(uri, 1600);
                    r = engine.decodeAny(b);
                    if (r == null && Math.max(b.getWidth(), b.getHeight()) > 800) {
                        float k = 800f / Math.max(b.getWidth(), b.getHeight());
                        Bitmap s2 = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
                        r = engine.decodeAny(s2);
                    }
                    if (r == null && engine.scannerProblem() != null) err = engine.scannerProblem();
                }
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String[] fr = r;
            final String fe = err;
            ui.post(() -> {
                if (fr == null) {
                    decodeType.setText(fe != null ? fe : "没有识别到葵花码或二维码，换一张更清晰的图试试");
                    decodeText.setText("");
                    ((View) decodeOpen.getParent()).setVisibility(View.GONE);
                    return;
                }
                decoded = fr[1];
                String kind = "WX_CODE".equals(fr[0]) ? "葵花码" : fr[0];
                decodeType.setText("识别结果（" + kind + "）");
                decodeText.setText(fr[1]);
                ((View) decodeOpen.getParent()).setVisibility(View.VISIBLE);
                decodeOpen.setEnabled(fr[1].matches("(?i)^(https?|weixin)://.+"));
            });
        });
    }

    private Bitmap loadPlainBitmap(Uri uri, int max) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
        if (o.outWidth <= 0) throw new IllegalArgumentException("无法读取这张图片");
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= max) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        o2.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b;
        try (InputStream in = getContentResolver().openInputStream(uri)) { b = BitmapFactory.decodeStream(in, null, o2); }
        if (b == null) throw new IllegalArgumentException("无法读取这张图片");
        if (Math.max(b.getWidth(), b.getHeight()) > max) {
            float k = (float) max / Math.max(b.getWidth(), b.getHeight());
            b = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
        }
        return b;
    }

    private void setBusy(boolean busy) {
        generate.setEnabled(!busy);
        generate.setText(busy ? "正在生成并校验…" : "生成葵花码");
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void showResult(WxEngine.Result r, boolean asText) {
        result = r.image;
        resultPayload = r.payload;
        preview.setImageBitmap(r.image);
        payloadText.setText(r.payload);
        if (r.verified) {
            verifyText.setTextColor(GREEN);
            verifyText.setText(asText
                ? "✓ 原生回读校验通过。按文本生成：微信扫码会直接显示这段文字，不会跳转"
                : "✓ 原生回读校验通过，码内链接与上面完全一致");
        } else {
            verifyText.setTextColor(0xFFE37318);
            verifyText.setText("未做回读校验：" + r.verifyNote);
        }
        resultCard.setVisibility(View.VISIBLE);
        resultCard.post(() -> ((ScrollView) resultCard.getParent().getParent()).smoothScrollTo(0, resultCard.getTop()));
    }

    private void showError(String msg) {
        status.setText(msg);
        status.setVisibility(View.VISIBLE);
    }

    // ---------- save / share ----------

    private void withStorage(Runnable r) {
        if (result == null) return;
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingAfterPermission = r;
            requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
            return;
        }
        r.run();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] grants) {
        if (req == REQ_PERM && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED && pendingAfterPermission != null) {
            pendingAfterPermission.run();
        } else if (req == REQ_PERM) {
            toast("没有存储权限，无法保存");
        }
        pendingAfterPermission = null;
    }

    private void saveResult(boolean share) {
        final Bitmap bmp = result;
        worker.execute(() -> {
            try {
                String name = "wxcode-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".png";
                Uri uri;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/葵花码");
                    cv.put(MediaStore.Images.Media.IS_PENDING, 1);
                    uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new IllegalStateException("相册不可写");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, out); }
                    cv.clear();
                    cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, cv, null, null);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "葵花码");
                    if (!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("无法创建相册目录");
                    File f = new File(dir, name);
                    try (OutputStream out = new FileOutputStream(f)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, out); }
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Images.Media.DATA, f.getAbsolutePath());
                    cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                    uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) uri = Uri.fromFile(f);
                }
                final Uri done = uri;
                ui.post(() -> {
                    if (share) {
                        Intent s = new Intent(Intent.ACTION_SEND);
                        s.setType("image/png");
                        s.putExtra(Intent.EXTRA_STREAM, done);
                        s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        s.setClipData(ClipData.newRawUri("wxcode", done));
                        startActivity(Intent.createChooser(s, "分享葵花码"));
                    } else {
                        toast("已保存到 相册 / Pictures/葵花码");
                    }
                });
            } catch (Exception e) {
                ui.post(() -> toast("保存失败：" + e.getMessage()));
            }
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }
}
