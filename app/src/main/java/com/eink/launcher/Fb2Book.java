package com.eink.launcher;

import android.content.res.Resources;
import android.util.Base64;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * FictionBook 2 (.fb2 / .fb2.zip). The XML is converted once into small
 * HTML strings, one per top level section, which the reader then renders.
 */
final class Fb2Book extends Book {
    private static final int MAX_IMAGES_CHARS = 6 * 1024 * 1024;
    private final List<String> html = new ArrayList<>();
    private final HashMap<String, String> binaries = new HashMap<>();

    Fb2Book(File f) {
        super(f);
    }

    private InputStream stream() throws Exception {
        if (!file.getName().toLowerCase(Locale.US).endsWith(".zip")) return new FileInputStream(file);
        ZipInputStream z = new ZipInputStream(new FileInputStream(file));
        ZipEntry e;
        while ((e = z.getNextEntry()) != null) {
            if (e.getName().toLowerCase(Locale.US).endsWith(".fb2")) return z;
        }
        z.close();
        throw new IllegalStateException("no fb2 in zip");
    }

    @Override
    void open() throws Exception {
        InputStream in = stream();
        try {
            XmlPullParser p = Covers.parser(in);
            StringBuilder cur = null;
            String curTitle = null;
            HashSet<String> images = new HashSet<>();
            int sectionDepth = 0, bodyDepth = 0;
            boolean inBody = false, notes = false, inTitle = false, inBookTitle = false;
            StringBuilder titleText = new StringBuilder();
            int imageChars = 0;
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = Covers.local(p.getName());
                    switch (tag) {
                        case "book-title":
                            inBookTitle = true;
                            break;
                        case "body":
                            inBody = true;
                            bodyDepth++;
                            String name = p.getAttributeValue(null, "name");
                            notes = name != null && name.contains("note");
                            if (cur == null) cur = new StringBuilder();
                            break;
                        case "section":
                            if (!inBody) break;
                            if (sectionDepth == 0 && !notes) {
                                if (cur != null && cur.length() > 0) flush(cur, curTitle);
                                cur = new StringBuilder();
                                curTitle = null;
                            }
                            sectionDepth++;
                            break;
                        case "title":
                            if (!inBody || cur == null) break;
                            inTitle = true;
                            titleText.setLength(0);
                            cur.append(sectionDepth <= 1 ? "<h2>" : "<h3>");
                            break;
                        case "subtitle":
                            if (cur != null) cur.append("<p><b>");
                            break;
                        case "p":
                            if (cur != null && inBody) cur.append(inTitle ? "" : "<p>");
                            break;
                        case "empty-line":
                            if (cur != null) cur.append("<br/>");
                            break;
                        case "emphasis":
                            if (cur != null) cur.append("<i>");
                            break;
                        case "strong":
                            if (cur != null) cur.append("<b>");
                            break;
                        case "sup":
                            if (cur != null) cur.append("<sup>");
                            break;
                        case "sub":
                            if (cur != null) cur.append("<sub>");
                            break;
                        case "epigraph": case "cite": case "poem":
                            if (cur != null) cur.append("<blockquote>");
                            break;
                        case "image":
                            if (cur == null || !inBody) break;
                            for (int i = 0; i < p.getAttributeCount(); i++) {
                                if (p.getAttributeName(i).endsWith("href")) {
                                    String id = p.getAttributeValue(i);
                                    if (id.startsWith("#")) id = id.substring(1);
                                    images.add(id);
                                    cur.append("<img src=\"").append(HtmlText.escape(id)).append("\"/>");
                                }
                            }
                            break;
                        case "binary":
                            String id = p.getAttributeValue(null, "id");
                            if (id != null && images.contains(id)) {
                                String data = p.nextText();
                                if (imageChars + data.length() <= MAX_IMAGES_CHARS) {
                                    binaries.put(id, data);
                                    imageChars += data.length();
                                }
                            }
                            break;
                    }
                } else if (ev == XmlPullParser.END_TAG) {
                    String tag = Covers.local(p.getName());
                    switch (tag) {
                        case "book-title":
                            inBookTitle = false;
                            break;
                        case "body":
                            bodyDepth--;
                            inBody = bodyDepth > 0;
                            notes = false;
                            break;
                        case "section":
                            if (!inBody) break;
                            sectionDepth--;
                            break;
                        case "title":
                            if (!inTitle || cur == null) break;
                            inTitle = false;
                            cur.append(sectionDepth <= 1 ? "</h2>" : "</h3>");
                            if (curTitle == null && titleText.length() > 0) curTitle = titleText.toString().trim();
                            break;
                        case "subtitle":
                            if (cur != null) cur.append("</b></p>");
                            break;
                        case "p":
                            if (cur != null && inBody) cur.append(inTitle ? "<br/>" : "</p>");
                            if (inTitle) titleText.append(' ');
                            break;
                        case "v":
                            if (cur != null) cur.append("<br/>");
                            break;
                        case "emphasis":
                            if (cur != null) cur.append("</i>");
                            break;
                        case "strong":
                            if (cur != null) cur.append("</b>");
                            break;
                        case "sup":
                            if (cur != null) cur.append("</sup>");
                            break;
                        case "sub":
                            if (cur != null) cur.append("</sub>");
                            break;
                        case "epigraph": case "cite": case "poem":
                            if (cur != null) cur.append("</blockquote>");
                            break;
                    }
                } else if (ev == XmlPullParser.TEXT) {
                    String t = p.getText();
                    if (inBookTitle) {
                        if (t.trim().length() > 0) title = t.trim();
                    } else if (cur != null && inBody) {
                        cur.append(HtmlText.escape(t));
                        if (inTitle) titleText.append(t);
                    }
                }
            }
            if (cur != null && cur.length() > 0) flush(cur, curTitle);
            if (html.isEmpty()) throw new IllegalStateException("empty fb2");
        } finally {
            in.close();
        }
    }

    private void flush(StringBuilder b, String t) {
        String s = b.toString();
        if (s.replaceAll("<[^>]+>", "").trim().length() == 0 && !s.contains("<img")) return;
        html.add(s);
        add(t, s.length());
    }

    @Override
    CharSequence text(int chapter, Resources res, int maxW, int maxH) {
        return HtmlText.parse(html.get(chapter), new HtmlText.ImageSource() {
            @Override
            public byte[] load(String src) {
                String b64 = binaries.get(src);
                return b64 == null ? null : Base64.decode(b64, Base64.DEFAULT);
            }
        }, res, maxW, maxH);
    }
}
