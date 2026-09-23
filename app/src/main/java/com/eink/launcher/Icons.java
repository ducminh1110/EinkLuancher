package com.eink.launcher;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Line-art icons in the BOOX style, drawn with vector paths.
 *
 * Every icon is defined on a 24x24 grid with a tiny SVG-like path language
 * (M L H V C Q A Z, plus "O cx cy r" for circles and "R x y w h r" for rounded
 * rectangles). Paths are parsed lazily once and then drawn scaled, so icons are
 * crisp at any screen density and cost no bitmap memory at all (important on
 * 256 MB devices).
 */
final class Icons {

    // Bottom tab icons (have a filled "selected" variant).
    static final int T_LIBRARY = 0, T_STORE = 1, T_STORAGE = 2, T_APPS = 3, T_SETTINGS = 4;
    // Toolbar icons.
    static final int SEARCH = 5, SNOWFLAKE = 6, MENU = 7, BACK = 8, SORT = 9, GRID = 10, LIST = 11,
            NEW_FOLDER = 12, CLOSE = 13, REFRESH = 14, CHECK = 15, CHEVRON = 16, SELECT = 17;
    // App glyphs.
    static final int IMAGE = 18, SCREENSAVER = 19, RSS = 20, AI = 21, GLOBE = 22, DICTIONARY = 23,
            PLAYSTORE = 24, TRANSFER = 25, MIC = 26, CALENDAR = 27, MUSIC = 28, CLOCK = 29, CAMERA = 30,
            CALCULATOR = 31, MAIL = 32, PERSON = 33, PHONE = 34, CHAT = 35, DOWNLOAD = 36, FOLDER = 37,
            MAP = 38, VIDEO = 39, NOTE = 40, BOOK = 41, APK = 42, FILE = 43, GEAR = 44, BAG = 45;
    // Settings glyphs.
    static final int LANGUAGE = 46, DATE = 47, POWER = 48, NETWORK = 49, BLUETOOTH = 50, LOCK = 51,
            ACCOUNT = 52, DISPLAY = 53, SOUND = 54, SDCARD = 55, ACCESSIBILITY = 56, HOME = 57, INFO = 58,
            SLIDERS = 59, DEVICE = 60, BADGE = 61, BOLT = 62, ROTATE = 63;

    private static final String HEX_POINTY = "M10.44 3.1 Q12 2.2 13.56 3.1 L18.93 6.2 Q20.49 7.1 20.49 8.9 L20.49 15.1 Q20.49 16.9 18.93 17.8 L13.56 20.9 Q12 21.8 10.44 20.9 L5.07 17.8 Q3.51 16.9 3.51 15.1 L3.51 8.9 Q3.51 7.1 5.07 6.2 Z";
    private static final String HEX_FLAT = "M20.9 10.44 Q21.8 12 20.9 13.56 L17.8 18.93 Q16.9 20.49 15.1 20.49 L8.9 20.49 Q7.1 20.49 6.2 18.93 L3.1 13.56 Q2.2 12 3.1 10.44 L6.2 5.07 Q7.1 3.51 8.9 3.51 L15.1 3.51 Q16.9 3.51 17.8 5.07 Z";
    private static final String STORE = "M3 9 L4.5 3.5 H19.5 L21 9 Q21 11 19 11 V19.5 Q19 20.5 18 20.5 H6 Q5 20.5 5 19.5 V11 Q3 11 3 9 Z";
    private static final String FLOPPY = "M5 3 H16 L21 8 V19 Q21 21 19 21 H5 Q3 21 3 19 V5 Q3 3 5 3 Z";
    private static final String FOLDER_P = "M3 6.5 Q3 5 4.5 5 H9.5 L11.5 7.5 H19.5 Q21 7.5 21 9 V18 Q21 19.5 19.5 19.5 H4.5 Q3 19.5 3 18 Z";

    // name, stroke, fill, detail (inverted when selected), silhouette (filled when selected)
    private static final String[][] DEFS = {
        {"T_LIBRARY", "R5 2.5 14 19 2", null, "M8.5 7.5 H15.5 M8.5 11.5 H15.5 M8.5 15.5 H12.5", "R5 2.5 14 19 2"},
        {"T_STORE", STORE, null, "M5 11 H19 M9.5 15.5 H14.5", STORE},
        {"T_STORAGE", FLOPPY, null, "M7.5 3 V8 H14.5 V3 M7 21 V14.5 H17 V21", FLOPPY},
        {"T_APPS", HEX_POINTY, null, "M12 12 L7.8 9.6 M12 12 L16.2 9.6 M12 12 V16.8", HEX_POINTY},
        {"T_SETTINGS", HEX_FLAT, null, "O12 12 3.3", HEX_FLAT},

        {"SEARCH", "O10.8 10.8 7 M15.9 15.9 L21 21", null, null, null},
        {"SNOWFLAKE", "M12 2.5 L12 21.5 M20.23 7.25 L3.77 16.75 M3.77 7.25 L20.23 16.75 M14.6 3.4 L12 6.2 L9.4 3.4 M5.85 5.45 L6.98 9.1 L3.25 9.95 M3.25 14.05 L6.98 14.9 L5.85 18.55 M9.4 20.6 L12 17.8 L14.6 20.6 M18.15 18.55 L17.02 14.9 L20.75 14.05 M20.75 9.95 L17.02 9.1 L18.15 5.45", null, null, null},
        {"MENU", "O12 12 9.5 M7.8 8.6 H16.2 M7.8 12 H16.2 M7.8 15.4 H16.2", null, null, null},
        {"BACK", "M20 12 H4.5 M10.5 5.5 L4 12 L10.5 18.5", null, null, null},
        {"SORT", "M7.5 4 V20 M3.5 8 L7.5 4 L11.5 8 M16.5 20 V4 M12.5 16 L16.5 20 L20.5 16", null, null, null},
        {"GRID", "R4 4 6.5 6.5 1.2 R13.5 4 6.5 6.5 1.2 R4 13.5 6.5 6.5 1.2 R13.5 13.5 6.5 6.5 1.2", null, null, null},
        {"LIST", "M9 6 H20 M9 12 H20 M9 18 H20", "O4.8 6 1.3 O4.8 12 1.3 O4.8 18 1.3", null, null},
        {"NEW_FOLDER", FOLDER_P + " M12 10.5 V16.5 M9 13.5 H15", null, null, null},
        {"CLOSE", "M6 6 L18 18 M18 6 L6 18", null, null, null},
        {"REFRESH", "M21 12 A9 9 0 1 1 12 3 C14.52 3 16.93 4 18.74 5.74 L21 8 M21 3 V8 H16", null, null, null},
        {"CHECK", "M5 12.5 L10 17.5 L19.5 7", null, null, null},
        {"CHEVRON", "M9 5 L16 12 L9 19", null, null, null},
        {"SELECT", "R4 4 16 16 2.5 M8 12 L11 15 L16.5 9", null, null, null},

        {"IMAGE", "R3.5 3.5 17 17 2.5 O9 9 1.8 M20.5 15.5 L15.5 10.5 L5 20.5", null, null, null},
        {"SCREENSAVER", "R5 3 14 18 2 O9.8 8.3 1.5 M19 15 L14.5 10.5 L6 19.3", null, null, null},
        {"RSS", "M5 11 A8 8 0 0 1 13 19 M5 4.5 A14.5 14.5 0 0 1 19.5 19", "O6 18 1.7", null, null},
        {"AI", "M4 18.5 L8.8 5.5 L13.6 18.5 M5.8 14 H11.8 M18 10.5 V18.5", "O18 6.8 1.25", null, null},
        {"GLOBE", "O12 12 9 M3.2 12 H20.8 M12 3 C15.6 6 15.6 18 12 21 C8.4 18 8.4 6 12 3", null, null, null},
        {"DICTIONARY", "R5 3 14 18 2 M5 17.5 H19 M7.5 14 L10 7 L12.5 14 M8.4 11.8 H11.6 O15 12 2 M17 10 V14", null, null, null},
        {"PLAYSTORE", "M6 3.5 Q5 3 5 4.3 V19.7 Q5 21 6 20.5 L19.3 12.9 Q20.3 12 19.3 11.1 Z M5.4 3.9 L15 15.3 M5.4 20.1 L15 8.7", null, null, null},
        {"TRANSFER", "R4 4 16 16 2.5 M8 10 H16 L13.5 7.5 M16 14 H8 L10.5 16.5", null, null, null},
        {"MIC", "R9 2.5 6 11.5 3 M5.5 11 A6.5 6.5 0 0 0 18.5 11 M12 17.5 V21.5 M8.5 21.5 H15.5", null, null, null},
        {"CALENDAR", "R4 5 16 15.5 2 M4 10 H20 M8.5 3 V7 M15.5 3 V7 M9 15.2 L11.2 17.4 L15.2 13.2", null, null, null},
        {"MUSIC", "O8.7 17.3 2.9 M11.6 17.3 V4 C13 6.5 16.5 6.5 17.5 9.5", null, null, null},
        {"CLOCK", "O12 12 9 M12 6.8 V12 L15.5 14", null, null, null},
        {"CAMERA", "M3.5 8.5 Q3.5 7 5 7 H7.8 L9.5 4.5 H14.5 L16.2 7 H19 Q20.5 7 20.5 8.5 V18 Q20.5 19.5 19 19.5 H5 Q3.5 19.5 3.5 18 Z O12 13 3.6", null, null, null},
        {"CALCULATOR", "R5 2.5 14 19 2.5 R8 5.5 8 4 0.8", "O8.8 13 1.1 O12 13 1.1 O15.2 13 1.1 O8.8 17 1.1 O12 17 1.1 O15.2 17 1.1", null, null},
        {"MAIL", "R3 5.5 18 13 2 M3.8 7 L12 13 L20.2 7", null, null, null},
        {"PERSON", "O12 7.8 4 M4.5 20.5 Q4.5 13.8 12 13.8 Q19.5 13.8 19.5 20.5", null, null, null},
        {"PHONE", "M7 3.5 H9.2 L10.8 8 L8.8 9.8 Q10.5 13.5 14.2 15.2 L16 13.2 L20.5 14.8 V17 Q20.5 20.5 17 20.5 Q3.5 19.5 3.5 7 Q3.5 3.5 7 3.5 Z", null, null, null},
        {"CHAT", "M4 6 Q4 4.5 5.5 4.5 H18.5 Q20 4.5 20 6 V15 Q20 16.5 18.5 16.5 H10.5 L6.5 20 V16.5 H5.5 Q4 16.5 4 15 Z M8 9 H16 M8 12.2 H13", null, null, null},
        {"DOWNLOAD", "M12 3.5 V15 M7 10.5 L12 15.5 L17 10.5 M4.5 20 H19.5", null, null, null},
        {"FOLDER", FOLDER_P, null, null, null},
        {"MAP", "M12 21.5 C12 21.5 19 15.2 19 9.8 A7 7 0 0 0 5 9.8 C5 15.2 12 21.5 12 21.5 Z O12 9.8 2.6", null, null, null},
        {"VIDEO", "R3 5 18 14 2.5 M10 9 V15 L15.2 12 Z", null, null, null},
        {"NOTE", "M13 4.5 H6 Q4.5 4.5 4.5 6 V18 Q4.5 19.5 6 19.5 H18 Q19.5 19.5 19.5 18 V11 M17.8 3.2 L20.8 6.2 L12.5 14.5 H9.5 V11.5 Z", null, null, null},
        {"BOOK", "M12 6.5 C10 5 7 4.5 3.5 5 V18.5 C7 18 10 18.5 12 20 C14 18.5 17 18 20.5 18.5 V5 C17 4.5 14 5 12 6.5 Z M12 6.5 V20", null, null, null},
        {"APK", "M12 2.5 L20.5 7 V17 L12 21.5 L3.5 17 V7 Z M3.5 7 L12 11.5 L20.5 7 M12 11.5 V21.5 M7.8 4.8 L16.2 9.2", null, null, null},
        {"FILE", "M14 3 H6.5 Q5 3 5 4.5 V19.5 Q5 21 6.5 21 H17.5 Q19 21 19 19.5 V8 Z M14 3 V8 H19", null, null, null},
        {"GEAR", HEX_FLAT + " O12 12 3.3", null, null, null},
        {"BAG", "M5 8 H19 L18 20.5 H6 Z M9 10.5 V7 A3 3 0 0 1 15 7 V10.5", null, null, null},

        {"LANGUAGE", "M3.5 5.5 H12.5 M8 3.5 V5.5 M10.8 5.5 C10.3 9.5 7.8 12.5 4 14 M5.5 8.5 C6.7 10.8 8.8 12.6 11.5 13.8 M12.5 20.5 L16.5 10.5 L20.5 20.5 M14 17 H19", null, null, null},
        {"DATE", "R4 5 16 15.5 2 M4 10 H20 M8.5 3 V7 M15.5 3 V7", "O8.2 13.7 1.1 O12 13.7 1.1 O15.8 13.7 1.1 O8.2 17.2 1.1 O12 17.2 1.1", null, null},
        {"POWER", "R7 4.5 10 17 2 M10 2.5 H14", "R9.5 11.5 5 7.5 0.6", null, null},
        {"NETWORK", "M2.5 9 A13.5 13.5 0 0 1 21.5 9 M5.5 12.5 A9 9 0 0 1 18.5 12.5 M8.7 16 A4.7 4.7 0 0 1 15.3 16", "O12 19.3 1.4", null, null},
        {"BLUETOOTH", "M7 7.5 L17 16.5 L12 21 V3 L17 7.5 L7 16.5", null, null, null},
        {"LOCK", "R5 10.5 14 10.5 2 M8 10.5 V7.5 A4 4 0 0 1 16 7.5 V10.5 M12 14.5 V17", null, null, null},
        {"ACCOUNT", "O12 12 9.5 O12 9.7 3.2 M6.3 18.6 Q7.5 14.8 12 14.8 Q16.5 14.8 17.7 18.6", null, null, null},
        {"DISPLAY", "M2.5 12 C5 7.2 8.3 5 12 5 C15.7 5 19 7.2 21.5 12 C19 16.8 15.7 19 12 19 C8.3 19 5 16.8 2.5 12 Z O12 12 3.2", null, null, null},
        {"SOUND", "M4 9.5 H7.5 L12 5.5 V18.5 L7.5 14.5 H4 Z M15.5 9 A4.2 4.2 0 0 1 15.5 15 M18 6.5 A8 8 0 0 1 18 17.5", null, null, null},
        {"SDCARD", "M9 3 H17 Q19 3 19 5 V19 Q19 21 17 21 H7 Q5 21 5 19 V7 Z M9.5 7 V9.5 M12.5 7 V9.5 M15.5 7 V9.5", null, null, null},
        {"ACCESSIBILITY", "O12 12 9.5 M7.5 9.8 H16.5 M12 9.8 V13.8 L9.5 17.8 M12 13.8 L14.5 17.8", "O12 6.9 1.3", null, null},
        {"HOME", "M3.5 11 L12 3.8 L20.5 11 M5.5 9.3 V20 H18.5 V9.3 M10 20 V14.5 H14 V20", null, null, null},
        {"INFO", "O12 12 9.5 M12 11 V16.8", "O12 7.7 1.25", null, null},
        {"SLIDERS", "M4 7 H11 M15 7 H20 O13 7 2 M4 12 H6 M10 12 H20 O8 12 2 M4 17 H13 M17 17 H20 O15 17 2", null, null, null},
        {"DEVICE", "R5 2.5 14 19 2 R7.8 5.3 8.4 11.5 0.5 M10.5 19 H13.5", null, null, null},
        {"BADGE", "M17.8 10.4 Q18.7 12 17.8 13.6 L16 16.7 Q15.1 18.3 13.3 18.3 L10.7 18.3 Q8.9 18.3 8 16.7 L6.2 13.6 Q5.3 12 6.2 10.4 L8 7.3 Q8.9 5.7 10.7 5.7 L13.3 5.7 Q15.1 5.7 16 7.3 Z", "O12 12 2.2", null, null},
        {"BOLT", null, "M13.5 2 L5 13.5 H11 L10 22 L19 10 H13 Z", null, null},
        {"ROTATE", "M4 12 A8 8 0 0 1 18.2 7 M20 12 A8 8 0 0 1 5.8 17 M18.5 3 V7.3 H14.2 M5.5 21 V16.7 H9.8", null, null, null},
    };

    private static final Path[] STROKE = new Path[DEFS.length];
    private static final Path[] FILL = new Path[DEFS.length];
    private static final Path[] DETAIL = new Path[DEFS.length];
    private static final Path[] SIL = new Path[DEFS.length];
    private static final boolean[] PARSED = new boolean[DEFS.length];

    private static final Paint STROKE_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint FILL_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF TMP = new RectF();

    static {
        STROKE_PAINT.setStyle(Paint.Style.STROKE);
        STROKE_PAINT.setStrokeCap(Paint.Cap.ROUND);
        STROKE_PAINT.setStrokeJoin(Paint.Join.ROUND);
        FILL_PAINT.setStyle(Paint.Style.FILL);
    }

    private Icons() {}

    static final float STROKE_W = 1.7f;

    /** Draws icon {@code id} centered at (cx, cy) with the given size in pixels. */
    static void draw(Canvas c, int id, float cx, float cy, float size, int color) {
        draw(c, id, cx, cy, size, color, STROKE_W);
    }

    static void draw(Canvas c, int id, float cx, float cy, float size, int color, float strokeUnits) {
        ensure(id);
        int save = c.save();
        float k = size / 24f;
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(k, k);
        STROKE_PAINT.setColor(color);
        STROKE_PAINT.setStrokeWidth(strokeUnits);
        FILL_PAINT.setColor(color);
        if (STROKE[id] != null) c.drawPath(STROKE[id], STROKE_PAINT);
        if (DETAIL[id] != null) c.drawPath(DETAIL[id], STROKE_PAINT);
        if (FILL[id] != null) c.drawPath(FILL[id], FILL_PAINT);
        c.restoreToCount(save);
    }

    /** Tab icons: selected ones are drawn filled black with white details. */
    static void drawTab(Canvas c, int id, float cx, float cy, float size, boolean selected) {
        if (!selected) {
            draw(c, id, cx, cy, size, Ui.INK, 1.8f);
            return;
        }
        ensure(id);
        int save = c.save();
        float k = size / 24f;
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(k, k);
        FILL_PAINT.setColor(Ui.BLACK);
        STROKE_PAINT.setStrokeWidth(1.8f);
        STROKE_PAINT.setColor(Ui.BLACK);
        if (SIL[id] != null) c.drawPath(SIL[id], FILL_PAINT);
        if (STROKE[id] != null) c.drawPath(STROKE[id], STROKE_PAINT);
        STROKE_PAINT.setColor(Ui.WHITE);
        if (DETAIL[id] != null) c.drawPath(DETAIL[id], STROKE_PAINT);
        c.restoreToCount(save);
    }

    private static void ensure(int id) {
        if (PARSED[id]) return;
        String[] d = DEFS[id];
        STROKE[id] = parse(d[1]);
        FILL[id] = parse(d[2]);
        DETAIL[id] = parse(d[3]);
        SIL[id] = parse(d[4]);
        PARSED[id] = true;
    }

    // ------------------------------------------------------------------ parser

    private static int pos;
    private static String src;

    static Path parse(String d) {
        if (d == null) return null;
        Path p = new Path();
        src = d;
        pos = 0;
        float x = 0, y = 0, sx = 0, sy = 0;
        char cmd = 'M';
        while (true) {
            skipSep();
            if (pos >= src.length()) break;
            char ch = src.charAt(pos);
            if (Character.isLetter(ch)) {
                cmd = ch;
                pos++;
                if (cmd == 'Z' || cmd == 'z') {
                    p.close();
                    x = sx;
                    y = sy;
                    continue;
                }
            }
            boolean rel = Character.isLowerCase(cmd);
            float ox = rel ? x : 0, oy = rel ? y : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M':
                    x = ox + num(); y = oy + num();
                    p.moveTo(x, y);
                    sx = x; sy = y;
                    cmd = rel ? 'l' : 'L';
                    break;
                case 'L':
                    x = ox + num(); y = oy + num();
                    p.lineTo(x, y);
                    break;
                case 'H':
                    x = ox + num();
                    p.lineTo(x, y);
                    break;
                case 'V':
                    y = oy + num();
                    p.lineTo(x, y);
                    break;
                case 'C': {
                    float x1 = ox + num(), y1 = oy + num(), x2 = ox + num(), y2 = oy + num();
                    x = ox + num(); y = oy + num();
                    p.cubicTo(x1, y1, x2, y2, x, y);
                    break;
                }
                case 'Q': {
                    float x1 = ox + num(), y1 = oy + num();
                    x = ox + num(); y = oy + num();
                    p.quadTo(x1, y1, x, y);
                    break;
                }
                case 'A': {
                    float rx = num(), ry = num();
                    num(); // x-axis rotation (unused)
                    boolean large = num() != 0, sweep = num() != 0;
                    float nx = ox + num(), ny = oy + num();
                    arc(p, x, y, nx, ny, rx, ry, large, sweep);
                    x = nx; y = ny;
                    break;
                }
                case 'O': {
                    float cx = num(), cy = num(), r = num();
                    p.addCircle(cx, cy, r, Path.Direction.CW);
                    break;
                }
                case 'R': {
                    float rx = num(), ry = num(), w = num(), h = num(), r = num();
                    TMP.set(rx, ry, rx + w, ry + h);
                    p.addRoundRect(TMP, r, r, Path.Direction.CW);
                    break;
                }
                default:
                    pos = src.length();
            }
        }
        src = null;
        return p;
    }

    private static void skipSep() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == ',' || c == '\n' || c == '\t') pos++;
            else break;
        }
    }

    private static float num() {
        skipSep();
        int start = pos;
        if (pos < src.length() && (src.charAt(pos) == '-' || src.charAt(pos) == '+')) pos++;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '.') pos++;
            else break;
        }
        return Float.parseFloat(src.substring(start, pos));
    }

    /** SVG elliptical arc (no rotation) converted to center form. */
    private static void arc(Path p, float x1, float y1, float x2, float y2, float rx, float ry,
                            boolean large, boolean sweep) {
        rx = Math.abs(rx);
        ry = Math.abs(ry);
        if (rx == 0 || ry == 0) {
            p.lineTo(x2, y2);
            return;
        }
        double dx2 = (x1 - x2) / 2.0, dy2 = (y1 - y2) / 2.0;
        double lambda = (dx2 * dx2) / (rx * rx) + (dy2 * dy2) / (ry * ry);
        if (lambda > 1) {
            double sq = Math.sqrt(lambda);
            rx *= sq;
            ry *= sq;
        }
        double rx2 = rx * rx, ry2 = ry * ry;
        double num = rx2 * ry2 - rx2 * dy2 * dy2 - ry2 * dx2 * dx2;
        double den = rx2 * dy2 * dy2 + ry2 * dx2 * dx2;
        double coef = den == 0 ? 0 : Math.sqrt(Math.max(0, num / den));
        if (large == sweep) coef = -coef;
        double cxp = coef * (rx * dy2 / ry);
        double cyp = coef * (-ry * dx2 / rx);
        double cx = cxp + (x1 + x2) / 2.0, cy = cyp + (y1 + y2) / 2.0;
        double ux = (dx2 - cxp) / rx, uy = (dy2 - cyp) / ry;
        double vx = (-dx2 - cxp) / rx, vy = (-dy2 - cyp) / ry;
        double theta = Math.atan2(uy, ux);
        double delta = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
        if (!sweep && delta > 0) delta -= 2 * Math.PI;
        else if (sweep && delta < 0) delta += 2 * Math.PI;
        TMP.set((float) (cx - rx), (float) (cy - ry), (float) (cx + rx), (float) (cy + ry));
        p.arcTo(TMP, (float) Math.toDegrees(theta), (float) Math.toDegrees(delta), false);
    }
}
