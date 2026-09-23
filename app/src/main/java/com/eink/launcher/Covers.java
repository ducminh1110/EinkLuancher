package com.eink.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Base64;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Extracts book covers without any third party library:
 * EPUB (OPF manifest), FB2 / FB2.ZIP (base64 binary), MOBI / AZW / AZW3
 * (EXTH cover record), CBZ (first image) and plain images.
 * PDF/DjVu have no renderer on Android 4.4, they get a drawn cover instead.
 */
final class Covers {
    private static final int MAX_BYTES = 6 * 1024 * 1024;

    private Covers() {}

    static boolean supports(String ext) {
        switch (ext) {
            case "epub": case "fb2": case "zip": case "mobi": case "azw": case "azw3": case "prc":
            case "cbz": case "jpg": case "jpeg": case "png": case "gif": case "bmp": case "webp":
                return true;
            default:
                return false;
        }
    }

    static Bitmap extract(File f, String ext, int w, int h) throws Exception {
        byte[] data = null;
        switch (ext) {
            case "epub":
                data = epub(f);
                break;
            case "fb2":
                data = fb2(new java.io.FileInputStream(f));
                break;
            case "zip":
                if (f.getName().toLowerCase(Locale.US).endsWith(".fb2.zip")) data = fb2Zip(f);
                break;
            case "mobi": case "azw": case "azw3": case "prc":
                data = mobi(f);
                break;
            case "cbz":
                data = cbz(f);
                break;
            default:
                return decodeFile(f, w, h);
        }
        return data == null ? null : decode(data, w, h);
    }

    // ------------------------------------------------------------------ EPUB

    private static byte[] epub(File f) throws Exception {
        ZipFile zf = new ZipFile(f);
        try {
            String opfPath = null;
            ZipEntry container = zf.getEntry("META-INF/container.xml");
            if (container != null) {
                XmlPullParser p = parser(zf.getInputStream(container));
                int ev;
                while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && "rootfile".equals(local(p.getName()))) {
                        opfPath = p.getAttributeValue(null, "full-path");
                        break;
                    }
                }
            }
            String href = null;
            if (opfPath != null) {
                ZipEntry opf = zf.getEntry(opfPath);
                if (opf != null) href = opfCover(zf.getInputStream(opf));
                if (href != null) {
                    int slash = opfPath.lastIndexOf('/');
                    String base = slash >= 0 ? opfPath.substring(0, slash + 1) : "";
                    href = resolve(base, href);
                }
            }
            ZipEntry img = href != null ? zf.getEntry(href) : null;
            if (img == null) {
                // Fallback: any image with "cover" in its name, else the first image.
                ZipEntry first = null;
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    String n = e.getName().toLowerCase(Locale.US);
                    if (!isImage(n)) continue;
                    if (n.contains("cover")) {
                        img = e;
                        break;
                    }
                    if (first == null) first = e;
                }
                if (img == null) img = first;
            }
            return img == null ? null : readAll(zf.getInputStream(img));
        } finally {
            zf.close();
        }
    }

    private static String opfCover(InputStream in) throws Exception {
        XmlPullParser p = parser(in);
        String coverId = null, propHref = null, guessHref = null, firstImage = null;
        HashMap<String, String> hrefs = new HashMap<>();
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev != XmlPullParser.START_TAG) continue;
            String tag = local(p.getName());
            if ("meta".equals(tag)) {
                if ("cover".equals(p.getAttributeValue(null, "name"))) {
                    coverId = p.getAttributeValue(null, "content");
                }
            } else if ("item".equals(tag)) {
                String id = p.getAttributeValue(null, "id");
                String href = p.getAttributeValue(null, "href");
                String type = p.getAttributeValue(null, "media-type");
                String props = p.getAttributeValue(null, "properties");
                if (href == null) continue;
                if (id != null) hrefs.put(id, href);
                boolean image = type != null ? type.startsWith("image/") : isImage(href.toLowerCase(Locale.US));
                if (!image) continue;
                if (props != null && props.contains("cover-image")) propHref = href;
                if (guessHref == null && ((id != null && id.toLowerCase(Locale.US).contains("cover"))
                        || href.toLowerCase(Locale.US).contains("cover"))) guessHref = href;
                if (firstImage == null) firstImage = href;
            }
        }
        in.close();
        if (propHref != null) return propHref;
        if (coverId != null && hrefs.containsKey(coverId)) return hrefs.get(coverId);
        if (guessHref != null) return guessHref;
        return firstImage;
    }

    static String resolve(String base, String href) {
        try {
            href = URLDecoder.decode(href, "UTF-8");
        } catch (Exception ignored) {
        }
        String path = base + href;
        // Normalize "a/b/../c".
        List<String> parts = new ArrayList<>();
        for (String s : path.split("/")) {
            if (s.isEmpty() || ".".equals(s)) continue;
            if ("..".equals(s)) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else parts.add(s);
        }
        StringBuilder b = new StringBuilder();
        for (String s : parts) {
            if (b.length() > 0) b.append('/');
            b.append(s);
        }
        return b.toString();
    }

    // ------------------------------------------------------------------- FB2

    private static byte[] fb2Zip(File f) throws Exception {
        ZipInputStream z = new ZipInputStream(new java.io.FileInputStream(f));
        try {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (e.getName().toLowerCase(Locale.US).endsWith(".fb2")) return fb2(z);
            }
            return null;
        } finally {
            z.close();
        }
    }

    private static byte[] fb2(InputStream in) throws Exception {
        try {
            XmlPullParser p = parser(in);
            String coverId = null;
            boolean inCover = false;
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = local(p.getName());
                    if ("coverpage".equals(tag)) inCover = true;
                    else if (inCover && "image".equals(tag) && coverId == null) {
                        for (int i = 0; i < p.getAttributeCount(); i++) {
                            if (p.getAttributeName(i).endsWith("href")) {
                                coverId = p.getAttributeValue(i);
                                if (coverId.startsWith("#")) coverId = coverId.substring(1);
                            }
                        }
                    } else if ("binary".equals(tag)) {
                        String id = p.getAttributeValue(null, "id");
                        if (coverId == null || coverId.equals(id)) {
                            String b64 = p.nextText();
                            return Base64.decode(b64, Base64.DEFAULT);
                        }
                    }
                } else if (ev == XmlPullParser.END_TAG && "coverpage".equals(local(p.getName()))) {
                    inCover = false;
                }
            }
            return null;
        } finally {
            in.close();
        }
    }

    // ------------------------------------------------------------------ MOBI

    private static byte[] mobi(File f) throws Exception {
        RandomAccessFile raf = new RandomAccessFile(f, "r");
        try {
            long len = raf.length();
            if (len < 100) return null;
            raf.seek(76);
            int n = raf.readUnsignedShort();
            if (n <= 1 || 78 + n * 8L > len) return null;
            long[] off = new long[n];
            for (int i = 0; i < n; i++) {
                raf.seek(78 + i * 8L);
                off[i] = raf.readInt() & 0xFFFFFFFFL;
            }
            long r0 = off[0];
            raf.seek(r0 + 16);
            byte[] magic = new byte[4];
            raf.readFully(magic);
            if (magic[0] != 'M' || magic[1] != 'O' || magic[2] != 'B' || magic[3] != 'I') return null;
            int headerLen = raf.readInt();
            raf.seek(r0 + 0x6C);
            int firstImage = raf.readInt();
            raf.seek(r0 + 0x80);
            int exthFlags = raf.readInt();
            int coverOffset = -1, thumbOffset = -1;
            if ((exthFlags & 0x40) != 0) {
                long exth = r0 + 16 + headerLen;
                raf.seek(exth);
                raf.readFully(magic);
                if (magic[0] == 'E' && magic[1] == 'X' && magic[2] == 'T' && magic[3] == 'H') {
                    raf.readInt(); // header length
                    int count = raf.readInt();
                    long pos = exth + 12;
                    for (int i = 0; i < count && i < 1000; i++) {
                        raf.seek(pos);
                        int type = raf.readInt();
                        int rlen = raf.readInt();
                        if (rlen < 8) break;
                        if (type == 201 && rlen >= 12) coverOffset = raf.readInt();
                        else if (type == 202 && rlen >= 12) thumbOffset = raf.readInt();
                        pos += rlen;
                    }
                }
            }
            if (firstImage <= 0 || firstImage >= n) return null;
            int idx = firstImage + (coverOffset >= 0 ? coverOffset : (thumbOffset >= 0 ? thumbOffset : 0));
            if (idx >= n) return null;
            long start = off[idx], end = idx + 1 < n ? off[idx + 1] : len;
            if (end <= start || end - start > MAX_BYTES) return null;
            byte[] data = new byte[(int) (end - start)];
            raf.seek(start);
            raf.readFully(data);
            return data;
        } finally {
            raf.close();
        }
    }

    // ------------------------------------------------------------------- CBZ

    private static byte[] cbz(File f) throws Exception {
        ZipFile zf = new ZipFile(f);
        try {
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory() && isImage(e.getName().toLowerCase(Locale.US))) names.add(e.getName());
            }
            if (names.isEmpty()) return null;
            Collections.sort(names);
            return readAll(zf.getInputStream(zf.getEntry(names.get(0))));
        } finally {
            zf.close();
        }
    }

    // --------------------------------------------------------------- helpers

    static XmlPullParser parser(InputStream in) throws Exception {
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(in, null);
        return p;
    }

    static String local(String name) {
        int i = name.indexOf(':');
        return i >= 0 ? name.substring(i + 1) : name;
    }

    static boolean isImage(String n) {
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".gif")
                || n.endsWith(".bmp") || n.endsWith(".webp");
    }

    static byte[] readAll(InputStream in) throws Exception {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] buf = new byte[16 * 1024];
            int r, total = 0;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > MAX_BYTES) return null;
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static int sample(int sw, int sh, int w, int h) {
        int s = 1;
        while (sw / (s * 2) >= w && sh / (s * 2) >= h) s *= 2;
        return s;
    }

    private static Bitmap decode(byte[] data, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample(o.outWidth, o.outHeight, w, h);
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return fill(BitmapFactory.decodeByteArray(data, 0, data.length, o), w, h);
    }

    private static Bitmap decodeFile(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample(o.outWidth, o.outHeight, w, h);
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return fill(BitmapFactory.decodeFile(f.getPath(), o), w, h);
    }

    /** Scales + center-crops to exactly w x h (RGB_565, white background). */
    private static Bitmap fill(Bitmap src, int w, int h) {
        if (src == null) return null;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        c.drawColor(Ui.WHITE);
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        int x = (src.getWidth() - cw) / 2, y = (src.getHeight() - ch) / 2;
        Rect s = new Rect(x, y, x + cw, y + ch);
        c.drawBitmap(src, s, new Rect(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
        src.recycle();
        return out;
    }
}
