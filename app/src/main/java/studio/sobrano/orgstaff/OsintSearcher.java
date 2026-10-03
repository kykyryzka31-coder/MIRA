package studio.sobrano.orgstaff;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.parser.Parser;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

final class OsintSearcher {
    interface Progress { void onProgress(String text); }

    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36";
    private static final Pattern FULL_FIO = Pattern.compile("([А-ЯЁ][а-яё-]{1,35}\\s+[А-ЯЁ][а-яё-]{1,35}\\s+[А-ЯЁ][а-яё-]{1,35})");
    private static final Pattern INITIAL_FIO = Pattern.compile("([А-ЯЁ][а-яё-]{1,35}\\s+[А-ЯЁ]\\.\\s?[А-ЯЁ]\\.)");

    private static final LinkedHashMap<String,String[]> ROLES = new LinkedHashMap<>();
    static {
        ROLES.put("Генеральный директор", new String[]{"генеральный директор", "гендиректор"});
        ROLES.put("Директор", new String[]{"директор"});
        ROLES.put("Ректор", new String[]{"ректор"});
        ROLES.put("Заместитель генерального директора", new String[]{"заместитель генерального директора", "заместитель гендиректора"});
        ROLES.put("Заместитель директора", new String[]{"заместитель директора"});
        ROLES.put("Проректор", new String[]{"проректор"});
        ROLES.put("Главный бухгалтер", new String[]{"главный бухгалтер"});
        ROLES.put("Главный инженер", new String[]{"главный инженер"});
        ROLES.put("Главный конструктор", new String[]{"главный конструктор"});
    }

    static SearchResult search(String inn, Progress progress) {
        SearchResult out = new SearchResult();
        out.inn = inn;
        try {
            if (progress != null) progress.onProgress("Определяю организацию…");
            List<SourceRef> general = webSearch(inn + " ИНН организация руководство", 10);
            out.sources.addAll(general);
            out.orgName = inferOrgName(general, inn);

            ExecutorService pool = Executors.newFixedThreadPool(4);
            List<Future<RoleResults>> futures = new ArrayList<>();
            for (Map.Entry<String,String[]> e : ROLES.entrySet()) {
                final String role = e.getKey();
                final String phrase = e.getValue()[0];
                futures.add(pool.submit(() -> new RoleResults(role, webSearch(inn + " \"" + phrase + "\"", 8))));
            }
            pool.shutdown();

            int completed = 0;
            Map<String,PersonRecord> merged = new LinkedHashMap<>();
            for (Future<RoleResults> f : futures) {
                RoleResults rr;
                try { rr = f.get(18, TimeUnit.SECONDS); }
                catch (Exception ex) { continue; }
                completed++;
                if (progress != null) progress.onProgress("Проверяю руководство: " + completed + "/" + ROLES.size());
                for (SourceRef s : rr.sources) {
                    if (!containsSource(out.sources, s.url)) out.sources.add(s);
                    String candidate = extractNameNearRole(rr.role, s.title + " " + s.snippet);
                    if (candidate == null) continue;
                    String key = rr.role + "|" + candidate.toLowerCase(Locale.ROOT);
                    PersonRecord p = merged.computeIfAbsent(key, k -> new PersonRecord(rr.role, candidate));
                    if (!containsSource(p.sources, s.url)) p.sources.add(s);
                }
            }
            out.people.addAll(merged.values());
            out.people.sort((a,b) -> {
                int c = Boolean.compare(b.confirmed(), a.confirmed());
                if (c != 0) return c;
                return a.role.compareTo(b.role);
            });
            if (out.orgName == null || out.orgName.isBlank()) out.orgName = "Организация по ИНН " + inn;
            if (out.sources.isEmpty()) out.error = "Поисковик не вернул результаты. Проверьте интернет или повторите запрос позже.";
        } catch (Exception e) {
            out.orgName = "Организация по ИНН " + inn;
            out.error = "Не удалось завершить поиск: " + safe(e.getMessage());
        }
        return out;
    }

    private static class RoleResults {
        final String role; final List<SourceRef> sources;
        RoleResults(String role, List<SourceRef> sources) { this.role = role; this.sources = sources; }
    }

    static List<SourceRef> webSearch(String query, int limit) {
        LinkedHashMap<String,SourceRef> merged = new LinkedHashMap<>();

        addAllUnique(merged, bingRss(query, limit));
        if (merged.size() < Math.min(4, limit)) addAllUnique(merged, ddgLite(query, limit));
        if (merged.size() < Math.min(4, limit)) addAllUnique(merged, ddgHtml(query, limit));

        List<SourceRef> out = new ArrayList<>(merged.values());
        if (out.size() > limit) return new ArrayList<>(out.subList(0, limit));
        return out;
    }

    private static void addAllUnique(Map<String,SourceRef> map, List<SourceRef> items) {
        for (SourceRef s : items) {
            if (s.url == null || s.url.isBlank()) continue;
            String key = canonicalUrlKey(s.url);
            if (!key.isBlank() && !map.containsKey(key)) map.put(key, s);
        }
    }

    private static String canonicalUrlKey(String url) {
        try {
            URL u = new URL(url);
            String host = u.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
            String path = u.getPath() == null ? "" : u.getPath().replaceAll("/+$", "");
            return host + path;
        } catch (Exception e) {
            return url == null ? "" : url.trim();
        }
    }

    private static List<SourceRef> bingRss(String query, int limit) {
        List<SourceRef> out = new ArrayList<>();
        try {
            String url = "https://www.bing.com/search?format=rss&setlang=ru-RU&q=" + URLEncoder.encode(query, "UTF-8");
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
                    .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
                    .parser(Parser.xmlParser())
                    .timeout(12000)
                    .get();
            for (Element item : doc.select("item")) {
                String title = item.selectFirst("title") == null ? "" : item.selectFirst("title").text();
                String link = item.selectFirst("link") == null ? "" : item.selectFirst("link").text();
                String desc = item.selectFirst("description") == null ? "" : item.selectFirst("description").text();
                if (!link.startsWith("http")) continue;
                out.add(new SourceRef(title, link, Jsoup.parse(desc).text()));
                if (out.size() >= limit) break;
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static List<SourceRef> ddgLite(String query, int limit) {
        List<SourceRef> out = new ArrayList<>();
        try {
            String url = "https://lite.duckduckgo.com/lite/?q=" + URLEncoder.encode(query, "UTF-8");
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
                    .timeout(12000)
                    .get();

            Elements links = doc.select("a.result-link, a[href]");
            for (Element a : links) {
                String raw = a.attr("href");
                String href = normalizeDdgUrl(raw);
                String title = a.text().trim();
                if (href.isBlank() || title.length() < 3) continue;
                if (href.contains("duckduckgo.com") && !raw.contains("uddg=")) continue;

                Element row = a.closest("tr");
                String snippet = "";
                if (row != null) {
                    Element next = row.nextElementSibling();
                    if (next != null) snippet = next.text();
                }
                out.add(new SourceRef(title, href, snippet));
                if (out.size() >= limit) break;
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static List<SourceRef> ddgHtml(String query, int limit) {
        List<SourceRef> out = new ArrayList<>();
        try {
            String url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8");
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
                    .timeout(12000)
                    .get();
            Elements results = doc.select(".result");
            for (Element r : results) {
                Element a = r.selectFirst("a.result__a");
                if (a == null) continue;
                Element sn = r.selectFirst(".result__snippet");
                String href = normalizeDdgUrl(a.attr("href"));
                if (href.isBlank()) continue;
                out.add(new SourceRef(a.text(), href, sn == null ? "" : sn.text()));
                if (out.size() >= limit) break;
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static String normalizeDdgUrl(String href) {
        try {
            if (href.startsWith("//")) href = "https:" + href;
            URL u = new URL(href);
            if (u.getHost().contains("duckduckgo.com") && u.getPath().contains("/l/")) {
                String q = u.getQuery();
                if (q != null) for (String p : q.split("&")) {
                    int i = p.indexOf('=');
                    if (i > 0 && p.substring(0,i).equals("uddg")) return URLDecoder.decode(p.substring(i+1), "UTF-8");
                }
            }
            return href.startsWith("http") ? href : "";
        } catch (Exception e) { return ""; }
    }

    static String extractNameNearRole(String role, String text) {
        if (text == null) return null;
        String normalized = text.replace('\u00A0', ' ').replaceAll("\\s+", " ");
        Matcher m = FULL_FIO.matcher(normalized);
        List<String> candidates = new ArrayList<>();
        while (m.find()) candidates.add(m.group(1));
        Matcher mi = INITIAL_FIO.matcher(normalized);
        while (mi.find()) candidates.add(mi.group(1));
        if (candidates.isEmpty()) return null;

        String lower = normalized.toLowerCase(Locale.ROOT);
        String[] roleTerms = ROLES.getOrDefault(role, new String[]{role.toLowerCase(Locale.ROOT)});
        int rolePos = -1;
        for (String t : roleTerms) {
            int p = lower.indexOf(t.toLowerCase(Locale.ROOT));
            if (p >= 0 && (rolePos < 0 || p < rolePos)) rolePos = p;
        }
        String best = null; int bestDist = Integer.MAX_VALUE;
        for (String c : candidates) {
            if (looksLikeOrg(c)) continue;
            int p = normalized.indexOf(c);
            int d = rolePos < 0 ? p : Math.abs(p - rolePos);
            if (d < bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    private static boolean looksLikeOrg(String c) {
        String x = c.toLowerCase(Locale.ROOT);
        String[] bad = {"российская федерация", "московская область", "санкт петербург", "федеральная налоговая", "единый государственный", "общество с ограниченной", "публичное акционерное"};
        for (String b : bad) if (x.contains(b)) return true;
        return false;
    }

    private static String inferOrgName(List<SourceRef> sources, String inn) {
        for (SourceRef s : sources) {
            String t = s.title.replaceAll("(?i)\\s*[|—-]\\s*.*$", "").trim();
            t = t.replaceAll("(?i)\\bИНН\\s*" + Pattern.quote(inn) + "\\b", "").trim();
            t = t.replaceAll("(?i)\\bреквизиты\\b.*$", "").trim();
            if (t.length() >= 4 && t.length() <= 120 && !t.toLowerCase(Locale.ROOT).contains("поиск")) return t;
        }
        return null;
    }

    private static boolean containsSource(List<SourceRef> list, String url) {
        if (url == null || url.isBlank()) return false;
        for (SourceRef s : list) if (url.equals(s.url)) return true;
        return false;
    }

    private static String safe(String s) { return s == null || s.isBlank() ? "неизвестная ошибка" : s; }
}
