import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * 不依赖 java.desktop 的启动器图标生成器：
 * 手写 PNG 编码 + 4x 超采样光栅化（JDK 精简镜像里没有 AWT）。
 */
public class IconGen {

    // ---------- PNG ----------
    static void png(File f, int w, int h, int[] argb) throws IOException {
        byte[] raw = new byte[(w * 4 + 1) * h];
        int o = 0;
        for (int y = 0; y < h; y++) {
            raw[o++] = 0; // filter none
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                raw[o++] = (byte) ((c >> 16) & 0xFF);
                raw[o++] = (byte) ((c >> 8) & 0xFF);
                raw[o++] = (byte) (c & 0xFF);
                raw[o++] = (byte) ((c >>> 24) & 0xFF);
            }
        }
        Deflater d = new Deflater(9);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            int n = d.deflate(buf);
            z.write(buf, 0, n);
        }
        d.end();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        be32(ihdr, w);
        be32(ihdr, h);
        ihdr.write(8);   // bit depth
        ihdr.write(6);   // RGBA
        ihdr.write(0);
        ihdr.write(0);
        ihdr.write(0);
        chunk(out, "IHDR", ihdr.toByteArray());
        chunk(out, "IDAT", z.toByteArray());
        chunk(out, "IEND", new byte[0]);
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(out.toByteArray());
        fo.close();
    }

    static void be32(ByteArrayOutputStream o, int v) {
        o.write((v >>> 24) & 0xFF);
        o.write((v >>> 16) & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write(v & 0xFF);
    }

    static void chunk(ByteArrayOutputStream o, String type, byte[] data) throws IOException {
        be32(o, data.length);
        byte[] t = type.getBytes("US-ASCII");
        o.write(t);
        o.write(data);
        CRC32 c = new CRC32();
        c.update(t);
        c.update(data);
        be32(o, (int) c.getValue());
    }

    // ---------- 画布 ----------
    static final int SS = 4; // 超采样倍数

    static class Canvas {
        int n;      // 逻辑边长
        int[] px;   // ARGB
        Canvas(int n) {
            this.n = n;
            px = new int[n * n];
        }
        void blend(int x, int y, int color, float a) {
            if (x < 0 || y < 0 || x >= n || y >= n) return;
            if (a <= 0) return;
            if (a > 1) a = 1;
            int i = y * n + x;
            int dst = px[i];
            int da = (dst >>> 24) & 0xFF;
            int sr = (color >> 16) & 0xFF, sg = (color >> 8) & 0xFF, sb = color & 0xFF;
            int dr = (dst >> 16) & 0xFF, dg = (dst >> 8) & 0xFF, db = dst & 0xFF;
            float sa = a;
            float outA = sa + da / 255f * (1 - sa);
            if (outA <= 0) { px[i] = 0; return; }
            int r = Math.round((sr * sa + dr * (da / 255f) * (1 - sa)) / outA);
            int g = Math.round((sg * sa + dg * (da / 255f) * (1 - sa)) / outA);
            int b = Math.round((sb * sa + db * (da / 255f) * (1 - sa)) / outA);
            px[i] = (Math.round(outA * 255) << 24) | (r << 16) | (g << 8) | b;
        }
        // 圆角矩形，coverage 由到矩形的距离场给出（1px 抗锯齿）
        void roundRect(float x0, float y0, float x1, float y1, float r, int color) {
            for (int y = (int) Math.floor(y0) - 1; y <= (int) Math.ceil(y1) + 1; y++) {
                for (int x = (int) Math.floor(x0) - 1; x <= (int) Math.ceil(x1) + 1; x++) {
                    float d = sdRoundRect(x + 0.5f, y + 0.5f, x0, y0, x1, y1, r);
                    float cov = clamp01(0.5f - d);
                    blend(x, y, color, cov);
                }
            }
        }
        static float sdRoundRect(float px, float py, float x0, float y0, float x1, float y1, float r) {
            float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f;
            float hx = (x1 - x0) / 2f - r, hy = (y1 - y0) / 2f - r;
            float dx = Math.abs(px - cx) - hx;
            float dy = Math.abs(py - cy) - hy;
            float ax = Math.max(dx, 0), ay = Math.max(dy, 0);
            return (float) Math.sqrt(ax * ax + ay * ay) + Math.min(Math.max(dx, dy), 0) - r;
        }
        void line(float x0, float y0, float x1, float y1, float w, int color, boolean round) {
            float r = w / 2f;
            int minx = (int) Math.floor(Math.min(x0, x1) - r - 1);
            int maxx = (int) Math.ceil(Math.max(x0, x1) + r + 1);
            int miny = (int) Math.floor(Math.min(y0, y1) - r - 1);
            int maxy = (int) Math.ceil(Math.max(y0, y1) + r + 1);
            for (int y = miny; y <= maxy; y++) {
                for (int x = minx; x <= maxx; x++) {
                    float d = sdSegment(x + 0.5f, y + 0.5f, x0, y0, x1, y1) - r;
                    float cov = clamp01(0.5f - d);
                    blend(x, y, color, cov);
                }
            }
        }
        static float sdSegment(float px, float py, float ax, float ay, float bx, float by) {
            float pax = px - ax, pay = py - ay, bax = bx - ax, bay = by - ay;
            float h = Math.max(0, Math.min(1, (pax * bax + pay * bay) / (bax * bax + bay * bay + 1e-6f)));
            float dx = pax - bax * h, dy = pay - bay * h;
            return (float) Math.sqrt(dx * dx + dy * dy);
        }
        static float clamp01(float v) {
            return v < 0 ? 0 : (v > 1 ? 1 : v);
        }
        // 降采样到目标尺寸
        int[] down(int size) {
            int[] out = new int[size * size];
            int f = n / size;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    long a = 0, r = 0, g = 0, b = 0;
                    for (int dy = 0; dy < f; dy++) {
                        for (int dx = 0; dx < f; dx++) {
                            int c = px[(y * f + dy) * n + (x * f + dx)];
                            int ca = (c >>> 24) & 0xFF;
                            a += ca;
                            r += ((c >> 16) & 0xFF) * ca;
                            g += ((c >> 8) & 0xFF) * ca;
                            b += (c & 0xFF) * ca;
                        }
                    }
                    int cnt = f * f;
                    int av = (int) (a / cnt);
                    if (av == 0) { out[y * size + x] = 0; continue; }
                    long sum = (long) av * cnt;
                    out[y * size + x] = (av << 24)
                            | ((int) (r / Math.max(1, sum)) << 16)
                            | ((int) (g / Math.max(1, sum)) << 8)
                            | (int) (b / Math.max(1, sum));
                }
            }
            return out;
        }
    }

    // ---------- 三套预置图标 ----------
    interface Art {
        void draw(Canvas c, int n);
    }

    /** 织机标记：三经三纬 + 一处填色的交织格 */
    static void weave(Canvas c, int n, int bg, int line, int accent, boolean accentCell, float pad) {
        float s = n;
        float m = s * pad;
        c.roundRect(m, m, s - m, s - m, s * 0.20f, bg);
        float x0 = s * 0.30f, x1 = s * 0.70f, y0 = s * 0.30f, y1 = s * 0.70f;
        float lw = s * 0.030f;
        if (accentCell) {
            c.roundRect(s * 0.30f, s * 0.30f, s * 0.50f, s * 0.50f, s * 0.02f, accent);
        }
        for (int i = 0; i < 3; i++) {
            float t = i / 2f;
            float x = x0 + (x1 - x0) * t;
            float y = y0 + (y1 - y0) * t;
            c.line(x, y0, x, y1, lw, line, true);
            c.line(x0, y, x1, y, lw, line, true);
        }
        // 一根穿出的梭线：铜色对角线
        c.line(s * 0.24f, s * 0.76f, s * 0.62f, s * 0.90f, s * 0.026f, accent, true);
    }

    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        int[] sizes = {48, 72, 96, 144, 192};
        String[] dirs = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};

        // 1) Loom —— 墨底铜线
        // 2) 织机 —— 纸底墨线
        // 3) Loom Lab —— 苔绿底纸线
        for (int i = 0; i < sizes.length; i++) {
            File d = new File(res, dirs[i]);
            d.mkdirs();
            int size = sizes[i];
            int n = size * SS;

            Canvas c1 = new Canvas(n);
            weave(c1, n, 0xFF171614, 0xFFEDE8DC, 0xFFD9784C, true, 0f);
            png(new File(d, "ic_loom.png"), size, size, c1.down(size));

            Canvas c2 = new Canvas(n);
            weave(c2, n, 0xFFF2EFE7, 0xFF191A17, 0xFFB4512A, true, 0f);
            png(new File(d, "ic_weave.png"), size, size, c2.down(size));

            Canvas c3 = new Canvas(n);
            weave(c3, n, 0xFF2F6B4A, 0xFFF2EFE7, 0xFFD9C38A, false, 0f);
            png(new File(d, "ic_lab.png"), size, size, c3.down(size));
        }
        System.out.println("icons ok -> " + res.getAbsolutePath());
    }
}
