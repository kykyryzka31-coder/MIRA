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
        SearchResult out = new SearchResult();
        out.inn = inn;
        try {
            if (progress != null) progress.onProgress("Получаю сведения ЕГРЮЛ…");
            JSONObject root = getJson("summary?inn=" + enc(inn), 30000);
            if (!root.optBoolean("ok", false)) {
                out.orgName = "Организация по ИНН " + inn;
                out.error = root.optString("error", "Сервер поиска вернул ошибку.");
                return out;
            }
            if (progress != null) progress.onProgress("Формирую карточку организации…");
            parseSearchResult(out, root, "people");
            return out;
        } catch (SocketTimeoutException e) {
            out.orgName = "Организация по ИНН " + inn;
            out.error = "Сервер поиска не успел ответить. Повторите запрос.";
            return out;
        } catch (Exception e) {
            out.orgName = "Организация по ИНН " + inn;
            out.error = "Не удалось связаться с сервером поиска: " + safe(e);
            return out;
        }
    }

    static PeopleResult loadManagement(String inn) {
        return loadPeople("management?inn=" + enc(inn), "people", 120000);
    }

    static PeopleResult loadEmployees(String inn) {
        return loadPeople("employees?inn=" + enc(inn), "employees", 120000);
    }

    static PersonProfile loadPersonProfile(String inn, String name, String role) {
        PersonProfile out = new PersonProfile();
        try {
            String path = "person?inn=" + enc(inn) + "&name=" + enc(name) + "&role=" + enc(role);
            JSONObject root = getJson(path, 120000);
            if (!root.optBoolean("ok", false)) {
                out.error = root.optString("error", "Не удалось собрать профиль.");
                return out;
            }
            JSONObject p = root.optJSONObject("person");
            if (p == null) {
                out.error = "Профиль не найден.";
                return out;
            }
            out.birthDate = p.optString("birthDate", "");
            if (p.has("age") && !p.isNull("age")) out.age = String.valueOf(p.optInt("age"));
            out.birthplace = p.optString("birthplace", "");
            out.workSince = p.optString("workSince", "");
            out.tenure = p.optString("tenure", "");
            out.privacy = p.optString("privacy", "");
            addStrings(out.education, p.optJSONArray("education"));
            addStrings(out.career, p.optJSONArray("career"));
            addSources(out.sources, p.optJSONArray("sources"));
            return out;
        } catch (Exception e) {
            out.error = e instanceof SocketTimeoutException
                    ? "Глубокий поиск занял слишком много времени. Попробуйте ещё раз."
                    : "Не удалось загрузить публичный профиль: " + safe(e);
            return out;
        }
    }

    private static PeopleResult loadPeople(String path, String key, int timeout) {
        PeopleResult out = new PeopleResult();
        try {
            JSONObject root = getJson(path, timeout);
            if (!root.optBoolean("ok", false)) {
                out.error = root.optString("error", "Сервер поиска вернул ошибку.");
                return out;
            }
            addPeople(out.people, root.optJSONArray(key));
            addSources(out.sources, root.optJSONArray("sources"));
        } catch (Exception e) {
            out.error = e instanceof SocketTimeoutException
                    ? "Поиск занял слишком много времени. Попробуйте ещё раз."
                    : "Не удалось получить данные: " + safe(e);
        }
        return out;
    }

    private static void parseSearchResult(SearchResult out, JSONObject root, String peopleKey) {
        JSONObject org = root.optJSONObject("organization");
        out.orgName = org == null ? null : org.optString("name", null);
        if (org != null) {
            out.organization.legalName = org.optString("legalName", "");
            out.organization.ogrn = org.optString("ogrn", "");
            out.organization.kpp = org.optString("kpp", "");
            out.organization.region = org.optString("region", "");
            out.organization.legalAddress = org.optString("legalAddress", "");
            out.organization.website = org.optString("website", "");
            out.organization.registrationDate = org.optString("registrationDate", "");
            out.organization.createdDate = org.optString("createdDate", "");
            out.organization.age = org.optString("age", "");
            out.organization.staffCount = org.optString("staffCount", "");
            out.organization.foundersCount = org.optString("foundersCount", "");
            out.organization.capital = org.optString("capital", "");
            out.organization.okvedCode = org.optString("okvedCode", "");
            out.organization.okvedText = org.optString("okvedText", "");
        }
        addPeople(out.people, root.optJSONArray(peopleKey));
        addSources(out.sources, root.optJSONArray("sources"));
        if (out.orgName == null || out.orgName.isBlank()) out.orgName = "Организация по ИНН " + out.inn;
    }

    private static void addPeople(List<PersonRecord> target, JSONArray people) {
        if (people == null) return;
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
            target.add(person);
        }
    }

    private static JSONObject getJson(String path, int readTimeout) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "OrgStaff-Mobile/1.2.0 Android");
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? conn.getInputStream() : conn.getErrorStream();
            String body = readAll(stream);
            JSONObject root = body.isBlank() ? new JSONObject() : new JSONObject(body);
            if (status < 200 || status >= 300) {
                if (!root.has("error")) root.put("error", "HTTP " + status);
                root.put("ok", false);
            }
            return root;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void addStrings(List<String> target, JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty() && !target.contains(s)) target.add(s);
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

    private static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); }
        catch (Exception e) { return ""; }
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

    private static String safe(Exception e) {
        String s = e.getMessage();
        return s == null || s.isBlank() ? "неизвестная ошибка" : s;
    }
}
