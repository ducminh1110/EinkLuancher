package com.eink.launcher;

import android.content.res.Resources;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain text and HTML files. The file is never loaded as a whole: it is cut
 * into ~48 KB byte ranges on line boundaries, and only the range being read
 * is decoded. A 10 MB novel therefore costs the same RAM as a short story.
 */
final class TxtBook extends Book {
    private static final int CHUNK = 48 * 1024;
    private final boolean html;
    private Charset charset;
    private int unit = 1;          // 2 for UTF-16
    private boolean bigEndian;
    private final List<long[]> ranges = new ArrayList<>();

    TxtBook(File f, boolean html) {
        super(f);
        this.html = html;
    }

    @Override
    void open() throws Exception {
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            long len = raf.length();
            byte[] head = new byte[(int) Math.min(len, 64 * 1024)];
            raf.readFully(head);
            long start = 0;
            if (head.length >= 3 && (head[0] & 0xFF) == 0xEF && (head[1] & 0xFF) == 0xBB && (head[2] & 0xFF) == 0xBF) {
                charset = Charset.forName("UTF-8");
                start = 3;
            } else if (head.length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xFE) {
                charset = Charset.forName("UTF-16LE");
                unit = 2;
                start = 2;
            } else if (head.length >= 2 && (head[0] & 0xFF) == 0xFE && (head[1] & 0xFF) == 0xFF) {
                charset = Charset.forName("UTF-16BE");
                unit = 2;
                bigEndian = true;
                start = 2;
            } else {
                charset = html ? htmlCharset(head) : (isUtf8(head) ? Charset.forName("UTF-8") : Charset.forName("windows-1252"));
            }
            List<long[]> heads = new ArrayList<>();
            List<String> headTitles = new ArrayList<>();
            if (!html) findHeadings(raf, start, len, heads, headTitles);
            if (heads.size() >= 2) {
                // Chapters from "Chương 1 / Chapter 2 / 第三章 ..." lines.
                if (heads.get(0)[0] - start > 512) {
                    addRange(raf, start, heads.get(0)[0], null);
                }
                for (int i = 0; i < heads.size(); i++) {
                    long s0 = heads.get(i)[0];
                    long e0 = i + 1 < heads.size() ? heads.get(i + 1)[0] : len;
                    addRange(raf, s0, e0, headTitles.get(i));
                }
            } else {
                addRange(raf, start, len, null);
            }
            if (ranges.isEmpty()) {
                ranges.add(new long[]{0, 0});
                add(null, 1);
            }
        } finally {
            raf.close();
        }
    }

    /** Adds [start, end) as one or more chapters of at most ~CHUNK bytes, cut at line breaks. */
    private void addRange(RandomAccessFile raf, long start, long end, String title) throws Exception {
        byte[] win = new byte[4096];
        int part = 0;
        while (start < end) {
            long stop = Math.min(end, start + CHUNK);
            if (stop < end && end - stop < CHUNK / 3) stop = end; // avoid a tiny last part
            if (stop < end) {
                // Extend to the next line break (or '>' for HTML) so no line / tag is cut.
                raf.seek(stop);
                int n = raf.read(win);
                int cut = -1;
                for (int i = 0; i + unit <= n; i += unit) {
                    int c = unit == 1 ? win[i] : (bigEndian ? win[i + 1] : win[i]);
                    int other = unit == 1 ? 0 : (bigEndian ? win[i] : win[i + 1]);
                    if (other == 0 && (c == '\n' || (html && c == '>'))) {
                        cut = i + unit;
                        break;
                    }
                }
                if (cut > 0) stop += cut;
                stop = Math.min(end, stop);
            }
            String t;
            if (title != null) t = part == 0 ? title : title + " (" + (part + 1) + ")";
            else t = html ? null : firstLine(raf, start, stop);
            add(t != null && t.length() > 0 ? t : null, stop - start);
            ranges.add(new long[]{start, stop});
            start = stop;
            part++;
        }
    }

    private static final Pattern HEADING = Pattern.compile(
            "^(chương|chuong|chapter|hồi|hoi|phần|phan|quyển|quyen|tập|part|book|第)\\s*"
                    + "([0-9]+|[ivxlcdm]+|[一二三四五六七八九十百千零]+)(\\b|章|回|节|卷|[:.\\-\\s]|$).{0,80}$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Streams the file once and records the byte offset of every chapter heading line. */
    private void findHeadings(RandomAccessFile raf, long start, long len, List<long[]> heads, List<String> titles)
            throws Exception {
        raf.seek(start);
        java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(raf.getFD()), 32 * 1024);
        byte[] line = new byte[256];
        int ll = 0;
        boolean tooLong = false;
        long pos = start, lineStart = start;
        int b0 = -1;
        int b;
        while ((b = in.read()) >= 0) {
            pos++;
            int ch;
            if (unit == 2) {
                if (b0 < 0) {
                    b0 = b;
                    continue;
                }
                ch = bigEndian ? ((b0 << 8) | b) : ((b << 8) | b0);
                if (ll + 2 <= line.length) {
                    line[ll++] = (byte) b0;
                    line[ll++] = (byte) b;
                } else tooLong = true;
                b0 = -1;
            } else {
                ch = b;
                if (ll < line.length) line[ll++] = (byte) b;
                else tooLong = true;
            }
            if (ch == '\n') {
                if (!tooLong && ll > unit * 2) {
                    String t = new String(line, 0, ll, charset).trim();
                    if (t.length() > 0 && t.length() <= 90 && HEADING.matcher(t).matches()) {
                        heads.add(new long[]{lineStart});
                        titles.add(t);
                        if (heads.size() > 5000) break;
                    }
                }
                ll = 0;
                tooLong = false;
                lineStart = pos;
            }
        }
        // Drop headings that are too close to each other (e.g. a table of contents at the start).
        for (int i = heads.size() - 1; i > 0; i--) {
            if (heads.get(i)[0] - heads.get(i - 1)[0] < 200) {
                heads.remove(i - 1);
                titles.remove(i - 1);
            }
        }
    }

    private String firstLine(RandomAccessFile raf, long start, long end) throws Exception {
        byte[] b = new byte[(int) Math.min(240, end - start)];
        raf.seek(start);
        raf.readFully(b);
        String s = new String(b, charset);
        for (String line : s.split("\n")) {
            line = line.trim();
            if (line.length() > 0) return line.length() > 48 ? line.substring(0, 48) + "…" : line;
        }
        return null;
    }

    @Override
    CharSequence text(int chapter, Resources res, int maxW, int maxH) throws Exception {
        long[] r = ranges.get(chapter);
        byte[] b = new byte[(int) (r[1] - r[0])];
        RandomAccessFile raf = new RandomAccessFile(file, "r");
        try {
            raf.seek(r[0]);
            raf.readFully(b);
        } finally {
            raf.close();
        }
        String s = new String(b, charset);
        if (html) {
            final File dir = file.getParentFile();
            return HtmlText.parse(s, new HtmlText.ImageSource() {
                @Override
                public byte[] load(String src) throws Exception {
                    File f = new File(dir, Covers.resolve("", src));
                    return f.isFile() ? Covers.readAll(new java.io.FileInputStream(f)) : null;
                }
            }, res, maxW, maxH);
        }
        return HtmlText.trim(s.replace("\r\n", "\n").replace('\r', '\n'));
    }

    // --------------------------------------------------------------- charset

    static boolean isUtf8(byte[] b) {
        CharsetDecoder d = Charset.forName("UTF-8").newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        // Ignore a possibly cut multi-byte sequence at the very end.
        int n = b.length;
        int back = 0;
        while (back < 3 && n - back - 1 >= 0 && (b[n - back - 1] & 0xC0) == 0x80) back++;
        if (n - back - 1 >= 0 && (b[n - back - 1] & 0xC0) == 0xC0) n = n - back - 1;
        try {
            d.decode(ByteBuffer.wrap(b, 0, n));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static final Pattern ENC = Pattern.compile("(?:encoding|charset)\\s*=\\s*[\"']?([A-Za-z0-9_\\-]+)");

    private static Charset htmlCharset(byte[] head) {
        String s = new String(head, 0, Math.min(head.length, 2048), Charset.forName("ISO-8859-1"));
        Matcher m = ENC.matcher(s);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (Exception ignored) {
            }
        }
        return isUtf8(head) ? Charset.forName("UTF-8") : Charset.forName("windows-1252");
    }

    /** Decodes an (X)HTML document honoring its XML declaration / meta charset. */
    static String decodeXml(byte[] data) {
        int off = 0;
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) off = 3;
        Charset cs = Charset.forName("UTF-8");
        String head = new String(data, off, Math.min(data.length - off, 1024), Charset.forName("ISO-8859-1"));
        Matcher m = ENC.matcher(head);
        if (m.find()) {
            try {
                cs = Charset.forName(m.group(1).toUpperCase(Locale.US));
            } catch (Exception ignored) {
            }
        }
        return new String(data, off, data.length - off, cs);
    }
}
