package studio.sobrano.orgstaff;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final int BLUE = Color.rgb(22,119,242);
    private final int TEXT = Color.rgb(10,27,58);
    private final int MUTED = Color.rgb(101,120,153);
    private final int BG = Color.rgb(247,250,255);
    private final int BORDER = Color.rgb(225,233,244);
    private final int GREEN = Color.rgb(20,158,72);
    private final int LIGHT_BLUE = Color.rgb(235,245,255);
    private final int LIGHT_GREEN = Color.rgb(233,249,238);

    private FrameLayout content;
    private LinearLayout nav;
    private ExecutorService worker = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private String activeTab = "search";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        prefs = getSharedPreferences("orgstaff", MODE_PRIVATE);
        buildShell();
        showSearch();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(8), dp(6), dp(8), dp(8));
        nav.setBackgroundColor(Color.WHITE);
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(72)));
        setContentView(root);
        rebuildNav();
    }

    private void rebuildNav() {
        nav.removeAllViews();
        nav.addView(navItem("⌕", "Поиск", "search"), weight());
        nav.addView(navItem("◷", "История", "history"), weight());
        nav.addView(navItem("☆", "Избранное", "favorites"), weight());
        nav.addView(navItem("○", "Профиль", "profile"), weight());
    }

    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, -1, 1f); }

    private View navItem(String icon, String label, String tab) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        boolean active = tab.equals(activeTab);
        TextView i = tv(icon, 23, active ? BLUE : MUTED, true);
        TextView t = tv(label, 12, active ? BLUE : MUTED, active);
        box.addView(i);
        box.addView(t);
        box.setOnClickListener(v -> {
            if (tab.equals("search")) showSearch();
            else if (tab.equals("history")) showHistory();
            else if (tab.equals("favorites")) showFavorites();
            else showProfile();
        });
        return box;
    }

    private void setTab(String tab) { activeTab = tab; rebuildNav(); }
    private void replace(View v) { content.removeAllViews(); content.addView(v, new FrameLayout.LayoutParams(-1,-1)); }

    private ScrollView scroll(LinearLayout body) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(body, new ScrollView.LayoutParams(-1,-2));
        return sv;
    }

    private LinearLayout page() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(18), dp(20), dp(26));
        return l;
    }

    private void showSearch() {
        setTab("search");
        LinearLayout body = page();
        body.addView(header("▥", "OrgStaff Mobile", "Поиск руководства по ИНН"));
        body.addView(space(20));
        TextView desc = tv("Актуальная информация о руководстве организаций из открытых источников", 16, MUTED, false);
        desc.setLineSpacing(0,1.18f);
        body.addView(desc);
        body.addView(space(18));

        LinearLayout card = card(Color.WHITE);
        TextView label = tv("Введите ИНН", 18, TEXT, true);
        card.addView(label);
        card.addView(space(10));
        EditText input = new EditText(this);
        input.setHint("10 или 12 цифр");
        input.setTextColor(TEXT); input.setHintTextColor(Color.rgb(148,163,184));
        input.setTextSize(18); input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setPadding(dp(16),0,dp(16),0);
        input.setBackground(rounded(Color.rgb(252,253,255), BORDER, 14, 1));
        card.addView(input, new LinearLayout.LayoutParams(-1, dp(58)));
        card.addView(space(12));
        Button search = primaryButton("⌕   Найти");
        card.addView(search, new LinearLayout.LayoutParams(-1, dp(58)));
        search.setOnClickListener(v -> {
            String inn = input.getText().toString().trim();
            if (!InnValidator.isValid(inn)) {
                input.setError("Проверьте ИНН: контрольная сумма не совпадает"); return;
            }
            hideKeyboard(input);
            beginSearch(inn);
        });
        body.addView(card);
        body.addView(space(16));

        LinearLayout tips = card(LIGHT_BLUE);
        tips.addView(tv("💡  Полезные советы", 17, TEXT, true));
        tips.addView(space(8));
        tips.addView(tv("• Введите ИНН организации\n• Приложение ищет упоминания должностных лиц в открытых веб-источниках\n• Подтверждение ставится только при совпадении нескольких независимых доменов\n• Каждый найденный результат можно открыть вместе с источником", 14, MUTED, false));
        body.addView(tips);
        body.addView(space(18));
        body.addView(sectionTitle("Недавние поиски", "Все ›", v -> showHistory()));
        addRecentRows(body, 3);
        replace(scroll(body));
    }

    private void beginSearch(String inn) {
        LinearLayout body = page();
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(header("▥", "OrgStaff Mobile", "Поиск руководства по ИНН"));
        body.addView(space(80));
        ProgressBar pb = new ProgressBar(this);
        body.addView(pb, new LinearLayout.LayoutParams(dp(56),dp(56)));
        body.addView(space(18));
        TextView status = tv("Запускаю поиск…", 18, TEXT, true);
        status.setGravity(Gravity.CENTER);
        body.addView(status);
        body.addView(space(10));
        TextView note = tv("Используются публично доступные веб-источники. Некоторые сайты могут временно ограничивать автоматический поиск.", 13, MUTED, false);
        note.setGravity(Gravity.CENTER);
        body.addView(note);
        replace(scroll(body));

        worker.submit(() -> {
            SearchResult result = OsintSearcher.search(inn, text -> runOnUiThread(() -> status.setText(text)));
            saveHistory(result);
            runOnUiThread(() -> showResult(result));
        });
    }

    private void showResult(SearchResult r) {
        setTab("search");
        LinearLayout body = page();
        body.addView(backHeader(r.orgName == null ? "Результат" : r.orgName, "ИНН " + r.inn, this::showSearch));
        body.addView(space(12));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip("Открытые источники", LIGHT_GREEN, GREEN));
        chips.addView(spaceH(8));
        chips.addView(chip(r.sources.size() + " источн.", LIGHT_BLUE, BLUE));
        body.addView(chips);
        body.addView(space(14));

        LinearLayout info = card(Color.WHITE);
        info.addView(infoRow("ИНН", r.inn));
        info.addView(divider());
        info.addView(infoRow("Дата проверки", new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date())));
        info.addView(divider());
        info.addView(infoRow("Найдено лиц", String.valueOf(r.people.size())));
        body.addView(info);

        body.addView(space(14));
        Button fav = secondaryButton(isFavorite(r.inn) ? "★  В избранном" : "☆  Добавить в избранное");
        fav.setOnClickListener(v -> { toggleFavorite(r.inn, r.orgName); showResult(r); });
        body.addView(fav, new LinearLayout.LayoutParams(-1, dp(50)));

        if (r.error != null) {
            body.addView(space(14));
            LinearLayout warn = card(Color.rgb(255,248,232));
            warn.addView(tv("Поиск выполнен не полностью", 16, Color.rgb(138,83,0), true));
            warn.addView(space(4));
            warn.addView(tv(r.error, 13, Color.rgb(138,83,0), false));
            body.addView(warn);
        }

        body.addView(space(22));
        body.addView(sectionTitle("Руководство", r.people.size() + " найдено", null));
        if (r.people.isEmpty()) {
            LinearLayout empty = card(Color.WHITE);
            empty.addView(tv("Подтверждённых ФИО по автоматическому поиску не найдено.", 15, TEXT, true));
            empty.addView(space(6));
            empty.addView(tv("Ниже сохранены найденные источники. Это лучше, чем показывать непроверенное имя как факт.", 13, MUTED, false));
            body.addView(empty);
        } else {
            for (PersonRecord p : r.people) body.addView(personRow(p, r));
        }

        body.addView(space(22));
        body.addView(sectionTitle("Источники", String.valueOf(r.sources.size()), null));
        int shown = 0;
        for (SourceRef s : r.sources) {
            body.addView(sourceRow(s));
            if (++shown >= 12) break;
        }
        replace(scroll(body));
    }

    private View personRow(PersonRecord p, SearchResult r) {
        LinearLayout c = card(Color.WHITE);
        c.setPadding(dp(16),dp(14),dp(16),dp(14));
        TextView role = tv(p.role, 13, MUTED, false);
        TextView name = tv(p.name, 17, TEXT, true);
        TextView st = tv((p.confirmed() ? "✓ Подтверждено" : "◷ Найдено в источнике") + "  •  " + p.sources.size() + " ист.", 12, p.confirmed()?GREEN:BLUE, true);
        c.addView(role); c.addView(space(3)); c.addView(name); c.addView(space(5)); c.addView(st);
        c.setOnClickListener(v -> showPerson(p, r));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(10)); c.setLayoutParams(lp);
        return c;
    }

    private void showPerson(PersonRecord p, SearchResult r) {
        setTab("search");
        LinearLayout body = page();
        body.addView(backHeader("Карточка лица", r.orgName, () -> showResult(r)));
        body.addView(space(12));
        LinearLayout hero = card(Color.WHITE);
        TextView avatar = tv("○", 46, BLUE, true); avatar.setGravity(Gravity.CENTER);
        hero.addView(avatar, new LinearLayout.LayoutParams(-1, dp(62)));
        TextView name = tv(p.name, 24, TEXT, true); name.setGravity(Gravity.CENTER); hero.addView(name);
        TextView role = tv(p.role, 15, MUTED, false); role.setGravity(Gravity.CENTER); hero.addView(role);
        body.addView(hero);
        body.addView(space(12));
        LinearLayout info = card(Color.WHITE);
        info.addView(infoRow("Статус", p.confirmed() ? "Подтверждено" : "Требует проверки")); info.addView(divider());
        info.addView(infoRow("Источников", String.valueOf(p.sources.size()))); info.addView(divider());
        info.addView(infoRow("ИНН организации", r.inn));
        body.addView(info);
        body.addView(space(18));
        body.addView(sectionTitle("Источники", String.valueOf(p.sources.size()), null));
        for (SourceRef s : p.sources) body.addView(sourceRow(s));
        body.addView(space(10));
        LinearLayout note = card(LIGHT_BLUE);
        note.addView(tv("Примечание", 16, TEXT, true));
        note.addView(space(5));
        note.addView(tv("Карточка собрана из открытых источников. Статус «Подтверждено» означает совпадение ФИО и должности минимум на двух независимых доменах, но не заменяет официальную кадровую справку.", 13, MUTED, false));
        body.addView(note);
        replace(scroll(body));
    }

    private View sourceRow(SourceRef s) {
        LinearLayout c = card(Color.WHITE);
        c.setPadding(dp(14),dp(12),dp(14),dp(12));
        TextView title = tv(s.title.isBlank()?s.domain():s.title, 15, TEXT, true);
        title.setMaxLines(2); c.addView(title);
        if (!s.snippet.isBlank()) { TextView sn = tv(s.snippet, 12, MUTED, false); sn.setMaxLines(3); c.addView(space(3)); c.addView(sn); }
        if (!s.domain().isBlank()) { c.addView(space(4)); c.addView(tv("↗ " + s.domain(), 12, BLUE, true)); }
        c.setOnClickListener(v -> openUrl(s.url));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(9)); c.setLayoutParams(lp);
        return c;
    }

    private void showHistory() {
        setTab("history");
        LinearLayout body = page();
        body.addView(header("◷", "История", "Недавние поиски"));
        body.addView(space(20));
        List<HistoryItem> items = getHistory();
        body.addView(sectionTitle("Запросы", items.size() + "", null));
        if (items.isEmpty()) body.addView(emptyCard("История пока пуста", "После первого поиска запрос появится здесь."));
        for (HistoryItem h : items) body.addView(historyRow(h));
        if (!items.isEmpty()) {
            body.addView(space(12));
            Button clear = secondaryButton("Очистить историю");
            clear.setOnClickListener(v -> { prefs.edit().remove("history").apply(); showHistory(); });
            body.addView(clear, new LinearLayout.LayoutParams(-1,dp(48)));
        }
        replace(scroll(body));
    }

    private View historyRow(HistoryItem h) {
        LinearLayout c = card(Color.WHITE);
        c.addView(tv(h.org, 17, TEXT, true));
        c.addView(tv("ИНН " + h.inn, 13, MUTED, false));
        c.addView(space(5));
        c.addView(tv(new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date(h.time)), 12, MUTED, false));
        c.setOnClickListener(v -> beginSearch(h.inn));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.setMargins(0,0,0,dp(10)); c.setLayoutParams(lp);
        return c;
    }

    private void showFavorites() {
        setTab("favorites");
        LinearLayout body = page();
        body.addView(header("☆", "Избранное", "Сохранённые организации"));
        body.addView(space(20));
        Map<String,String> favs = getFavorites();
        if (favs.isEmpty()) body.addView(emptyCard("Избранное пусто", "Добавьте организацию из результатов поиска."));
        for (Map.Entry<String,String> e : favs.entrySet()) {
            HistoryItem h = new HistoryItem(e.getKey(), e.getValue(), System.currentTimeMillis());
            body.addView(historyRow(h));
        }
        replace(scroll(body));
    }

    private void showProfile() {
        setTab("profile");
        LinearLayout body = page();
        body.addView(header("○", "OrgStaff Mobile", "Профиль приложения"));
        body.addView(space(20));
        LinearLayout c = card(Color.WHITE);
        c.addView(tv("Версия", 13, MUTED, false)); c.addView(tv("1.0.0", 18, TEXT, true));
        c.addView(space(14)); c.addView(divider()); c.addView(space(14));
        c.addView(tv("Назначение", 13, MUTED, false));
        c.addView(tv("Поиск текущего руководства организаций по ИНН в общедоступных веб-источниках.", 15, TEXT, false));
        body.addView(c);
        body.addView(space(14));
        LinearLayout privacy = card(LIGHT_BLUE);
        privacy.addView(tv("Как обрабатываются данные", 16, TEXT, true));
        privacy.addView(space(6));
        privacy.addView(tv("ИНН используется только для формирования поисковых запросов. Приложение не требует регистрации и не запрашивает контакты, геолокацию или доступ к файлам.", 13, MUTED, false));
        body.addView(privacy);
        replace(scroll(body));
    }

    private View header(String icon, String title, String sub) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = tv(icon, 30, BLUE, true); logo.setGravity(Gravity.CENTER); logo.setBackground(rounded(LIGHT_BLUE, Color.TRANSPARENT, 16,0));
        row.addView(logo, new LinearLayout.LayoutParams(dp(62),dp(62)));
        LinearLayout tx = new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL); tx.setPadding(dp(12),0,0,0);
        tx.addView(tv(title, 25, TEXT, true)); tx.addView(tv(sub, 14, MUTED, false));
        row.addView(tx, new LinearLayout.LayoutParams(0,-2,1f)); return row;
    }

    private View backHeader(String title, String sub, Runnable back) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView b = tv("‹", 44, MUTED, false); b.setGravity(Gravity.CENTER); b.setOnClickListener(v -> back.run());
        row.addView(b, new LinearLayout.LayoutParams(dp(42),dp(58)));
        LinearLayout tx = new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL); tx.setPadding(dp(8),0,0,0);
        TextView t = tv(title, 24, TEXT, true); t.setMaxLines(2); tx.addView(t); if (sub != null) tx.addView(tv(sub, 14, MUTED, false));
        row.addView(tx,new LinearLayout.LayoutParams(0,-2,1f)); return row;
    }

    private View sectionTitle(String left, String right, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(2),dp(4),dp(2),dp(10));
        r.addView(tv(left, 21, TEXT, true), new LinearLayout.LayoutParams(0,-2,1f));
        TextView x = tv(right == null ? "" : right, 13, BLUE, true); if (click != null) x.setOnClickListener(click); r.addView(x); return r;
    }

    private View infoRow(String k, String v) {
        LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0,dp(7),0,dp(7));
        r.addView(tv(k, 13, MUTED, false), new LinearLayout.LayoutParams(0,-2,1f));
        TextView val = tv(v, 14, TEXT, true); val.setGravity(Gravity.END); val.setMaxLines(2); r.addView(val,new LinearLayout.LayoutParams(0,-2,1.35f)); return r;
    }

    private TextView chip(String s, int bg, int fg) {
        TextView t = tv(s, 12, fg, true); t.setGravity(Gravity.CENTER); t.setPadding(dp(12),dp(8),dp(12),dp(8)); t.setBackground(rounded(bg,Color.TRANSPARENT,20,0)); return t;
    }

    private LinearLayout card(int color) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(18),dp(17),dp(18),dp(17)); l.setBackground(rounded(color, BORDER, 20, 1)); return l;
    }

    private View emptyCard(String title, String text) {
        LinearLayout c = card(Color.WHITE); c.addView(tv(title,17,TEXT,true)); c.addView(space(5)); c.addView(tv(text,13,MUTED,false)); return c;
    }

    private GradientDrawable rounded(int fill, int stroke, int radius, int sw) {
        GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); if (sw>0) g.setStroke(dp(sw), stroke); return g;
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setLineSpacing(0,1.14f); return t;
    }

    private Button primaryButton(String s) {
        Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(18); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackground(rounded(BLUE,Color.TRANSPARENT,16,0)); return b;
    }
    private Button secondaryButton(String s) {
        Button b = new Button(this); b.setText(s); b.setTextColor(BLUE); b.setTextSize(14); b.setAllCaps(false); b.setBackground(rounded(Color.WHITE, Color.rgb(190,215,246),14,1)); return b;
    }

    private View divider() { View v = new View(this); v.setBackgroundColor(BORDER); v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1))); return v; }
    private View space(int d) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(1,dp(d))); return s; }
    private View spaceH(int d) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(d),1)); return s; }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE); if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(),0);
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { Toast.makeText(this,"Не удалось открыть источник",Toast.LENGTH_SHORT).show(); }
    }

    private void addRecentRows(LinearLayout body, int max) {
        List<HistoryItem> h = getHistory();
        if (h.isEmpty()) { body.addView(emptyCard("Пока пусто", "Здесь появятся последние организации.")); return; }
        for (int i=0;i<Math.min(max,h.size());i++) body.addView(historyRow(h.get(i)));
    }

    private static class HistoryItem {
        String inn, org; long time;
        HistoryItem(String i,String o,long t){inn=i;org=o;time=t;}
    }

    private void saveHistory(SearchResult r) {
        try {
            JSONArray old = new JSONArray(prefs.getString("history","[]"));
            JSONArray next = new JSONArray();
            JSONObject current = new JSONObject(); current.put("inn",r.inn); current.put("org",r.orgName); current.put("time",System.currentTimeMillis()); next.put(current);
            for (int i=0;i<old.length() && next.length()<20;i++) { JSONObject o=old.optJSONObject(i); if(o!=null && !r.inn.equals(o.optString("inn"))) next.put(o); }
            prefs.edit().putString("history",next.toString()).apply();
        } catch(Exception ignored){}
    }

    private List<HistoryItem> getHistory() {
        List<HistoryItem> out = new ArrayList<>();
        try { JSONArray a=new JSONArray(prefs.getString("history","[]")); for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i); if(o!=null) out.add(new HistoryItem(o.optString("inn"),o.optString("org","Организация"),o.optLong("time")));} } catch(Exception ignored){}
        return out;
    }

    private Map<String,String> getFavorites() {
        LinkedHashMap<String,String> out=new LinkedHashMap<>();
        try { JSONObject o=new JSONObject(prefs.getString("favorites","{}")); Iterator<String> it=o.keys(); while(it.hasNext()){String k=it.next();out.put(k,o.optString(k,"Организация"));} } catch(Exception ignored){}
        return out;
    }
    private boolean isFavorite(String inn){ return getFavorites().containsKey(inn); }
    private void toggleFavorite(String inn,String org){
        try { JSONObject o=new JSONObject(prefs.getString("favorites","{}")); if(o.has(inn))o.remove(inn); else o.put(inn,org); prefs.edit().putString("favorites",o.toString()).apply(); } catch(Exception ignored){}
    }
}
