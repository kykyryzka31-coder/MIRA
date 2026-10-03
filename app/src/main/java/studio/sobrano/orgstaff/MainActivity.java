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
    private String activePersonRequest = "";

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
        tips.addView(tv("• Введите ИНН организации\n• Приложение ищет упоминания должностных лиц в открытых веб-источниках\n• Подтверждение ставится по официальному ЕГРЮЛ ФНС либо нескольким независимым источникам\n• Каждый найденный результат можно открыть вместе с источником", 14, MUTED, false));
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
            SearchResult result = ServerApi.search(inn, text -> runOnUiThread(() -> status.setText(text)));
            saveHistory(result);
            runOnUiThread(() -> showResult(result));
        });
    }

    private void showResult(SearchResult r) {
        activePersonRequest = "";
        setTab("search");
        LinearLayout body = page();
        body.addView(backHeader(r.orgName == null ? "Результат" : r.orgName, "ИНН " + r.inn, this::showSearch));
        body.addView(space(12));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip("Открытые данные", LIGHT_GREEN, GREEN));
        chips.addView(spaceH(8));
        chips.addView(chip(r.people.size() + " подтвержд.", LIGHT_BLUE, BLUE));
        body.addView(chips);
        body.addView(space(14));

        Button fav = secondaryButton(isFavorite(r.inn) ? "★  В избранном" : "☆  Добавить в избранное");
        fav.setOnClickListener(v -> { toggleFavorite(r.inn, r.orgName); showResult(r); });
        body.addView(fav, new LinearLayout.LayoutParams(-1, dp(50)));

        if (r.error != null) {
            body.addView(space(12));
            LinearLayout warn = card(Color.rgb(255,248,232));
            warn.addView(tv("Данные получены не полностью", 16, Color.rgb(138,83,0), true));
            warn.addView(space(4));
            warn.addView(tv(r.error, 13, Color.rgb(138,83,0), false));
            body.addView(warn);
        }

        body.addView(space(16));

        LinearLayout orgContent = new LinearLayout(this);
        orgContent.setOrientation(LinearLayout.VERTICAL);
        addOrganizationInfo(orgContent, r);
        body.addView(expandableSection("Об организации", organizationSummary(r), false, orgContent));
        body.addView(space(12));

        LinearLayout leadershipContent = new LinearLayout(this);
        leadershipContent.setOrientation(LinearLayout.VERTICAL);
        renderPeople(leadershipContent, r.people, r, "Официально найденное руководство пока не определено.");
        leadershipContent.addView(space(8));
        Button moreLeadership = secondaryButton("Найти расширенное руководство");
        leadershipContent.addView(moreLeadership, new LinearLayout.LayoutParams(-1, dp(48)));
        moreLeadership.setOnClickListener(v -> {
            moreLeadership.setEnabled(false);
            moreLeadership.setText("Ищу руководство…");
            worker.submit(() -> {
                SearchResult loaded = ServerApi.loadManagement(r.inn);
                runOnUiThread(() -> {
                    if (loaded.error != null) {
                        moreLeadership.setEnabled(true);
                        moreLeadership.setText("Повторить расширенный поиск");
                        Toast.makeText(this, loaded.error, Toast.LENGTH_LONG).show();
                        return;
                    }
                    mergeSourcesInto(r.sources, loaded.sources);
                    List<PersonRecord> merged = mergePeople(r.people, loaded.people);
                    r.people.clear();
                    r.people.addAll(merged);
                    leadershipContent.removeAllViews();
                    renderPeople(leadershipContent, r.people, r, "Расширенное руководство не найдено.");
                    TextView done = tv("✓ Расширенный поиск завершён", 12, GREEN, true);
                    leadershipContent.addView(done);
                });
            });
        });
        body.addView(expandableSection("Руководство", r.people.size() + " найдено", false, leadershipContent));
        body.addView(space(12));

        LinearLayout employeesContent = new LinearLayout(this);
        employeesContent.setOrientation(LinearLayout.VERTICAL);
        employeesContent.addView(tv("Список загружается отдельно, чтобы основной поиск оставался быстрым. Здесь показываются только сотрудники, которых удалось публично связать именно с этим юрлицом.", 13, MUTED, false));
        employeesContent.addView(space(10));
        Button loadEmployees = secondaryButton("Загрузить публичных сотрудников");
        employeesContent.addView(loadEmployees, new LinearLayout.LayoutParams(-1, dp(48)));
        loadEmployees.setOnClickListener(v -> {
            loadEmployees.setEnabled(false);
            loadEmployees.setText("Ищу сотрудников…");
            worker.submit(() -> {
                SearchResult loaded = ServerApi.loadEmployees(r.inn);
                runOnUiThread(() -> {
                    employeesContent.removeAllViews();
                    if (loaded.error != null) {
                        employeesContent.addView(emptyCard("Не удалось загрузить список", loaded.error));
                        Button retry = secondaryButton("Повторить");
                        retry.setOnClickListener(x -> showResult(r));
                        employeesContent.addView(space(8));
                        employeesContent.addView(retry, new LinearLayout.LayoutParams(-1, dp(46)));
                        return;
                    }
                    mergeSourcesInto(r.sources, loaded.sources);
                    List<PersonRecord> employees = new ArrayList<>();
                    Set<String> leadershipNames = new HashSet<>();
                    for (PersonRecord p : r.people) leadershipNames.add(p.name.toLowerCase(Locale.ROOT));
                    for (PersonRecord p : loaded.people) {
                        if (!leadershipNames.contains(p.name.toLowerCase(Locale.ROOT))) employees.add(p);
                    }
                    if (employees.isEmpty()) {
                        employeesContent.addView(emptyCard(
                                "Дополнительные сотрудники пока не найдены",
                                "Это не означает, что других сотрудников нет — открытого полного штатного списка у организации может не быть."
                        ));
                    } else {
                        employeesContent.addView(tv("Публично найдено: " + employees.size(), 14, MUTED, true));
                        employeesContent.addView(space(8));
                        renderPeople(employeesContent, employees, r, "");
                    }
                });
            });
        });
        body.addView(expandableSection("Публично найденные сотрудники", "нажмите, чтобы открыть", false, employeesContent));
        body.addView(space(12));

        LinearLayout sourcesContent = new LinearLayout(this);
        sourcesContent.setOrientation(LinearLayout.VERTICAL);
        renderSources(sourcesContent, r.sources, 30);
        body.addView(expandableSection("Источники", r.sources.size() + " доступно", false, sourcesContent));

        body.addView(space(14));
        LinearLayout note = card(LIGHT_BLUE);
        note.addView(tv("Как читать результаты", 15, TEXT, true));
        note.addView(space(5));
        note.addView(tv("Приложение показывает только то, что удалось подтвердить в открытых источниках. Отсутствие человека в списке не означает, что он не работает в организации.", 12, MUTED, false));
        body.addView(note);
        replace(scroll(body));
    }

    private String organizationSummary(SearchResult r) {
        ArrayList<String> parts = new ArrayList<>();
        if (!r.organization.region.isBlank()) parts.add(r.organization.region);
        if (!r.organization.age.isBlank()) parts.add(r.organization.age);
        if (!r.organization.staffCount.isBlank()) parts.add("штат " + r.organization.staffCount);
        return parts.isEmpty() ? "реквизиты и открытые сведения" : String.join(" • ", parts);
    }

    private void addOrganizationInfo(LinearLayout box, SearchResult r) {
        OrganizationInfo o = r.organization;
        if (!o.legalName.isBlank()) { box.addView(infoRow("Полное название", o.legalName)); box.addView(divider()); }
        box.addView(infoRow("ИНН", r.inn));
        if (!o.kpp.isBlank()) { box.addView(divider()); box.addView(infoRow("КПП", o.kpp)); }
        if (!o.ogrn.isBlank()) { box.addView(divider()); box.addView(infoRow("ОГРН", o.ogrn)); }
        if (!o.region.isBlank()) { box.addView(divider()); box.addView(infoRow("Регион", o.region)); }
        if (!o.registrationDate.isBlank()) { box.addView(divider()); box.addView(infoRow("Регистрация", o.registrationDate)); }
        if (!o.age.isBlank()) { box.addView(divider()); box.addView(infoRow("Возраст организации", o.age)); }
        if (!o.staffCount.isBlank()) { box.addView(divider()); box.addView(infoRow("Численность", o.staffCount)); }
        if (!o.capital.isBlank()) { box.addView(divider()); box.addView(infoRow("Уставной капитал", o.capital)); }
        if (!o.okvedCode.isBlank() || !o.okvedText.isBlank()) {
            box.addView(divider());
            box.addView(infoRow("Основной ОКВЭД", (o.okvedCode + " " + o.okvedText).trim()));
        }
        if (!o.legalAddress.isBlank()) { box.addView(divider()); box.addView(infoRow("Юридический адрес", o.legalAddress)); }
        if (!o.website.isBlank()) { box.addView(divider()); box.addView(infoRow("Сайт", o.website)); }

        boolean needsMore = o.legalAddress.isBlank() && o.website.isBlank() &&
                o.staffCount.isBlank() && o.capital.isBlank() && o.okvedCode.isBlank();
        if (needsMore) {
            box.addView(space(10));
            TextView hint = tv("Расширенные реквизиты загружаются только по запросу.", 12, MUTED, false);
            box.addView(hint);
            box.addView(space(8));
            Button details = secondaryButton("Загрузить подробности");
            box.addView(details, new LinearLayout.LayoutParams(-1, dp(46)));
            details.setOnClickListener(v -> {
                details.setEnabled(false);
                details.setText("Загружаю…");
                worker.submit(() -> {
                    SearchResult loaded = ServerApi.loadDetails(r.inn);
                    runOnUiThread(() -> {
                        if (loaded.error != null) {
                            details.setEnabled(true);
                            details.setText("Повторить загрузку");
                            Toast.makeText(this, loaded.error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        boolean gotExtra =
                                !loaded.organization.legalAddress.isBlank() ||
                                !loaded.organization.website.isBlank() ||
                                !loaded.organization.staffCount.isBlank() ||
                                !loaded.organization.capital.isBlank() ||
                                !loaded.organization.okvedCode.isBlank() ||
                                !loaded.organization.createdDate.isBlank();
                        r.organization.mergeFrom(loaded.organization);
                        mergeSourcesInto(r.sources, loaded.sources);
                        if (!gotExtra) {
                            details.setText("Дополнительных сведений не найдено");
                            details.setEnabled(false);
                            hint.setText("Основные реквизиты выше подтверждены. Дополнительные открытые сведения сейчас недоступны.");
                            return;
                        }
                        box.removeAllViews();
                        addOrganizationInfo(box, r);
                    });
                });
            });
        }
    }

    private View expandableSection(String title, String subtitle, boolean open, LinearLayout inner) {
        LinearLayout outer = card(Color.WHITE);
        outer.setPadding(dp(16), dp(12), dp(16), dp(14));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.addView(tv(title, 20, TEXT, true));
        if (subtitle != null && !subtitle.isBlank()) textBox.addView(tv(subtitle, 12, MUTED, false));
        head.addView(textBox, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = tv(open ? "▴" : "▾", 24, BLUE, true);
        arrow.setGravity(Gravity.CENTER);
        head.addView(arrow, new LinearLayout.LayoutParams(dp(42), dp(42)));

        inner.setVisibility(open ? View.VISIBLE : View.GONE);
        outer.addView(head);
        outer.addView(inner);
        head.setOnClickListener(v -> {
            boolean show = inner.getVisibility() != View.VISIBLE;
            inner.setVisibility(show ? View.VISIBLE : View.GONE);
            arrow.setText(show ? "▴" : "▾");
        });
        return outer;
    }

    private void renderPeople(LinearLayout box, List<PersonRecord> people, SearchResult r, String emptyText) {
        if (people.isEmpty()) {
            if (emptyText != null && !emptyText.isBlank()) box.addView(emptyCard("Нет подтверждённых данных", emptyText));
            return;
        }
        for (PersonRecord p : people) box.addView(personRow(p, r));
    }

    private List<PersonRecord> mergePeople(List<PersonRecord> first, List<PersonRecord> second) {
        LinkedHashMap<String,PersonRecord> map = new LinkedHashMap<>();
        for (PersonRecord p : first) map.put(p.name.toLowerCase(Locale.ROOT), p);
        for (PersonRecord p : second) {
            String key = p.name.toLowerCase(Locale.ROOT);
            if (!map.containsKey(key)) {
                map.put(key, p);
            } else {
                PersonRecord old = map.get(key);
                for (SourceRef s : p.sources) {
                    boolean exists = false;
                    for (SourceRef x : old.sources) if (x.url.equals(s.url)) { exists = true; break; }
                    if (!exists) old.sources.add(s);
                }
            }
        }
        return new ArrayList<>(map.values());
    }

    private void mergeSourcesInto(List<SourceRef> target, List<SourceRef> add) {
        for (SourceRef s : add) {
            boolean exists = false;
            for (SourceRef x : target) if (x.url.equals(s.url)) { exists = true; break; }
            if (!exists) target.add(s);
        }
    }

    private void renderSources(LinearLayout box, List<SourceRef> sources, int max) {
        if (sources.isEmpty()) {
            box.addView(tv("Источники появятся после расширенного поиска.", 13, MUTED, false));
            return;
        }
        int shown = 0;
        for (SourceRef s : sources) {
            box.addView(sourceRow(s));
            if (++shown >= max) break;
        }
        if (sources.size() > shown) box.addView(tv("Ещё источников: " + (sources.size() - shown), 12, MUTED, false));
    }

    private View personRow(PersonRecord p, SearchResult r) {
        LinearLayout c = card(Color.rgb(252,253,255));
        c.setPadding(dp(14),dp(12),dp(14),dp(12));
        TextView role = tv(p.role, 13, MUTED, false);
        TextView name = tv(p.name, 17, TEXT, true);
        String statusLabel = p.officialRegistry() ? "✓ ЕГРЮЛ ФНС" : (p.officialSite() ? "✓ Официальный сайт" : (p.confirmed() ? "✓ Подтверждено" : "◷ Открытый источник"));
        TextView st = tv(statusLabel + "  •  " + p.sources.size() + " ист.", 12, p.confirmed()?GREEN:BLUE, true);
        c.addView(role); c.addView(space(3)); c.addView(name); c.addView(space(5)); c.addView(st);
        c.setOnClickListener(v -> showPerson(p, r));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(8)); c.setLayoutParams(lp);
        return c;
    }

    private void showPerson(PersonRecord p, SearchResult r) {
        String requestKey = r.inn + "|" + p.name + "|" + p.role;
        activePersonRequest = requestKey;
        showPersonLoading(p, r);
        worker.submit(() -> {
            PersonProfile profile = ServerApi.loadPersonProfile(r.inn, p.name, p.role);
            runOnUiThread(() -> {
                if (requestKey.equals(activePersonRequest)) showPersonProfile(p, r, profile);
            });
        });
    }

    private void showPersonLoading(PersonRecord p, SearchResult r) {
        setTab("search");
        LinearLayout body = page();
        body.addView(backHeader("Карточка сотрудника", r.orgName, () -> { activePersonRequest = ""; showResult(r); }));
        body.addView(space(12));
        body.addView(personHero(p));
        body.addView(space(12));
        LinearLayout loading = card(Color.WHITE);
        ProgressBar pb = new ProgressBar(this);
        loading.addView(pb, new LinearLayout.LayoutParams(dp(44),dp(44)));
        loading.addView(space(8));
        loading.addView(tv("Собираю публичный профиль…", 16, TEXT, true));
        loading.addView(tv("Биография, период работы, образование и профессиональные источники подгружаются отдельно.", 12, MUTED, false));
        body.addView(loading);
        replace(scroll(body));
    }

    private View personHero(PersonRecord p) {
        LinearLayout hero = card(Color.WHITE);
        TextView avatar = tv("○", 46, BLUE, true);
        avatar.setGravity(Gravity.CENTER);
        hero.addView(avatar, new LinearLayout.LayoutParams(-1, dp(58)));
        TextView name = tv(p.name, 23, TEXT, true);
        name.setGravity(Gravity.CENTER);
        hero.addView(name);
        TextView role = tv(p.role, 14, MUTED, false);
        role.setGravity(Gravity.CENTER);
        hero.addView(role);
        return hero;
    }

    private void showPersonProfile(PersonRecord p, SearchResult r, PersonProfile profile) {
        setTab("search");
        LinearLayout body = page();
        body.addView(backHeader("Карточка сотрудника", r.orgName, () -> { activePersonRequest = ""; showResult(r); }));
        body.addView(space(12));
        body.addView(personHero(p));
        body.addView(space(12));

        LinearLayout basic = new LinearLayout(this);
        basic.setOrientation(LinearLayout.VERTICAL);
        basic.addView(infoRow("Организация", r.orgName));
        basic.addView(divider());
        basic.addView(infoRow("ИНН", r.inn));
        basic.addView(divider());
        basic.addView(infoRow("Статус", p.officialRegistry() ? "ЕГРЮЛ ФНС" : (p.confirmed() ? "Подтверждено" : "Открытый источник")));
        if (!profile.birthDate.isBlank()) { basic.addView(divider()); basic.addView(infoRow("Дата/год рождения", profile.birthDate)); }
        if (!profile.age.isBlank()) { basic.addView(divider()); basic.addView(infoRow("Возраст", profile.age + " лет")); }
        if (!profile.birthplace.isBlank()) { basic.addView(divider()); basic.addView(infoRow("Место рождения", profile.birthplace)); }
        body.addView(expandableSection("Основная информация", "публичные сведения", true, basic));
        body.addView(space(10));

        LinearLayout work = new LinearLayout(this);
        work.setOrientation(LinearLayout.VERTICAL);
        work.addView(infoRow("Должность", p.role));
        if (!profile.workSince.isBlank()) { work.addView(divider()); work.addView(infoRow("Публично подтверждено с", profile.workSince)); }
        if (!profile.tenure.isBlank()) { work.addView(divider()); work.addView(infoRow("Период", profile.tenure)); }
        if (profile.workSince.isBlank()) {
            work.addView(divider());
            work.addView(tv("Точный публично подтверждённый период пока не определён.", 12, MUTED, false));
        }
        body.addView(expandableSection("Работа в организации", profile.tenure.isBlank() ? "период уточняется" : profile.tenure, true, work));
        body.addView(space(10));

        LinearLayout education = new LinearLayout(this);
        education.setOrientation(LinearLayout.VERTICAL);
        addBulletTexts(education, profile.education, "Подтверждённых сведений об образовании пока не найдено.");
        body.addView(expandableSection("Образование", profile.education.size() + " фактов", false, education));
        body.addView(space(10));

        LinearLayout career = new LinearLayout(this);
        career.setOrientation(LinearLayout.VERTICAL);
        addBulletTexts(career, profile.career, "Подтверждённая карьерная хронология пока не собрана.");
        body.addView(expandableSection("Карьера", profile.career.size() + " фактов", false, career));
        body.addView(space(10));

        LinearLayout sources = new LinearLayout(this);
        sources.setOrientation(LinearLayout.VERTICAL);
        List<SourceRef> allSources = new ArrayList<>(p.sources);
        mergeSourcesInto(allSources, profile.sources);
        renderSources(sources, allSources, 30);
        body.addView(expandableSection("Источники", allSources.size() + " доступно", false, sources));

        body.addView(space(12));
        if (profile.error != null) {
            LinearLayout warn = card(Color.rgb(255,248,232));
            warn.addView(tv("Часть профиля не загрузилась", 15, Color.rgb(138,83,0), true));
            warn.addView(tv(profile.error, 12, Color.rgb(138,83,0), false));
            body.addView(warn);
            body.addView(space(10));
        }
        LinearLayout note = card(LIGHT_BLUE);
        note.addView(tv("Конфиденциальность", 15, TEXT, true));
        note.addView(space(5));
        note.addView(tv(profile.privacy.isBlank()
                ? "Показываются только профессиональные и биографические сведения из открытых источников. Домашние адреса и личные контакты не собираются."
                : profile.privacy, 12, MUTED, false));
        body.addView(note);
        replace(scroll(body));
    }

    private void addBulletTexts(LinearLayout box, List<String> items, String empty) {
        if (items.isEmpty()) {
            box.addView(tv(empty, 13, MUTED, false));
            return;
        }
        for (String item : items) {
            TextView t = tv("• " + item, 13, TEXT, false);
            t.setPadding(0, dp(5), 0, dp(5));
            box.addView(t);
        }
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
        c.addView(tv("Версия", 13, MUTED, false)); c.addView(tv("1.2.0", 18, TEXT, true));
        c.addView(space(14)); c.addView(divider()); c.addView(space(14));
        c.addView(tv("Назначение", 13, MUTED, false));
        c.addView(tv("Структурированный поиск организации, руководства и публично найденных сотрудников по ИНН через серверный API и открытые источники.", 15, TEXT, false));
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
        TextView val = tv(v, 14, TEXT, true); val.setGravity(Gravity.END); val.setMaxLines(7); r.addView(val,new LinearLayout.LayoutParams(0,-2,1.35f)); return r;
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
