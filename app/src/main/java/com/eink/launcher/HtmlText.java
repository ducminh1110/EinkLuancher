package com.eink.launcher;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Layout;
import android.text.style.AlignmentSpan;
import android.text.style.ImageSpan;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns book HTML (EPUB / MOBI / FB2) into a styled CharSequence using the
 * platform {@link Html} parser (no extra library). Images are decoded
 * down-sampled to the page size as RGB_565, with a per-chapter memory budget
 * so a picture-heavy chapter cannot exhaust a 256 MB device.
 */
final class HtmlText {
    interface ImageSource {
        byte[] load(String src) throws Exception;
    }

    private static final int IMAGE_BUDGET = 6 * 1024 * 1024;

    private static final Pattern HEAD = Pattern.compile("<head[\\s>].*?</head>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern STYLE = Pattern.compile("<(style|script)[\\s>].*?</\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SVG_IMAGE = Pattern.compile("<image\\b[^>]*?(?:xlink:)?href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_DECL = Pattern.compile("<\\?xml[^>]*\\?>|<!DOCTYPE[^>]*>", Pattern.CASE_INSENSITIVE);

    private HtmlText() {}

    static String clean(String html) {
        html = XML_DECL.matcher(html).replaceAll("");
        html = HEAD.matcher(html).replaceAll("");
        html = STYLE.matcher(html).replaceAll("");
        Matcher m = SVG_IMAGE.matcher(html);
        if (m.find()) html = m.replaceAll("<img src=\"$1\"/>");
        return html;
    }

    static CharSequence parse(String html, final ImageSource src, final Resources res, final int maxW, final int maxH) {
        final int[] budget = {IMAGE_BUDGET};
        Html.ImageGetter getter = new Html.ImageGetter() {
            @Override
            public Drawable getDrawable(String source) {
                Drawable empty = new ColorDrawable(0);
                empty.setBounds(0, 0, 0, 0);
                if (src == null || source == null || budget[0] <= 0) return empty;
                try {
                    byte[] data = src.load(source);
                    if (data == null) return empty;
                    Bitmap b = decode(data, maxW, maxH, budget);
                    if (b == null) return empty;
                    BitmapDrawable d = new BitmapDrawable(res, b);
                    d.setBounds(0, 0, b.getWidth(), b.getHeight());
                    return d;
                } catch (Throwable t) {
                    return empty;
                }
            }
        };
        Spanned sp = Html.fromHtml(clean(html), getter, null);
        return centerImages(trim(sp));
    }

    /** Pictures look best centered, like in any reader. */
    private static CharSequence centerImages(CharSequence cs) {
        if (!(cs instanceof Spanned)) return cs;
        Spanned sp = (Spanned) cs;
        ImageSpan[] imgs = sp.getSpans(0, sp.length(), ImageSpan.class);
        if (imgs.length == 0) return cs;
        SpannableStringBuilder b = cs instanceof SpannableStringBuilder ? (SpannableStringBuilder) cs
                : new SpannableStringBuilder(cs);
        String s = b.toString();
        for (ImageSpan img : imgs) {
            int start = b.getSpanStart(img), end = b.getSpanEnd(img);
            if (start < 0) continue;
            int ps = s.lastIndexOf('\n', start - 1) + 1;
            int pe = s.indexOf('\n', end);
            pe = pe < 0 ? s.length() : pe + 1;
            b.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), ps, pe, Spanned.SPAN_PARAGRAPH);
        }
        return b;
    }

    static Bitmap decode(byte[] data, int maxW, int maxH, int[] budget) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int w = o.outWidth, h = o.outHeight;
        if (w <= 0 || h <= 0) return null;
        float scale = Math.min(maxW / (float) w, maxH / (float) h);
        // Upscale only pictures that are already fairly big (keep inline icons small).
        if (scale > 1f && w < maxW * 0.5f) scale = 1f;
        int tw = Math.max(1, Math.round(w * scale)), th = Math.max(1, Math.round(h * scale));
        if (tw * th * 2 > budget[0]) return null;
        int sample = 1;
        while (w / (sample * 2) >= tw && h / (sample * 2) >= th) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (b == null) return null;
        if (b.getWidth() != tw || b.getHeight() != th) {
            Bitmap s = Bitmap.createScaledBitmap(b, tw, th, true);
            if (s != b) b.recycle();
            b = s;
        }
        budget[0] -= tw * th * 2;
        return b;
    }

    /** Removes leading / trailing blank lines (they would create empty pages). */
    static CharSequence trim(CharSequence cs) {
        int start = 0, end = cs.length();
        while (start < end && Character.isWhitespace(cs.charAt(start))) start++;
        while (end > start && Character.isWhitespace(cs.charAt(end - 1))) end--;
        if (start == 0 && end == cs.length()) return cs;
        return new SpannableStringBuilder(cs, start, end);
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<') b.append("&lt;");
            else if (c == '>') b.append("&gt;");
            else if (c == '&') b.append("&amp;");
            else b.append(c);
        }
        return b.toString();
    }
}
