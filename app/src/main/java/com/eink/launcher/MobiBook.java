package com.eink.launcher;

import android.content.res.Resources;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MOBI / AZW / AZW3 / PRC without DRM. Text records are decompressed
 * (PalmDOC LZ77 or Huffman/CDIC) into HTML, split at page breaks into
 * chapters; pictures are read from the image records on demand.
 */
final class MobiBook extends Book {
    private static final int PART = 48 * 1024;
    private RandomAccessFile raf;
    private long[] off;
    private long len;
    private int firstImage = -1;
    private String html;
    private final List<int[]> parts = new ArrayList<>();

    MobiBook(File f) {
        super(f);
    }

    private byte[] record(int i) throws Exception {
        long s = off[i], e = i + 1 < off.length ? off[i + 1] : len;
        if (e < s || e - s > 16 * 1024 * 1024) throw new IllegalStateException("bad record");
        byte[] b = new byte[(int) (e - s)];
        raf.seek(s);
        raf.readFully(b);
        return b;
    }

    private static int u16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static int s32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static long u32(byte[] b, int o) {
        return s32(b, o) & 0xFFFFFFFFL;
    }

    @Override
    void open() throws Exception {
        raf = new RandomAccessFile(file, "r");
        len = raf.length();
        raf.seek(76);
        int n = raf.readUnsignedShort();
        if (n < 2) throw new IllegalStateException("not a mobi");
        off = new long[n];
        for (int i = 0; i < n; i++) {
            raf.seek(78 + i * 8L);
            off[i] = raf.readInt() & 0xFFFFFFFFL;
        }
        byte[] r0 = record(0);
        int compression = u16(r0, 0);
        int textRecords = u16(r0, 8);
        int encryption = u16(r0, 12);
        if (encryption != 0) throw new SecurityException("DRM");
        if (r0.length < 0x84 || r0[16] != 'M' || r0[17] != 'O' || r0[18] != 'B' || r0[19] != 'I') {
            throw new IllegalStateException("no MOBI header");
        }
        int headerLen = s32(r0, 20);
        int encoding = s32(r0, 28);
        firstImage = s32(r0, 0x6C);
        int huffRec = s32(r0, 0x70), huffCount = s32(r0, 0x74);
        int extraFlags = headerLen >= 0xE4 && r0.length >= 0xF4 ? u16(r0, 0xF2) : 0;
        int nameOff = s32(r0, 0x54), nameLen = s32(r0, 0x58);
        Charset cs = Charset.forName(encoding == 65001 ? "UTF-8" : "windows-1252");
        if (nameOff > 0 && nameLen > 0 && nameOff + nameLen <= r0.length) {
            title = new String(r0, nameOff, nameLen, cs);
        }
        readExthTitle(r0, headerLen, cs);

        Huff huff = null;
        if (compression == 17480) {
            huff = new Huff();
            huff.loadHuff(record(huffRec));
            for (int i = 1; i < huffCount; i++) huff.loadCdic(record(huffRec + i));
        } else if (compression != 1 && compression != 2) {
            throw new IllegalStateException("compression " + compression);
        }

        ByteArrayOutputStream text = new ByteArrayOutputStream(Math.max(64 * 1024, s32(r0, 4)));
        byte[] buf = new byte[8192];
        for (int i = 1; i <= textRecords && i < n; i++) {
            byte[] d = record(i);
            int size = d.length - trailing(d, d.length, extraFlags);
            if (size <= 0) continue;
            if (compression == 1) {
                text.write(d, 0, size);
            } else if (compression == 2) {
                int outLen = palmdoc(d, size, buf);
                text.write(buf, 0, outLen);
            } else {
                byte[] in = new byte[size];
                System.arraycopy(d, 0, in, 0, size);
                byte[] out = huff.unpack(in);
                text.write(out, 0, out.length);
            }
        }
        html = new String(text.toByteArray(), cs);
        text = null;
        split();
        if (parts.isEmpty()) throw new IllegalStateException("empty book");
    }

    private void readExthTitle(byte[] r0, int headerLen, Charset cs) {
        try {
            if ((s32(r0, 0x80) & 0x40) == 0) return;
            int p = 16 + headerLen;
            if (r0[p] != 'E' || r0[p + 1] != 'X' || r0[p + 2] != 'T' || r0[p + 3] != 'H') return;
            int count = s32(r0, p + 8);
            int q = p + 12;
            for (int i = 0; i < count && q + 8 <= r0.length; i++) {
                int type = s32(r0, q), rl = s32(r0, q + 4);
                if (rl < 8) break;
                if (type == 503 && q + rl <= r0.length) title = new String(r0, q + 8, rl - 8, cs);
                q += rl;
            }
        } catch (Exception ignored) {
        }
    }

    private static final Pattern BREAK = Pattern.compile("<mbp:pagebreak", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("<h[1-4][^>]*>(.*?)</h[1-4]>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private void split() {
        List<Integer> cuts = new ArrayList<>();
        cuts.add(0);
        Matcher m = BREAK.matcher(html);
        while (m.find()) if (m.start() > cuts.get(cuts.size() - 1)) cuts.add(m.start());
        cuts.add(html.length());
        for (int i = 0; i + 1 < cuts.size(); i++) {
            int s = cuts.get(i), e = cuts.get(i + 1);
            while (e - s > PART * 3 / 2) {
                // Split long runs at a paragraph end.
                int c = html.indexOf("</p>", s + PART);
                if (c < 0 || c > e - 1024) c = html.indexOf('>', s + PART);
                if (c < 0 || c >= e) break;
                c = html.indexOf('>', c) + 1;
                addPart(s, c);
                s = c;
            }
            addPart(s, e);
        }
    }

    private void addPart(int s, int e) {
        if (e <= s) return;
        String head = html.substring(s, Math.min(e, s + 6000));
        if (head.replaceAll("<[^>]+>", "").trim().length() == 0 && !head.contains("<img") && e - s < 6000) return;
        String t = null;
        Matcher m = HEADING.matcher(head);
        if (m.find()) {
            t = android.text.Html.fromHtml(m.group(1).replaceAll("<img[^>]*>", "")).toString().trim();
            if (t.length() > 60) t = t.substring(0, 60) + "…";
            if (t.length() == 0) t = null;
        }
        parts.add(new int[]{s, e});
        add(t, e - s);
    }

    private static final Pattern RECINDEX = Pattern.compile("recindex\\s*=\\s*[\"']?0*(\\d+)[\"']?", Pattern.CASE_INSENSITIVE);

    @Override
    CharSequence text(int chapter, Resources res, int maxW, int maxH) {
        int[] p = parts.get(chapter);
        String h = RECINDEX.matcher(html.substring(p[0], p[1])).replaceAll("src=\"rec:$1\"");
        return HtmlText.parse(h, new HtmlText.ImageSource() {
            @Override
            public byte[] load(String src) throws Exception {
                int n = -1;
                if (src.startsWith("rec:")) {
                    n = Integer.parseInt(src.substring(4));
                } else if (src.startsWith("kindle:embed:")) {
                    String code = src.substring(13);
                    int q = code.indexOf('?');
                    if (q >= 0) code = code.substring(0, q);
                    n = Integer.parseInt(code.toUpperCase(Locale.US), 32);
                }
                if (n <= 0 || firstImage <= 0) return null;
                int idx = firstImage + n - 1;
                return idx < off.length ? record(idx) : null;
            }
        }, res, maxW, maxH);
    }

    @Override
    void close() {
        try {
            if (raf != null) raf.close();
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------ helpers

    /** Size of the trailing entries appended to each text record. */
    static int trailing(byte[] d, int size, int flags) {
        int num = 0;
        int test = flags >> 1;
        while (test != 0) {
            if ((test & 1) != 0) num += entrySize(d, size - num);
            test >>= 1;
        }
        if ((flags & 1) != 0 && size - num - 1 >= 0) num += (d[size - num - 1] & 0x3) + 1;
        return num;
    }

    private static int entrySize(byte[] d, int size) {
        int bitpos = 0, result = 0;
        while (size > 0) {
            int v = d[size - 1] & 0xFF;
            result |= (v & 0x7F) << bitpos;
            bitpos += 7;
            size--;
            if ((v & 0x80) != 0 || bitpos >= 28 || size == 0) return result;
        }
        return result;
    }

    /** PalmDOC LZ77 decompression of one record into out (returns length). */
    static int palmdoc(byte[] in, int len, byte[] out) {
        int i = 0, o = 0;
        while (i < len && o < out.length) {
            int c = in[i++] & 0xFF;
            if (c >= 1 && c <= 8) {
                while (c-- > 0 && i < len && o < out.length) out[o++] = in[i++];
            } else if (c < 128) {
                out[o++] = (byte) c;
            } else if (c >= 0xC0) {
                out[o++] = ' ';
                if (o < out.length) out[o++] = (byte) (c ^ 0x80);
            } else {
                if (i >= len) break;
                c = (c << 8) | (in[i++] & 0xFF);
                int dist = (c >> 3) & 0x07FF, n = (c & 7) + 3;
                int from = o - dist;
                if (from < 0) break;
                while (n-- > 0 && o < out.length) out[o++] = out[from++];
            }
        }
        return o;
    }

    /** Mobipocket Huffman / CDIC decompressor. */
    static final class Huff {
        private final int[] d1len = new int[256];
        private final boolean[] d1term = new boolean[256];
        private final long[] d1max = new long[256];
        private final long[] mincode = new long[33], maxcode = new long[33];
        private final List<byte[]> dict = new ArrayList<>();
        private final List<Boolean> done = new ArrayList<>();

        void loadHuff(byte[] h) {
            if (h[0] != 'H' || h[1] != 'U' || h[2] != 'F' || h[3] != 'F') throw new IllegalStateException("HUFF");
            int off1 = (int) u32(h, 8), off2 = (int) u32(h, 12);
            for (int i = 0; i < 256; i++) {
                long v = u32(h, off1 + i * 4);
                int codelen = (int) (v & 0x1F);
                d1len[i] = codelen;
                d1term[i] = (v & 0x80) != 0;
                d1max[i] = (((v >>> 8) + 1) << (32 - codelen)) - 1;
            }
            mincode[0] = 0;
            maxcode[0] = (1L << 32) - 1;
            for (int k = 1; k <= 32; k++) {
                long mn = u32(h, off2 + (k - 1) * 8), mx = u32(h, off2 + (k - 1) * 8 + 4);
                mincode[k] = mn << (32 - k);
                maxcode[k] = ((mx + 1) << (32 - k)) - 1;
            }
        }

        void loadCdic(byte[] c) {
            if (c[0] != 'C' || c[1] != 'D' || c[2] != 'I' || c[3] != 'C') throw new IllegalStateException("CDIC");
            long phrases = u32(c, 8);
            int bits = (int) u32(c, 12);
            int n = (int) Math.min(1L << bits, phrases - dict.size());
            for (int i = 0; i < n; i++) {
                int o = u16(c, 16 + i * 2);
                int blen = u16(c, 16 + o);
                int l = blen & 0x7FFF;
                byte[] s = new byte[l];
                System.arraycopy(c, 18 + o, s, 0, l);
                dict.add(s);
                done.add((blen & 0x8000) != 0);
            }
        }

        byte[] unpack(byte[] data) {
            long bitsleft = data.length * 8L;
            byte[] d = new byte[data.length + 8];
            System.arraycopy(data, 0, d, 0, data.length);
            int pos = 0;
            long x = u64(d, 0);
            int n = 32;
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 3);
            while (true) {
                if (n <= 0) {
                    pos += 4;
                    x = u64(d, pos);
                    n += 32;
                }
                long code = (x >>> n) & 0xFFFFFFFFL;
                int idx = (int) (code >>> 24);
                int codelen = d1len[idx];
                long max = d1max[idx];
                if (!d1term[idx]) {
                    while (codelen < 32 && code < mincode[codelen]) codelen++;
                    max = maxcode[codelen];
                }
                n -= codelen;
                bitsleft -= codelen;
                if (bitsleft < 0 || codelen == 0) break;
                int r = (int) ((max - code) >>> (32 - codelen));
                if (r < 0 || r >= dict.size()) break;
                byte[] slice = dict.get(r);
                if (!done.get(r)) {
                    done.set(r, true); // guards against loops, like the reference decoder
                    slice = unpack(slice);
                    dict.set(r, slice);
                }
                out.write(slice, 0, slice.length);
            }
            return out.toByteArray();
        }

        private static long u64(byte[] b, int o) {
            return (u32(b, o) << 32) | u32(b, o + 4);
        }
    }
}
