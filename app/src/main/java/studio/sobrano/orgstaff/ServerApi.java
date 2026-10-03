package studio.sobrano.orgstaff;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class ServerApi {
    private static final String BASE = "https://tgstock.ru/api/orgstaff/";

    static SearchResult search(String inn, OsintSearcher.Progress progress) {
        if (progress != null) progress.onProgress("Проверяю ЕГРЮЛ ФНС…");
        return loadPeopleResult("summary", inn, "people", 30000);
    }

    static SearchResult loadManagement(String inn) {
        return loadPeopleResult("management", inn, "people", 120000);
    }

    static SearchResult loadEmployees(String inn) {
        return loadPeopleResult("employees", inn, "employees", 120000);
    }

    static SearchResult loadDetails(String inn) {
        SearchResult out = new SearchResult();
        out.inn = inn;
        try {
            JSONObject root = request("details", params("inn", inn), 90000);
            if (!root.optBoolean("ok", false)) {
                out.error = root.optString("error", "Не удалось загрузить сведения.");
                return out;
            }
            parseOrganization(root.optJSONObject("organization"), out);
            addSources(out.sources, root.optJSONArray("sources"));
        } catch (Exception e) {
            out.error = humanError(e);
        }
        return out;
    }

    static PersonProfile loadPersonProfile(String inn, String name, String role) {
        PersonProfile out = new PersonProfile();
        out.name = name == null ? "" : name;
        out.role = role == null ? "" : role;
        try {
            LinkedHashMap<String,String> q = new LinkedHashMap<>();
            q.put("inn", inn);
            q.put("name", out.name);
            q.put("role", out.role);
            JSONObject root = request("person", q, 120000);
            if (!root.optBoolean("ok", false)) {
                out.error = root.optString("error", "Не удалось собрать профиль.");
                return out;
            }
            JSONObject p = root.optJSONObject("person");
            if (p == null) {
                out.error = "Сервер не вернул карточку человека.";
                return out;
            }
            out.name = p.optString("name", out.name);
            out.role = p.optString("role", out.role);
            out.birthDate = p.optString("birthDate", "");
            if (p.has("age") && !p.isNull("age")) out.age = String.valueOf(p.optInt("age"));
            out.birthplace = p.optString("birthplace", "");
            out.workSince = p.optString("workSince", "");
            out.tenure = p.optString("tenure", "");
            out.privacy = p.optString("privacy", "");
            addStrings(out.education, p.optJSONArray("education"));
            addStrings(out.career, p.optJSONArray("career"));
            addSources(out.sources, p.optJSONArray("sources"));
        } catch (Exception e) {
            out.error = humanError(e);
        }
        return out;
    }

    private static SearchResult loadPeopleResult(String endpoint, String inn, String peopleKey, int timeout) {
        SearchResult out = new SearchResult();
        out.inn = inn;
        try {
            JSONObject root = request(endpoint, params("inn", inn), timeout);
            if (!root.optBoolean("ok", false)) {
                out.error = root.optString("error", "Сервер поиска вернул ошибку.");
                out.orgName = "Организация по ИНН " + inn;
                return out;
            }

            parseOrganization(root.optJSONObject("organization"), out);
            JSONArray people = root.optJSONArray(peopleKey);
            if (people != null) {
                for (int i = 0; i < people.length(); i++) {
                    JSONObject p = people.optJSONObject(i);
                    if (p == null) continue;
                    String role = p.optString("role", "").trim();
                    String name = p.optString("name", "").trim();
                    if (role.isEmpty() || name.isEmpty()) continue;
                    PersonRecord person = new PersonRecord(role, name);
                    if (p.has("confirmed")) {
                        person.setServerStatus(p.optBoolean("confirmed", false), p.optString("confidence", ""));
                    }
                    addSources(person.sources, p.optJSONArray("sources"));
                    out.people.add(person);
                }
            }
            addSources(out.sources, root.optJSONArray("sources"));
            if (out.orgName == null || out.orgName.isBlank()) out.orgName = "Организация по ИНН " + inn;
        } catch (Exception e) {
            out.orgName = "Организация по ИНН " + inn;
            out.error = humanError(e);
        }
        return out;
    }

    private static void parseOrganization(JSONObject org, SearchResult out) {
        if (org == null) return;
        OrganizationInfo o = out.organization;
        o.name = org.optString("name", "");
        o.legalName = org.optString("legalName", "");
        o.ogrn = org.optString("ogrn", "");
        o.kpp = org.optString("kpp", "");
        o.region = org.optString("region", "");
        o.legalAddress = org.optString("legalAddress", "");
        o.website = org.optString("website", "");
        o.registrationDate = org.optString("registrationDate", "");
        o.createdDate = org.optString("createdDate", "");
        o.age = org.optString("age", "");
        o.staffCount = org.optString("staffCount", "");
        o.foundersCount = org.optString("foundersCount", "");
        o.capital = org.optString("capital", "");
        o.okvedCode = org.optString("okvedCode", "");
        o.okvedText = org.optString("okvedText", "");
        out.orgName = o.name;
    }

    private static JSONObject request(String endpoint, Map<String,String> query, int readTimeout) throws Exception {
        StringBuilder url = new StringBuilder(BASE).append(endpoint);
        if (query != null && !query.isEmpty()) {
            url.append('?');
            boolean first = true;
            for (Map.Entry<String,String> e : query.entrySet()) {
                if (!first) url.append('&');
                first = false;
                url.append(URLEncoder.encode(e.getKey(), "UTF-8"))
                        .append('=')
                        .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
            }
        }

        HttpURLConnection conn = (HttpURLConnection) new URL(url.toString()).openConnection();
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "OrgStaff-Mobile/1.2.0 Android");
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(stream);
            if (body.isBlank()) throw new IOException("Пустой ответ сервера");
            JSONObject root = new JSONObject(body);
            if (status < 200 || status >= 300) {
                throw new IOException(root.optString("error", "HTTP " + status));
            }
            return root;
        } finally {
            conn.disconnect();
        }
    }

    private static Map<String,String> params(String key, String value) {
        LinkedHashMap<String,String> out = new LinkedHashMap<>();
        out.put(key, value);
        return out;
    }

    private static void addStrings(List<String> target, JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            String value = a.optString(i, "").trim();
            if (!value.isEmpty() && !target.contains(value)) target.add(value);
        }
    }

    private static void addSources(List<SourceRef> target, JSONArray a) {
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

    private static String humanError(Exception e) {
        if (e instanceof SocketTimeoutException) return "Сервер поиска не успел ответить. Повторите запрос.";
        String msg = e.getMessage();
        return "Не удалось получить данные: " + (msg == null || msg.isBlank() ? "неизвестная ошибка" : msg);
    }
}
