package com.wxcode.studio;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

import com.tencent.qbar.QbarNative;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline WX_CODE engine. The bundled libwechatQrMod.so has one instruction patched
 * (0x78d40: ldrb w8,[x20,#0x90] -> mov w8,#1) so Encode(format=18) always takes the
 * native SVG renderer and copies the SVG document into the output byte array.
 */
final class WxEngine {
    static final class Result {
        Bitmap image;      // export size
        String payload;    // what is actually encoded
        boolean verified;  // native round-trip decode matched
        String verifyNote; // why not verified, if not
    }

    private static final int CAPACITY = 4 * 1024 * 1024;
    private static boolean loaded;
    private static String loadError;
    private static int scannerId = -1;
    private static String scannerError;

    private final Context context;
    private Bitmap badge;

    WxEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    static synchronized String ensureLoaded() {
        if (loaded) return null;
        if (loadError != null) return loadError;
        try {
            System.loadLibrary("c++_shared");
            System.loadLibrary("opencv_world");
            System.loadLibrary("XNet");
            System.loadLibrary("wechatQrMod");
            String v = QbarNative.GetVersion();
            if (v == null) throw new IllegalStateException("GetVersion 返回空");
            loaded = true;
            return null;
        } catch (Throwable t) {
            loadError = "原生库加载失败（需要 64 位 ARM 手机）：" + t;
            return loadError;
        }
    }

    static String version() {
        try { return QbarNative.GetVersion(); } catch (Throwable t) { return "?"; }
    }

    synchronized Result generate(String url, Bitmap logo, int exportSize) throws Exception {
        return generate(url, logo, exportSize, false);
    }

    synchronized Result generate(String input, Bitmap logo, int exportSize, boolean asText) throws Exception {
        String err = ensureLoaded();
        if (err != null) throw new IllegalStateException(err);
        String payload;
        if (asText) {
            payload = input.replace("\r\n", "\n");
            if (payload.trim().isEmpty()) throw new IllegalArgumentException("内容不能为空");
            int n = payload.getBytes(StandardCharsets.UTF_8).length;
            if (n > 512) throw new IllegalArgumentException("文本太长（" + n + " 字节，上限 512 字节，约 170 个汉字）");
        } else {
            payload = UrlNormalizer.normalize(input);
        }
        String svg = encodeSvg(payload);
        Result r = new Result();
        r.payload = payload;
        Bitmap preparedLogo = logo == null ? null : prepareLogo(logo);
        Bitmap check = render(svg, preparedLogo, 600);
        String decoded;
        try {
            decoded = decode(check);
        } catch (Throwable t) {
            decoded = null;
            if (scannerError == null) scannerError = "回读校验不可用：" + t.getMessage();
        }
        if (decoded != null && decoded.equals(payload)) {
            r.verified = true;
        } else if (decoded != null) {
            throw new IllegalStateException("图片未通过原生回读校验（读出内容与链接不一致），未返回结果");
        } else if (scannerError == null) {
            throw new IllegalStateException("生成的图片没能被原生解码器读回，未返回结果。可以换一个 Logo 或缩短链接再试");
        } else {
            r.verified = false;
            r.verifyNote = scannerError;
        }
        r.image = exportSize == 600 ? check : render(svg, preparedLogo, exportSize);
        if (r.image != check) check.recycle();
        return r;
    }

    private String encodeSvg(String payload) {
        byte[] out = new byte[CAPACITY];
        int[] dims = new int[8];
        int rc = QbarNative.Encode(out, dims, payload, 18, 0, "UTF-8", 3);
        int n = 0;
        while (n < out.length && out[n] != 0) n++;
        String svg = new String(out, 0, n, StandardCharsets.UTF_8);
        if (rc != 0 || n == 0) {
            throw new IllegalStateException("链接超过当前葵花码可用容量，请缩短链接后重试");
        }
        if (!svg.contains("<svg") || !svg.contains("width=\"600\"")) {
            throw new IllegalStateException("原生生成器返回了未知图像格式");
        }
        return svg;
    }

    // ---------- rendering ----------

    private static final Pattern IMAGE = Pattern.compile("<image\\b([^>]*)/?>");
    private static final Pattern CIRCLE = Pattern.compile("<circle\\b([^>]*)/?>");
    private static final Pattern LINE = Pattern.compile("<line\\b([^>]*)/?>");
    private static final Pattern ELEMENT = Pattern.compile("<(image|circle|line)\\b([^>]*)/?>");
    private static final Pattern WIDTH = Pattern.compile("stroke-width\\s*:\\s*([0-9.]+)");
    private static final Pattern RGB = Pattern.compile("stroke\\s*:\\s*rgb\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");

    private static String attr(String attrs, String name) {
        Matcher m = Pattern.compile("(?:^|\\s)" + Pattern.quote(name) + "\\s*=\\s*\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    private static float num(String attrs, String name) {
        String v = attr(attrs, name);
        if (v == null) throw new IllegalStateException("原生图像缺少属性 " + name);
        return Float.parseFloat(v.trim());
    }

    private static int color(String v, int fallback) {
        if (v == null) return fallback;
        v = v.trim().toLowerCase();
        if (v.equals("black")) return Color.BLACK;
        if (v.equals("white")) return Color.WHITE;
        try { return Color.parseColor(v); } catch (Exception e) { return fallback; }
    }

    private Bitmap badge() throws IOException {
        if (badge == null) {
            try (InputStream in = context.getAssets().open("badge.png")) {
                badge = BitmapFactory.decodeStream(in);
            }
        }
        return badge;
    }

    Bitmap render(String svg, Bitmap logo, int size) throws IOException {
        float s = size / 600f;
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        c.scale(s, s);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.BUTT);
        Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        int images = 0, shapes = 0;
        Matcher m = ELEMENT.matcher(svg);
        while (m.find()) {
            String tag = m.group(1), a = m.group(2);
            if (tag.equals("image")) {
                images++;
                RectF dst = new RectF(num(a, "x"), num(a, "y"), num(a, "x") + num(a, "width"), num(a, "y") + num(a, "height"));
                c.drawBitmap(badge(), null, dst, img);
            } else if (tag.equals("circle")) {
                shapes++;
                fill.setColor(color(attr(a, "fill"), Color.BLACK));
                c.drawCircle(num(a, "cx"), num(a, "cy"), num(a, "r"), fill);
            } else {
                shapes++;
                String style = attr(a, "style");
                float w = 1f;
                int col = Color.BLACK;
                if (style != null) {
                    Matcher wm = WIDTH.matcher(style);
                    if (wm.find()) w = Float.parseFloat(wm.group(1));
                    Matcher cm = RGB.matcher(style);
                    if (cm.find()) col = Color.rgb(Integer.parseInt(cm.group(1)), Integer.parseInt(cm.group(2)), Integer.parseInt(cm.group(3)));
                }
                String sw = attr(a, "stroke-width");
                if (sw != null) w = Float.parseFloat(sw);
                stroke.setStrokeWidth(w);
                stroke.setColor(col);
                c.drawLine(num(a, "x1"), num(a, "y1"), num(a, "x2"), num(a, "y2"), stroke);
            }
        }
        if (images != 1 || shapes < 20) {
            bmp.recycle();
            throw new IllegalStateException("原生图像模板不匹配");
        }
        if (logo != null) {
            c.save();
            Path clip = new Path();
            clip.addCircle(300, 300, 130, Path.Direction.CW);
            c.clipPath(clip);
            c.drawBitmap(logo, null, new RectF(170, 170, 430, 430), img);
            c.restore();
        }
        return bmp;
    }

    /** Crop transparent edges, fit inside 360px, center on a 512px white square (as images.py). */
    static Bitmap prepareLogo(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if ((px[y * w + x] >>> 24) != 0) {
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) throw new IllegalArgumentException("Logo 不能是完全透明的图片");
        int cw = maxX - minX + 1, ch = maxY - minY + 1;
        float k = Math.min(1f, 360f / Math.max(cw, ch));
        int tw = Math.max(1, Math.round(cw * k)), th = Math.max(1, Math.round(ch * k));
        Bitmap out = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        int left = (512 - tw) / 2, top = (512 - th) / 2;
        c.drawBitmap(src, new Rect(minX, minY, maxX + 1, maxY + 1), new Rect(left, top, left + tw, top + th), p);
        return out;
    }

    // ---------- native decode (round-trip check) ----------

    private synchronized int scanner() {
        if (scannerId >= 0) return scannerId;
        if (scannerError != null) return -1;
        try {
            File dir = new File(context.getFilesDir(), "qbar");
            File marker = new File(dir, ".ready-1");
            if (!marker.isFile()) {
                copyAssets(context.getAssets(), "qbar", dir);
                new FileOutputStream(marker).close();
            }
            QbarNative.QbarAiModelParam p = new QbarNative.QbarAiModelParam();
            p.detectModelVersion = "V1.5.0.26";
            p.superResolutionModelVersion = "V1.1.0.26";
            p.enable_seg = true;
            p.qbar_segmentation_model_path_ = new File(dir, "QBarModels/V1.1.0.26/qbar_seg.xnet").getAbsolutePath();
            p.detect_model_bin_path_ = new File(dir, "QBarModels/V1.5.0.26/qbar_detect.xnet").getAbsolutePath();
            p.detect_model_param_path_ = "";
            p.superresolution_model_bin_path_ = new File(dir, "QBarModels/V1.1.0.26/qbar_sr.xnet").getAbsolutePath();
            p.superresolution_model_param_path_ = new File(dir, "srnet.param").getAbsolutePath();
            int id = QbarNative.Init(1, true, true, true, true, 1, 0, "ANY", "UTF-8", p);
            if (id < 0) { scannerError = "回读解码器初始化失败（" + id + "）"; return -1; }
            QbarNative.SetReaders(new int[] {3, 2}, 2, id);
            scannerId = id;
            return id;
        } catch (Throwable t) {
            scannerError = "回读解码器不可用：" + t.getMessage();
            return -1;
        }
    }

    /** Returns decoded WX_CODE payload, or null if nothing / not WX_CODE. */
    String decode(Bitmap bmp) {
        String[] r = decodeAny(bmp);
        return r != null && "WX_CODE".equals(r[0]) ? r[1] : null;
    }

    /** Decodes any code the scanner supports. Returns {type, text} or null. */
    String[] decodeAny(Bitmap bmp) {
        int id = scanner();
        if (id < 0) return null;
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        byte[] gray = new byte[w * h];
        for (int i = 0; i < px.length; i++) {
            int c = px[i];
            int a = (c >>> 24) & 255;
            int v = (((c >> 16) & 255) * 299 + ((c >> 8) & 255) * 587 + (c & 255) * 114 + 500) / 1000;
            v = (v * a + 255 * (255 - a)) / 255; // transparent -> white
            gray[i] = (byte) v;
        }
        synchronized (WxEngine.class) {
            QbarNative.ScanImage(gray, w, h, id);
            byte[] type = new byte[128], data = new byte[65536], charset = new byte[128];
            int[] lens = new int[3];
            int found = QbarNative.GetOneResult(type, data, charset, lens, id);
            if (found != 1) return null;
            String t = new String(type, 0, Math.max(0, Math.min(lens[0], type.length)), StandardCharsets.UTF_8);
            String text = new String(Arrays.copyOf(data, Math.max(0, Math.min(lens[1], data.length))), StandardCharsets.UTF_8);
            return new String[] {t, text};
        }
    }

    String scannerProblem() { return scannerError; }

    private static void copyAssets(AssetManager am, String path, File target) throws IOException {
        String[] children = am.list(path);
        if (children != null && children.length > 0) {
            if (!target.isDirectory() && !target.mkdirs()) throw new IOException("mkdir " + target);
            for (String c : children) copyAssets(am, path + "/" + c, new File(target, c));
            return;
        }
        try (InputStream in = am.open(path)) {
            try (OutputStream out = new FileOutputStream(target)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
        }
    }
}
