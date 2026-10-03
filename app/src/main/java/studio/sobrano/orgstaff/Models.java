package studio.sobrano.orgstaff;

import java.util.*;

final class SourceRef {
    final String title;
    final String url;
    final String snippet;
    SourceRef(String title, String url, String snippet) {
        this.title = title == null ? "Источник" : title.trim();
        this.url = url == null ? "" : url.trim();
        this.snippet = snippet == null ? "" : snippet.trim();
    }
    String domain() {
        try { return new java.net.URL(url).getHost().replace("www.", ""); }
        catch (Exception e) { return ""; }
    }
}

final class PersonRecord {
    final String role;
    final String name;
    final List<SourceRef> sources = new ArrayList<>();
    PersonRecord(String role, String name) { this.role = role; this.name = name; }
    boolean confirmed() {
        Set<String> domains = new HashSet<>();
        for (SourceRef s : sources) if (!s.domain().isEmpty()) domains.add(s.domain());
        return domains.size() >= 2;
    }
}

final class SearchResult {
    String inn;
    String orgName;
    String error;
    final List<PersonRecord> people = new ArrayList<>();
    final List<SourceRef> sources = new ArrayList<>();
}
