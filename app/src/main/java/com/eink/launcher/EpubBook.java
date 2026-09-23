package com.eink.launcher;

import android.content.res.Resources;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** EPUB 2/3: OPF spine = chapters, titles from the NCX or the EPUB 3 nav document. */
final class EpubBook extends Book {
    private ZipFile zip;
    private final List<String> paths = new ArrayList<>();

    EpubBook(File f) {
        super(f);
    }

    private static String dir(String path) {
        int i = path.lastIndexOf('/');
        return i >= 0 ? path.substring(0, i + 1) : "";
    }

    private static String noFragment(String href) {
        int i = href.indexOf('#');
        return i >= 0 ? href.substring(0, i) : href;
    }

    @Override
    void open() throws Exception {
        zip = new ZipFile(file);
        String opfPath = null;
        ZipEntry container = zip.getEntry("META-INF/container.xml");
        if (container != null) {
            XmlPullParser p = Covers.parser(zip.getInputStream(container));
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && "rootfile".equals(Covers.local(p.getName()))) {
                    opfPath = p.getAttributeValue(null, "full-path");
                    break;
                }
            }
        }
        if (opfPath == null || zip.getEntry(opfPath) == null) throw new IllegalStateException("no opf");
        String base = dir(opfPath);

        HashMap<String, String[]> manifest = new HashMap<>(); // id -> {path, type, props}
        List<String> spine = new ArrayList<>();
        String ncxId = null, navPath = null;
        XmlPullParser p = Covers.parser(zip.getInputStream(zip.getEntry(opfPath)));
        int ev;
        boolean inTitle = false;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                String tag = Covers.local(p.getName());
                if ("title".equals(tag) && p.getName().contains(":")) {
                    inTitle = true;
                } else if ("item".equals(tag)) {
                    String id = p.getAttributeValue(null, "id"), href = p.getAttributeValue(null, "href");
                    if (id == null || href == null) continue;
                    String props = p.getAttributeValue(null, "properties");
                    String path = Covers.resolve(base, href);
                    manifest.put(id, new String[]{path, String.valueOf(p.getAttributeValue(null, "media-type")), props});
                    if (props != null && props.contains("nav")) navPath = path;
                } else if ("spine".equals(tag)) {
                    ncxId = p.getAttributeValue(null, "toc");
                } else if ("itemref".equals(tag)) {
                    String idref = p.getAttributeValue(null, "idref");
                    if (idref != null) spine.add(idref);
                }
            } else if (ev == XmlPullParser.TEXT && inTitle) {
                String t = p.getText().trim();
                if (t.length() > 0) title = t;
                inTitle = false;
            } else if (ev == XmlPullParser.END_TAG) {
                inTitle = false;
            }
        }

        HashMap<String, String> toc = new HashMap<>();
        String[] ncx = ncxId != null ? manifest.get(ncxId) : null;
        if (ncx != null) readNcx(ncx[0], toc);
        if (toc.isEmpty() && navPath != null) readNav(navPath, toc);

        for (String idref : spine) {
            String[] item = manifest.get(idref);
            if (item == null) continue;
            String type = item[1];
            if (!(type.contains("html") || type.contains("xml"))) continue;
            ZipEntry e = zip.getEntry(item[0]);
            if (e == null) continue;
            paths.add(item[0]);
            add(toc.get(item[0]), e.getSize() > 0 ? e.getSize() : 1024);
        }
        if (paths.isEmpty()) throw new IllegalStateException("empty spine");
    }

    private void readNcx(String path, HashMap<String, String> toc) {
        try {
            ZipEntry e = zip.getEntry(path);
            if (e == null) return;
            XmlPullParser p = Covers.parser(zip.getInputStream(e));
            String base = dir(path), label = null;
            boolean inText = false;
            int ev;
            while ((ev = p.next()) != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG) {
                    String tag = Covers.local(p.getName());
                    if ("text".equals(tag)) inText = true;
                    else if ("content".equals(tag) && label != null) {
                        String src = p.getAttributeValue(null, "src");
                        if (src != null) {
                            String key = Covers.resolve(base, noFragment(src));
                            if (!toc.containsKey(key)) toc.put(key, label);
                        }
                        label = null;
                    }
                } else if (ev == XmlPullParser.TEXT && inText) {
                    label = p.getText().trim();
                    inText = false;
                } else if (ev == XmlPullParser.END_TAG) {
                    inText = false;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static final Pattern NAV_LINK = Pattern.compile("<a\\b[^>]*href\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");

    private void readNav(String path, HashMap<String, String> toc) {
        try {
            ZipEntry e = zip.getEntry(path);
            if (e == null) return;
            String html = new String(Covers.readAll(zip.getInputStream(e)), "UTF-8");
            Matcher m = NAV_LINK.matcher(html);
            String base = dir(path);
            while (m.find()) {
                String label = android.text.Html.fromHtml(TAGS.matcher(m.group(2)).replaceAll("")).toString().trim();
                String key = Covers.resolve(base, noFragment(m.group(1)));
                if (label.length() > 0 && !toc.containsKey(key)) toc.put(key, label);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    CharSequence text(int chapter, Resources res, int maxW, int maxH) throws Exception {
        final String path = paths.get(chapter);
        byte[] data = Covers.readAll(zip.getInputStream(zip.getEntry(path)));
        if (data == null) return "";
        String html = TxtBook.decodeXml(data);
        final String base = dir(path);
        return HtmlText.parse(html, new HtmlText.ImageSource() {
            @Override
            public byte[] load(String src) throws Exception {
                ZipEntry e = zip.getEntry(Covers.resolve(base, noFragment(src)));
                return e == null ? null : Covers.readAll(zip.getInputStream(e));
            }
        }, res, maxW, maxH);
    }

    @Override
    void close() {
        try {
            if (zip != null) zip.close();
        } catch (Exception ignored) {
        }
    }
}
