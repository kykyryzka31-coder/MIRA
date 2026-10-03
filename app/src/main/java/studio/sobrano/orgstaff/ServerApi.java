package studio.sobrano.orgstaff;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

final class ServerApi {
    private static final String ENDPOINT = "https://tgstock.ru/api/orgstaff/search?inn=";

    static SearchResult search(String inn, OsintSearcher.Progress progress) {
        SearchResult out = new SearchResult();
        out.inn = inn;
        HttpURLConnection conn = null;
        try {
            if (progress != null) progress.onProgress("Подключаюсь к серверу поиска…");
            URL url = new URL(ENDPOINT + URLEncoder.encode(inn, "UTF-8"));
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(120000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "OrgStaff-Mobile/1.1.0 Android");

            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(stream);
            JSONObject root = new JSONObject(body);

            if (status < 200 || status >= 300 || !root.optBoolean("ok", false)) {
                out.orgName = "Организация по ИНН " + inn;
                out.error = root.optString("error", "Сервер поиска вернул ошибку HTTP " + status);
                return out;
            }

            if (progress != null) progress.onProgress("Обрабатываю найденные источники…");
            JSONObject org = root.optJSONObject("organization");
            out.orgName = org == null ? null : org.optString("name", null);

            JSONArray people = root.optJSONArray("people");
            if (people != null) {
                for (int i = 0; i < people.length(); i++) {
                    JSONObject p = people.optJSONObject(i);
                    if (p == null) continue;
                    String role = p.optString("role", "").trim();
                    String name = p.optString("name", "").trim();
                    if (role.isEmpty() || name.isEmpty()) continue;
                    PersonRecord person = new PersonRecord(role, name);
                    if (p.has("confirmed")) {
                        person.setServerStatus(
                                p.optBoolean("confirmed", false),
                                p.optString("confidence", "")
                        );
                    }
                    addSources(person.sources, p.optJSONArray("sources"));
                    out.people.add(person);
                }
            }

            addSources(out.sources, root.optJSONArray("sources"));
            if (out.orgName == null || out.orgName.isBlank()) {
                out.orgName = "Организация по ИНН " + inn;
            }
            return out;
        } catch (SocketTimeoutException e) {
            out.orgName = "Организация по ИНН " + inn;
            out.error = "Сервер поиска не успел ответить. Повторите запрос.";
            return out;
        } catch (Exception e) {
            out.orgName = "Организация по ИНН " + inn;
            out.error = "Не удалось связаться с сервером поиска: " +
                    (e.getMessage() == null ? "неизвестная ошибка" : e.getMessage());
            return out;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void addSources(java.util.List<SourceRef> target, JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s == null) continue;
            String url = s.optString("url", "").trim();
            if (url.isEmpty()) continue;
            boolean exists = false;
            for (SourceRef old : target) if (url.equals(old.url)) { exists = true; break; }
            if (!exists) target.add(new SourceRef(
                    s.optString("title", "Источник"),
                    url,
                    s.optString("snippet", "")
            ));
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
